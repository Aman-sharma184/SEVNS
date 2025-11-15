package com.group_43.sevns;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class HospitalLoginActivity extends AppCompatActivity {

    private EditText editEmail, editPassword;
    private Button btnLogin, btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hospital_login);

        editEmail = findViewById(R.id.email);
        editPassword = findViewById(R.id.password);
        btnLogin = findViewById(R.id.btnLogin);
        //btnRegister = findViewById(R.id.btnRegister);

        // Simulated pre-filled credentials for testing
        editEmail.setText("hospital@emergency.com");
        editPassword.setText("hospital123");

        btnLogin.setOnClickListener(v -> handleLogin());

        // Registration is not implemented for the simulated app
        //btnRegister.setOnClickListener(v -> Toast.makeText(this, "Registration not available in this demo.", Toast.LENGTH_SHORT).show());
    }

    private void handleLogin() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both email and password.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (SimulatedDatabase.authenticateUser(email, password)) {
            Toast.makeText(this, "Hospital Login Successful!", Toast.LENGTH_SHORT).show();
            // Navigate to the dashboard
            Intent intent = new Intent(HospitalLoginActivity.this, HospitalDashboardActivity.class);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "Invalid credentials for Hospital.", Toast.LENGTH_LONG).show();
        }
    }
}
