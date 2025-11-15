package com.group_43.sevns;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class HospitalDashboardActivity extends AppCompatActivity {
    private ListView listReports;
    private Button btnRefreshReports;
    private List<AccidentReport> activeReports;
    private ArrayAdapter<AccidentReport> adapter;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hospital_dashboard);

        listReports = findViewById(R.id.listReports);
        btnRefreshReports = findViewById(R.id.btnRefreshReports);

        fetchReports();
        btnRefreshReports.setOnClickListener(v -> fetchReports());

        listReports.setOnItemClickListener((parent, view, position, id) -> {
            AccidentReport selectedReport = activeReports.get(position);
            showReportActionsDialog(selectedReport);
        });
    }

    private void fetchReports() {
        activeReports = SimulatedDatabase.getActiveReports();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, activeReports);
        listReports.setAdapter(adapter);
        Toast.makeText(this,
                activeReports.isEmpty() ? "No new active reports." : (activeReports.size() + " active reports loaded."),
                Toast.LENGTH_SHORT).show();
    }

    private void showReportActionsDialog(AccidentReport report) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Report ID: " + report.getId().substring(0, 8) + " - Status: " + report.getStatus())
                .setMessage("Details:\n" + report.getDescription() +
                        "\n\nLocation: " + report.getLatitude() + ", " + report.getLongitude())
                .setNeutralButton("Close", (dialog, id) -> dialog.dismiss());

        if ("Reported".equals(report.getStatus())) {
            builder.setPositiveButton("Acknowledge & Dispatch", (dialog, id) -> acknowledgeAndDispatch(report));
        }
        builder.create().show();
    }

    private void acknowledgeAndDispatch(AccidentReport report) {
        report.setStatus("Dispatched");
        report.setDriverId("driver1"); // TODO: replace with real driver selection
        SimulatedDatabase.updateReport(report);
        Toast.makeText(this,
                "Report " + report.getId().substring(0, 8) + " DISPATCHED to Driver 1.",
                Toast.LENGTH_LONG).show();
        fetchReports();
    }
}

