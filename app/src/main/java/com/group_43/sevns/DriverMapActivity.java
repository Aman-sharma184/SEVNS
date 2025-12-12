package com.group_43.sevns;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DriverMapActivity extends AppCompatActivity {

    private MapView map;
    private TextView tvEta, tvStatus;
    private Button btnComplete, btndriverSignOut;
    private String DocumentId;
    private AccidentReport assignedReport;

    private MyLocationNewOverlay myLocationOverlay;
    private Polyline roadOverlay;
    private GeoPoint currentLocation;
    private GeoPoint accidentLocation;
    private GeoPoint hospitalLocation;

    private static final String GH_API_KEY = "8c9870a4-4437-4685-9517-24cd9f46fdb8";
    private static final String GH_BASE_URL = "https://graphhopper.com/api/1/route";

    private static final int REQUEST_PERMISSIONS_CODE = 1;
    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private boolean isFirstLocationUpdate = true;
    private Handler routeUpdateHandler = new Handler(Looper.getMainLooper());
    private static final long ROUTE_UPDATE_INTERVAL = 30000;
    private static final float MIN_DISTANCE_FOR_UPDATE = 50f;
    private GeoPoint lastRouteUpdateLocation;
    private String currentDriverId, DriverId;

    private boolean isRouteBeingCalculated = false;

    // Journey states
    private enum JourneyState {
        TO_ACCIDENT,
        AT_ACCIDENT,
        TO_HOSPITAL,
        COMPLETED
    }

    private JourneyState currentState = JourneyState.TO_ACCIDENT;
    private static final float ARRIVAL_THRESHOLD = 100f; // 100 meters

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.driver_screen);

        tvEta = findViewById(R.id.tvEta);
        tvStatus = findViewById(R.id.tvStatus);
        btnComplete = findViewById(R.id.btnComplete);
        btndriverSignOut = findViewById(R.id.btndriverSignOut);
        map = findViewById(R.id.map);

        currentDriverId = "Driver-" + FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("Drivers")
                .document(currentDriverId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        DriverId = doc.getString("Driver_ID");
                        findAndDisplayAssignedReport();
                    }
                });

        if (map == null || tvEta == null || btnComplete == null) {
            Toast.makeText(this, "Error: UI components not found", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        requestPermissionsIfNecessary(REQUIRED_PERMISSIONS);

        btnComplete.setOnClickListener(v -> handleActionButton());
        btnComplete.setEnabled(false);

        if (btndriverSignOut != null) {
            btndriverSignOut.setOnClickListener(v -> signOut());
        }
    }

    private void handleActionButton() {
        switch (currentState) {
            case AT_ACCIDENT:
                markAccidentReached();
                break;
            case TO_HOSPITAL:
                Toast.makeText(this, "Please reach the hospital to complete", Toast.LENGTH_SHORT).show();
                break;
            case COMPLETED:
                markReportCompleted();
                break;
        }
    }

    @SuppressLint("SetTextI18n")
    private void findAndDisplayAssignedReport() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Accidents")
                .whereEqualTo("status", "Acknowledged")
                .whereEqualTo("driverId", DriverId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        DocumentSnapshot document = task.getResult().getDocuments().get(0);
                        assignedReport = document.toObject(AccidentReport.class);
                        DocumentId = document.getId();

                        if (assignedReport != null) {
                            tvEta.setText("Accident Location Found! Plotting route...");
                            btnComplete.setText("Mark Reached");
                            btnComplete.setEnabled(false);
                            currentState = JourneyState.TO_ACCIDENT;
                            tvStatus.setText("Now heading to accident location...");

                            fetchHospitalLocation(assignedReport.getAssignedHospitalId());
                            setupMapAndRoute();
                        } else {
                            tvEta.setText("Error loading accident data.");
                            btnComplete.setEnabled(false);
                            setupLiveLocationOnly(); // Fallback to live location
                        }
                    } else {
                        // NO CASE ASSIGNED - Show only live location
                        tvEta.setText("No assigned case.");
                        tvStatus.setText("Available - Live tracking active");
                        Toast.makeText(this, "No assigned case. Live location active.", Toast.LENGTH_LONG).show();
                        btnComplete.setEnabled(false);
                        btnComplete.setText("No Active Case");

                        setupLiveLocationOnly();
                    }
                })
                .addOnFailureListener(e -> {
                    tvEta.setText("Error loading data.");
                    Toast.makeText(this, "Error fetching data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    setupLiveLocationOnly(); // Fallback to live location on error
                });
    }

    private void fetchHospitalLocation(String hospitalId) {
        if (hospitalId == null || hospitalId.isEmpty()) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("Hospitals")
                .document(hospitalId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Double lat = doc.getDouble("latitude");
                        Double lon = doc.getDouble("longitude");

                        if (lat != null && lon != null) {
                            hospitalLocation = new GeoPoint(lat, lon);
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error fetching hospital location", Toast.LENGTH_SHORT).show()
                );
    }

    private void setupMapAndRoute() {
        if (assignedReport == null || map == null) return;

        try {
            map.setTileSource(TileSourceFactory.MAPNIK);
            map.setBuiltInZoomControls(true);
            map.setMultiTouchControls(true);

            IMapController ctl = map.getController();
            ctl.setZoom(15.0);

            accidentLocation = new GeoPoint(
                    assignedReport.getLatitude(),
                    assignedReport.getLongitude()
            );
            ctl.setCenter(accidentLocation);

            Marker accidentMarker = new Marker(map);
            accidentMarker.setPosition(accidentLocation);
            accidentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            accidentMarker.setTitle("Accident: " + assignedReport.getDescription()
                    + "\n\n" + assignedReport.getAddress());
            map.getOverlays().add(accidentMarker);

            setupLocationTracking();

            map.invalidate();
        } catch (Exception e) {
            Toast.makeText(this, "Error setting up map: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void setupLiveLocationOnly() {
        try {
            map.setTileSource(TileSourceFactory.MAPNIK);
            map.setBuiltInZoomControls(true);
            map.setMultiTouchControls(true);

            IMapController ctl = map.getController();
            ctl.setZoom(15.0);

            // Clear any existing overlays
            map.getOverlays().clear();

            setupLocationTrackingNoRoute();

            map.invalidate();
        } catch (Exception e) {
            Toast.makeText(this, "Error setting up live location: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupLocationTracking() {
        try {
            GpsMyLocationProvider gpsProvider = new GpsMyLocationProvider(this);
            gpsProvider.setLocationUpdateMinTime(2000);
            gpsProvider.setLocationUpdateMinDistance(5);

            myLocationOverlay = new MyLocationNewOverlay(gpsProvider, map);
            myLocationOverlay.enableMyLocation();
            myLocationOverlay.enableFollowLocation();
            Bitmap original = BitmapFactory.decodeResource(getResources(), R.drawable.ambulance);

            int iconWidth = 70;
            int iconHeight = 70;

            Bitmap resizedIcon = Bitmap.createScaledBitmap(original, iconWidth, iconHeight, true);
            myLocationOverlay.setDirectionIcon(resizedIcon);

            myLocationOverlay.runOnFirstFix(() -> {
                Location location = myLocationOverlay.getLastFix();
                if (location != null) {
                    runOnUiThread(() -> onLocationUpdate(location));
                }
            });

            map.getOverlays().add(myLocationOverlay);

            startLocationUpdateTimer();

        } catch (Exception e) {
            Toast.makeText(this, "Error setting up location: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void setupLocationTrackingNoRoute() {
        try {
            GpsMyLocationProvider gpsProvider = new GpsMyLocationProvider(this);
            gpsProvider.setLocationUpdateMinTime(2000);
            gpsProvider.setLocationUpdateMinDistance(5);

            myLocationOverlay = new MyLocationNewOverlay(gpsProvider, map);
            myLocationOverlay.enableMyLocation();
            myLocationOverlay.enableFollowLocation();

            Bitmap original = BitmapFactory.decodeResource(getResources(), R.drawable.ambulance);
            int iconWidth = 70;
            int iconHeight = 70;
            Bitmap resizedIcon = Bitmap.createScaledBitmap(original, iconWidth, iconHeight, true);
            myLocationOverlay.setDirectionIcon(resizedIcon);

            myLocationOverlay.runOnFirstFix(() -> {
                Location location = myLocationOverlay.getLastFix();
                if (location != null) {
                    runOnUiThread(() -> onLiveLocationUpdate(location));
                }
            });

            map.getOverlays().add(myLocationOverlay);
            startLiveLocationTimer();

        } catch (Exception e) {
            Toast.makeText(this, "Error setting up location tracking: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startLocationUpdateTimer() {
        routeUpdateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (myLocationOverlay != null) {
                    Location location = myLocationOverlay.getLastFix();
                    if (location != null) {
                        onLocationUpdate(location);
                    }
                }
                routeUpdateHandler.postDelayed(this, 5000);
            }
        }, 5000);
    }

    private void startLiveLocationTimer() {
        routeUpdateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (myLocationOverlay != null) {
                    Location location = myLocationOverlay.getLastFix();
                    if (location != null) {
                        onLiveLocationUpdate(location);
                    }
                }
                routeUpdateHandler.postDelayed(this, 5000);
            }
        }, 5000);
    }

    private void onLiveLocationUpdate(Location location) {
        if (location == null) return;

        currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());

        updateDriverLocationInFirestore(currentLocation);

            tvStatus.setText("Live Location Active");
    }

    private void onLocationUpdate(Location location) {
        if (location == null || isRouteBeingCalculated) return;

        if (assignedReport == null) {
            onLiveLocationUpdate(location);
            return;
        }

        currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());

        updateDriverLocationInFirestore(currentLocation);

        switch (currentState) {
            case TO_ACCIDENT:
                if (accidentLocation != null) {
                    float distanceToAccident = (float) currentLocation.distanceToAsDouble(accidentLocation);

                    if (distanceToAccident <= ARRIVAL_THRESHOLD) {
                        currentState = JourneyState.AT_ACCIDENT;
                        btnComplete.setEnabled(true);
                        btnComplete.setText("Mark Reached");
                        tvStatus.setText("You have arrived at the accident location!");
                        Toast.makeText(this, "Arrived at accident location!", Toast.LENGTH_LONG).show();
                    }
                }
                break;

            case TO_HOSPITAL:
                if (hospitalLocation != null) {
                    float distanceToHospital = (float) currentLocation.distanceToAsDouble(hospitalLocation);

                    if (distanceToHospital <= ARRIVAL_THRESHOLD) {
                        currentState = JourneyState.COMPLETED;
                        btnComplete.setEnabled(true);
                        btnComplete.setText("Mark Complete");
                        tvStatus.setText("Arrived at hospital! Click to complete case.");
                        Toast.makeText(this, "Arrived at hospital!", Toast.LENGTH_LONG).show();
                    }
                }
                break;
        }

        if (isFirstLocationUpdate) {
            isFirstLocationUpdate = false;
            lastRouteUpdateLocation = currentLocation;

            GeoPoint destination = (currentState == JourneyState.TO_HOSPITAL) ? hospitalLocation : accidentLocation;

            if (destination != null) {
                ArrayList<GeoPoint> points = new ArrayList<>();
                points.add(currentLocation);
                points.add(destination);
                BoundingBox box = BoundingBox.fromGeoPoints(points);
                map.zoomToBoundingBox(box, true);

                tvEta.setText("Calculating route from your location...");
                calculateRoute(currentLocation, destination);
            }
        } else {
            if (shouldUpdateRoute(currentLocation)) {
                lastRouteUpdateLocation = currentLocation;
                GeoPoint destination = (currentState == JourneyState.TO_HOSPITAL) ? hospitalLocation : accidentLocation;

                if (destination != null) {
                    tvEta.setText("Recalculating route...");
                    calculateRoute(currentLocation, destination);
                }
            }
        }
    }

    private void markAccidentReached() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (assignedReport == null || DocumentId == null) {
            Toast.makeText(this, "No assigned case.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("Accidents")
                .document(DocumentId)
                .update("status", "Reached at Location")
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Marked as reached accident location!", Toast.LENGTH_SHORT).show();

                    currentState = JourneyState.TO_HOSPITAL;
                    btnComplete.setText("Mark Complete");
                    btnComplete.setEnabled(false); // Will enable when close to hospital
                    tvStatus.setText("Now heading to hospital...");

                    map.getOverlays().clear();

                    if (hospitalLocation != null) {
                        Marker hospitalMarker = new Marker(map);
                        hospitalMarker.setPosition(hospitalLocation);
                        hospitalMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                        hospitalMarker.setTitle("Hospital Destination");
                        map.getOverlays().add(hospitalMarker);

                        map.getOverlays().add(myLocationOverlay);

                        isFirstLocationUpdate = true;
                        if (currentLocation != null) {
                            calculateRoute(currentLocation, hospitalLocation);
                        }

                        map.invalidate();
                    } else {
                        Toast.makeText(this, "Hospital location not available", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error updating status: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private boolean shouldUpdateRoute(GeoPoint newLocation) {
        if (lastRouteUpdateLocation == null) return true;

        float distance = (float) lastRouteUpdateLocation.distanceToAsDouble(newLocation);
        return distance >= MIN_DISTANCE_FOR_UPDATE;
    }

    private void calculateRoute(GeoPoint start, GeoPoint end) {
        if (isRouteBeingCalculated) return;
        isRouteBeingCalculated = true;
        new Thread(() -> {
            fetchGraphHopperRoute(start, end);
            isRouteBeingCalculated = false;
        }).start();
    }

    private void fetchGraphHopperRoute(GeoPoint start, GeoPoint end) {
        if (start == null || end == null) {
            showStraightLineFallback(end, start);
            runOnUiThread(() -> tvEta.setText("ETA: unavailable (invalid locations)"));
            return;
        }

        OkHttpClient client = new OkHttpClient();

        String url = GH_BASE_URL
                + "?point=" + start.getLatitude() + "," + start.getLongitude()
                + "&point=" + end.getLatitude() + "," + end.getLongitude()
                + "&vehicle=car&locale=en&instructions=false&points_encoded=false"
                + "&key=" + GH_API_KEY;

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                final String errorMsg = "HTTP " + response.code() + ": " + response.message();
                runOnUiThread(() -> Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show());
                showStraightLineFallback(end, start);
                runOnUiThread(() -> tvEta.setText("ETA: unavailable (using straight line)"));
                return;
            }

            String body = response.body() != null ? response.body().string() : "";

            if (body.isEmpty()) {
                showStraightLineFallback(end, start);
                runOnUiThread(() -> tvEta.setText("ETA: unavailable (empty response)"));
                return;
            }

            JSONObject json = new JSONObject(body);
            JSONArray paths = json.getJSONArray("paths");

            if (paths.length() == 0) {
                showStraightLineFallback(end, start);
                runOnUiThread(() -> tvEta.setText("ETA: unavailable (no route found)"));
                return;
            }

            JSONObject firstPath = paths.getJSONObject(0);
            JSONObject points = firstPath.getJSONObject("points");
            JSONArray coords = points.getJSONArray("coordinates");

            ArrayList<GeoPoint> routePoints = new ArrayList<>();
            for (int i = 0; i < coords.length(); i++) {
                JSONArray c = coords.getJSONArray(i);
                double lon = c.getDouble(0);
                double lat = c.getDouble(1);
                routePoints.add(new GeoPoint(lat, lon));
            }

            final int minutes;
            final double distanceKm;
            if (firstPath.has("time")) {
                long timeMs = firstPath.getLong("time");
                minutes = (int) (timeMs / 1000 / 60);
            } else {
                minutes = -1;
            }

            if (firstPath.has("distance")) {
                distanceKm = firstPath.getDouble("distance") / 1000.0;
            } else {
                distanceKm = -1;
            }

            runOnUiThread(() -> {
                if (map == null) return;

                if (roadOverlay != null) {
                    map.getOverlays().remove(roadOverlay);
                }
                roadOverlay = new Polyline(map);
                roadOverlay.setPoints(routePoints);
                roadOverlay.setWidth(10f);
                roadOverlay.setColor(0xFF2196F3);
                map.getOverlays().add(roadOverlay);
                map.invalidate();

                String destination = (currentState == JourneyState.TO_HOSPITAL) ? "hospital" : "accident";

                if (minutes >= 0 && distanceKm >= 0) {
                    tvEta.setText(String.format("ETA to %s: ~%d mins (%.1f km)", destination, minutes, distanceKm));
                } else if (minutes >= 0) {
                    tvEta.setText("ETA to " + destination + ": ~" + minutes + " mins");
                } else {
                    tvEta.setText("ETA: available");
                }
            });

        } catch (IOException ioe) {
            showStraightLineFallback(end, start);
            runOnUiThread(() -> {
                if (tvEta != null) {
                    tvEta.setText("ETA: unavailable");
                }
                Toast.makeText(DriverMapActivity.this,
                        "Network error: " + ioe.getMessage(),
                        Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            showStraightLineFallback(end, start);
            runOnUiThread(() -> {
                if (tvEta != null) {
                    tvEta.setText("ETA: unavailable");
                }
                Toast.makeText(DriverMapActivity.this,
                        "Routing error: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void showStraightLineFallback(GeoPoint accidentPoint, GeoPoint myLoc) {
        if (accidentPoint == null || myLoc == null) return;

        runOnUiThread(() -> {
            if (map == null) return;

            if (roadOverlay != null) {
                map.getOverlays().remove(roadOverlay);
            }
            roadOverlay = new Polyline(map);
            ArrayList<GeoPoint> pts = new ArrayList<>();
            pts.add(myLoc);
            pts.add(accidentPoint);
            roadOverlay.setPoints(pts);
            roadOverlay.setWidth(8f);
            roadOverlay.setColor(0xFFFF0000);
            map.getOverlays().add(roadOverlay);
            map.invalidate();
        });
    }

    private void markReportCompleted() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (assignedReport == null || DocumentId == null) {
            Toast.makeText(this,
                    "No assigned case to complete.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("Accidents")
                .document(DocumentId)
                .update("status", "Completed")
                .addOnSuccessListener(unused -> {
                    db.collection("Drivers")
                            .whereEqualTo("Driver_ID", currentDriverId)
                            .get()
                            .addOnSuccessListener(querySnapshot -> {
                                if (!querySnapshot.isEmpty()) {
                                    String driverDocId = querySnapshot.getDocuments().get(0).getId();
                                    db.collection("Drivers")
                                            .document(driverDocId)
                                            .update("status", "Available")
                                            .addOnSuccessListener(u -> {
                                                Toast.makeText(this,
                                                        "Case completed! You are now available for new cases.",
                                                        Toast.LENGTH_LONG).show();

                                                isFirstLocationUpdate = true;
                                                lastRouteUpdateLocation = null;
                                                assignedReport = null;
                                                DocumentId = null;
                                                roadOverlay = null;
                                                currentState = JourneyState.TO_ACCIDENT;
                                                hospitalLocation = null;

                                                map.getOverlays().clear();
                                                map.getOverlays().add(myLocationOverlay);
                                                map.invalidate();
                                                btnComplete.setEnabled(false);

                                                findAndDisplayAssignedReport();
                                            });
                                }
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void requestPermissionsIfNecessary(String[] permissions) {
        ArrayList<String> req = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) {
                req.add(p);
            }
        }
        if (!req.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    req.toArray(new String[0]),
                    REQUEST_PERMISSIONS_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this,
                        "Map needs Location permission.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) {
            map.onResume();
        }
        if (myLocationOverlay != null) {
            myLocationOverlay.enableMyLocation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) {
            map.onPause();
        }
        if (myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (routeUpdateHandler != null) {
            routeUpdateHandler.removeCallbacksAndMessages(null);
        }
    }

    private void signOut() {
        try {
            SharedPreferences prefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            editor.apply();

            FirebaseAuth.getInstance().signOut();

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("Drivers")
                    .whereEqualTo("Driver_ID", DriverId)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!querySnapshot.isEmpty()) {
                            String driverDocId = querySnapshot.getDocuments().get(0).getId();
                            db.collection("Drivers")
                                    .document(driverDocId)
                                    .update("status", "Unavailable");
                        }
                    });

            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Error signing out: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateDriverLocationInFirestore(GeoPoint location) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Drivers")
                .document(currentDriverId)
                .update(
                        "latitude", location.getLatitude(),
                        "longitude", location.getLongitude()
                );
    }
}
