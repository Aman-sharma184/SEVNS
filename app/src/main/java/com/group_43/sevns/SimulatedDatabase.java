package com.group_43.sevns;

import java.util.*;

public class SimulatedDatabase {
    private static final Map<String, AccidentReport> reports = new HashMap<>();
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
