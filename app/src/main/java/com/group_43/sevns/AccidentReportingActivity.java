package com.group_43.sevns;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

public class AccidentReportingActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private EditText editPhone, editDesc;
    private TextView tvLocation, tvStatus;
    private Button btnReport;
    private FusedLocationProviderClient fusedLocationClient;
    private Location lastKnownLocation;
    private String currentReportId;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.accident_reporting);

        editPhone = findViewById(R.id.editPhone);
        editDesc = findViewById(R.id.editDesc);
        tvLocation = findViewById(R.id.tvLocation);
        tvStatus = findViewById(R.id.tvStatus);
        btnReport = findViewById(R.id.btnReport);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        btnReport.setEnabled(false);
        checkLocationPermissionAndFetch();

        btnReport.setOnClickListener(v -> submitReport());
    }

    private void checkLocationPermissionAndFetch() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        } else {
            fetchLastLocation();
        }
    }

    private void fetchLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            tvLocation.setText("Location permission required to proceed.");
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                lastKnownLocation = location;
                tvLocation.setText(String.format("Location: %.6f, %.6f",
                        location.getLatitude(), location.getLongitude()));
                btnReport.setEnabled(true);
            } else {
                tvLocation.setText("Location not found. Please enable GPS.");
                btnReport.setEnabled(false);
            }
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLastLocation();
            } else {
                Toast.makeText(this, "Location permission denied. Cannot report accident.", Toast.LENGTH_LONG).show();
                tvLocation.setText("Location permission denied.");
                btnReport.setEnabled(false);
            }
        }
    }

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

        AccidentReport report = new AccidentReport(
                lastKnownLocation.getLatitude(),
                lastKnownLocation.getLongitude(),
                phone,
                description
        );

        // TODO: Switch to Firebase later if needed; for now, simulated DB
        SimulatedDatabase.saveReport(report);
        currentReportId = report.getId();
        tvStatus.setText("Status: Report submitted! ID: " + currentReportId.substring(0, 8));
        btnReport.setEnabled(false);

        Intent intent = new Intent(this, AccidentStatusActivity.class);
        intent.putExtra("REPORT_ID", currentReportId);
        startActivity(intent);
        finish();
    }
}
