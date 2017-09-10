package net.gmsworld.devicelocator.Utilities;

import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class Network {

    private static final String TAG = Network.class.getSimpleName();

    private static final String FORM_ENCODING = "application/x-www-form-urlencoded;charset=UTF-8";

    private static final int SOCKET_TIMEOUT = 60 * 1000; //1 minute

    private static final Map<String, String> defaultHeaders = new HashMap<String, String>();

    static {
        defaultHeaders.put("X-GMS-AppId", "2");
        defaultHeaders.put("X-GMS-Scope", "dl");
        defaultHeaders.put("User-Agent", "Device Locator/0.2-10 (+http://www.gms-world.net)");
    }

    protected static boolean isNetworkAvailable(final Context context) {
        final ConnectivityManager connectivityManager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        return connectivityManager.getActiveNetworkInfo() != null && connectivityManager.getActiveNetworkInfo().isConnected();
    }

    public static void get(final String urlString, final OnGetFinishListener onGetFinishListener) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                HttpURLConnection urlConnection = null;
                try {
                    URL url = new URL(urlString);
                    urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setConnectTimeout(SOCKET_TIMEOUT);
                    urlConnection.setReadTimeout(SOCKET_TIMEOUT);

                    for (Map.Entry<String, String> header : defaultHeaders.entrySet()) {
                        urlConnection.setRequestProperty(header.getKey(), header.getValue());
                    }

                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());

                    BufferedReader r = new BufferedReader(new InputStreamReader(in));
                    StringBuilder total = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) {
                        total.append(line).append('\n');
                    }

                    onGetFinishListener.onGetFinish(total.toString(), urlConnection.getResponseCode(), urlString);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    int responseCode = 1;
                    if (urlConnection != null) {
                        try {
                            responseCode = urlConnection.getResponseCode();
                        } catch (IOException e1) {
                        }
                    }
                    onGetFinishListener.onGetFinish("{}", responseCode, urlString);
                }
            }
        };

        thread.start();
    }

    /*protected static void get(final String urlString, final Map<String, String> headers, final OnGetFinishListener onGetFinishListener) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                HttpURLConnection urlConnection = null;
                try {
                    URL url = new URL(urlString);
                    urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setConnectTimeout(SOCKET_TIMEOUT);
                    urlConnection.setReadTimeout(SOCKET_TIMEOUT);

                    for (Map.Entry<String, String> header : defaultHeaders.entrySet()) {
                        urlConnection.setRequestProperty(header.getKey(), header.getValue());
                    }

                    for (Map.Entry<String, String> header : headers.entrySet()) {
                        urlConnection.setRequestProperty(header.getKey(), header.getValue());
                    }

                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());

                    BufferedReader r = new BufferedReader(new InputStreamReader(in));
                    StringBuilder total = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) {
                        total.append(line).append('\n');
                    }

                    onGetFinishListener.onGetFinish(total.toString(), urlConnection.getResponseCode(), urlString);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    int responseCode = 1;
                    if (urlConnection != null) {
                        try {
                            responseCode = urlConnection.getResponseCode();
                        } catch (IOException e1) {
                        }
                    }
                    onGetFinishListener.onGetFinish("{}", responseCode, urlString);
                }
            }
        };

        thread.start();
    }*/

    public static void post(final String urlString, final String content, final String contentType, final OnGetFinishListener onGetFinishListener) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestMethod("POST");
                    urlConnection.setConnectTimeout(SOCKET_TIMEOUT);
                    urlConnection.setReadTimeout(SOCKET_TIMEOUT);

                    for (Map.Entry<String, String> header : defaultHeaders.entrySet()) {
                        urlConnection.setRequestProperty(header.getKey(), header.getValue());
                    }

                    if (content != null) {
                        urlConnection.setRequestProperty("Content-Length", Integer.toString(content.length()));

                        if (contentType != null) {
                            urlConnection.setRequestProperty("Content-Type", contentType);
                        } else {
                            urlConnection.setRequestProperty("Content-Type", FORM_ENCODING);
                        }

                        urlConnection.setDoInput(true);
                        urlConnection.setDoOutput(true);

                        //Log.d(TAG, "Sending post: " + content);

                        //Send request
                        DataOutputStream wr = new DataOutputStream(urlConnection.getOutputStream());
                        wr.writeBytes(content);
                        wr.flush();
                        wr.close();
                    } else {
                        urlConnection.connect();
                    }

                    onGetFinishListener.onGetFinish(null, urlConnection.getResponseCode(), urlString);
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage(), e);
                    onGetFinishListener.onGetFinish(null, -1, urlString);
                }
            }
        };

        thread.start();
    }

    public static void post(final String urlString, final String content, final String contentType, final Map<String, String> headers, final OnGetFinishListener onGetFinishListener) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestMethod("POST");
                    urlConnection.setConnectTimeout(SOCKET_TIMEOUT);
                    urlConnection.setReadTimeout(SOCKET_TIMEOUT);

                    for (Map.Entry<String, String> header : defaultHeaders.entrySet()) {
                        urlConnection.setRequestProperty(header.getKey(), header.getValue());
                    }

                    for (Map.Entry<String, String> header : headers.entrySet()) {
                        urlConnection.setRequestProperty(header.getKey(), header.getValue());
                    }

                    if (content != null) {
                        urlConnection.setRequestProperty("Content-Length", Integer.toString(content.length()));

                        if (contentType != null) {
                            urlConnection.setRequestProperty("Content-Type", contentType);
                        } else {
                            urlConnection.setRequestProperty("Content-Type", FORM_ENCODING);
                        }

                        urlConnection.setDoInput(true);
                        urlConnection.setDoOutput(true);

                        //Log.d(TAG, "Sending post: " + content);

                        //Send request
                        DataOutputStream wr = new DataOutputStream(urlConnection.getOutputStream());
                        wr.writeBytes(content);
                        wr.flush();
                        wr.close();
                    } else {
                        urlConnection.connect();
                    }

                    onGetFinishListener.onGetFinish(null, urlConnection.getResponseCode(), urlString);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    onGetFinishListener.onGetFinish(null, -1, urlString);
                }
            }
        };

        thread.start();
    }

    public static void uploadScreenshot(final String fileUrl, final byte[] file, final String filename, final Map<String, String> headers, final OnGetFinishListener onGetFinishListener) {

        Thread thread = new Thread() {
            @Override
            public void run() {

                InputStream is = null;
                String response = null;

                final String attachmentName = "screenshot";
                final String crlf = "\r\n";
                final String twoHyphens = "--";
                final String boundary = "*****";

                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(fileUrl).openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(SOCKET_TIMEOUT);
                    conn.setReadTimeout(SOCKET_TIMEOUT);

                    for (Map.Entry<String, String> header : defaultHeaders.entrySet()) {
                        conn.setRequestProperty(header.getKey(), header.getValue());
                    }

                    for (Map.Entry<String, String> header : headers.entrySet()) {
                        conn.setRequestProperty(header.getKey(), header.getValue());
                    }

                    conn.setRequestProperty("X-GMS-Silent", "true");
                    conn.setRequestProperty("X-GMS-BucketName", "device-locator");
                    conn.setRequestProperty("Accept-Encoding", "gzip");

                    //write file
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                    DataOutputStream request = new DataOutputStream(conn.getOutputStream());

                    request.writeBytes(twoHyphens + boundary + crlf);
                    request.writeBytes("Content-Disposition: form-data; name=\"" + attachmentName + "\";filename=\"" + filename + "\"" + crlf);
                    request.writeBytes(crlf);

                    IOUtils.write(file, request);

                    request.writeBytes(crlf);
                    request.writeBytes(twoHyphens + boundary + twoHyphens + crlf);

                    request.flush();
                    request.close();

                    int responseCode = conn.getResponseCode();

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        if (conn.getContentType().indexOf("gzip") != -1) {
                            is = new GZIPInputStream(conn.getInputStream());
                        } else {
                            is = conn.getInputStream();
                        }
                    } else {
                        is = conn.getErrorStream();
                        Log.e(TAG, fileUrl + " loading error: " + responseCode);
                    }

                    if (is != null) {
                        //Read response
                        response = IOUtils.toString(is, "UTF-8");
                        Log.d(TAG, "Received following server response: " + response);
                    }
                    onGetFinishListener.onGetFinish(response, responseCode, fileUrl);
                } catch (Throwable e) {
                    Log.d(TAG, ".uploadScreenshot() exception: " + e.getMessage(), e);
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                    } catch (IOException e) {
                    }
                }

            }
        };

        thread.start();
    }

    public interface OnGetFinishListener {
        void onGetFinish(String result, int responseCode, String url);
    }
}
