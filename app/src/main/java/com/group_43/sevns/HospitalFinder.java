package com.group_43.sevns;
import android.app.Activity;
import android.widget.Toast;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.FieldValue;

import java.util.ArrayList;
import java.util.List;

public class HospitalFinder {

    private static final int INITIAL_RADIUS_KM = 10;
    private static final int RADIUS_INCREMENT_KM = 5;
    private static final int MAX_RADIUS_KM = 1000;

    private final Activity activity;

    public HospitalFinder(Activity activity) {
        this.activity = activity;
    }

    public void findNearestHospital(String caseId,
                                    AccidentReport accidentData,
                                    List<String> excludedHospitals) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        findHospitalsInRadius(db, caseId, accidentData, INITIAL_RADIUS_KM, excludedHospitals);
    }

    private void findHospitalsInRadius(FirebaseFirestore db,
                                       String caseId,
                                       AccidentReport accidentData,
                                       int radiusKm,
                                       List<String> excludedHospitals) {

        db.collection("Hospitals")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    List<HospitalDistance> hospitalsInRange = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String hospitalId = doc.getId();

                        if (excludedHospitals.contains(hospitalId)) continue;

                        Double lat = doc.getDouble("latitude");
                        Double lon = doc.getDouble("longitude");

                        if (lat != null && lon != null) {
                            float distance = calculateDistance(
                                    accidentData.getLatitude(),
                                    accidentData.getLongitude(),
                                    lat,
                                    lon
                            );

                            if (distance <= radiusKm) {
                                hospitalsInRange.add(new HospitalDistance(hospitalId, distance));
                            }
                        }
                    }

                    if (!hospitalsInRange.isEmpty()) {

                        hospitalsInRange.sort((h1, h2) -> Float.compare(h1.distance, h2.distance));

                        String nearestId = hospitalsInRange.get(0).hospitalId;
                        float nearestDist = hospitalsInRange.get(0).distance;

                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity,
                                    "Nearest hospital found at " + String.format("%.2f", nearestDist) + " km",
                                    Toast.LENGTH_SHORT).show();
                        });

                        assignToHospital(db, caseId, accidentData, nearestId);

                    } else if (radiusKm < MAX_RADIUS_KM) {

                        int newRadius = radiusKm + RADIUS_INCREMENT_KM;

                        activity.runOnUiThread(() ->
                                Toast.makeText(activity,
                                        "Searching in " + newRadius + " km radius...",
                                        Toast.LENGTH_SHORT).show()
                        );

                        findHospitalsInRadius(db, caseId, accidentData, newRadius, excludedHospitals);

                    } else {
                        activity.runOnUiThread(() ->
                                Toast.makeText(activity,
                                        "No hospital available within 1000 km",
                                        Toast.LENGTH_LONG).show()
                        );

                        updateStatusNoHospital(db, caseId);
                    }

                })
                .addOnFailureListener(e ->
                        Toast.makeText(activity, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private float calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0] / 1000; // meters → km
    }

    private void assignToHospital(FirebaseFirestore db, String caseId, AccidentReport data, String hospitalId) {
        db.collection("Accidents")
                .document(caseId)
                .update(
                        "assignedHospitalId", hospitalId,
                        "status", "Pending",
                        "declinedHospitals", data.getDeclinedHospitals()
                )
                .addOnSuccessListener(aVoid -> {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity,
                                    "Case assigned to hospital successfully",
                                    Toast.LENGTH_SHORT).show()
                    );
                })
                .addOnFailureListener(e -> {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity,
                                    "Failed to assign hospital: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show()
                    );
                });
    }

    private void updateStatusNoHospital(FirebaseFirestore db, String caseId) {
        db.collection("Accidents")
                .document(caseId)
                .update("status", "No hospital available within 1000 km")
                .addOnSuccessListener(aVoid -> {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity,
                                    "No hospitals available in range",
                                    Toast.LENGTH_LONG).show()
                    );
                })
                .addOnFailureListener(e -> {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity,
                                    "Failed to update status: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show()
                    );
                });
    }

    /**
     * Handles hospital decline and finds next nearest hospital
     */
    public void handleHospitalDecline(String caseId, String currentHospitalId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // First, fetch the accident report to get current data
        db.collection("Accidents")
                .document(caseId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        AccidentReport accidentData = documentSnapshot.toObject(AccidentReport.class);

                        if (accidentData != null) {
                            accidentData.addDeclinedHospital(currentHospitalId);

                            List<String> declinedList = (List<String>) documentSnapshot.get("declinedHospitals");
                            if (declinedList != null) {
                                for (String hospitalId : declinedList) {
                                    accidentData.addDeclinedHospital(hospitalId);
                                }
                            }

                            db.collection("Accidents")
                                    .document(caseId)
                                    .update(
                                            "declinedHospitals", FieldValue.arrayUnion(currentHospitalId),
                                            "assignedHospitalId", ""
                                    )
                                    .addOnSuccessListener(aVoid -> {
                                        activity.runOnUiThread(() ->
                                                Toast.makeText(activity,
                                                        "Searching for next nearest hospital...",
                                                        Toast.LENGTH_SHORT).show()
                                        );

                                        findNearestHospital(caseId, accidentData, accidentData.getDeclinedHospitals());
                                    })
                                    .addOnFailureListener(e -> {
                                        activity.runOnUiThread(() ->
                                                Toast.makeText(activity,
                                                        "Failed to update declined hospitals: " + e.getMessage(),
                                                        Toast.LENGTH_SHORT).show()
                                        );
                                    });
                        }
                    } else {
                        activity.runOnUiThread(() ->
                                Toast.makeText(activity,
                                        "Accident report not found",
                                        Toast.LENGTH_SHORT).show()
                        );
                    }
                })
                .addOnFailureListener(e -> {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity,
                                    "Error fetching accident data: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show()
                    );
                });
    }

    private static class HospitalDistance {
        String hospitalId;
        float distance;

        HospitalDistance(String id, float dist) {
            this.hospitalId = id;
            this.distance = dist;
        }
    }
}