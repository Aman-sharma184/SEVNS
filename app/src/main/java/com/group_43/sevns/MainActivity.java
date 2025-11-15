package com.group_43.sevns;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnUser = findViewById(R.id.btnUser);
        Button btnHospital = findViewById(R.id.btnHospital);
        Button btnAmbulance = findViewById(R.id.btnAmbulance);

        btnUser.setOnClickListener(v ->
                startActivity(new Intent(this, AccidentReportingActivity.class))
        );
        btnHospital.setOnClickListener(v ->
                startActivity(new Intent(this, HospitalLoginActivity.class))
        );
        btnAmbulance.setOnClickListener(v ->
                startActivity(new Intent(this, DriverLoginActivity.class))
        );
    }
}
