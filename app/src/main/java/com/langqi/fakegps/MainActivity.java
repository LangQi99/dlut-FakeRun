package com.langqi.fakegps;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = "langqi_log";
    private boolean isPermissionGranted = false;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                if (result.containsValue(false)) {
                    Log.d(LOG_TAG, "权限被拒绝，无法启动服务");
                    isPermissionGranted = false;
                } else {
                    Log.d(LOG_TAG, "权限已授予");
                    isPermissionGranted = true;
                }
                startFakeGPSActivity();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        Log.d(LOG_TAG, "MainActivity::checkAndRequestPermissions");
        String[] requiredPermissions = { Manifest.permission.ACCESS_FINE_LOCATION };

        boolean allPermissionsGranted = true;
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (allPermissionsGranted) {
            isPermissionGranted = true;
            startFakeGPSActivity();
        } else {
            requestPermissionLauncher.launch(requiredPermissions);
        }
    }

    private void startFakeGPSActivity() {
        Intent intent = new Intent(this, FakeGPSActivity.class);
        startActivity(intent);
        finish();
    }
}
