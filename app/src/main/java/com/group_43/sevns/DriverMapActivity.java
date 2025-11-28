package com.group_43.sevns;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import java.util.ArrayList;

public class DriverMapActivity extends AppCompatActivity {
    private MapView map;
    private TextView tvEta;
    private Button btnComplete;
    private String driverId;
    private AccidentReport assignedReport;

    private final int REQUEST_PERMISSIONS_CODE = 1;
    private final String[] REQUIRED_PERMISSIONS = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        setContentView(R.layout.driver_screen);

        tvEta = findViewById(R.id.tvEta);
        btnComplete = findViewById(R.id.btnComplete);
        map = findViewById(R.id.map);

        requestPermissionsIfNecessary(REQUIRED_PERMISSIONS);

        findAndDisplayAssignedReport();
        btnComplete.setOnClickListener(v -> markReportCompleted());
    }

    @SuppressLint("SetTextI18n")
    private void findAndDisplayAssignedReport() {
        for (AccidentReport report : SimulatedDatabase.getActiveReports()) {
            if (driverId != null && driverId.equals(report.getDriverId()) && "Dispatched".equals(report.getStatus())) {
                assignedReport = report;
                break;
            }
        }
        if (assignedReport != null) {
            tvEta.setText("Accident Location Found! Plotting route...");
            btnComplete.setEnabled(true);
            setupMapAndRoute();
        } else {
            tvEta.setText("No assigned case to you.");
            Toast.makeText(this, "No assigned Case.", Toast.LENGTH_LONG).show();
            btnComplete.setEnabled(false);
        }
    }

    private void setupMapAndRoute() {
        if (assignedReport == null) return;

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);

        IMapController ctl = map.getController();
        ctl.setZoom(15.0);
        GeoPoint accidentPoint = new GeoPoint(assignedReport.getLatitude(), assignedReport.getLongitude());
        ctl.setCenter(accidentPoint);

        Marker m = new Marker(map);
        m.setPosition(accidentPoint);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setTitle("Accident: " + assignedReport.getDescription() + "\n\n" + assignedReport.getAddress());
        map.getOverlays().add(m);
        btnComplete.setEnabled(true);

        // TODO: replace with free routing ETA (OpenRouteService) if you want real ETA
        tvEta.setText("ETA: 15 mins (Simulated)");
        map.invalidate();
    }

    private void markReportCompleted() {
        if (assignedReport != null) {
            assignedReport.setStatus("Completed");
            SimulatedDatabase.updateReport(assignedReport);
            Toast.makeText(this, "Report marked COMPLETED.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void requestPermissionsIfNecessary(String[] permissions) {
        ArrayList<String> req = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                req.add(p);
            }
        }
        if (!req.isEmpty()) {
            ActivityCompat.requestPermissions(this, req.toArray(new String[0]), REQUEST_PERMISSIONS_CODE);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            boolean allGranted = true;
            for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            if (!allGranted) {
                Toast.makeText(this, "Map needs Location permission.", Toast.LENGTH_LONG).show();
            } else {
                Context ctx = getApplicationContext();
                Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
            }
        }
    }
}

