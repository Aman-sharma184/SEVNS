package com.group_43.sevns;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DriverLoginActivity extends AppCompatActivity {

    private EditText editEmail, editPassword;
    private Button btnLogin, btnRegister, btnConfirmHospital, btnCancelHospital;
    private CardView loginFormCard, hospitalSelectionCard;
    private AutoCompleteTextView hospitalSpinner;
    private ProgressBar hospitalLoadingProgress;

    private String driverUid;
    private Map<String, String> hospitalMap = new HashMap<>();
    private List<String> hospitalNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.driver_login);

        // Initialize views
        editEmail = findViewById(R.id.email);
        editPassword = findViewById(R.id.password);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        loginFormCard = findViewById(R.id.loginFormCard);
        hospitalSelectionCard = findViewById(R.id.hospitalSelectionCard);
        hospitalSpinner = findViewById(R.id.hospitalSpinner);
        hospitalLoadingProgress = findViewById(R.id.hospitalLoadingProgress);
        btnConfirmHospital = findViewById(R.id.btnConfirmHospital);
        btnCancelHospital = findViewById(R.id.btnCancelHospital);

        // Set click listeners
        btnLogin.setOnClickListener(v -> handleLogin());
        btnRegister.setOnClickListener(v -> startActivity(new Intent(this, DriverRegister.class)));
        btnConfirmHospital.setOnClickListener(v -> confirmHospitalSelection());
        btnCancelHospital.setOnClickListener(v -> cancelHospitalSelection());
    }

    private void handleLogin() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both email and password.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    btnLogin.setEnabled(true);
                    if (task.isSuccessful()) {
                        driverUid = "Driver-" + FirebaseAuth.getInstance().getCurrentUser().getUid();
                        checkDriverInFirestore(driverUid);
                    } else {
                        Toast.makeText(DriverLoginActivity.this, "Authentication failed.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkDriverInFirestore(String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Drivers")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        // Show hospital selection
                        showHospitalSelection();
                    } else {
                        FirebaseAuth.getInstance().signOut();
                        Toast.makeText(this, "Not a Driver account", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Firestore Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showHospitalSelection() {
        // Hide login form and show hospital selection
        loginFormCard.setVisibility(View.GONE);
        hospitalSelectionCard.setVisibility(View.VISIBLE);
        hospitalLoadingProgress.setVisibility(View.VISIBLE);

        // Load hospitals from Firestore
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("Hospitals")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    hospitalLoadingProgress.setVisibility(View.GONE);
                    hospitalNames.clear();
                    hospitalMap.clear();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String hospitalName = doc.getString("name");
                        String hospitalId = doc.getId();

                        if (hospitalName != null) {
                            hospitalNames.add(hospitalName);
                            hospitalMap.put(hospitalName, hospitalId);
                        }
                    }

                    if (hospitalNames.isEmpty()) {
                        Toast.makeText(this, "No hospitals available", Toast.LENGTH_SHORT).show();
                        cancelHospitalSelection();
                        return;
                    }

                    // Set up the dropdown adapter
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_dropdown_item_1line,
                            hospitalNames
                    );
                    hospitalSpinner.setAdapter(adapter);
                })
                .addOnFailureListener(e -> {
                    hospitalLoadingProgress.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load hospitals: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    cancelHospitalSelection();
                });
    }

    private void confirmHospitalSelection() {
        String selectedHospitalName = hospitalSpinner.getText().toString().trim();

        if (selectedHospitalName.isEmpty()) {
            Toast.makeText(this, "Please select a hospital", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!hospitalMap.containsKey(selectedHospitalName)) {
            Toast.makeText(this, "Invalid hospital selection", Toast.LENGTH_SHORT).show();
            return;
        }

        String selectedHospitalId = hospitalMap.get(selectedHospitalName);

        // Disable button while processing
        btnConfirmHospital.setEnabled(false);
        hospitalLoadingProgress.setVisibility(View.VISIBLE);

        updateDriverWithHospital(driverUid, selectedHospitalId, selectedHospitalName);
    }

    private void updateDriverWithHospital(String uid, String hospitalId, String hospitalName) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "Available");
        updates.put("hospitalId", hospitalId);
        updates.put("hospitalName", hospitalName);

        db.collection("Drivers")
                .document(uid)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    hospitalLoadingProgress.setVisibility(View.GONE);
                    Toast.makeText(this,
                            "Login Successful - Assigned to " + hospitalName,
                            Toast.LENGTH_SHORT).show();

                    MainActivity.saveLoginData(this, "driver", uid);
                    startActivity(new Intent(DriverLoginActivity.this, DriverMapActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    hospitalLoadingProgress.setVisibility(View.GONE);
                    btnConfirmHospital.setEnabled(true);
                    Toast.makeText(this, "Failed to update hospital: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void cancelHospitalSelection() {
        // Show login form and hide hospital selection
        loginFormCard.setVisibility(View.VISIBLE);
        hospitalSelectionCard.setVisibility(View.GONE);

        // Sign out the user
        FirebaseAuth.getInstance().signOut();
        Toast.makeText(this, "Login cancelled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (hospitalSelectionCard.getVisibility() == View.VISIBLE) {
            cancelHospitalSelection();
        } else {
            super.onBackPressed();
        }
    }
}