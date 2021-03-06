package net.gmsworld.devicelocator.utilities;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.squareup.tape2.QueueFile;

import net.gmsworld.devicelocator.R;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by jstakun on 5/31/17.
 */

public class Files {

    private static final String TAG = Files.class.getSimpleName();

    public static File getFilesDir(Context context, String filename, boolean isExternal) {
        String absolutePath = null;
        if (context != null) {
            if (isExternal) {
                absolutePath = context.getExternalFilesDir(null).getAbsolutePath();
                absolutePath = absolutePath.substring(0, absolutePath.length() - 6); //remove /files from the end
            } else {
                absolutePath = context.getFilesDir().getAbsolutePath();
            }
        }
        return new File(absolutePath, filename);
    }

    public static List<String> readFileByLinesFromContextDir(String filename, Context context) {
        List<String> lines = new ArrayList<>();
        QueueFile queueFile = null;

        try {
            File fc = getFilesDir(context, filename, false);
            if (fc.exists()) {
                long start = System.nanoTime();
                queueFile = new QueueFile.Builder(fc).build();
                Iterator<byte[]> iterator = queueFile.iterator();
                int count = 0;
                while (iterator.hasNext()) {
                    byte[] element = iterator.next();
                    lines.add(new String(element));
                    count++;
                }
                long time = System.nanoTime() - start;
                Log.d(TAG, "File " + filename + " containing " + count + " points processed in " + time + " ns.");
            }
        } catch(Exception e){
            Log.d(TAG, e.getMessage(), e);
        } finally {
            if (queueFile != null) {
                try {
                    queueFile.close();
                } catch (IOException e) {
                    Log.d(TAG, e.getMessage(), e);
                }
            }
        }

        return lines;
    }

    public static int getRoutePoints(Context context) {
        return getQueueSize(AbstractLocationManager.ROUTE_FILE, context);
    }

    public static int getAuditComands(Context context) {
        return getQueueSize(AbstractCommand.AUDIT_FILE, context);
    }

    private static int getQueueSize(String queueName, Context context) {
        int numOfPoints=0;
        QueueFile queueFile = null;

        try {
            File fc = getFilesDir(context, queueName, false);
            if (fc.exists()) {
                queueFile = new QueueFile.Builder(fc).build();
                numOfPoints = queueFile.size();
                Log.d(TAG, "Queue file " + queueName + " has " + numOfPoints + " items.");
            }
        } catch(Exception e){
            Log.d(TAG, e.getMessage(), e);
        } finally {
            if (queueFile != null) {
                try {
                    queueFile.close();
                } catch (IOException e) {
                    Log.d(TAG, e.getMessage(), e);
                }
            }
        }

        return numOfPoints;
    }

    public static void deleteFileFromContextDir(String filename, Context context, boolean external) {
        deleteFile(getFilesDir(context, filename, external));
    }

    public static void deleteFileFromCache(String filename, Context context, boolean isExternal) {
        String cacheDir;
        if (isExternal) {
            cacheDir = context.getExternalCacheDir().getAbsolutePath();
        } else {
            cacheDir = context.getCacheDir().getAbsolutePath();
        }
        deleteFile(new File(cacheDir, filename));
    }

    private static void deleteFile(File file) {
        if (file.exists()) {
            boolean deleted = file.delete();
            Log.d(TAG, "File " + file.getAbsolutePath() + " deleted: " + deleted);
        } else {
            Log.d(TAG, "File " + file.getAbsolutePath() + " doesn't exists!");
        }
    }

    public static void appendLineToFileFromContextDir(File fc, String line, int maxLines, int removeLines) {
        QueueFile queueFile = null;

        try {
            queueFile = new QueueFile.Builder(fc).build();
            queueFile.add(line.getBytes());
            if (maxLines > 0 && removeLines > 0 && queueFile.size() > maxLines) {
                queueFile.remove(removeLines);
            }
        } catch(Exception e){
            Log.d(TAG, e.getMessage(), e);
        } finally {
            if (queueFile != null) {
                try {
                    queueFile.close();
                } catch (IOException e) {
                    Log.d(TAG, e.getMessage(), e);
                }
            }
        }
    }

    public static void galleryAddPic(File imageFile, Context context) {
        if (Permissions.haveWriteStoragePermission(context)) {
            try {
                final String path = StringUtils.remove(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath(), "Pictures") + context.getString(R.string.app_name);
                Log.d(TAG, "Saving image to the path: " + path);
                File storageDir = new File(path, imageFile.getName());
                FileUtils.copyFile(imageFile, storageDir);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        } else {
            Log.w(TAG, "Camera image won't be saved on device due to missing write storage permission!");
        }
    }
}
