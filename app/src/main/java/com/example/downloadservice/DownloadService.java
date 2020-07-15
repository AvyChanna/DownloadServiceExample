package com.example.downloadservice;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

// This will be a s̶t̶a̶r̶t̶e̶d̶ ̶s̶e̶r̶v̶i̶c̶e̶ started and bound service.
// binding will enable easy communication through public methods
public class DownloadService extends Service {

    // This custom binder will allow us to call public method(s) - getPercent
    public class LocalBinder extends Binder {
        DownloadService getService() {
            // Return this instance of LocalService so clients can call public methods
            return DownloadService.this;
        }
    }

    public int progress;
    private final String TAG = getClass().getSimpleName();
    private final IBinder binder = new LocalBinder();
    private OkHttpClient client;
    private ProgressListener progressListener;
    private Context context;
    // Target we publish for clients to send messages to IncomingHandler.
    final Messenger mMessenger = new Messenger(new IncomingHandler(Looper.myLooper()));

    @Override
    public void onCreate() {
        context = this;
        progressListener = new ProgressListener() {
            boolean firstUpdate = true;

            @Override
            public void update(long bytesRead, long contentLength, boolean done) {
                Log.d(TAG, String.valueOf(bytesRead));
                if (done) {
                    progress = 100;
                } else {
                    if (firstUpdate) {
                        firstUpdate = false;
                        if (contentLength == -1)
                            progress = 50; // I don't know content length
                    }
                    if (contentLength != -1) {
                        progress = (int) ((100 * bytesRead) / contentLength);

                    }

                }
                try {
                    Message msg = Message.obtain(null, AppConst.MSG_RECEIVE_PROGRESS);
                    Bundle b = new Bundle();
                    b.putInt(AppConst.DATA_RESPONSE_PROGRESS, progress);
                    msg.setData(b);
                    mMessenger.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };
        client = new OkHttpClient.Builder()
                .addNetworkInterceptor(chain -> {
                    Response originalResponse = chain.proceed(chain.request());
                    return originalResponse.newBuilder()
                            .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                            .build();
                })
                .build();
        progress = 0;
    }

    @NonNull
    public static String getFileNameFromURL(String url) {
        if (url == null) {
            return "download.bin";
        }
        try {
            URL resource = new URL(url);
            String host = resource.getHost();
            if (host.length() > 0 && url.endsWith(host)) {
                return host;
            }
        } catch (MalformedURLException e) {
            return "download.bin";
        }

        int startIndex = url.lastIndexOf('/') + 1;
        int length = url.length();

        // find end index for ?
        int lastQMPos = url.lastIndexOf('?');
        if (lastQMPos == -1) {
            lastQMPos = length;
        }

        // find end index for #
        int lastHashPos = url.lastIndexOf('#');
        if (lastHashPos == -1) {
            lastHashPos = length;
        }

        // calculate the end index
        int endIndex = Math.min(lastQMPos, lastHashPos);
        return url.substring(startIndex, endIndex);
    }

    @Override
    public int onStartCommand(@NonNull Intent intent, int flags, final int startId) {
//        return START_STICKY;
        final String url = intent.getStringExtra(AppConst.INTENT_DOWNLOAD_URL);
        final String path = getFileNameFromURL(url);
        if (url == null) {
            progress = -1;
            return START_REDELIVER_INTENT;
        }
        progress = 0;
        final Request request = new Request.Builder()
                .url(url)
                .build();
        new Thread(() -> {
            Looper.prepare();
            try {
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    showToast("Response Error");
                    progress = -1;
//                        Looper.myLooper().quit();
                }
                BufferedSource downloadedData = Objects.requireNonNull(response.body()).source();
                if (saveBufferToFile(path, downloadedData))
                    progress = 100;
                else progress = -1;
            } catch (Exception e) {
                // Logging error
                e.printStackTrace();
                showToast("Exception Occurred");
                progress = -1;
//                    Looper.myLooper().quit();
            }
        }).start();
        showToast("Service start");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    // Handler of incoming messages from client
    class IncomingHandler extends Handler {
        IncomingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case AppConst.MSG_SEND_PROGRESS:
                    try {
                        Message resp = Message.obtain(null, AppConst.MSG_RECEIVE_PROGRESS);
                        Bundle bResp = new Bundle();
                        bResp.putInt(AppConst.DATA_RESPONSE_PROGRESS, progress);
                        resp.setData(bResp);
                        msg.replyTo.send(resp);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
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
    public void onDestroy() {
        showToast("Service Stopped");
    }

    // utils and boilerplate

    private static class ProgressResponseBody extends ResponseBody {

        private final ResponseBody responseBody;
        private final ProgressListener progressListener;
        private BufferedSource bufferedSource;

        ProgressResponseBody(ResponseBody responseBody, ProgressListener progressListener) {
            this.responseBody = responseBody;
            this.progressListener = progressListener;
        }

        @Override
        public MediaType contentType() {
            return responseBody.contentType();
        }

        @Override
        public long contentLength() {
            return responseBody.contentLength();
        }

        @NonNull
        @Override
        public BufferedSource source() {
            if (bufferedSource == null) {
                bufferedSource = Okio.buffer(source(responseBody.source()));
            }
            return bufferedSource;
        }

        @NonNull
        @Contract("_ -> new")
        private Source source(Source source) {
            return new ForwardingSource(source) {
                long totalBytesRead = 0L;

                @Override
                public long read(@NonNull Buffer sink, long byteCount) throws IOException {
                    long bytesRead = super.read(sink, byteCount);
                    // read() returns the number of bytes read, or -1 if this source is exhausted.
                    totalBytesRead += bytesRead != -1 ? bytesRead : 0;
                    progressListener.update(totalBytesRead, responseBody.contentLength(), bytesRead == -1);
                    return bytesRead;
                }
            };
        }
    }

    public boolean saveBufferToFile(String path, BufferedSource buff) {
        try {
            File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(folder, path);
            if (!folder.exists()) {
                boolean folderCreated = folder.mkdir();
                Log.d(TAG, "folder created " + folderCreated);
            }
            if (file.exists()) {
                boolean fileDeleted = file.delete();
                Log.v(TAG, "file deleted " + fileDeleted);
            }
            Log.d(TAG, String.valueOf(folder));
            Log.d(TAG, String.valueOf(file));
            boolean fileCreated = file.createNewFile();
            Log.d(TAG, "file created" + fileCreated);
            BufferedSink sink = Okio.buffer(Okio.sink(file));
            sink.writeAll(buff);
            sink.close();
            return true;
        } catch (IOException ignored) {
        }
        return false;
    }

    void showToast(String str) {
        Toast.makeText(context, str, Toast.LENGTH_SHORT).show();
    }

    interface ProgressListener {
        void update(long bytesRead, long contentLength, boolean done);
    }

}
