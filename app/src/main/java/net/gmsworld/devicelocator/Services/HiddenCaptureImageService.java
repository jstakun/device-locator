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

import net.gmsworld.devicelocator.BroadcastReceivers.SmsReceiver;
import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.Utilities.Network;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jstakun on 9/6/17.
 */

public class HiddenCaptureImageService extends HiddenCameraService {

    private static final String TAG = HiddenCaptureImageService.class.getSimpleName();
    private boolean isTest = false;
    private String sender = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isTest = intent.getBooleanExtra("test", false);
        sender = intent.getStringExtra(sender);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

            if (HiddenCameraUtils.canOverDrawOtherApps(this)) {

                CameraConfig cameraConfig = new CameraConfig()
                        .getBuilder(this)
                        .setCameraFacing(CameraFacing.FRONT_FACING_CAMERA)
                        .setCameraResolution(CameraResolution.HIGH_RESOLUTION) //.MEDIUM_RESOLUTION)
                        .setImageFormat(CameraImageFormat.FORMAT_JPEG)
                        .setImageRotation(CameraRotation.ROTATION_270)
                        .build();

                startCamera(cameraConfig);

                new android.os.Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            takePicture();
                        } catch (Throwable e) {
                            Log.e(TAG, "Failed to take a picture!", e);
                        }
                    }
                });
            } else {
                Log.e(TAG, "Draw over other apps permission is missing. Can't take a picture");
            }
        } else {
            Log.e(TAG, "Camera permission not available");
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onImageCapture(@NonNull File imageFile) {
        if (!isTest) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888; //.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);

            Log.d(TAG, "Image will be sent to server");

            //send image to server and send notification with link

            Map<String, String> headers = new HashMap<String, String>();

            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN_KEY, "");
            String uploadUrl = "https://www.gms-world.net/";
            if (StringUtils.isNotEmpty(tokenStr)) {
                headers.put("Authorization", "Bearer " + tokenStr);
                uploadUrl += "s";
            }

            headers.put("X-GMS-AppId", "2");
            headers.put("X-GMS-Scope", "dl");

            Network.uploadScreenshot(uploadUrl + "/imageUpload", out.toByteArray(), "screenshot_device_locator.jpg", headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String imageUrl, int responseCode, String url) {
                    if (StringUtils.isNotEmpty(imageUrl)) {
                        //send notification with image url
                        String email = settings.getString("email", "");
                        String phoneNumber = settings.getString("phoneNumber", "");
                        String telegramId = settings.getString("telegramId", "");

                        Intent newIntent = new Intent(HiddenCaptureImageService.this, SmsSenderService.class);
                        newIntent.putExtra("email", email);
                        newIntent.putExtra("telegramId", telegramId);
                        newIntent.putExtra("command", SmsReceiver.TAKE_PHOTO_COMMAND);
                        newIntent.putExtra("param1", imageUrl);
                        if (StringUtils.isNotEmpty(sender)) {
                            newIntent.putExtra("phoneNumber", sender);
                        } else {
                            newIntent.putExtra("phoneNumber", phoneNumber);
                        }
                        HiddenCaptureImageService.this.startService(newIntent);
                    } else {
                        Log.e(TAG, "Received empty image url!");
                    }
                }
            });
        } else {
            Toast.makeText(this, "Device Locator will take photo when wrong password or pin will be entered to unlock this device.", Toast.LENGTH_LONG).show();
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("hiddenCamera", true).commit();
        }

        stopSelf();
    }

    @Override
    public void onCameraError(@CameraError.CameraErrorCodes int errorCode) {
        switch (errorCode) {
            case CameraError.ERROR_CAMERA_OPEN_FAILED:
                //Camera open failed. Probably because another application
                //is using the camera
                Log.e(TAG, "Cannot open camera.");
                Toast.makeText(this, "Camera opening failed. There might be someting wrong with your front camera. Please try again.", Toast.LENGTH_LONG).show();
                PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("hiddenCamera", false).commit();
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
