package com.group_43.sevns;

import java.util.ArrayList;
import java.util.List;

public class AccidentReport {
    private String id;
    private double latitude;
    private double longitude;
    private String phoneNumber;
    private String description;
    private String address;
    private String assignedHospitalId, driverId;
    private long timestamp;
    private String status;
    private boolean completed;
    private String responseMessage;
    private List<String> declinedHospitals;

    public AccidentReport() {
        this.declinedHospitals = new ArrayList<>();
    }

    public AccidentReport(String id, double latitude, double longitude, String phoneNumber,
                          String description, String address, String assignedHospitalId, String driverId,
                          long timestamp, String status, boolean completed, String responseMessage) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phoneNumber = phoneNumber;
        this.description = description;
        this.address = address;
        this.assignedHospitalId = assignedHospitalId;
        this.driverId = driverId;
        this.timestamp = timestamp;
        this.status = status;
        this.completed = completed;
        this.responseMessage = responseMessage;
        this.declinedHospitals = new ArrayList<>();
    }

    // Getters
    public String getId() { return id; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getDescription() { return description; }
    public String getAddress() { return address; }
    public String getAssignedHospitalId() { return assignedHospitalId; }
    public String getdriverId() { return driverId; }
    public long getTimestamp() { return timestamp; }
    public String getStatus() { return status; }
    public boolean isCompleted() { return completed; }
    public String getResponseMessage() { return responseMessage; }

    public List<String> getDeclinedHospitals() {
        if (declinedHospitals == null) {
            declinedHospitals = new ArrayList<>();
        }
        return declinedHospitals;
    }

    public void setId(String id) { this.id = id; }
    public void setStatus(String status) { this.status = status; }


    public void addDeclinedHospital(String hospitalId) {
        if (declinedHospitals == null) {
            declinedHospitals = new ArrayList<>();
        }
        if (hospitalId != null && !hospitalId.isEmpty() && !declinedHospitals.contains(hospitalId)) {
            declinedHospitals.add(hospitalId);
        }
    }

    @Override
    public String toString() {
        String statusDisplay = status != null ? status.toUpperCase() : "UNKNOWN";
        return "Case: " + id + "\n" +
                "Status: " + statusDisplay + "\n" +
                "Contact: " + phoneNumber;
    }
}