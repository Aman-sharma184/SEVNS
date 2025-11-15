package com.group_43.sevns;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AccidentStatusActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accident_status);

        TextView tvTrackingId = findViewById(R.id.tvTrackingId);
        TextView tvStatus = findViewById(R.id.tvStatusOnly);
        Button btnRefresh = findViewById(R.id.btnRefresh);

        String reportId = getIntent().getStringExtra("REPORT_ID");
        tvTrackingId.setText("Tracking ID: " + (reportId != null ? reportId.substring(0, 8) : "-"));

        Runnable refresh = () -> {
            AccidentReport r = SimulatedDatabase.getReportById(reportId);
            tvStatus.setText(r == null ? "Not found" : ("Status: " + r.getStatus()));
        };
        btnRefresh.setOnClickListener(v -> refresh.run());
        refresh.run();
    }
}

