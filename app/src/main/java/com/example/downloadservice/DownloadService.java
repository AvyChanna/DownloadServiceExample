package com.example.downloadservice;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

import okhttp3.Interceptor;
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

    int progress;
    private static final String TAG = "DownloadService";
    private final IBinder binder = new LocalBinder();
    OkHttpClient client;
    ProgressListener progressListener;
    Context context;

    //    private RequestQueue reqQueue;
    void showToast(String str) {
        Toast.makeText(context, str, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCreate() {
//        reqQueue = Volley.newRequestQueue(this);
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
                            progress = 50;
                    }
                    if (contentLength != -1) {
                        progress = (int) ((100 * bytesRead) / contentLength);
                        System.out.format("%d%% done\n", progress);
                    }
                }
            }
        };
        client = new OkHttpClient.Builder()
                .addNetworkInterceptor(new Interceptor() {
                    @NotNull
                    @Override
                    public Response intercept(@NotNull Chain chain) throws IOException {
                        Response originalResponse = chain.proceed(chain.request());
                        return originalResponse.newBuilder()
                                .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                                .build();
                    }
                })
                .build();
        progress = 0;
    }
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
        }
        catch(MalformedURLException e) {
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
    public int onStartCommand(Intent intent, int flags, final int startId) {
        final String url = intent.getStringExtra("DownloadURL");
        final String path = getFileNameFromURL(url);


        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                Request request = new Request.Builder()
                        .url(url)
                        .build();


                try{
                    Response response = client.newCall(request).execute();
                    if (!response.isSuccessful()) {
                        showToast("Response Error");
                        progress = -1;
//                        Looper.myLooper().quit();
                    }
                    BufferedSource downloadedData = Objects.requireNonNull(response.body()).source();
                    File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File file = new File(folder, path);
                    if (!folder.exists()) {
                        boolean folderCreated = folder.mkdir();
                        Log.d(TAG, "folder created "+ folderCreated);
                    }
                    if (file.exists()) {
                        boolean fileDeleted = file.delete();
                        Log.v(TAG, "file deleted "+ fileDeleted);
                    }
                    Log.d(TAG, String.valueOf(folder));
                    Log.d(TAG, String.valueOf(file));
                    boolean fileCreated = file.createNewFile();
                    Log.d(TAG, "file created" + fileCreated);
                    BufferedSink sink = Okio.buffer(Okio.sink(file));
                    sink.writeAll(downloadedData);
                    sink.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    showToast("Exception Occurred");
                    progress = -1;
//                    Looper.myLooper().quit();
                }
            }
        }).start();

        //        StringRequest downloadRequest= new StringRequest(url, path, new Response.Listener<String>() {
//            @Override
//            public void onResponse(String path) {
//                progress = 100;
//            }
//        }, new Response.ErrorListener() {
//            @Override
//            public void onErrorResponse(VolleyError error) {
//                Toast.makeText(context, "Error while downloading: " + error.toString(), Toast.LENGTH_LONG).show();
//                progress = -1;
//            }
//        });
//        downloadRequest.get
//        downloadRequest.setOnProgressListener(new Response.ProgressListener() {
//            @Override
//            public void onProgress(long downloaded, long totalSize) {
//                int percentage = (int) ((downloaded / ((float) totalSize)) * 100);
//                progress = percentage;
//            }
//        });
//        downloadRequest.setTag(TAG);
//        reqQueue.add(downloadRequest);
//        reqQueue.start();
        showToast("Service start");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
//        reqQueue.cancelAll(TAG);
//        reqQueue.stop();
        showToast("Service Stopped");
    }

    public int getPercentage() {
        return progress;
    }

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

        @NotNull
        @Override
        public BufferedSource source() {
            if (bufferedSource == null) {
                bufferedSource = Okio.buffer(source(responseBody.source()));
            }
            return bufferedSource;
        }

        private Source source(Source source) {
            return new ForwardingSource(source) {
                long totalBytesRead = 0L;

                @Override
                public long read(@NotNull Buffer sink, long byteCount) throws IOException {
                    long bytesRead = super.read(sink, byteCount);
                    // read() returns the number of bytes read, or -1 if this source is exhausted.
                    totalBytesRead += bytesRead != -1 ? bytesRead : 0;
                    progressListener.update(totalBytesRead, responseBody.contentLength(), bytesRead == -1);
                    return bytesRead;
                }
            };
        }
    }

    interface ProgressListener {
        void update(long bytesRead, long contentLength, boolean done);
    }

}
