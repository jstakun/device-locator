package net.gmsworld.devicelocator.utilities;

import android.content.Context;
import android.util.Log;

import com.squareup.tape2.QueueFile;

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
        String absolutePath;
        if (isExternal) {
            absolutePath = context.getExternalFilesDir(null).getAbsolutePath();
            absolutePath = absolutePath.substring(0, absolutePath.length() - 6); //remove /files from the end
        } else {
            absolutePath = context.getFilesDir().getAbsolutePath();
        }
        return new File(absolutePath, filename);
    }

    public static List<String> readFileByLinesFromContextDir(String filename, Context context) {
        List<String> lines = new ArrayList<String>();
        QueueFile queueFile = null;

        try {
            File fc = getFilesDir(context, filename, false);
            if (fc.exists()) {
                long start = System.nanoTime();
                queueFile = new QueueFile.Builder(fc).build();
                Iterator<byte[]> iterator = queueFile.iterator();
                while (iterator.hasNext()) {
                    byte[] element = iterator.next();
                    lines.add(new String(element));
                }
                long time = System.nanoTime() - start;
                Log.d(TAG, "Route file processed in " + time + " ns.");
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

    public static boolean hasRoutePoints(String filename, Context context, int numOfPoints) {
        int i=0;
        QueueFile queueFile = null;

        try {
            File fc = getFilesDir(context, filename, false);
            if (fc.exists()) {
                queueFile = new QueueFile.Builder(fc).build();
                i = queueFile.size();
                Log.d(TAG, "Route file has " + i + " points.");
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

        return (i >= numOfPoints);
    }

    public static void deleteFileFromContextDir(String filename, Context context, boolean external) {
        File file = getFilesDir(context, filename, external);
        if (file.exists()) {
            boolean deleted = file.delete();
            Log.d(TAG, "File " + file.getAbsolutePath() + " deleted: " + deleted);
        } else {
            Log.d(TAG, "File " + file.getAbsolutePath() + " doesn't exists!");
        }
    }

    public static void appendLineToFileFromContextDir(File fc, String line) {
        QueueFile queueFile = null;

        try {
            queueFile = new QueueFile.Builder(fc).build();
            queueFile.add(line.getBytes());
            if (queueFile.size() > 20000) {
                queueFile.remove(1000);
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
}
