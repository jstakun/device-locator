package net.gmsworld.devicelocator.services;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.androidhiddencamera.CameraConfig;
import com.androidhiddencamera.CameraError;
import com.androidhiddencamera.HiddenCameraService;
import com.androidhiddencamera.HiddenCameraUtils;
import com.androidhiddencamera.config.CameraFacing;
import com.androidhiddencamera.config.CameraImageFormat;
import com.androidhiddencamera.config.CameraResolution;
import com.androidhiddencamera.config.CameraRotation;

import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.Files;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.NotificationUtils;
import net.gmsworld.devicelocator.utilities.Permissions;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;
import net.gmsworld.devicelocator.utilities.Toaster;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

import static android.graphics.Bitmap.CompressFormat.JPEG;

/**
 * Created by jstakun on 9/6/17.
 */

public class HiddenCaptureImageService extends HiddenCameraService implements OnLocationUpdatedListener {

    public static final String STATUS = "hiddenCamera";

    private static final int NOTIFICATION_ID = 1111;
    private static final String TAG = HiddenCaptureImageService.class.getSimpleName();
    private static final DecimalFormat latAndLongFormat = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);

    static {
        latAndLongFormat.applyPattern("#.######");
    }

    private boolean isTest = false;
    private String sender = null, app = null;
    private Location location;

    private static boolean isRunning = false;

    private PreferencesUtils settings;
    private Toaster toaster;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");
        settings = new PreferencesUtils(this);
        toaster = new Toaster(this);
        SmartLocation.with(this).location(new LocationGooglePlayServicesWithFallbackProvider(this)).oneFix().start(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        SmartLocation.with(this).location(new LocationGooglePlayServicesWithFallbackProvider(this)).stop();
    }

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand()");
        isTest = intent.getBooleanExtra("test", false);
        sender = intent.getStringExtra("sender");
        app = intent.getStringExtra("app");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, NotificationUtils.buildWorkerNotification(this, NOTIFICATION_ID, null, "Camera image capture in progress...", true));
        }

        if (Permissions.haveCameraPermission(this)) {

            if (HiddenCameraUtils.canOverDrawOtherApps(this) && !isRunning) {

                try {

                    isRunning = true;

                    Log.d(TAG, "Preparing camera");

                    CameraConfig cameraConfig = new CameraConfig()
                        .getBuilder(this)
                        .setCameraFacing(CameraFacing.FRONT_FACING_CAMERA)
                        .setCameraResolution(CameraResolution.HIGH_RESOLUTION) //.MEDIUM_RESOLUTION)
                        .setImageFormat(CameraImageFormat.FORMAT_JPEG)
                        .setImageRotation(CameraRotation.ROTATION_270)
                        .build();

                    Log.d(TAG, "Starting camera");

                    startCamera(cameraConfig);

                    Log.d(TAG, "Picture will be taken in 1 second");

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (isRunning) {
                                try {
                                    takePicture();
                                } catch (Throwable e) {
                                    Log.e(TAG, "Failed to take a picture!", e);
                                }
                            } else {
                                Log.e(TAG, "Failed to start camera. No photo will be taken!");
                            }
                        }
                    }, 1000);
                } catch (Throwable e) {
                    Log.e(TAG, e.getMessage(), e);
                    onCameraError(CameraError.ERROR_CAMERA_OPEN_FAILED);
                }
            } else {
                Log.e(TAG, "Draw over other apps permission is missing. Can't take a picture!");
                stop();
            }
        } else {
            if (isRunning) {
                Log.e(TAG, "Camera is running. Skipping this request!");
            } else {
                Log.e(TAG, "Camera permission not available!");
            }
            stop();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onImageCapture(final @NonNull File imageFile) {
        if (!isTest) {
            //save photo to device gallery
            Files.galleryAddPic(imageFile, this);
            //
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
                    String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN);
                    String uploadUrl = getString(R.string.photoUploadUrl);
                    if (StringUtils.isNotEmpty(tokenStr)) {
                        headers.put("Authorization", "Bearer " + tokenStr);
                        uploadUrl = getString(R.string.securePhotoUploadUrl);
                    }

                    headers.put(Messenger.DEVICE_NAME_HEADER, Messenger.getDeviceId(this, true));
                    headers.put(Messenger.DEVICE_ID_HEADER, Messenger.getDeviceId(this, false));

                    if (location != null) {
                        headers.put(Messenger.LAT_HEADER, latAndLongFormat.format(location.getLatitude()));
                        headers.put(Messenger.LNG_HEADER, latAndLongFormat.format(location.getLongitude()));
                        if (location.hasAccuracy()) {
                            headers.put(Messenger.ACC_HEADER, Float.toString(location.getAccuracy()));
                        }
                        if (location.hasSpeed()) {
                            headers.put(Messenger.SPD_HEADER, Float.toString(location.getSpeed()));
                        }
                        settings.setLong(Messenger.LOCATION_SENT_MILLIS, System.currentTimeMillis());
                    }

                    Network.uploadScreenshot(this, uploadUrl, out.toByteArray(), "screenshot_device_locator" + suffix, headers, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String imageUrl, int responseCode, String url) {
                            if (StringUtils.startsWith(imageUrl, "http://") || StringUtils.startsWith(imageUrl, "https://")) {
                                //send notification with image url
                                Bundle extras = new Bundle();
                                extras.putString("imageUrl", imageUrl);
                                if (StringUtils.isNotEmpty(sender)) {
                                    extras.putString("phoneNumber", sender);
                                }
                                extras.putString("adminTelegramId", getString(R.string.telegram_notification));
                                SmsSenderService.initService(HiddenCaptureImageService.this, true, true, true, app, Command.TAKE_PHOTO_COMMAND, null, null, extras);
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
            settings.setBoolean(STATUS, true);
            boolean deleted = imageFile.delete();
            Log.d(TAG, "Camera photo deleted: " + deleted);
            toaster.showServiceToast("Camera enabled!");
            //send ui broadcast
            Intent broadcastIntent = new Intent();
            Log.d(TAG, "Sending UI Update Broadcast");
            broadcastIntent.setAction(Command.UPDATE_UI_ACTION);
            sendBroadcast(broadcastIntent);
        }

        isRunning = false;
        stop();
    }

    @Override
    public void onCameraError(@CameraError.CameraErrorCodes int errorCode) {
        switch (errorCode) {
            case CameraError.ERROR_CAMERA_OPEN_FAILED:
                //Camera open failed. Probably because another application is using the camera
                Log.e(TAG, "Cannot open camera.");
                settings.setBoolean(STATUS, false);
                toaster.showServiceToast("Camera opening failed.");
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
        stop();
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

    public static boolean isNotBusy() {
        return !isRunning;
    }

    private void stop() {
        stopCamera();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        } else {
            stopSelf();
        }
    }
}
