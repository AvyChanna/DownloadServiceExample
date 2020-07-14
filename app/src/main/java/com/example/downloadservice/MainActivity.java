package com.example.downloadservice;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
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
        askForPermission();
    }

    private void askForPermission() {
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {


            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);

            } else {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
            }
        } else if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(getApplicationContext(), "Permission was denied", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (ActivityCompat.checkSelfPermission(this, permissions[0]) == PackageManager.PERMISSION_GRANTED) {

            if (requestCode == 101)
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    public void startDownload(View view) {
        String url = mText.getText().toString();
        if (!url.startsWith("http")) {
            Toast.makeText(this, "Invalid URL. Try 'https://www.example.com'", Toast.LENGTH_SHORT).show();
            return;
        }
        mButton.setEnabled(false);
        final Intent intent = new Intent(this.getBaseContext(), DownloadService.class);
        intent.putExtra("DownloadURL", url);
        Log.d(TAG, Environment.getExternalStorageDirectory() + File.separator + "down.bin");
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        serviceHandler = new Handler(Looper.getMainLooper());
        final Runnable r = new Runnable() {
            public void run() {
                if (!mBound) {
                    serviceHandler.postDelayed(this, delayMillis);
                    return;
                }
                int progress = mService.getPercentage();
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
        serviceHandler.postDelayed(r, delayMillis);

    }

    @Override
    protected void onDestroy() {
        if (mBound)
            unbindService(mConnection);
        super.onDestroy();
    }
}