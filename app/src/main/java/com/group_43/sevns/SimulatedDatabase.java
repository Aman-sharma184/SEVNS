package com.group_43.sevns;

import java.util.*;

public class SimulatedDatabase {
    private static final Map<String, AccidentReport> reports = new HashMap<>();
    private static final Map<String, String> userCredentials = new HashMap<>();

    static {
        userCredentials.put("hospital@emergency.com", "hospital123");
        userCredentials.put("driver1@ambulance.com", "driver123");
    }

    public static boolean authenticateUser(String email, String password) {
        String stored = userCredentials.get(email);
        return stored != null && stored.equals(password);
    }

    public static void saveReport(AccidentReport report) {
        if (report.getId() == null) {
            report.setId(UUID.randomUUID().toString());
        }
        reports.put(report.getId(), report);
    }

    public static AccidentReport getReportById(String id) {
        return reports.get(id);
    }

    public static void updateReport(AccidentReport report) {
        reports.put(report.getId(), report);
    }

    public static List<AccidentReport> getActiveReports() {
        List<AccidentReport> active = new ArrayList<>();
        for (AccidentReport r : reports.values()) {
            if (!"Completed".equalsIgnoreCase(r.getStatus())) {
                active.add(r);
            }
        }
        active.sort((r1, r2) -> Long.compare(r2.getTimestamp(), r1.getTimestamp()));
        return active;
    }
}
