package com.group_43.sevns;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.Priority;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;

public class AccidentReportingActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int INITIAL_RADIUS_KM = 10;
    private static final int RADIUS_INCREMENT_KM = 5;
    private static final int MAX_RADIUS_KM = 100;

    private TextView addressTextView, tvStatus;
    private EditText editPhone, editDesc;
    private Button btnReport;
    private FusedLocationProviderClient fusedLocationClient;
    private Location lastKnownLocation;
    private LocationCallback locationCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.accident_reporting);

        // ---------- UI INITIALIZATION ----------
        addressTextView = findViewById(R.id.addressTextView);
        tvStatus = findViewById(R.id.tvStatus);
        editPhone = findViewById(R.id.editPhone);
        editDesc = findViewById(R.id.editDesc);
        btnReport = findViewById(R.id.btnReport);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        btnReport.setEnabled(false);

        setupLocationCallback();
        requestLocationPermission();
        btnReport.setOnClickListener(v -> submitReport());
    }

    private void requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        } else {
            startLocationUpdates();
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            boolean granted = grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (granted) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Location permission denied. Cannot report accident.", Toast.LENGTH_LONG).show();
                addressTextView.setText("Location permission denied.");
                btnReport.setEnabled(false);
            }
        }
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;

                lastKnownLocation = locationResult.getLastLocation();
                if (lastKnownLocation != null) {
                    double lat = lastKnownLocation.getLatitude();
                    double lon = lastKnownLocation.getLongitude();
                    getAddressFromCoordinates(lat, lon);
                }
            }
        };
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest =
                new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        1000
                )
                        .setMinUpdateIntervalMillis(500)
                        .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
            return;

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
    }

    @SuppressLint("SetTextI18n")
    private void getAddressFromCoordinates(double lat, double lon) {
        new Thread(() -> {
            try {
                String urlStr = "https://nominatim.openstreetmap.org/reverse?lat=" + lat + "&lon=" + lon + "&format=json";
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "HospitalApp/1.0");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null)
                    result.append(line);

                reader.close();

                JSONObject json = new JSONObject(result.toString());
                String address = json.optString("display_name");

                runOnUiThread(() -> {
                    addressTextView.setText(address);
                    btnReport.setEnabled(true);
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                });

            } catch (Exception e) {
                runOnUiThread(() -> addressTextView.setText("Error fetching address: " + e.getMessage()));
            }
        }).start();
    }

    private void assignToHospital(FirebaseFirestore db, String caseId, AccidentReport accidentData, String hospitalId) {
        accidentData.setAssignedHospitalId(hospitalId);
        accidentData.setStatus("pending");

        db.collection("Accidents")
                .document(caseId)
                .set(accidentData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Report sent to nearest hospital!", Toast.LENGTH_SHORT).show();
                    navigateToStatus(caseId);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @SuppressLint("SetTextI18n")
    private void markAsNoHospitalAvailable(FirebaseFirestore db, String caseId, AccidentReport accidentData) {
        accidentData.setStatus("no_hospital_available");

        db.collection("Accidents")
                .document(caseId)
                .set(accidentData)
                .addOnSuccessListener(aVoid -> {
                    tvStatus.setText("Status: No hospital available within 100 km");
                    Toast.makeText(this, "No hospital available in your area", Toast.LENGTH_LONG).show();
                    navigateToStatus(caseId);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private float calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0] / 1000; // Convert meters to km
    }

    private static String generateCaseId() {
        int randomNum = (int) (Math.random() * 90000) + 10000;
        return "CASE" + randomNum;
    }

    private void createUniqueCaseId(FirebaseFirestore db, OnCaseIdGenerated callback) {
        String newCaseId = generateCaseId();

        db.collection("Accidents")
                .whereEqualTo("id", newCaseId)
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        callback.onGenerated(newCaseId);
                    } else {
                        createUniqueCaseId(db, callback);
                    }
                })
                .addOnFailureListener(e -> callback.onGenerated(null));
    }

    public interface OnCaseIdGenerated {
        void onGenerated(String caseId);
    }

    @SuppressLint("SetTextI18n")
    private void submitReport() {
        if (lastKnownLocation == null) {
            Toast.makeText(this, "Please wait for location to be fetched.", Toast.LENGTH_SHORT).show();
            return;
        }

        String phone = editPhone.getText().toString().trim();
        String description = editDesc.getText().toString().trim();

        if (phone.isEmpty() || description.isEmpty()) {
            Toast.makeText(this, "Please enter phone number and description.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        createUniqueCaseId(db, caseId -> {
            if (caseId == null) {
                Toast.makeText(this, "Error generating Case ID!", Toast.LENGTH_SHORT).show();
                return;
            }

            AccidentReport data = new AccidentReport(
                    caseId,
                    lastKnownLocation.getLatitude(),
                    lastKnownLocation.getLongitude(),
                    phone,
                    description,
                    addressTextView.getText().toString(),
                    "",
                    "",
                    System.currentTimeMillis(),
                    "searching",
                    false,
                    ""
            );

            tvStatus.setText("Status: Searching for nearest hospital...");
            btnReport.setEnabled(false);
            HospitalFinder finder = new HospitalFinder(this);
            List<String> excludedHospitals = Collections.emptyList();
            finder.findNearestHospital(caseId, data, excludedHospitals);
            navigateToStatus(caseId);
            db.collection("Accidents")
                    .document(caseId)
                    .set(data)
                    .addOnSuccessListener(documentReference ->
                            Toast.makeText(this, "Data sent to Nearest Hospital!", Toast.LENGTH_SHORT).show()
                    )
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
            navigateToStatus(caseId);
        });
    }

    private void navigateToStatus(String caseId) {
        Intent intent = new Intent(this, TrackCaseActivity.class);
        intent.putExtra("CASE_ID", caseId);
        startActivity(intent);
        finish();
    }

}