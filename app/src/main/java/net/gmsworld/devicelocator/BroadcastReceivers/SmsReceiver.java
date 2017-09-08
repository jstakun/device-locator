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
import android.util.Patterns;
import android.widget.Toast;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.Services.HiddenCaptureImageService;
import net.gmsworld.devicelocator.Services.RouteTrackingService;
import net.gmsworld.devicelocator.Services.SmsSenderService;
import net.gmsworld.devicelocator.Utilities.Messenger;
import net.gmsworld.devicelocator.Utilities.Permissions;
import net.gmsworld.devicelocator.Utilities.RouteTrackingServiceUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.StringTokenizer;

public class SmsReceiver extends BroadcastReceiver {

    private final static String TAG = SmsReceiver.class.getSimpleName();

    public final static String START_COMMAND = "startdl"; //start route tracking
    public final static String STOP_COMMAND = "stopdl"; //stop route tracking
    public final static String RESET_COMMAND = "resetdl"; //start route tracking and delete old route points if exists
    public final static String ROUTE_COMMAND = "routedl"; //share currently recorded route
    public final static String MUTE_COMMAND = "mutedl"; //mute phone
    public final static String NORMAL_COMMAND = "normaldl"; //unmute phone
    public final static String SHARE_COMMAND = "locatedl"; //share current location via sms
    public final static String RADIUS_COMMAND = "radiusdl"; //change tracking radius, usage radiusdl x where is number of meters > 0
    public final static String CALL_COMMAND = "calldl"; //call to sender
    public final static String GPS_HIGH_COMMAND = "gpshighdl"; //set high gps accuracy
    public final static String GPS_BALANCED_COMMAND = "gpsbalancedl"; //set balanced gps accuracy
    public final static String NOTIFY_COMMAND = "notifydl"; //set notification email, phone or telegram chat id
    public final static String AUDIO_COMMAND = "audiodl"; //enable audio transmitter
    public final static String NOAUDIO_COMMAND = "noaudiodl"; //disable audio transmitter
    //TODO add take photo command
    public final static String TAKE_PHOTO_COMMAND = "photodl"; //take photo and send link

    @Override
    public void onReceive(Context context, Intent intent) {
        if (findKeyword(context, intent)) return;
        if (findStartRouteTrackerServiceStartCommand(context, intent)) return;
        if (findStopRouteTrackerServiceStartCommand(context, intent)) return;
        if (findResetRouteTrackerServiceStartCommand(context, intent)) return;
        if (findMuteCommand(context, intent)) return;
        if (findNormalCommand(context, intent)) return;
        if (findChangeRadiusRouteTrackerServiceCommand(context, intent)) return;
        if (findStartPhoneCallCommand(context, intent)) return;
        if (findShareRouteCommand(context, intent)) return;
        if (findGpsHighAccuracyCommand(context, intent)) return;
        if (findGpsLowAccuracyCommand(context, intent)) return;
        if (findNotifyCommand(context, intent)) return;
        if (findAudioCommand(context, intent)) return;
        if (findNoAudioCommand(context, intent)) return;
        if (findTakePhotoCommand(context, intent)) return;
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
            newIntent.putExtra("notificationNumber", phoneNumber);
            newIntent.putExtra("email", email);
            newIntent.putExtra("telegramId", telegramId);
            newIntent.putExtra("command", START_COMMAND);
            context.startService(newIntent);
            return true;
        } else {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String telegramId = settings.getString("telegramId", "");

            if (StringUtils.isNotEmpty(telegramId)) {
                sender = getSenderAddress(context, intent, START_COMMAND + "t");
                if (sender != null) {
                    int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
                    String phoneNumber = settings.getString("phoneNumber", "");
                    String email = settings.getString("email", "");

                    RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, false);

                    SharedPreferences.Editor editor = settings.edit();
                    editor.putBoolean("motionDetectorRunning", true);
                    editor.commit();

                    Intent newIntent = new Intent(context, SmsSenderService.class);
                    newIntent.putExtra("telegramId", telegramId);
                    newIntent.putExtra("command", START_COMMAND);
                    context.startService(newIntent);
                    return true;
                }
            }
        }
        return false;
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
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String telegramId = settings.getString("telegramId", "");

            if (StringUtils.isNotEmpty(telegramId)) {
                sender = getSenderAddress(context, intent, RESET_COMMAND + "t");
                if (sender != null) {
                    int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
                    String phoneNumber = settings.getString("phoneNumber", "");
                    String email = settings.getString("email", "");

                    RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, true);

                    SharedPreferences.Editor editor = settings.edit();
                    editor.putBoolean("motionDetectorRunning", true);
                    editor.commit();

                    Intent newIntent = new Intent(context, SmsSenderService.class);
                    newIntent.putExtra("telegramId", telegramId);
                    newIntent.putExtra("command", RESET_COMMAND);
                    context.startService(newIntent);
                    return true;
                }
            }
        }
        return false;
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
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String telegramId = settings.getString("telegramId", "");

            if (StringUtils.isNotEmpty(telegramId)) {
                sender = getSenderAddress(context, intent, STOP_COMMAND + "t");
                if (sender != null) {
                    RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false);

                    SharedPreferences.Editor editor = settings.edit();
                    editor.putBoolean("motionDetectorRunning", false);
                    editor.commit();

                    Intent newIntent = new Intent(context, SmsSenderService.class);
                    newIntent.putExtra("telegramId", telegramId);
                    newIntent.putExtra("command", STOP_COMMAND);
                    context.startService(newIntent);
                    return true;
                }
            }
        }
        return false;
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

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String title = settings.getString("routeTitle", "");

            if (StringUtils.isEmpty(title)) {
                title = "devicelocatorroute_" + Messenger.getDeviceId(context) + "_" + System.currentTimeMillis();
                SharedPreferences.Editor editor = settings.edit();
                editor.putString("routeTitle", title);
                editor.commit();
            }

            routeTracingService.putExtra(RouteTrackingService.COMMAND, RouteTrackingService.COMMAND_ROUTE);
            routeTracingService.putExtra("title", title);
            routeTracingService.putExtra("phoneNumber", sender);
            context.startService(routeTracingService);

            return true;
        } else {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String telegramId = settings.getString("telegramId", "");
            if (StringUtils.isNotEmpty(telegramId)) {
                sender = getSenderAddress(context, intent, ROUTE_COMMAND + "t");
                if (sender != null) {
                    String title = settings.getString("routeTitle", "");

                    if (StringUtils.isEmpty(title)) {
                        title = "devicelocatorroute_" + Messenger.getDeviceId(context) + "_" + System.currentTimeMillis();
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString("routeTitle", title);
                        editor.commit();
                    }

                    Intent routeTracingService = new Intent(context, RouteTrackingService.class);
                    routeTracingService.putExtra(RouteTrackingService.COMMAND, RouteTrackingService.COMMAND_ROUTE);
                    routeTracingService.putExtra("title", title);
                    routeTracingService.putExtra("telegramId", telegramId);
                    context.startService(routeTracingService);
                    return true;
                }
            }
        }
        return false;
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

    private boolean findAudioCommand(Context context, Intent intent) {
        String sender = getSenderAddress(context, intent, AUDIO_COMMAND);

        if (sender != null) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("useAudio", true);
            editor.commit();

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", AUDIO_COMMAND);
            context.startService(newIntent);
            return true;
        } else {
            return false;
        }
    }

    private boolean findNoAudioCommand(Context context, Intent intent) {
        String sender = getSenderAddress(context, intent, NOAUDIO_COMMAND);

        if (sender != null) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("useAudio", false);
            editor.commit();

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", NOAUDIO_COMMAND);
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
        String sender = getSenderAddress(context, intent, SHARE_COMMAND);

        if (sender != null) {
            if (!Permissions.haveSendSMSAndLocationPermission(context)) {
                try {
                    Permissions.setPermissionNotification(context);
                } catch (Exception e) {
                    Toast.makeText(context, R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            context.startService(newIntent);
            return true;
        } else {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String telegramId = settings.getString("telegramId", "");
            if (StringUtils.isNotEmpty(telegramId)) {
                sender = getSenderAddress(context, intent, SHARE_COMMAND + "t");
                if (sender != null) {
                    Intent newIntent = new Intent(context, SmsSenderService.class);
                    newIntent.putExtra("telegramId", telegramId);
                    context.startService(newIntent);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean findGpsHighAccuracyCommand(Context context, Intent intent) {
        String sender = getSenderAddress(context, intent, GPS_HIGH_COMMAND);

        if (sender != null) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt("gpsAccuracy", 1);
            editor.commit();
            RouteTrackingServiceUtils.setGpsAccuracy(context, RouteTrackingService.COMMAND_GPS_HIGH);
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
            RouteTrackingServiceUtils.setGpsAccuracy(context, RouteTrackingService.COMMAND_GPS_BALANCED);
            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", GPS_BALANCED_COMMAND);
            context.startService(newIntent);
            return true;
        } else {
            return false;
        }
    }

    private boolean findNotifyCommand(Context context, Intent intent) {
        ArrayList<SmsMessage> list = null;
        try {
            String keyword = NOTIFY_COMMAND;
            String token = PreferenceManager.getDefaultSharedPreferences(context).getString("token", "");
            keyword += token;
            list = getMessagesWithKeyword(keyword, intent.getExtras());
        } catch (Exception e) {
            return false;
        }

        if (list.size() > 0) {
            String[] tokens = list.get(0).getMessageBody().split(" ");
            String email = null;
            String phoneNumber = null;
            String telegramId = null;
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

            for (int i=0;i<tokens.length;i++) {
                String token = tokens[i]; //validate
                if (token.startsWith("m:")) {
                    String newEmailAddress = token.substring(2);
                    if ((StringUtils.isNotEmpty(newEmailAddress) && Patterns.EMAIL_ADDRESS.matcher(newEmailAddress).matches()) || StringUtils.isEmpty(newEmailAddress)) {
                        email = newEmailAddress;
                        if (!StringUtils.equals(settings.getString("email", ""), email)) {
                            Messenger.sendEmailRegistrationRequest(context, email, 1);
                        }
                    }
                } else if (token.startsWith("p:")) {
                    String newPhoneNumber = token.substring(2);
                    if ((StringUtils.isNotEmpty(newPhoneNumber) && Patterns.PHONE.matcher(newPhoneNumber).matches()) || StringUtils.isEmpty(newPhoneNumber)) {
                        phoneNumber = newPhoneNumber;
                    }
                } else if (token.startsWith("t:")) {
                    String newTelegramId = token.substring(2);
                    if ((StringUtils.isNumeric(newTelegramId) && StringUtils.isNotEmpty(newTelegramId)) || StringUtils.isEmpty(newTelegramId)) {
                        telegramId = newTelegramId;
                        if (!StringUtils.equals(settings.getString("telegramId", ""), telegramId)) {
                            Messenger.sendTelegramRegistrationRequest(context, telegramId, 1);
                        }
                    }
                }
            }
            
            if (telegramId != null || phoneNumber != null || email != null) {
                SharedPreferences.Editor editor = settings.edit();
                if (email != null) {
                    editor.putString("email", email);
                } else {
                    email = settings.getString("email", "");
                }
                if (phoneNumber != null) {
                    editor.putString("phoneNumber", phoneNumber);
                } else {
                    phoneNumber = settings.getString("phoneNumber", "");
                }
                if (telegramId != null) {
                    editor.putString("telegramId", telegramId);
                } else {
                    telegramId = settings.getString("telegramId", "");
                }
                editor.commit();

                int radius = settings.getInt("radius", 100);

                RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, phoneNumber, email, telegramId);
                
                Intent newIntent = new Intent(context, SmsSenderService.class);
                newIntent.putExtra("phoneNumber", list.get(0).getOriginatingAddress());
                newIntent.putExtra("notificationNumber", phoneNumber);
                newIntent.putExtra("email", email);
                newIntent.putExtra("telegramId", telegramId);
                newIntent.putExtra("command", NOTIFY_COMMAND);
                context.startService(newIntent);
            }
            return true;
        } else {
            return false;
        }
    }

    private boolean findTakePhotoCommand(Context context, Intent intent) {
        String sender = getSenderAddress(context, intent, TAKE_PHOTO_COMMAND);

        if (sender != null) {
            Intent cameraIntent = new Intent(context, HiddenCaptureImageService.class);
            context.startService(cameraIntent);
            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", TAKE_PHOTO_COMMAND);
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
                } else if (keyword.startsWith(NOTIFY_COMMAND)) {
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