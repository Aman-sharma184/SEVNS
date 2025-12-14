package com.group_43.sevns;
import android.app.Activity;
import android.widget.Toast;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class HospitalFinder {

    private static final int INITIAL_RADIUS_KM = 10;
    private static final int RADIUS_INCREMENT_KM = 5;
    private static final int MAX_RADIUS_KM = 50;

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
                                    "Nearest hospital found at " + nearestDist + " km",
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
                                        "No hospital available within 50 km",
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
                .update("assignedHospitalId", hospitalId,
                        "status", "Pending");
    }

    private void updateStatusNoHospital(FirebaseFirestore db, String caseId) {
        db.collection("Accidents")
                .document(caseId)
                .update("status", "No hospital available within 50 km")
                .addOnSuccessListener(aVoid -> {
                })
                .addOnFailureListener(e -> {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity,
                                    "Failed to update status: " + e.getMessage(),
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