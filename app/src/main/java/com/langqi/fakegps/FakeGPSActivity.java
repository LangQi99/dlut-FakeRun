package com.langqi.fakegps;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class FakeGPSActivity extends AppCompatActivity {
    private static final String LOG_TAG = "langqi_log";
    private static final int REQUEST_CODE_PICK_KML = 1;

    private EditText kmlFilePath;
    private TextView kmlInfo;
    private EditText inputSpeed;
    private EditText inputRepetitions;
    private EditText inputInterval;
    private EditText inputAltitude;
    private EditText inputAcceleration;
    private ProgressBar progressBar;
    private Button btnStart;
    private Button btnPause;
    private Button btnResume;
    private Button btnStop;

    private List<Coordinate> coordinates = new ArrayList<>();
    private List<Double> progressList = new ArrayList<>();
    private double totalDistance = 0;
    private boolean isRunning = false;
    private boolean isPaused = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private double accumulatedDistance = 0;
    private long lastUpdateTime = 0;
    private Timer timer = new Timer();

    private final ActivityResultLauncher<String> getContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    kmlFilePath.setText(uri.toString());
                    processKmlFile(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fake_gps);

        initializeViews();
        setupClickListeners();
        loadDefaultKmlFile();
        updateButtonStates();
    }

    private void initializeViews() {
        kmlFilePath = findViewById(R.id.kml_file_path);
        kmlInfo = findViewById(R.id.kml_info);
        inputSpeed = findViewById(R.id.input_speed);
        inputRepetitions = findViewById(R.id.input_repetitions);
        inputInterval = findViewById(R.id.input_interval);
        inputAltitude = findViewById(R.id.input_altitude);
        inputAcceleration = findViewById(R.id.input_acceleration);
        progressBar = findViewById(R.id.progress_bar);
        btnStart = findViewById(R.id.btn_start);
        btnPause = findViewById(R.id.btn_pause);
        btnResume = findViewById(R.id.btn_resume);
        btnStop = findViewById(R.id.btn_stop);

        findViewById(R.id.btn_increase_speed).setOnClickListener(v -> {
            try {
                double currentSpeed = Double.parseDouble(inputSpeed.getText().toString());
                inputSpeed.setText(String.format("%.2f", currentSpeed + 0.1));
            } catch (NumberFormatException e) {
                inputSpeed.setText("0.00");
            }
        });

        findViewById(R.id.btn_decrease_speed).setOnClickListener(v -> {
            try {
                double currentSpeed = Double.parseDouble(inputSpeed.getText().toString());
                if (currentSpeed > 0.1) {
                    inputSpeed.setText(String.format("%.2f", currentSpeed - 0.1));
                }
            } catch (NumberFormatException e) {
                inputSpeed.setText("0.00");
            }
        });
    }

    private void setupClickListeners() {
        findViewById(R.id.btn_browse)
                .setOnClickListener(v -> getContent.launch("application/vnd.google-earth.kml+xml"));

        btnStart.setOnClickListener(v -> startProcessing());
        btnPause.setOnClickListener(v -> pauseProcessing());
        btnResume.setOnClickListener(v -> resumeProcessing());
        btnStop.setOnClickListener(v -> stopProcessing());
    }

    private void processKmlFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, "无法读取 KML 文件", Toast.LENGTH_SHORT).show();
                return;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);

            NodeList coordinatesList = document.getElementsByTagName("coordinates");
            if (coordinatesList.getLength() == 0) {
                Toast.makeText(this, "未找到坐标数据", Toast.LENGTH_SHORT).show();
                return;
            }

            String coordinatesText = coordinatesList.item(0).getTextContent().trim();
            parseCoordinates(coordinatesText);
            calculateProgressList();

            kmlInfo.setText(String.format("路径点数量: %d, 路径总距离: %.2f m",
                    coordinates.size(), totalDistance));
        } catch (IOException | ParserConfigurationException | SAXException e) {
            Log.e(LOG_TAG, "处理 KML 文件时出错", e);
            Toast.makeText(this, "处理 KML 文件时出错", Toast.LENGTH_SHORT).show();
        }
    }

    private void parseCoordinates(String coordinatesText) {
        coordinates.clear();
        String[] points = coordinatesText.split("\\s+");
        for (String point : points) {
            String[] parts = point.split(",");
            if (parts.length >= 2) {
                double longitude = Double.parseDouble(parts[0]);
                double latitude = Double.parseDouble(parts[1]);
                coordinates.add(new Coordinate(latitude, longitude));
            }
        }
    }

    private void calculateProgressList() {
        progressList.clear();
        progressList.add(0.0);
        for (int i = 1; i < coordinates.size(); i++) {
            double distance = calculateDistance(coordinates.get(i - 1), coordinates.get(i));
            progressList.add(progressList.get(i - 1) + distance);
        }
        totalDistance = progressList.get(progressList.size() - 1);
    }

    private double calculateDistance(Coordinate p1, Coordinate p2) {
        // 使用 Vincenty 公式计算两点之间的距离
        double a = 6378137; // WGS84 椭球体长半轴
        double f = 1 / 298.257223563; // WGS84 椭球体扁率
        double b = (1 - f) * a; // 短半轴

        double L = Math.toRadians(p2.longitude - p1.longitude);
        double U1 = Math.atan((1 - f) * Math.tan(Math.toRadians(p1.latitude)));
        double U2 = Math.atan((1 - f) * Math.tan(Math.toRadians(p2.latitude)));
        double sinU1 = Math.sin(U1);
        double cosU1 = Math.cos(U1);
        double sinU2 = Math.sin(U2);
        double cosU2 = Math.cos(U2);

        double lambda = L;
        double lambdaP;
        int iterations = 0;
        double sinSigma = 0;
        double cosSigma = 0;
        double sigma = 0;
        double sinAlpha = 0;
        double cosSqAlpha = 0;
        double cos2SigmaM = 0;

        do {
            double sinLambda = Math.sin(lambda);
            double cosLambda = Math.cos(lambda);
            sinSigma = Math.sqrt((cosU2 * sinLambda) * (cosU2 * sinLambda) +
                    (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda));
            if (sinSigma == 0) {
                return 0;
            }
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
            sigma = Math.atan2(sinSigma, cosSigma);
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma;
            cosSqAlpha = 1 - sinAlpha * sinAlpha;
            cos2SigmaM = cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha;
            if (Double.isNaN(cos2SigmaM)) {
                cos2SigmaM = 0;
            }
            double C = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha));
            lambdaP = lambda;
            lambda = L + (1 - C) * f * sinAlpha
                    * (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)));
        } while (Math.abs(lambda - lambdaP) > 1e-12 && ++iterations < 200);

        if (iterations >= 200) {
            return 0;
        }

        double uSq = cosSqAlpha * (a * a - b * b) / (b * b);
        double A = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)));
        double B = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)));
        double deltaSigma = B * sinSigma * (cos2SigmaM + B / 4 * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
                B / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)));
        double s = b * A * (sigma - deltaSigma);

        return s;
    }

    private void startProcessing() {
        if (coordinates.isEmpty()) {
            Toast.makeText(this, "请先选择 KML 文件", Toast.LENGTH_SHORT).show();
            return;
        }

        isRunning = true;
        isPaused = false;
        accumulatedDistance = 0;
        lastUpdateTime = System.currentTimeMillis();
        timer.start();
        updateButtonStates();
        startLocationMockService();
        startLocationUpdates();
    }

    private void pauseProcessing() {
        isPaused = true;
        timer.pause();
        updateButtonStates();
    }

    private void resumeProcessing() {
        isPaused = false;
        timer.start();
        lastUpdateTime = System.currentTimeMillis();
        updateButtonStates();
        startLocationUpdates();
    }

    private void stopProcessing() {
        isRunning = false;
        isPaused = false;
        timer.reset();
        accumulatedDistance = 0;
        progressBar.setProgress(0);
        updateButtonStates();
        stopLocationMockService();
    }

    private void updateButtonStates() {
        boolean hasCoordinates = !coordinates.isEmpty();
        btnStart.setEnabled(hasCoordinates);
        btnPause.setEnabled(isRunning && !isPaused);
        btnResume.setEnabled(isRunning && isPaused);
        btnStop.setEnabled(isRunning);
    }

    private void startLocationUpdates() {
        if (!isRunning || isPaused)
            return;

        long currentTime = System.currentTimeMillis();
        double timeDiff = (currentTime - lastUpdateTime) / 1000.0;
        lastUpdateTime = currentTime;

        double speed = Double.parseDouble(inputSpeed.getText().toString());
        accumulatedDistance += timeDiff * speed;

        int repetitions = Integer.parseInt(inputRepetitions.getText().toString());
        double totalPathDistance = totalDistance * repetitions;

        if (accumulatedDistance > totalPathDistance) {
            stopProcessing();
            return;
        }

        Coordinate currentCoord = getCoordinateByDisplacement(accumulatedDistance, repetitions);
        double altitude = Double.parseDouble(inputAltitude.getText().toString());
        double acceleration = Double.parseDouble(inputAcceleration.getText().toString());

        Intent intent = new Intent("com.langqi.fakegps.MOCK_LOCATION");
        intent.putExtra("lat", String.valueOf(currentCoord.latitude));
        intent.putExtra("lng", String.valueOf(currentCoord.longitude));
        intent.putExtra("alt", String.valueOf(altitude));
        intent.putExtra("speed", String.valueOf(speed));
        intent.putExtra("acc", String.valueOf(acceleration));
        sendBroadcast(intent);

        progressBar.setProgress((int) ((accumulatedDistance / totalPathDistance) * 100));

        double interval = Double.parseDouble(inputInterval.getText().toString());
        handler.postDelayed(this::startLocationUpdates, (long) (interval * 1000));
    }

    private Coordinate getCoordinateByDisplacement(double displacement, int repetitions) {
        double totalPathDistance = totalDistance * repetitions;
        displacement = displacement % totalDistance;

        for (int i = 1; i < progressList.size(); i++) {
            if (progressList.get(i) >= displacement) {
                double k = (displacement - progressList.get(i - 1)) / (progressList.get(i) - progressList.get(i - 1));
                Coordinate p1 = coordinates.get(i - 1);
                Coordinate p2 = coordinates.get(i);
                return new Coordinate(
                        p1.latitude + k * (p2.latitude - p1.latitude),
                        p1.longitude + k * (p2.longitude - p1.longitude));
            }
        }
        return coordinates.get(coordinates.size() - 1);
    }

    private void startLocationMockService() {
        Intent serviceIntent = new Intent(this, LocationMockService.class);
        startService(serviceIntent);
    }

    private void stopLocationMockService() {
        Intent serviceIntent = new Intent(this, LocationMockService.class);
        stopService(serviceIntent);
    }

    private void loadDefaultKmlFile() {
        try {
            InputStream inputStream = getResources().openRawResource(R.raw.three_km);
            if (inputStream != null) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document document = builder.parse(inputStream);

                NodeList coordinatesList = document.getElementsByTagName("coordinates");
                if (coordinatesList.getLength() > 0) {
                    String coordinatesText = coordinatesList.item(0).getTextContent().trim();
                    parseCoordinates(coordinatesText);
                    calculateProgressList();
                    kmlInfo.setText(String.format("路径点数量: %d, 路径总距离: %.2f m",
                            coordinates.size(), totalDistance));
                    kmlFilePath.setText("默认路径 (3km.kml)");
                }
                inputStream.close();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "加载默认KML文件时出错", e);
        }
    }

    private static class Coordinate {
        double latitude;
        double longitude;

        Coordinate(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private static class Timer {
        private long startTime;
        private long elapsedTime;
        private boolean paused;

        Timer() {
            reset();
        }

        void start() {
            if (paused) {
                startTime = System.currentTimeMillis() - elapsedTime;
                paused = false;
            }
        }

        void pause() {
            if (!paused) {
                elapsedTime = System.currentTimeMillis() - startTime;
                paused = true;
            }
        }

        void reset() {
            startTime = 0;
            elapsedTime = 0;
            paused = true;
        }

        long getElapsedTime() {
            if (paused) {
                return elapsedTime;
            } else {
                return System.currentTimeMillis() - startTime;
            }
        }
    }
}