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
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
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
    private Messenger replyMessenger = null;
    private boolean mBound;
    private ServiceConnection mConnection;
    private ProgressBar mProgressBar;
    private Button mButton;
    private EditText mText;

    class ResponseHandler extends Handler {
        ResponseHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case AppConst.MSG_RECEIVE_PROGRESS:
                    int progress = msg.getData().getInt(AppConst.DATA_RESPONSE_PROGRESS, 0);
                    showProgress(progress);
                    break;
//                case AppConst.MSG_DUMMY:
//                    // add more cases here
//                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mProgressBar = findViewById(R.id.progressBar);
        mButton = findViewById(R.id.button);
        mText = findViewById(R.id.editText);
        mConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder iBinder) {
                replyMessenger = new Messenger(iBinder);
                mBound = true;
            }

            public void onServiceDisconnected(ComponentName className) {
                // This is called when the connection with the service has been unexpectedly disconnected -- that is,
                // its process crashed.
                replyMessenger = null;
                mBound = false;
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        askForPermission();
        final Intent connectIntent = new Intent(this.getBaseContext(), DownloadService.class);
        connectIntent.putExtra(AppConst.INTENT_CHECK_SERVICE, true);
        startService(connectIntent);
        bindService(connectIntent, mConnection, Context.BIND_AUTO_CREATE);
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
        Message msg = Message.obtain(null, AppConst.MSG_SEND_PROGRESS);
        msg.replyTo = new Messenger(new ResponseHandler(Looper.myLooper()));
        Bundle b = new Bundle();
        msg.setData(b);
        try {
            replyMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
            showProgress(0);
        }
    }


    //    public void pollForProgress(final Intent intent) {
//        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
//        serviceHandler = new Handler(Looper.getMainLooper());
//        Runnable pollService = new Runnable() {
//            public void run() {
//                if (!mBound) {
//                    serviceHandler.postDelayed(this, delayMillis);
//                    return;
//                }
//                int progress = mService.getProgress();
//                Log.d("progress", String.valueOf(progress));
//                if (progress < 0 || progress >= 100) {
//                    // Service is done
//                    mProgressBar.setProgress(0);
//                    stopService(intent);
//                    unbindService(mConnection);
//                    mButton.setEnabled(true);
//                } else {
//                    mProgressBar.setProgress(progress);
//                    serviceHandler.postDelayed(this, delayMillis);
//                }
//            }
//        };
//
//        // This starts our runnable.
//        serviceHandler.postDelayed(pollService, delayMillis);
//    }
    void showProgress(int progress) {
        if (progress < 0 || progress > 100)
            progress = 0;
        mProgressBar.setProgress(progress);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
    }
}