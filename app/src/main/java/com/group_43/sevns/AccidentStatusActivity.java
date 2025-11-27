package com.group_43.sevns;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ServerTimestamp;

public class AccidentStatusActivity extends AppCompatActivity {

    FirebaseFirestore db = FirebaseFirestore.getInstance();
    TextView tvStatusOnly, editCaseid;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accident_status);

        editCaseid = findViewById(R.id.editCaseid);
        tvStatusOnly = findViewById(R.id.tvStatusOnly);
        Button btnRefresh = findViewById(R.id.btnRefresh);

        btnRefresh.setOnClickListener(v -> {
            String case_id = editCaseid.getText().toString();
            listenToStatus(case_id);
        });


        // GET case ID safely
        String case_id = getIntent().getStringExtra("CASE_ID");

        if (case_id == null || case_id.trim().isEmpty()) {
            case_id = "";
            Toast.makeText(this, "Track your Case!", Toast.LENGTH_SHORT).show();
            editCaseid.setText(case_id);
            listenToStatus(case_id);
        } else {
            editCaseid.setText(case_id);
            listenToStatus(case_id);
        }

    }

    public void listenToStatus(String case_Id) {

        db.collection("Accidents")
                .whereEqualTo("id", case_Id)
                .addSnapshotListener((value, error) -> {

                    if (error != null) return;
                    if (value == null || value.isEmpty()) {
                        tvStatusOnly.setText("Status: Case Not Found");
                        return;
                    }

                    // There will be only 1 match
                    DocumentSnapshot snapshot = value.getDocuments().get(0);

                    String status = snapshot.getString("status");

                    tvStatusOnly.setText("Status: " + status);
                });
    ;}
}
