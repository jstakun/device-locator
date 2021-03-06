package net.gmsworld.devicelocator.utilities;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.R;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class Network {

    private static final String TAG = Network.class.getSimpleName();

    private static final String FORM_ENCODING = "application/x-www-form-urlencoded;charset=UTF-8";

    private static final int SOCKET_TIMEOUT = 60 * 1000; //1 minute

    private static Map<String, String> defaultHeaders;

    public static boolean isNetworkAvailable(final Context context) {
        final ConnectivityManager connectivityManager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        if (connectivityManager != null) {
            final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null) {
                return networkInfo.isConnected();
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public static void get(final Context context, final String urlString, final Map<String, String> headers, final OnGetFinishListener onGetFinishListener) {
        new GetTask(context, urlString, headers, onGetFinishListener).execute();
    }

    public static void post(final Context context, final String urlString, final String content, final String contentType, final Map<String, String> headers, final OnGetFinishListener onGetFinishListener) {
        new PostTask(context, urlString, content, contentType, headers, onGetFinishListener).execute();
    }

    public static void uploadScreenshot(final Context context, final String fileUrl, final byte[] file, final String filename, final Map<String, String> headers, final OnGetFinishListener onGetFinishListener) {
        new UploadImageTask(context, fileUrl, file, filename, headers, onGetFinishListener).execute();
    }

    public interface OnGetFinishListener {
        void onGetFinish(String result, int responseCode, String url);
    }

    public static String get(Context context, String urlString, Map<String, String> headers) {
        HttpURLConnection urlConnection;
        String response = null;
        //int responseCode = -1;

        try {
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(SOCKET_TIMEOUT);
            urlConnection.setReadTimeout(SOCKET_TIMEOUT);

            for (Map.Entry<String, String> header : getDefaultHeaders(context).entrySet()) {
                urlConnection.setRequestProperty(header.getKey(), header.getValue());
            }

            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    urlConnection.setRequestProperty(header.getKey(), header.getValue());
                }
            }

            urlConnection.connect();

            InputStream in = new BufferedInputStream(urlConnection.getInputStream());

            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            StringBuilder total = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                total.append(line).append('\n');
            }

            response = total.toString();
            //responseCode = urlConnection.getResponseCode();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            //if (responseCode == -1) {
                //responseCode = 500;
            //response = e.getClass().getName() + ": " + e.getMessage();
            //}
        }

        return response;
    }

    private static void handleHttpStatus(String response, int responseCode, String urlString, Context context) {
        if (responseCode == 401) {
            if (context != null) {
                if (!StringUtils.startsWith(urlString, context.getString(R.string.authnUrl))) {
                    Log.d(TAG, "GMS Token is invalid and will be deleted from device!");
                    PreferenceManager.getDefaultSharedPreferences(context).edit().remove(DeviceLocatorApp.GMS_TOKEN).apply();
                } else {
                    Log.d(TAG, "GMS World User authentication failed!");
                }
            } else {
                Log.e(TAG, "GMS Token is invalid but can't be deleted from device!");
            }
        } else if (responseCode >= 400) {
            Log.e(TAG, "Received error response " + responseCode + ": " + response + " from " + urlString);
        } else {
            Log.d(TAG, "Received response " + responseCode + ": " + response + " from " + urlString);
        }
    }

    public static Map<String, String> getDefaultHeaders(Context context) {
        if (defaultHeaders == null) {
            defaultHeaders = new HashMap<>();
            defaultHeaders.put("X-GMS-AppId", "2");
            defaultHeaders.put("X-GMS-Scope", "dl");
            if (context != null) {
                defaultHeaders.put("User-Agent", AppUtils.getInstance().getUserAgent(context));
                defaultHeaders.put("Accept-Language", AppUtils.getInstance().getCurrentLocale(context).getLanguage());
                defaultHeaders.put("X-GMS-AppVersionId", Integer.toString(AppUtils.getInstance().getVersionCode(context)));
            }
        }
        return defaultHeaders;
    }

    private static String encodeQueryStringUTF8(final String queryString) throws Exception {
        String[] pairs = queryString.split("&");
        StringBuilder encQueryString = new StringBuilder();
        for (String pair : pairs) {
            if (pair.contains("=")) {
                int idx = pair.indexOf("=");
                if (encQueryString.length() > 0) {
                    encQueryString.append("&");
                }
                encQueryString.append(pair.substring(0, idx)).append("=").append(URLEncoder.encode(pair.substring(idx + 1), "UTF-8"));
            }

        }
        return encQueryString.toString();
    }

    static class PostTask extends AsyncTask<Void, Integer, Integer> {

        private final OnGetFinishListener onGetFinishListener;
        private final String urlString;
        private final String content;
        private final String contentType;
        private String response;
        private final Map<String, String> headers;
        private final WeakReference<Context> context;

        PostTask(final Context context, final String urlString, final String content, final String contentType, final Map<String, String> headers, final OnGetFinishListener onGetFinishListener) {
            this.urlString = urlString;
            this.content = content;
            this.contentType = contentType;
            this.headers = headers;
            this.context = new WeakReference<>(context);
            this.onGetFinishListener = onGetFinishListener;
        }

        @Override
        protected Integer doInBackground(Void... params) {
            int responseCode = -1;

            try {
                URL url = new URL(urlString);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setConnectTimeout(SOCKET_TIMEOUT);
                urlConnection.setReadTimeout(SOCKET_TIMEOUT);

                for (Map.Entry<String, String> header : getDefaultHeaders(context.get()).entrySet()) {
                    urlConnection.setRequestProperty(header.getKey(), header.getValue());
                }

                if (headers != null) {
                    for (Map.Entry<String, String> header : headers.entrySet()) {
                        urlConnection.setRequestProperty(header.getKey(), header.getValue());
                    }
                }

                if (content != null) {
                    String contentEnc;
                    if (contentType != null) {
                        contentEnc = content;
                        urlConnection.setRequestProperty("Content-Type", contentType);
                    } else {
                        contentEnc = encodeQueryStringUTF8(content);
                        urlConnection.setRequestProperty("Content-Type", FORM_ENCODING);
                    }

                    urlConnection.setRequestProperty("Content-Length", Integer.toString(contentEnc.length()));

                    urlConnection.setDoInput(true);
                    urlConnection.setDoOutput(true);

                    //Log.d(TAG, "Sending post: " + content);

                    //Send request
                    DataOutputStream wr = new DataOutputStream(urlConnection.getOutputStream());
                    wr.writeBytes(contentEnc);
                    wr.flush();
                    wr.close();
                } else {
                    urlConnection.connect();
                }

                responseCode = urlConnection.getResponseCode();

                if (urlConnection.getContentLength() > 0) {

                    InputStream in;

                    if (responseCode < 400) {
                        in = new BufferedInputStream(urlConnection.getInputStream());
                    } else {
                        in = new BufferedInputStream(urlConnection.getErrorStream());
                    }

                    BufferedReader r = new BufferedReader(new InputStreamReader(in));
                    StringBuilder total = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) {
                        total.append(line).append('\n');
                    }

                    response = total.toString();
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                if (responseCode == -1) {
                    responseCode = 500;
                    response = e.getClass().getName() + ": " + e.getMessage();
                }
            }
            return responseCode;
        }

        @Override
        public void onPostExecute(Integer responseCode) {
            if (onGetFinishListener != null) {
                onGetFinishListener.onGetFinish(response, responseCode, urlString);
                handleHttpStatus(response, responseCode, urlString, context.get());
            }
        }
    }

    static class UploadImageTask extends AsyncTask<Void, Integer, Integer> {

        private final OnGetFinishListener onGetFinishListener;
        private final String urlString;
        private final String filename;
        private String response;
        private final Map<String, String> headers;
        private final byte[] file;
        private final WeakReference<Context> context;

        UploadImageTask(final Context context, final String urlString, final byte[] file, final String filename, final Map<String, String> headers, final OnGetFinishListener onGetFinishListener) {
            this.context = new WeakReference<>(context);
            this.urlString = urlString;
            this.filename = filename;
            this.file = file;
            this.headers = headers;
            this.onGetFinishListener = onGetFinishListener;
        }

        @Override
        protected Integer doInBackground(Void... params) {
            InputStream is = null;
            int responseCode = -1;

            final String attachmentName = "screenshot";
            final String crlf = "\r\n";
            final String twoHyphens = "--";
            final String boundary = "*****";

            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(SOCKET_TIMEOUT);
                conn.setReadTimeout(SOCKET_TIMEOUT);

                for (Map.Entry<String, String> header : getDefaultHeaders(context.get()).entrySet()) {
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

                responseCode = conn.getResponseCode();

                if (conn.getContentLength() > 0) {

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        if (conn.getContentType().contains("gzip")) {
                            is = new GZIPInputStream(conn.getInputStream());
                        } else {
                            is = conn.getInputStream();
                        }
                    } else {
                        is = conn.getErrorStream();
                        Log.e(TAG, urlString + " loading error: " + responseCode);
                    }

                    if (is != null) {
                        //Read response
                        response = IOUtils.toString(is, "UTF-8");
                        //Log.d(TAG, "Received following server response: " + response);
                    }
                }
            } catch (Throwable e) {
                Log.d(TAG, ".uploadScreenshot() exception: " + e.getMessage(), e);
                if (responseCode == -1) {
                    responseCode = 500;
                    response = e.getClass().getName() + ": " + e.getMessage();
                }
            } finally {
                try {
                    if (is != null) {
                        is.close();
                    }
                } catch (Exception e) {
                    Log.d(TAG, ".uploadScreenshot() exception: " + e.getMessage(), e);
                }
            }
            return responseCode;
        }

        @Override
        public void onPostExecute(Integer responseCode) {
            if (onGetFinishListener != null) {
                onGetFinishListener.onGetFinish(response, responseCode, urlString);
                handleHttpStatus(response, responseCode, urlString, context.get());
            }
        }
    }

    static class GetTask extends AsyncTask<Void, Integer, Integer> {

        private final OnGetFinishListener onGetFinishListener;
        private final String urlString;
        private final WeakReference<Context> context;
        private final Map<String, String> headers;
        private String response;

        GetTask(final Context context, final String urlString, final Map<String, String> headers, final OnGetFinishListener onGetFinishListener) {
            this.context = new WeakReference<>(context);
            this.urlString = urlString;
            this.headers = headers;
            this.onGetFinishListener = onGetFinishListener;
        }

        @Override
        protected Integer doInBackground(Void... params) {
            HttpURLConnection urlConnection;
            int responseCode = -1;

            try {
                URL url = new URL(urlString);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setConnectTimeout(SOCKET_TIMEOUT);
                urlConnection.setReadTimeout(SOCKET_TIMEOUT);

                for (Map.Entry<String, String> header : getDefaultHeaders(context.get()).entrySet()) {
                    urlConnection.setRequestProperty(header.getKey(), header.getValue());
                }

                if (headers != null) {
                    for (Map.Entry<String, String> header : headers.entrySet()) {
                        urlConnection.setRequestProperty(header.getKey(), header.getValue());
                    }
                }

                urlConnection.connect();

                responseCode = urlConnection.getResponseCode();

                InputStream in;

                if (urlConnection.getContentLength() > 0) {

                    if (responseCode < 400) {
                        in = new BufferedInputStream(urlConnection.getInputStream());
                    } else {
                        in = new BufferedInputStream(urlConnection.getErrorStream());
                    }

                    BufferedReader r = new BufferedReader(new InputStreamReader(in));
                    StringBuilder total = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) {
                        total.append(line).append('\n');
                    }

                    response = total.toString();
                }

            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                if (responseCode == -1) {
                    responseCode = 500;
                    response = e.getClass().getName() + ": " + e.getMessage();
                }
            }

            return responseCode;
        }

        @Override
        public void onPostExecute(Integer responseCode) {
            if (onGetFinishListener != null) {
                onGetFinishListener.onGetFinish(response, responseCode, urlString);
                handleHttpStatus(response, responseCode, urlString, context.get());
            }
        }
    }
}