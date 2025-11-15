package com.group_43.sevns;

import java.io.Serializable;
import java.util.UUID;

public class AccidentReport implements Serializable {
    private String id;
    private double latitude;
    private double longitude;
    private String phoneNumber;
    private String description;
    private String status; // Reported, Acknowledged, Dispatched, Completed
    private long timestamp;
    private String driverId; // nullable

    public AccidentReport() { }

    public AccidentReport(double lat, double lon, String phone, String desc) {
        this.id = UUID.randomUUID().toString();
        this.latitude = lat;
        this.longitude = lon;
        this.phoneNumber = phone;
        this.description = desc;
        this.status = "Reported";
        this.timestamp = System.currentTimeMillis();
        this.driverId = null;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    @Override
    public String toString() {
        return "ID: " + id.substring(0, 8) + "\nStatus: " + status + "\nDesc: " + description;
    }
}

