package com.group_43.sevns;

public class AccidentReport {
    private String id;
    private double latitude;
    private double longitude;
    private String phone;
    private String description;
    private String address;
    private String status;
    private long timestamp;
    private String driverId;
    private String hospitalId;

    public AccidentReport() {}
    public AccidentReport(String id, double latitude, double longitude, String phone,
                          String description, String address, String status,
                          long timestamp, String driverId, String hospitalId) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phone = phone;
        this.description = description;
        this.address = address;
        this.status = "Pending";
        this.timestamp = timestamp;
        this.driverId = driverId;
        this.hospitalId = hospitalId;
    }


    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getAddress() { return address; }
    public String getPhoneNumber() { return phone; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getTimestamp() { return timestamp; }
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    @Override
    public String toString() {
        return " Case ID: " + id + "\nStatus: " + status + "\nDesc: " + description;
    }

}

