package com.langqi.fakegps;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

public class LocationMockService extends Service {
    private LocationManager locationManager;
    private static final String LOG_TAG = "langqi_log";
    private static final String ACTION_MOCK_LOCATION = "com.langqi.fakegps.MOCK_LOCATION";

    private final BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Log.d(LOG_TAG, "收到广播: " + intent.getAction());

            if (ACTION_MOCK_LOCATION.equals(intent.getAction())) {
                double lat = 0.0;
                double lng = 0.0;
                double alt = 0.0;
                float bea = 0.0f;
                float speed = 0.0f;
                float acc = 0.0f;
                try {
                    String latStr = intent.getStringExtra("lat");
                    String lngStr = intent.getStringExtra("lng");
                    String altStr = intent.getStringExtra("alt");
                    String beaStr = intent.getStringExtra("bea");
                    String speedStr = intent.getStringExtra("speed");
                    String accStr = intent.getStringExtra("acc");
                    if (latStr != null) {
                        lat = Double.parseDouble(latStr);
                    }
                    if (lngStr != null) {
                        lng = Double.parseDouble(lngStr);
                    }
                    if (altStr != null) {
                        alt = Double.parseDouble(altStr);
                    }
                    if (beaStr != null) {
                        bea = Float.parseFloat(beaStr);
                    }
                    if (speedStr != null) {
                        speed = Float.parseFloat(speedStr);
                    }
                    if (accStr != null) {
                        acc = Float.parseFloat(accStr);
                    }
                    Log.d(LOG_TAG, "解析到位置: lat=" + lat + ", lng=" + lng + ", alt=" + alt + ", bea=" + bea + ", speed="
                            + speed + ", acc=" + acc);
                    mockLocation(lat, lng, alt, bea, speed, acc);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    Log.e(LOG_TAG, "解析参数时遇到错误");
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "LocationMockService onCreate");
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        try {
            removeTestProviderNetwork();
            addTestProviderNetwork();
            Log.d(LOG_TAG, "Network provider setup completed");

            removeTestProviderGPS();
            addTestProviderGPS();
            Log.d(LOG_TAG, "GPS provider setup completed");

            initNotificationChannel();
            registerReceiver(locationReceiver, new IntentFilter(ACTION_MOCK_LOCATION), Context.RECEIVER_EXPORTED);
            Log.d(LOG_TAG, "Service setup completed");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Service setup failed: " + e.getMessage(), e);
        }
    }

    @SuppressLint("ForegroundServiceType")
    private void initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "mock_gps_channel",
                    "Mock GPS Service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);

            Notification notification = new Notification.Builder(this, "mock_gps_channel")
                    .setContentTitle("[FakeGPS v2.0] 模拟位置运行中 - made by langqi")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .build();

            startForeground(1, notification);
        }
    }

    private void mockLocation(double lat, double lng, double alt, float bea, float speed, float acc) {
        Log.d(LOG_TAG,
                "Mocking location: lat=" + lat + ", lng=" + lng + ", alt=" + alt + ", speed=" + speed + ", acc=" + acc);
        try {
            setLocationGPS(lat, lng, alt, bea, speed, acc);
            setLocationNetwork(lat, lng, alt, bea, speed, acc);
            Log.d(LOG_TAG, "Location mocked successfully");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to mock location: " + e.getMessage(), e);
        }
    }

    @Override
    public void onDestroy() {
        removeTestProviderNetwork();
        removeTestProviderGPS();
        unregisterReceiver(locationReceiver);
        super.onDestroy();
    }

    private void removeTestProviderNetwork() {
        try {
            Log.d(LOG_TAG, "开始移除网络测试提供者");
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Log.d(LOG_TAG, "网络提供者已启用，准备禁用并移除");
                try {
                    locationManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false);
                    Log.d(LOG_TAG, "成功禁用网络测试提供者");
                } catch (Exception e) {
                    Log.e(LOG_TAG, "无法禁用网络测试提供者: " + e.getMessage(), e);
                }
                try {
                    locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER);
                    Log.d(LOG_TAG, "成功移除网络测试提供者");
                } catch (Exception e) {
                    Log.e(LOG_TAG, "无法移除网络测试提供者: " + e.getMessage(), e);
                }
            } else {
                Log.d(LOG_TAG, "网络提供者未启用，无需移除");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "ERROR from removeTestProviderNetwork", e);
        }
    }

    @SuppressLint("wrongconstant")
    private void addTestProviderNetwork() {
        try {
            // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实NETWORK参数，不是随便写的)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.addTestProvider(LocationManager.NETWORK_PROVIDER, true, false,
                        true, true, true, true,
                        true, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_COARSE);
            } else {
                locationManager.addTestProvider(LocationManager.NETWORK_PROVIDER, true, false,
                        true, true, true, true,
                        true, Criteria.POWER_LOW, Criteria.ACCURACY_COARSE);
            }
            if (!locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
            }
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "ERROR from addTestProviderNetwork");
        }
    }

    private void removeTestProviderGPS() {
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                try {
                    locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false);
                } catch (Exception e) {
                    Log.w(LOG_TAG, "无法禁用 GPS 测试提供者: " + e.getMessage());
                }
                try {
                    locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
                    Log.d(LOG_TAG, "成功移除 GPS 测试提供者");
                } catch (Exception e) {
                    Log.w(LOG_TAG, "无法移除 GPS 测试提供者: " + e.getMessage());
                }
            } else {
                Log.d(LOG_TAG, "GPS 提供者未启用，无需移除");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "SERVICEGO: ERROR - removeTestProviderGPS", e);
        }
    }

    @SuppressLint("wrongconstant")
    private void addTestProviderGPS() {
        try {
            Log.d(LOG_TAG, "Adding GPS test provider");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE);
                Log.d(LOG_TAG, "GPS provider added for Android S+");
            } else {
                locationManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
                Log.d(LOG_TAG, "GPS provider added for older Android versions");
            }

            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
                Log.d(LOG_TAG, "GPS provider enabled");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to add GPS provider: " + e.getMessage(), e);
        }
    }

    private void setLocationGPS(double lat, double lng, double alt, float bea, float speed, float acc) {
        try {
            // 尽可能模拟真实的 GPS 数据
            Location loc = new Location(LocationManager.GPS_PROVIDER);
            loc.setAccuracy(Criteria.ACCURACY_FINE); // 设定此位置的估计水平精度，以米为单位。
            loc.setAltitude(alt); // 设置高度，在 WGS 84 参考坐标系中的米
            loc.setBearing(bea); // 方向（度）
            loc.setLatitude(lat); // 纬度（度）
            loc.setLongitude(lng); // 经度（度））
            loc.setAccuracy(acc);

            loc.setTime(System.currentTimeMillis()); // 本地时间
            loc.setSpeed(speed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            Bundle bundle = new Bundle();
            bundle.putInt("satellites", 7);
            loc.setExtras(bundle);

            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc);
        } catch (Exception e) {
            Log.e(LOG_TAG, "EROOR from setLocationGPS");
        }
    }

    private void setLocationNetwork(double lat, double lng, double alt, float bea, float speed, float acc) {
        try {
            // 尽可能模拟真实的 NETWORK 数据
            Location loc = new Location(LocationManager.NETWORK_PROVIDER);
            loc.setAccuracy(Criteria.ACCURACY_COARSE); // 设定此位置的估计水平精度，以米为单位。
            loc.setAltitude(alt); // 设置高度，在 WGS 84 参考坐标系中的米
            loc.setBearing(bea); // 方向（度）
            loc.setLatitude(lat); // 纬度（度）
            loc.setLongitude(lng); // 经度（度）
            loc.setTime(System.currentTimeMillis()); // 本地时间
            loc.setSpeed(speed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

            locationManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, loc);
        } catch (Exception e) {
            Log.e(LOG_TAG, "ERROR from setLocationNetwork");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
