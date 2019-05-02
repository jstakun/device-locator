package net.gmsworld.devicelocator.services;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.androidhiddencamera.CameraConfig;
import com.androidhiddencamera.CameraError;
import com.androidhiddencamera.HiddenCameraService;
import com.androidhiddencamera.HiddenCameraUtils;
import com.androidhiddencamera.config.CameraFacing;
import com.androidhiddencamera.config.CameraImageFormat;
import com.androidhiddencamera.config.CameraResolution;
import com.androidhiddencamera.config.CameraRotation;

import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.Files;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.NotificationUtils;
import net.gmsworld.devicelocator.utilities.Permissions;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

import static android.graphics.Bitmap.CompressFormat.JPEG;

/**
 * Created by jstakun on 9/6/17.
 */

public class HiddenCaptureImageService extends HiddenCameraService implements OnLocationUpdatedListener {

    private static final String TAG = HiddenCaptureImageService.class.getSimpleName();
    private static final DecimalFormat latAndLongFormat = new DecimalFormat("#.######");

    private boolean isTest = false;
    private String sender = null, app = null;
    private Location location;

    private static boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");
        SmartLocation.with(this).location(new LocationGooglePlayServicesWithFallbackProvider(this)).oneFix().start(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SmartLocation.with(this).location(new LocationGooglePlayServicesWithFallbackProvider(this)).stop();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isTest = intent.getBooleanExtra("test", false);
        sender = intent.getStringExtra("sender");
        app = intent.getStringExtra("app");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NotificationUtils.WORKER_NOTIFICATION_ID, NotificationUtils.buildWorkerNotification(this));
        }

        if (Permissions.haveCameraPermission(this)) {

            if (HiddenCameraUtils.canOverDrawOtherApps(this) && !isRunning) {

                isRunning = true;

                CameraConfig cameraConfig = new CameraConfig()
                        .getBuilder(this)
                        .setCameraFacing(CameraFacing.FRONT_FACING_CAMERA)
                        .setCameraResolution(CameraResolution.HIGH_RESOLUTION) //.MEDIUM_RESOLUTION)
                        .setImageFormat(CameraImageFormat.FORMAT_JPEG)
                        .setImageRotation(CameraRotation.ROTATION_270)
                        .build();

                try {
                    startCamera(cameraConfig);

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                takePicture();
                            } catch (Throwable e) {
                                Log.e(TAG, "Failed to take a picture!", e);
                            }
                        }
                    }, 1000);
                } catch (Throwable e) {
                    Log.e(TAG, e.getMessage(), e);
                    onCameraError(CameraError.ERROR_CAMERA_OPEN_FAILED);
                }
            } else {
                Log.e(TAG, "Draw over other apps permission is missing. Can't take a picture");
                stopSelf();
            }
        } else {
            if (isRunning) {
                Log.e(TAG, "Camera is running. Skipping this request!");
            } else {
                Log.e(TAG, "Camera permission not available!");
            }
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onImageCapture(final @NonNull File imageFile) {
        if (!isTest) {
            if (Network.isNetworkAvailable(this)) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888; //.RGB_565; //
                Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
                if (bitmap != null) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();

                    //final String suffix = ".png";
                    //bitmap.compress(PNG, 0, out);

                    final String suffix = ".jpg";
                    bitmap.compress(JPEG, 100, out);

                    Log.d(TAG, "Image will be sent to server");

                    //send image to server and send notification with link

                    Map<String, String> headers = new HashMap<>();

                    final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
                    String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN, "");
                    String uploadUrl = getString(R.string.photoUploadUrl);
                    if (StringUtils.isNotEmpty(tokenStr)) {
                        headers.put("Authorization", "Bearer " + tokenStr);
                        uploadUrl = getString(R.string.securePhotoUploadUrl);
                    }

                    headers.put("X-GMS-AppId", "2");
                    headers.put("X-GMS-Scope", "dl");
                    headers.put("X-GMS-DeviceName", Messenger.getDeviceId(this, true));

                    if (location != null) {
                        headers.put(Messenger.LAT_HEADER, latAndLongFormat.format(location.getLatitude()));
                        headers.put(Messenger.LNG_HEADER, latAndLongFormat.format(location.getLongitude()));
                        headers.put(Messenger.ACC_HEADER, Float.toString(location.getAccuracy()));
                    }

                    Network.uploadScreenshot(this, uploadUrl, out.toByteArray(), "screenshot_device_locator" + suffix, headers, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String imageUrl, int responseCode, String url) {
                            if (StringUtils.startsWith(imageUrl, "http://") || StringUtils.startsWith(imageUrl, "https://")) {
                                //send notification with image url
                                String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
                                String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
                                String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");

                                if (StringUtils.isNotEmpty(phoneNumber) || StringUtils.isNotEmpty(sender) || StringUtils.isNotEmpty(telegramId) || StringUtils.isNotEmpty(email)) {
                                    Intent newIntent = new Intent(HiddenCaptureImageService.this, SmsSenderService.class);
                                    newIntent.putExtra("command", Command.TAKE_PHOTO_COMMAND);
                                    newIntent.putExtra("imageUrl", imageUrl);
                                    if (StringUtils.isNotEmpty(app)) {
                                        newIntent.putExtra("app", app);
                                    } else {
                                        newIntent.putExtra("email", email);
                                        newIntent.putExtra("telegramId", telegramId);
                                        if (StringUtils.isNotEmpty(sender)) {
                                            newIntent.putExtra("phoneNumber", sender);
                                        } else {
                                            newIntent.putExtra("phoneNumber", phoneNumber);
                                        }
                                    }
                                    try {
                                        ContextCompat.startForegroundService(HiddenCaptureImageService.this, newIntent);
                                    } catch (Exception e) {
                                        Log.e(TAG, e.getMessage(), e);
                                    }
                                } else {
                                    Log.d(TAG, "Unable to send notification. No notifiers are set.");
                                }
                                Files.deleteFileFromCache(imageFile.getName(), HiddenCaptureImageService.this, true);
                            } else {
                                Log.e(TAG, "Received error response: " + imageUrl);
                            }
                        }
                    });
                } else {
                    Log.e(TAG, "Image file is empty!");
                }
            } else {
                Log.w(TAG, getString(R.string.no_network_error));
            }
        } else {
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("hiddenCamera", true).apply();
            boolean deleted = imageFile.delete();
            Log.d(TAG, "Camera photo deleted: " + deleted);
            //TODO user handler like in CommandService
            Toast.makeText(this, "Camera enabled!", Toast.LENGTH_LONG).show();
        }

        isRunning = false;
        stopSelf();
    }

    @Override
    public void onCameraError(@CameraError.CameraErrorCodes int errorCode) {
        switch (errorCode) {
            case CameraError.ERROR_CAMERA_OPEN_FAILED:
                //Camera open failed. Probably because another application is using the camera
                Log.e(TAG, "Cannot open camera.");
                PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("hiddenCamera", false).apply();
                //TODO user handler like in CommandService
                Toast.makeText(this, "Camera opening failed.", Toast.LENGTH_LONG).show();
                break;
            case CameraError.ERROR_IMAGE_WRITE_FAILED:
                //Image write failed. Please check if you have provided WRITE_EXTERNAL_STORAGE permission
                Log.e(TAG, "Cannot write image captured by camera.");
                break;
            case CameraError.ERROR_CAMERA_PERMISSION_NOT_AVAILABLE:
                //camera permission is not available
                //Ask for the camera permission before initializing it.
                Log.e(TAG, "Camera permission not available.");
                break;
            case CameraError.ERROR_DOES_NOT_HAVE_OVERDRAW_PERMISSION:
                //Display information dialog to the user with steps to grant "Draw over other app"
                //permission for the app.
                Log.e(TAG, "Draw over other app permission is missing");
                break;
            case CameraError.ERROR_DOES_NOT_HAVE_FRONT_CAMERA:
                Log.e(TAG, "Your device does not have front camera.");
                break;
        }

        isRunning = false;
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onLocationUpdated(Location location) {
        Log.d(TAG, "Location found with accuracy " + location.getAccuracy() + " m");
        this.location = location;
    }

    public static boolean isBusy() {
        return isRunning;
    }
}
