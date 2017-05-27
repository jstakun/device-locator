package net.gmsworld.devicelocator.Utilities;

import android.content.Context;
import android.net.ConnectivityManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class Network {

    private static final String TAG = Network.class.getSimpleName();

    private static final String FORM_ENCODING = "application/x-www-form-urlencoded;charset=UTF-8";

    protected static boolean isNetworkAvailable(final Context context) {
        final ConnectivityManager connectivityManager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        return connectivityManager.getActiveNetworkInfo() != null && connectivityManager.getActiveNetworkInfo().isConnected();
    }

    protected static void get(final String urlString, final OnGetFinishListener onGetFinishListener) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());

                    BufferedReader r = new BufferedReader(new InputStreamReader(in));
                    StringBuilder total = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) {
                        total.append(line).append('\n');
                    }

                    onGetFinishListener.onGetFinish(total.toString());
                } catch (Exception e) {
                    onGetFinishListener.onGetFinish("{}");
                }
            }
        };

        thread.start();
    }

    protected static void get(final String urlString, final Map<String, String> headers, final OnGetFinishListener onGetFinishListener) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

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

                    onGetFinishListener.onGetFinish(total.toString());
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    onGetFinishListener.onGetFinish("{}");
                }
            }
        };

        thread.start();
    }

    public static void post(final String urlString, final String content, final String contentType, final OnGetFinishListener onGetFinishListener) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestMethod("POST");

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

                    int responseCode = urlConnection.getResponseCode();

                    onGetFinishListener.onGetFinish(Integer.toString(responseCode));
                } catch (Exception e) {
                    onGetFinishListener.onGetFinish("500");
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

                    int responseCode = urlConnection.getResponseCode();

                    onGetFinishListener.onGetFinish(Integer.toString(responseCode));
                } catch (Exception e) {
                    onGetFinishListener.onGetFinish("500");
                }
            }
        };

        thread.start();
    }

    public static void execute(Runnable r) {
        Thread thread = new Thread(r);
        thread.start();
    }

    public static String getDeviceId(Context context) {
        String androidDeviceId = null;

        if (context != null) {
            //android.Manifest.permission.READ_PHONE_STATE required
            // get telephony imei
            try {
                final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                androidDeviceId = tm.getDeviceId(); //imei
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }

            // get internal android device id
            if (androidDeviceId == null || androidDeviceId.length() == 0) {
                try {
                    androidDeviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }

        if (androidDeviceId == null) {
            androidDeviceId = "unknown";
        }

        return androidDeviceId;
    }

    public interface OnGetFinishListener {
        void onGetFinish(String result);
    }
}
