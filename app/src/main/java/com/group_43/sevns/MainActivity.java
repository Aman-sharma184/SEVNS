package com.group_43.sevns;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_USER_ID = "userId";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkLoginStatus()) {
            return;
        }

        setContentView(R.layout.activity_main);

        Button btnUser = findViewById(R.id.btnUser);
        Button btnHospital = findViewById(R.id.btnHospital);
        Button btnAmbulance = findViewById(R.id.btnAmbulance);
        Button btnHospitalRegister = findViewById(R.id.btnHospitalRegister);
        Button btntrackCase = findViewById(R.id.btntrackCase);
        Button btnDriverRegister = findViewById(R.id.btnDriverRegister);

        btnUser.setOnClickListener(v ->
                startActivity(new Intent(this, AccidentReportingActivity.class)));

        btnHospitalRegister.setOnClickListener(v ->
                startActivity(new Intent(this, HospitalRegisterActivity.class)));

        btnHospital.setOnClickListener(v ->
                startActivity(new Intent(this, HospitalLoginActivity.class)));

        btnAmbulance.setOnClickListener(v ->
                startActivity(new Intent(this, DriverLoginActivity.class)));

        btntrackCase.setOnClickListener(v ->
                startActivity(new Intent(this, TrackCaseActivity.class)));

        btnDriverRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, DriverRegister.class));
        });
    }

    private boolean checkLoginStatus() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false);

        if (isLoggedIn) {
            String userType = prefs.getString(KEY_USER_TYPE, "");
            Intent intent = null;

            // Redirect based on user type
            switch (userType) {
                case "hospital":
                    intent = new Intent(this, HospitalDashboardActivity.class);
                    break;
                case "driver":
                    intent = new Intent(this, DriverMapActivity.class);
                    break;
                default:
                    clearLoginData();
                    return false;
            }

            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return true;
            }
        }

        return false;
    }

    public static void saveLoginData(AppCompatActivity activity, String userType, String userId) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USER_TYPE, userType);
        editor.putString(KEY_USER_ID, userId);
        editor.apply();
    }

    public static void clearLoginData(AppCompatActivity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
    }

    private void clearLoginData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        moveTaskToBack(true);
    }
}