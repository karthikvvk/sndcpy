package com.rom1v.sndcpy;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "sndcpy_prefs";
    private static final String PREF_SERVER_IP = "server_ip";
    private static final String PREF_SERVER_PORT = "server_port";
    private static final int DEFAULT_PORT = 28200;

    private static final int REQUEST_CODE_PERMISSION_AUDIO = 1;
    private static final int REQUEST_CODE_START_CAPTURE = 2;

    private EditText editServerIp;
    private EditText editServerPort;
    private Button btnStartStop;
    private TextView tvStatus;

    private boolean isStreaming = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editServerIp = findViewById(R.id.editServerIp);
        editServerPort = findViewById(R.id.editServerPort);
        btnStartStop = findViewById(R.id.btnStartStop);
        tvStatus = findViewById(R.id.tvStatus);

        // Restore saved values
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedIp = prefs.getString(PREF_SERVER_IP, "");
        int savedPort = prefs.getInt(PREF_SERVER_PORT, DEFAULT_PORT);
        editServerIp.setText(savedIp);
        editServerPort.setText(String.valueOf(savedPort));

        // Check if service is already running
        isStreaming = RecordService.isRunning();
        updateUiState();

        btnStartStop.setOnClickListener(v -> {
            if (isStreaming) {
                stopStreaming();
            } else {
                startStreaming();
            }
        });
    }

    private void startStreaming() {
        String ip = editServerIp.getText().toString().trim();
        String portStr = editServerPort.getText().toString().trim();

        if (TextUtils.isEmpty(ip)) {
            editServerIp.setError(getString(R.string.error_invalid_ip));
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            editServerPort.setError(getString(R.string.error_invalid_port));
            return;
        }

        // Save preferences
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(PREF_SERVER_IP, ip)
                .putInt(PREF_SERVER_PORT, port)
                .apply();

        // Hide keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(editServerIp.getWindowToken(), 0);

        // Request RECORD_AUDIO if not granted
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_PERMISSION_AUDIO);
            return;
        }

        // Request MediaProjection permission (screen/audio capture)
        requestMediaProjection();
    }

    private void requestMediaProjection() {
        MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mgr.createScreenCaptureIntent(), REQUEST_CODE_START_CAPTURE);
        tvStatus.setText(getString(R.string.status_connecting));
    }

    private void stopStreaming() {
        RecordService.stop(this);
        isStreaming = false;
        updateUiState();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestMediaProjection();
            } else {
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_START_CAPTURE) {
            if (resultCode == Activity.RESULT_OK) {
                String ip = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(PREF_SERVER_IP, "");
                int port = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getInt(PREF_SERVER_PORT, DEFAULT_PORT);
                RecordService.start(this, data, ip, port);
                isStreaming = true;
                updateUiState();
            } else {
                tvStatus.setText(getString(R.string.status_idle));
                Toast.makeText(this, "Permission denied — cannot capture audio", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateUiState() {
        if (isStreaming) {
            btnStartStop.setText(getString(R.string.btn_stop_streaming));
            btnStartStop.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFB71C1C)); // red
            tvStatus.setText(getString(R.string.status_streaming));
            tvStatus.setTextColor(0xFF2E7D32); // green
            editServerIp.setEnabled(false);
            editServerPort.setEnabled(false);
        } else {
            btnStartStop.setText(getString(R.string.btn_start_streaming));
            btnStartStop.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF1565C0)); // blue
            tvStatus.setText(getString(R.string.status_idle));
            tvStatus.setTextColor(0xFF757575); // grey
            editServerIp.setEnabled(true);
            editServerPort.setEnabled(true);
        }
    }
}
