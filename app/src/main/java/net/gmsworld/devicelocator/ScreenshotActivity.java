package net.gmsworld.devicelocator;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;

import androidx.appcompat.app.AppCompatActivity;

public class ScreenshotActivity extends AppCompatActivity {

    private static final String TAG = ScreenshotActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screenshot);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                takeScreenshot();
            }
        }, 2000L);
    }

    private void takeScreenshot() {
        try {
            // image naming and path  to include sd card  appending name you choose for file
            String mPath = Environment.getExternalStorageDirectory().toString() + "/" + System.currentTimeMillis() + ".jpg";

            // create bitmap screen capture
            View view = getWindow().getDecorView();
            //Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            //Canvas canvas = new Canvas(bitmap);
            //view.draw(canvas);

            view.setDrawingCacheEnabled(true);
            Bitmap bitmap = view.getDrawingCache(true);
            view.setDrawingCacheEnabled(false);

            File imageFile = new File(mPath);

            FileOutputStream outputStream = new FileOutputStream(imageFile);
            int quality = 100;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
            outputStream.close();

        } catch (Throwable e) {
            // Several error may come out with file handling or DOM
            Log.e(TAG, e.getMessage(), e);
        } finally {
            finish();
        }
    }
}