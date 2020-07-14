package com.example.downloadservice;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private final String TAG = getClass().getSimpleName();
    DownloadService mService;
    boolean mBound = false;
    // This connection will communicate with our service
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            DownloadService.LocalBinder binder = (DownloadService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
    private Handler serviceHandler;
    private ProgressBar mProgressBar;
    private Button mButton;
    private EditText mText;
    int delayMillis = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mProgressBar = findViewById(R.id.progressBar);
        mButton = findViewById(R.id.button);
        mText = findViewById(R.id.editText);
    }

    @Override
    protected void onStart() {
        super.onStart();
        askForPermission();
        final Intent connectIntent = new Intent(this.getBaseContext(), DownloadService.class);
        connectIntent.putExtra(IntentConstant.INTENT_CHECK_SERVICE, true);
        if (startService(connectIntent) != null) {
            // Service is already running. So, there is a download going on
            mButton.setEnabled(false);
            pollForProgress(connectIntent);
        } else {
            // Service is free to download next file
            mButton.setEnabled(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(mConnection);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final Intent downloadIntent = new Intent(this.getBaseContext(), DownloadService.class);
        bindService(downloadIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private void askForPermission() {
        String storagePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(MainActivity.this, storagePermission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{storagePermission}, 101);
        } else if (ContextCompat.checkSelfPermission(MainActivity.this, storagePermission) == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(getApplicationContext(), "Permission was denied", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (ActivityCompat.checkSelfPermission(this, permissions[0]) == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == 101)
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
        } else Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
    }

    public void startDownload(View view) {
        String url = mText.getText().toString();
        if (!url.startsWith("http")) {
            Toast.makeText(this, "Invalid URL. Try 'https://www.example.com'", Toast.LENGTH_SHORT).show();
            return;
        }
        mButton.setEnabled(false);
        final Intent downloadIntent = new Intent(this.getBaseContext(), DownloadService.class);
        downloadIntent.putExtra(IntentConstant.INTENT_DOWNLOAD_URL, url);
        startService(downloadIntent);
        pollForProgress(downloadIntent);

    }

    public void pollForProgress(final Intent intent) {
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        serviceHandler = new Handler(Looper.getMainLooper());
        Runnable pollService = new Runnable() {
            public void run() {
                if (!mBound) {
                    serviceHandler.postDelayed(this, delayMillis);
                    return;
                }
                int progress = mService.getProgress();
                Log.d("progress", String.valueOf(progress));
                if (progress < 0 || progress >= 100) {
                    // Service is done
                    mProgressBar.setProgress(0);
                    stopService(intent);
                    unbindService(mConnection);
                    mButton.setEnabled(true);
                } else {
                    mProgressBar.setProgress(progress);
                    serviceHandler.postDelayed(this, delayMillis);
                }
            }
        };

        // This starts our runnable.
        serviceHandler.postDelayed(pollService, delayMillis);
    }

    @Override
    protected void onDestroy() {
        if (mBound)
            unbindService(mConnection);
        super.onDestroy();
    }
}