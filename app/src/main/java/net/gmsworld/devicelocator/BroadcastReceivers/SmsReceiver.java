package net.gmsworld.devicelocator.BroadcastReceivers;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.Services.SmsSenderService;
import net.gmsworld.devicelocator.Utilities.RouteTrackingServiceUtils;
import net.gmsworld.devicelocator.Services.RouteTrackingService;
import net.gmsworld.devicelocator.Utilities.Network;
import net.gmsworld.devicelocator.Utilities.Permissions;

import java.util.ArrayList;
import java.util.StringTokenizer;

import org.apache.commons.lang3.StringUtils;

public class SmsReceiver extends BroadcastReceiver {

    private final static String TAG = SmsReceiver.class.getSimpleName();

    public final static String START_COMMAND = "startdl"; //start route tracking
    public final static String STOP_COMMAND = "stopdl"; //stop route tracking
    public final static String RESET_COMMAND = "resetdl"; //start route tracking and delete old route points if exists
    public final static String ROUTE_COMMAND = "routedl"; //share currently recorded route
    public final static String MUTE_COMMAND = "mutedl"; //mute phone
    public final static String NORMAL_COMMAND = "normaldl"; //unmute phone
    public final static String SHARE_COMMAND = "locatedl"; //share current location
    public final static String RADIUS_COMMAND = "radiusdl"; //change tracking radius, usage radiusdl x where is number of meters > 0
    public final static String CALL_COMMAND = "calldl"; //call to sender
    public final static String GPS_HIGH_COMMAND = "gpshighdl"; //set high gps accuracy
    public final static String GPS_BALANCED_COMMAND = "gpsbalancedl"; //set balanced gps accuracy

    @Override
    public void onReceive(Context context, Intent intent) {
        if (findKeyword(context, intent)) return;
        if (findStartRouteTrackerServiceStartCommand(context, intent)) return;
        if (findStartRouteTrackerServiceStartCommand(context, intent)) return;
        if (findResetRouteTrackerServiceStartCommand(context, intent)) return;
        if (findMuteCommand(context, intent)) return;
        if (findNormalCommand(context, intent)) return;
        if (findChangeRadiusRouteTrackerServiceCommand(context, intent)) return;
        if (findStartPhoneCallCommand(context, intent)) return;
        if (findShareRouteCommand(context, intent)) return;
        if (findGpsHighAccuracyCommand(context, intent)) return;
        if (findGpsLowAccuracyCommand(context, intent)) return;
    }

    private boolean findStartRouteTrackerServiceStartCommand(Context context, Intent intent) {
        String sender = getSenderAddress(context, intent, START_COMMAND);

        if (sender != null) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
            String phoneNumber = settings.getString("phoneNumber", "");
            String email = settings.getString("email", "");
            String telegramId = settings.getString("telegramId", "");

            RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, false);

            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("motionDetectorRunning", true);
            editor.commit();

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", START_COMMAND);
            context.startService(newIntent);
            return true;
        } else {
            return false;
        }
    }

    private boolean findResetRouteTrackerServiceStartCommand(Context context, Intent intent) {
        String sender = getSenderAddress(context, intent, RESET_COMMAND);

        if (sender != null) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
            String phoneNumber = settings.getString("phoneNumber", "");
            String email = settings.getString("email", "");
            String telegramId = settings.getString("telegramId", "");

            RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, true);

            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("motionDetectorRunning", true);
            editor.commit();

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", RESET_COMMAND);
            context.startService(newIntent);
            return true;
        } else {
            return false;
        }
    }

    private boolean findStopRouteTrackerServiceStartCommand(Context context, Intent intent) {
        String sender = getSenderAddress(context, intent, STOP_COMMAND);

        if (sender != null) {
            RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false);

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("motionDetectorRunning", false);
            editor.commit();

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", STOP_COMMAND);
            context.startService(newIntent);
            return true;
        } else {
            return false;
        }
    }

    private boolean findChangeRadiusRouteTrackerServiceCommand(Context context, Intent intent) {
        ArrayList<SmsMessage> list = null;
        try {
            String keyword = RADIUS_COMMAND;
            String token = PreferenceManager.getDefaultSharedPreferences(context).getString("token", "");
            keyword += token;
            list = getMessagesWithKeyword(keyword, intent.getExtras());
        } catch (Exception e) {
            return false;
        }

        if (list.size() > 0) {
            int radius = -1;
            String[] tokens = list.get(0).getMessageBody().split(" ");
            if (tokens.length == 2) {
                try {
                    radius = Integer.parseInt(tokens[1]);
                } catch (Exception e) {
                    Log.e(TAG, "Wrong radius: " + tokens[1]);
                }
            }
            if (radius > 0) {
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                String phoneNumber = settings.getString("phoneNumber", "");
                String email = settings.getString("email", "");
                String telegramId = settings.getString("telegramId", "");

                RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, phoneNumber, email, telegramId);

                SharedPreferences.Editor editor = settings.edit();
                editor.putInt("radius", radius);
                editor.commit();

                Intent newIntent = new Intent(context, SmsSenderService.class);
                newIntent.putExtra("phoneNumber", list.get(0).getOriginatingAddress());
                newIntent.putExtra("command", RADIUS_COMMAND);
                context.startService(newIntent);
            }
            return true;
        } else {
            return false;
        }
    }

    private boolean findStartPhoneCallCommand(Context context, Intent intent) {
        String sender = getSenderAddress(context, intent, CALL_COMMAND);

        if (sender != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
            Uri call = Uri.parse("tel:" + sender);
            Intent surf = new Intent(Intent.ACTION_CALL, call);
            //surf.setPackage("com.android.phone"); //use default phone
            //surf.setPackage("com.google.android.dialer");
            surf.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(surf);
            return true;
        } else {
            return false;
        }
    }

    private boolean findShareRouteCommand(Context context, Intent intent) {
        String sender = getSenderAddress(context, intent, ROUTE_COMMAND);

        if (sender != null) {
            Intent routeTracingService = new Intent(context, RouteTrackingService.class);
            routeTracingService.putExtra(RouteTrackingService.COMMAND, RouteTrackingService.COMMAND_ROUTE);

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String title = settings.getString("routeTitle", "");

            if (StringUtils.isEmpty(title)) {
                title = "devicelocatorroute_" + Network.getDeviceId(context) + "_" + System.currentTimeMillis();
                SharedPreferences.Editor editor = settings.edit();
                editor.putString("routeTitle", title);
                editor.commit();
            }

            routeTracingService.putExtra("title", title);
            routeTracingService.putExtra("phoneNumber", sender);
            context.startService(routeTracingService);

            //Intent newIntent = new Intent(context, SmsSenderService.class);
            //newIntent.putExtra("phoneNumber", sender);
            //newIntent.putExtra("command", ROUTE_COMMAND);
            //newIntent.putExtra("title", title);
            //newIntent.putExtra("size", GmsLocationManager.getInstance().getRouteSize());
            //context.startService(newIntent);

            return true;
        } else {
            return false;
        }
    }

    private boolean findMuteCommand(Context context, Intent intent) {
        String sender = getSenderAddress(context, intent, MUTE_COMMAND);

        if (sender != null) {
            final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioMode.setRingerMode(AudioManager.RINGER_MODE_SILENT);

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", MUTE_COMMAND);
            context.startService(newIntent);
            return true;
        } else {
            return false;
        }
    }

    private boolean findNormalCommand(Context context, Intent intent) {
        String sender = getSenderAddress(context, intent, NORMAL_COMMAND);

        if (sender != null) {
            final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioMode.setRingerMode(AudioManager.RINGER_MODE_NORMAL);

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", NORMAL_COMMAND);
            context.startService(newIntent);
            return true;
        } else {
            return false;
        }
    }

    private boolean findKeyword(Context context, Intent intent) {
        String keyword = PreferenceManager.getDefaultSharedPreferences(context).getString("keyword", "");

        if (keyword.length() == 0) {
            keyword = SHARE_COMMAND;
        }

        String token = PreferenceManager.getDefaultSharedPreferences(context).getString("token", "");
        keyword += token;

        ArrayList<SmsMessage> list = null;
        try {
            list = getMessagesWithKeyword(keyword, intent.getExtras());
        } catch (Exception e) {
            return false;
        }

        if (list.size() > 0) {
            if (!Permissions.haveSendSMSAndLocationPermission(context)) {
                try {
                    Permissions.setPermissionNotification(context);
                } catch (Exception e) {
                    Toast.makeText(context, R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
                }

                return true;
            }

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", list.get(0).getOriginatingAddress());
            context.startService(newIntent);
            return true;
        } else {
            return false;
        }
    }

    private boolean findGpsHighAccuracyCommand(Context context, Intent intent) {
        String sender = getSenderAddress(context, intent, GPS_HIGH_COMMAND);

        if (sender != null) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt("gpsAccuracy", 1);
            editor.commit();
            RouteTrackingServiceUtils.setHighGpsAccuracy(context);
            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", GPS_HIGH_COMMAND);
            context.startService(newIntent);
            return true;
        } else {
            return false;
        }
    }

    private boolean findGpsLowAccuracyCommand(Context context, Intent intent) {
        String sender = getSenderAddress(context, intent, GPS_BALANCED_COMMAND);

        if (sender != null) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt("gpsAccuracy", 0);
            editor.commit();
            RouteTrackingServiceUtils.setBalancedGpsAccuracy(context);
            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", GPS_BALANCED_COMMAND);
            context.startService(newIntent);
            return true;
        } else {
            return false;
        }
    }

    private ArrayList<SmsMessage> getMessagesWithKeyword(String keyword, Bundle bundle) {
        ArrayList<SmsMessage> list = new ArrayList<SmsMessage>();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            for (int i = 0; i < pdus.length; i++) {
                SmsMessage sms = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    String format = bundle.getString("format");
                    sms = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                } else {
                    sms = SmsMessage.createFromPdu((byte[]) pdus[i]);
                }

                if (StringUtils.equalsIgnoreCase(sms.getMessageBody(), keyword)) {
                    list.add(sms);
                } else if (keyword.startsWith(RADIUS_COMMAND)) {
                    StringTokenizer tokens = new StringTokenizer(sms.getMessageBody());
                    if (tokens.hasMoreTokens() && StringUtils.equalsIgnoreCase(tokens.nextToken(), keyword)) {
                        list.add(sms);
                    }
                }
            }
        }
        return list;
    }

    private String getSenderAddress(Context context, Intent intent, String command) {
        try {
            String token = PreferenceManager.getDefaultSharedPreferences(context).getString("token", "");
            command += token;
            ArrayList<SmsMessage> list = getMessagesWithKeyword(command, intent.getExtras());
            if (list.size() > 0) {
                return list.get(0).getOriginatingAddress();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return null;
    }
}