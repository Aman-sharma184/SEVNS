package com.group_43.sevns;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HospitalDashboardActivity extends AppCompatActivity {

    private ListView listReports;
    private Button btnsignOut;
    private TextView tvNoReports;
    private TextView tvCaseCount, hospitalname;
    private LinearLayout emptyStateContainer;
    private List<AccidentReport> assignedReports;
    private ArrayAdapter<AccidentReport> adapter;
    private String currentHospitalId;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hospital_dashboard);

        listReports = findViewById(R.id.listReports);
        btnsignOut = findViewById(R.id.btnsignOut);
        tvNoReports = findViewById(R.id.tvNoReports);
        tvCaseCount = findViewById(R.id.tvCaseCount);
        hospitalname = findViewById(R.id.hospital_name);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);

        assignedReports = new ArrayList<>();

        SharedPreferences prefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);
        currentHospitalId = prefs.getString("userId", "");

        if (currentHospitalId.isEmpty() && FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentHospitalId = "Hospital-" + FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                assignedReports
        );
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Hospitals")
                .document(currentHospitalId)
                .get()
                .addOnSuccessListener(hospitalDoc -> {
                            String hospitalName = hospitalDoc.getString("name");
                            hospitalname.setText(hospitalName);
                        }
                );


        listReports.setAdapter(adapter);

        fetchAssignedReports();

        btnsignOut.setOnClickListener(v -> signOut());

        listReports.setOnItemClickListener((parent, view, position, id) -> {
            AccidentReport selectedReport = assignedReports.get(position);
            showReportActionsDialog(selectedReport);
        });
    }

    private void signOut() {
        try {
            SharedPreferences prefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            editor.apply();

            FirebaseAuth.getInstance().signOut();

            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e("HOSPITAL", "Error during sign out", e);
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }


    private void fetchAssignedReports() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Accidents")
                .whereEqualTo("assignedHospitalId", currentHospitalId)
                .addSnapshotListener((value, error) -> {

                    if (error != null) {
                        Log.e("HOSPITAL", "Error fetching reports: " + error.getMessage());
                        return;
                    }

                    assignedReports.clear();

                    if (value != null && !value.isEmpty()) {
                        for (DocumentSnapshot doc : value) {
                            AccidentReport accident = doc.toObject(AccidentReport.class);
                            if (accident != null) {
                                String status = accident.getStatus();
                                if (status != null &&
                                        (status.equalsIgnoreCase("Pending") ||
                                                status.equalsIgnoreCase("Acknowledged") ||
                                                status.equalsIgnoreCase("Reached at Location"))) {
                                    assignedReports.add(accident);
                                }
                            }
                        }
                    }

                    adapter.notifyDataSetChanged();

                    if (tvCaseCount != null) {
                        tvCaseCount.setText(String.valueOf(assignedReports.size()));
                    }

                    if (emptyStateContainer != null) {
                        emptyStateContainer.setVisibility(assignedReports.isEmpty() ?
                                View.VISIBLE : View.GONE);
                    }

                    Log.d("HOSPITAL", "Assigned reports count: " + assignedReports.size());
                    Log.d("HOSPITAL", "Current Hospital ID: " + currentHospitalId);
                });
    }

    private double getDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0] / 1000;
    }

    private void showReportActionsDialog(AccidentReport report) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Hospitals")
                .document(currentHospitalId)
                .get()
                .addOnSuccessListener(hospitalDoc -> {
                    Double hospitalLat = hospitalDoc.getDouble("latitude");
                    Double hospitalLon = hospitalDoc.getDouble("longitude");

                    String distanceText = "";
                    if (hospitalLat != null && hospitalLon != null) {
                        double distance = getDistance(
                                hospitalLat, hospitalLon,
                                report.getLatitude(), report.getLongitude()
                        );
                        distanceText = "\n\nDistance: " + String.format("%.2f", distance) + " km";
                    }

                    String formattedTime = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            .format(new Date(report.getTimestamp()));

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Case ID: " + report.getId())
                            .setMessage(
                                    "Status: " + report.getStatus() + "\nDriver ID: " + report.getdriverId() +
                                            "\n\nDescription:\n" + report.getDescription() +
                                            "\n\nContact: " + report.getPhoneNumber() +
                                            "\n\nTime: " + formattedTime +
                                            distanceText +
                                            "\n\nLocation:\n" + report.getAddress())
                            .setNeutralButton("Close", (dialog, id) -> dialog.dismiss())
                            .setPositiveButton("Track", (dialog, id) ->
                                    Trackcase(report.getId())
                            );

                    if ("pending".equalsIgnoreCase(report.getStatus())) {
                        builder.setPositiveButton("Accept", (dialog, id) ->
                                acceptCase(report.getId())
                        );
                        builder.setNegativeButton("Decline", (dialog, id) ->
                                declineCase(report.getId())
                        );
                    }

                    builder.create().show();
                });
    }

    private void Trackcase(String caseId) {
        Intent intent = new Intent(this, TrackCaseActivity.class);
        intent.putExtra("CASE_ID", caseId);
        startActivity(intent);
    }

    private void acceptCase(String caseId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Find an available driver
        db.collection("Drivers")
                .whereEqualTo("status", "Available")
                .limit(1)
                .get()
                .addOnSuccessListener(driverSnap -> {
                    if (!driverSnap.isEmpty()) {
                        DocumentSnapshot driverDoc = driverSnap.getDocuments().get(0);
                        String driverDocId = driverDoc.getId();
                        String driverId = driverDoc.getString("Driver_ID");

                        // Update accident info
                        db.collection("Accidents")
                                .document(caseId)
                                .update(
                                        "status", "Acknowledged",
                                        "driverId", driverId,
                                        "hospitalId", currentHospitalId
                                )
                                .addOnSuccessListener(unused -> {
                                    db.collection("Drivers")
                                            .document(driverDocId)
                                            .update("status", "Unavailable")
                                            .addOnSuccessListener(u ->
                                                    Toast.makeText(this,
                                                            "Case accepted! Driver assigned.",
                                                            Toast.LENGTH_SHORT).show())
                                            .addOnFailureListener(e ->
                                                    Toast.makeText(this,
                                                            "Driver update failed: " + e.getMessage(),
                                                            Toast.LENGTH_SHORT).show());
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this,
                                                "Failed to accept case: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show());

                    } else {
                        Toast.makeText(this, "No available driver found!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Driver search failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void declineCase(String caseId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Accidents")
                .document(caseId)
                .update("declinedHospitals", currentHospitalId,
                        "assignedHospitalId", ""
                )
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this,
                            "Case declined. Searching for another hospital...",
                            Toast.LENGTH_SHORT).show();
                    HospitalFinder finder = new HospitalFinder(this);
                    List<String> excludedHospitals = Collections.emptyList();
                    AccidentReport AccidentReport = new AccidentReport();
                    finder.findNearestHospital(caseId, AccidentReport, excludedHospitals);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to decline case: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }


}