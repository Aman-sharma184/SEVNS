package com.group_43.sevns;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class TrackCaseActivity extends AppCompatActivity {

    private MapView map;
    private TextView tvStatus, tvEta;
    private EditText edtCaseId;
    private Button btnTrack;

    private FirebaseFirestore db;

    private Marker accidentMarker, driverMarker;
    private Polyline routeLine;

    private final String GRAPH_HOPPER_KEY = "8c9870a4-4437-4685-9517-24cd9f46fdb8";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_track_case);

        map = findViewById(R.id.map);
        tvStatus = findViewById(R.id.tvStatus);
        tvEta = findViewById(R.id.tvEta);
        edtCaseId = findViewById(R.id.edtCaseId);
        btnTrack = findViewById(R.id.btnTrack);

        db = FirebaseFirestore.getInstance();
        setupMap();

        btnTrack.setOnClickListener(v -> {
            String case_id = edtCaseId.getText().toString();
            loadCaseData(case_id);
        });

        String case_id = getIntent().getStringExtra("CASE_ID");

        if (case_id == null || case_id.trim().isEmpty()) {
            case_id = "";
            Toast.makeText(this, "Track your Case!", Toast.LENGTH_SHORT).show();
            edtCaseId.setText(case_id);
            loadCaseData(case_id);
        }
    }

    private void setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        IMapController controller = map.getController();
        controller.setZoom(15.0);
    }

    private void loadCaseData(String case_id) {
        db.collection("Accidents")
                .document(case_id)
                .get()
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) {
                        Toast.makeText(this, "Case not found!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double accidentLat = doc.getDouble("latitude");
                    double accidentLon = doc.getDouble("longitude");
                    String driverId = doc.getString("driverId");
                    String status = doc.getString("status");

                    tvStatus.setText("Status: " + status);

                    GeoPoint accidentPoint = new GeoPoint(accidentLat, accidentLon);
                    showAccidentMarker(accidentPoint);

                    loadDriverLocation(driverId, accidentPoint);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void loadDriverLocation(String driverId, GeoPoint accidentPoint) {

        db.collection("Drivers")
                .whereEqualTo("Driver_ID", driverId)
                .get()
                .addOnSuccessListener(query -> {

                    if (query.isEmpty()) {
                        Toast.makeText(this, "Driver not found!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    DocumentSnapshot doc = query.getDocuments().get(0);

                    double driverLat = doc.getDouble("latitude");
                    double driverLon = doc.getDouble("longitude");

                    GeoPoint driverPoint = new GeoPoint(driverLat, driverLon);
                    showDriverMarker(driverPoint);

                    drawRouteUsingGraphHopper(driverPoint, accidentPoint);

                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showAccidentMarker(GeoPoint point) {

        if (accidentMarker == null)
            accidentMarker = new Marker(map);

        accidentMarker.setPosition(point);
        accidentMarker.setTitle("Accident Location");
        accidentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        if (!map.getOverlays().contains(accidentMarker))
            map.getOverlays().add(accidentMarker);

        map.getController().animateTo(point);
        map.invalidate();
    }

    private void showDriverMarker(GeoPoint point) {

        if (driverMarker == null)
            driverMarker = new Marker(map);

        driverMarker.setPosition(point);
        driverMarker.setTitle("Ambulance Driver");
        driverMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        Bitmap bmp = BitmapFactory.decodeResource(getResources(), R.drawable.ambulance);
        Bitmap resized = Bitmap.createScaledBitmap(bmp, 70, 70, true);
        Drawable icon = new BitmapDrawable(getResources(), resized);

        driverMarker.setIcon(icon);

        if (!map.getOverlays().contains(driverMarker))
            map.getOverlays().add(driverMarker);

        map.invalidate();
    }

    private void drawRouteUsingGraphHopper(GeoPoint start, GeoPoint end) {

        new Thread(() -> {
            try {
                String urlStr =
                        "https://graphhopper.com/api/1/route?" +
                                "point=" + start.getLatitude() + "," + start.getLongitude() +
                                "&point=" + end.getLatitude() + "," + end.getLongitude() +
                                "&profile=car&locale=en&calc_points=true&points_encoded=false" +
                                "&key=" + GRAPH_HOPPER_KEY;

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.connect();

                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));

                StringBuilder sb = new StringBuilder();
                String line;

                while ((line = br.readLine()) != null)
                    sb.append(line);

                br.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                JSONArray paths = json.getJSONArray("paths");

                if (paths.length() == 0) return;

                JSONObject path = paths.getJSONObject(0);
                long timeMs = path.getLong("time");
                float minutes = timeMs / 1000f / 60f;

                JSONArray coords = path.getJSONObject("points").getJSONArray("coordinates");

                List<GeoPoint> points = new ArrayList<>();

                for (int i = 0; i < coords.length(); i++) {
                    JSONArray c = coords.getJSONArray(i);
                    points.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
                }

                runOnUiThread(() -> {
                    drawPolyline(points);
                    tvEta.setText("ETA: " + String.format("%.1f", minutes) + " min");
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Routing failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void drawPolyline(List<GeoPoint> points) {

        if (routeLine != null)
            map.getOverlays().remove(routeLine);

        routeLine = new Polyline();
        routeLine.setPoints(points);
        routeLine.setColor(Color.BLUE);
        routeLine.setWidth(10);

        map.getOverlays().add(routeLine);
        map.invalidate();
    }
}
