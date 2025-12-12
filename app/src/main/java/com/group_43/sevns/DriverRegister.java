package com.group_43.sevns;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class DriverRegister extends AppCompatActivity {

    private EditText editName, editEmail, editPassword, editPhone;
    private FirebaseAuth mAuth;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.driver_register);

        editName = findViewById(R.id.editDriverName);
        editEmail = findViewById(R.id.editDriverEmail);
        editPassword = findViewById(R.id.editDriverPassword);
        editPhone = findViewById(R.id.editDriverPhone);
        Button btnRegister = findViewById(R.id.btnRegisterDriver);

        mAuth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        requestLocationPermission();

        btnRegister.setOnClickListener(v -> registerDriver());
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                1001
        );
    }

    private static String generateDriverId() {
        int randomNum = (int) (Math.random() * 90000) + 10000;
        return "DRIVER" + randomNum;
    }

    private void createUniqueDriverId(FirebaseFirestore db, OnDriverIdGenerated callback) {

        String id = generateDriverId();

        db.collection("Drivers")
                .whereEqualTo("Driver_ID", id)
                .get()
                .addOnSuccessListener(q -> {

                    if (q.isEmpty()) {
                        callback.onGenerated(id);
                    } else {
                        createUniqueDriverId(db, callback);
                    }

                })
                .addOnFailureListener(e -> callback.onGenerated(null));
    }

    public interface OnDriverIdGenerated {
        void onGenerated(String driverId);
    }

    private void registerDriver() {
        String name = editName.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();
        String phone = editPhone.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please fill all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        String doc_id = "Driver-" + FirebaseAuth.getInstance().getCurrentUser().getUid();
                        FirebaseFirestore db = FirebaseFirestore.getInstance();

                        createUniqueDriverId(db, driverId -> {

                            if (driverId == null) {
                                Toast.makeText(this, "Could not generate Driver ID!", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            getDriverLocation((lat, lon) -> {

                                DriverRegistration data = new DriverRegistration(
                                        driverId,
                                        name,
                                        email,
                                        phone,
                                        "Unavailable",
                                        lat,
                                        lon
                                );

                                db.collection("Drivers")
                                        .document(doc_id)
                                        .set(data)
                                        .addOnSuccessListener(a -> {
                                            Toast.makeText(this, "Driver Registered Successfully!", Toast.LENGTH_LONG).show();
                                            startActivity(new Intent(this, DriverLoginActivity.class));
                                            finish();
                                        })
                                        .addOnFailureListener(e ->
                                                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                                        );
                            });

                        });

                    } else {
                        Toast.makeText(this,
                                "Registration Error: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }


    private void getDriverLocation(OnLocationFetched callback) {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            callback.onFetched(0.0, 0.0);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {

                    if (location != null) {
                        callback.onFetched(location.getLatitude(), location.getLongitude());
                    } else {
                        callback.onFetched(0.0, 0.0);
                    }

                })
                .addOnFailureListener(e -> callback.onFetched(0.0, 0.0));
    }

    public interface OnLocationFetched {
        void onFetched(double lat, double lon);
    }

    public static class DriverRegistration {

        public String Driver_ID;
        public String name, status, email, phone;
        public Double latitude, longitude;

        public DriverRegistration() {}

        public DriverRegistration(String Driver_ID, String name, String email, String phone,
                                  String status, Double latitude, Double longitude) {
            this.Driver_ID = Driver_ID;
            this.name = name;
            this.email = email;
            this.phone = phone;
            this.status = status;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
