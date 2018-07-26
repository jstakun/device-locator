package net.gmsworld.devicelocator.Services;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
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
import net.gmsworld.devicelocator.Utilities.Command;
import net.gmsworld.devicelocator.Utilities.Files;
import net.gmsworld.devicelocator.Utilities.Network;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static android.graphics.Bitmap.CompressFormat.JPEG;

/**
 * Created by jstakun on 9/6/17.
 */

public class HiddenCaptureImageService extends HiddenCameraService {

    private static final String TAG = HiddenCaptureImageService.class.getSimpleName();
    private boolean isTest = false;
    private String sender = null, app = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isTest = intent.getBooleanExtra("test", false);
        sender = intent.getStringExtra("sender");
        app = intent.getStringExtra("app");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

            if (HiddenCameraUtils.canOverDrawOtherApps(this)) {

                CameraConfig cameraConfig = new CameraConfig()
                        .getBuilder(this)
                        .setCameraFacing(CameraFacing.FRONT_FACING_CAMERA)
                        .setCameraResolution(CameraResolution.HIGH_RESOLUTION) //.MEDIUM_RESOLUTION)
                        .setImageFormat(CameraImageFormat.FORMAT_JPEG)
                        .setImageRotation(CameraRotation.ROTATION_270)
                        .build();

                try {
                    startCamera(cameraConfig);

                    new android.os.Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                takePicture();
                            } catch (Throwable e) {
                                Log.e(TAG, "Failed to take a picture!", e);
                            }
                        }
                    }, 1000);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    onCameraError(CameraError.ERROR_CAMERA_OPEN_FAILED);
                }
            } else {
                Log.e(TAG, "Draw over other apps permission is missing. Can't take a picture");
            }
        } else {
            Log.e(TAG, "Camera permission not available");
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

                    Map<String, String> headers = new HashMap<String, String>();

                    final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
                    String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN, "");
                    String uploadUrl = getString(R.string.photoUploadUrl);
                    if (StringUtils.isNotEmpty(tokenStr)) {
                        headers.put("Authorization", "Bearer " + tokenStr);
                        uploadUrl = getString(R.string.securePhotoUploadUrl);
                    }

                    headers.put("X-GMS-AppId", "2");
                    headers.put("X-GMS-Scope", "dl");

                    Network.uploadScreenshot(this, uploadUrl, out.toByteArray(), "screenshot_device_locator" + suffix, headers, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String imageUrl, int responseCode, String url) {
                            if (StringUtils.startsWith(imageUrl, "http://") || StringUtils.startsWith(imageUrl, "https://")) {
                                //send notification with image url
                                String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
                                String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
                                String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");

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
                                Files.deleteFileFromContextDir(imageFile.getName(), HiddenCaptureImageService.this, true);
                                HiddenCaptureImageService.this.startService(newIntent);
                            } else {
                                Log.e(TAG, "Received error response: " + imageUrl);
                                Log.d(TAG, "Image will be saved to local storage: " + imageFile.getAbsolutePath());
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
            Toast.makeText(this, "Camera enabled", Toast.LENGTH_LONG).show();
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("hiddenCamera", true).apply();
        }

        stopSelf();
    }

    @Override
    public void onCameraError(@CameraError.CameraErrorCodes int errorCode) {
        switch (errorCode) {
            case CameraError.ERROR_CAMERA_OPEN_FAILED:
                //Camera open failed. Probably because another application is using the camera
                Log.e(TAG, "Cannot open camera.");
                Toast.makeText(this, "Camera opening failed.", Toast.LENGTH_LONG).show();
                PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("hiddenCamera", false).apply();
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

        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
