// Add this to your AccidentStatusActivity or create a separate service

package com.group_43.sevns;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class AccidentStatusMonitor {

    private FirebaseFirestore db;
    private ListenerRegistration statusListener;

    public AccidentStatusMonitor() {
        this.db = FirebaseFirestore.getInstance();
    }

    public void monitorAccidentStatus(String caseId, StatusUpdateCallback callback) {
        statusListener = db.collection("Accidents")
                .document(caseId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String status = documentSnapshot.getString("status");
                        String assignedHospitalId = documentSnapshot.getString("assignedHospitalId");

                        if ("declined".equals(status) && assignedHospitalId != null) {
                            // Hospital declined, search for next available hospital
                            handleHospitalDecline(caseId, documentSnapshot, assignedHospitalId);
                        } else {
                            callback.onStatusUpdate(status);
                        }
                    }
                });
    }

    private void handleHospitalDecline(String caseId, DocumentSnapshot accidentDoc, String declinedHospitalId) {
        List<String> declinedHospitals = (List<String>) accidentDoc.get("declinedHospitals");
        if (declinedHospitals == null) {
            declinedHospitals = new ArrayList<>();
        }

        if (!declinedHospitals.contains(declinedHospitalId)) {
            declinedHospitals.add(declinedHospitalId);
        }

        Double latitude = accidentDoc.getDouble("latitude");
        Double longitude = accidentDoc.getDouble("longitude");

        if (latitude == null || longitude == null) {
            return;
        }

        List<String> finalDeclinedHospitals = declinedHospitals;
        db.collection("Accidents")
                .document(caseId)
                .update(
                        "status", "searching",
                        "declinedHospitals", declinedHospitals
                )
                .addOnSuccessListener(aVoid -> {
                    searchNextHospital(caseId, latitude, longitude, finalDeclinedHospitals);
                });
    }

    private void searchNextHospital(String caseId, double lat, double lon, List<String> excludedHospitals) {
        searchHospitalsInRadius(caseId, lat, lon, 10, excludedHospitals);
    }

    private void searchHospitalsInRadius(String caseId, double lat, double lon,
                                         int radiusKm, List<String> excludedHospitals) {
        db.collection("Hospitals")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<HospitalDistance> hospitalsInRange = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String hospitalId = doc.getId();

                        if (excludedHospitals.contains(hospitalId)) {
                            continue;
                        }

                        Double hospitalLat = doc.getDouble("latitude");
                        Double hospitalLon = doc.getDouble("longitude");

                        if (hospitalLat != null && hospitalLon != null) {
                            float distance = calculateDistance(lat, lon, hospitalLat, hospitalLon);

                            if (distance <= radiusKm) {
                                hospitalsInRange.add(new HospitalDistance(hospitalId, distance));
                            }
                        }
                    }

                    if (!hospitalsInRange.isEmpty()) {
                        hospitalsInRange.sort((h1, h2) -> Float.compare(h1.distance, h2.distance));
                        String nearestHospitalId = hospitalsInRange.get(0).hospitalId;

                        db.collection("Accidents")
                                .document(caseId)
                                .update(
                                        "assignedHospitalId", nearestHospitalId,
                                        "status", "pending"
                                );
                    } else if (radiusKm < 100) {
                        searchHospitalsInRadius(caseId, lat, lon, radiusKm + 5, excludedHospitals);
                    } else {
                        db.collection("Accidents")
                                .document(caseId)
                                .update("status", "no_hospital_available");
                    }
                });
    }

    private float calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0] / 1000; // Convert meters to km
    }

    public void stopMonitoring() {
        if (statusListener != null) {
            statusListener.remove();
        }
    }

    private static class HospitalDistance {
        String hospitalId;
        float distance;

        HospitalDistance(String hospitalId, float distance) {
            this.hospitalId = hospitalId;
            this.distance = distance;
        }
    }

    public interface StatusUpdateCallback {
        void onStatusUpdate(String status);
        void onError(String error);
    }
}