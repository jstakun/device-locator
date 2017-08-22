package net.gmsworld.devicelocator.Utilities;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jstakun on 5/31/17.
 */

public class Files {

    private static final String TAG = Files.class.getSimpleName();

    private static String readLine(InputStreamReader reader) throws IOException {
        int readChar = reader.read();
        if (readChar == -1) {
            return null;
        }
        StringBuilder string = new StringBuilder("");
        while (readChar != -1 && readChar != '\n') {
            if (readChar != '\r') {
                string.append((char) readChar);
            }
            readChar = reader.read();
        }
        return string.toString();
    }


    public static List<String> readFileByLinesFromContextDir(String filename, Context context) {
        InputStream is = null;
        //InputStreamReader isr = null;
        List<String> lines = new ArrayList<String>();

        try {
            File fc = new File(context.getFilesDir(), filename);
            if (fc.exists()) {
                is = new FileInputStream(fc);
                //isr = new InputStreamReader(is, "UTF8");

                //String line;
                //while ((line = readLine(isr)) != null) {
                //    lines.add(line);
                //}
                long start = System.nanoTime();
                BufferedReader in = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = in.readLine()) != null) {
                    lines.add(line);
                }
                long time = System.nanoTime() - start;
                Log.d(TAG, "Route file processed in " + time + " ns.");
            }
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        } finally {
            //if (isr != null) {
            //    try {
            //        isr.close();
            //    } catch (Exception e) {
            //        Log.d(TAG, e.getMessage(), e);
            //    }
            //}
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage(), e);
                }
            }
        }
        return lines;
    }

    public static boolean isRouteTracked(String filename, Context context, int numOfPoints) {
        InputStream is = null;
        int i=0;

        try {
            File fc = new File(context.getFilesDir(), filename);
            if (fc.exists()) {
                is = new FileInputStream(fc);
                BufferedReader in = new BufferedReader(new InputStreamReader(is));
                while (i < numOfPoints) {
                    String line = in.readLine();
                    if (line == null) {
                        break;
                    } else {
                        i += 1;
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage(), e);
                }
            }
        }
        return (i == numOfPoints);
    }

    public static int countLinesFromContextDir(String filename, Context context) {
        InputStream is = null;
        int lines= 0;

        try {
            File fc = new File(context.getFilesDir(), filename);
            if (fc.exists()) {
                is = new FileInputStream(fc);
                BufferedReader in = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = in.readLine()) != null) {
                    lines++;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage(), e);
                }
            }
        }
        return lines;
    }

    public static void deleteFileFromContextDir(String filename, Context context) {
        File file = new File(context.getFilesDir(), filename);
        if (file.exists()) {
            boolean deleted = file.delete();
            Log.d(TAG, "File " + filename + " deleted: " + deleted);
        } else {
            Log.d(TAG, "File " + filename + " doesn't exists");
        }
    }

    public static void appendLineToFileFromContextDir(String filename, Context context, String line) {
        FileOutputStream outputStream = null;
        try {
            line += "\n";
            outputStream = context.openFileOutput(filename, Context.MODE_APPEND);
            outputStream.write(line.getBytes());
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage(), e);
                }
            }
        }
    }
}
