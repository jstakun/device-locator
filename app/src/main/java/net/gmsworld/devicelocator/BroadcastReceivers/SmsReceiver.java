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
import net.gmsworld.devicelocator.Utilities.AbstractCommand;
import net.gmsworld.devicelocator.Utilities.Messenger;
import net.gmsworld.devicelocator.Utilities.Permissions;
import net.gmsworld.devicelocator.Utilities.RouteTrackingServiceUtils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.ArrayList;
import java.util.StringTokenizer;

public class SmsReceiver extends BroadcastReceiver {

    private final static String TAG = SmsReceiver.class.getSimpleName();

    //public
    public final static String RESUME_COMMAND = "resumedl"; //rs resume route tracking
    public final static String START_COMMAND = "startdl"; //s start route tracking and delete old route points if exists
    public final static String STOP_COMMAND = "stopdl"; //sp stop route tracking
    public final static String ROUTE_COMMAND = "routedl"; //r share currently recorded route
    public final static String SHARE_COMMAND = "locatedl"; //l hare current location via sms
    public final static String MUTE_COMMAND = "mutedl"; //m mute phone
    public final static String UNMUTE_COMMAND = "normaldl"; //um unmute phone
    public final static String CALL_COMMAND = "calldl"; //c call to sender
    public final static String RADIUS_COMMAND = "radiusdl"; //ra change tracking radius, usage radiusdl x where is number of meters > 0
    public final static String GPS_HIGH_COMMAND = "gpshighdl"; //g set high gps accuracy
    public final static String GPS_BALANCED_COMMAND = "gpsbalancedl"; //gb set balanced gps accuracy
    public final static String NOTIFY_COMMAND = "notifydl"; //n set notification email, phone or telegram chat id usage notifydl p:x m:y t:z where x is mobile phone number, y is email address and z is Telegram chat or channel id.
    public final static String AUDIO_COMMAND = "audiodl"; //a enable audio transmitter
    public final static String NOAUDIO_COMMAND = "noaudiodl"; //na disable audio transmitter

    //private
    public final static String TAKE_PHOTO_COMMAND = "photodl"; //p if all permissions set take photo and send link
    public final static String PIN_COMMAND = "pindl"; //send pin to notifiers (only when notifiers are set)

    private static AbstractCommand[] commands = {new StartRouteTrackerServiceStartCommand(), new ResumeRouteTrackerServiceStartCommand(),
            new StopRouteTrackerServiceStartCommand(), new ShareLocationCommand(), new ShareRouteCommand(),
            new MuteCommand(), new UnMuteCommand(), new StartPhoneCallCommand(), new ChangeRouteTrackerServiceRadiusCommand(),
            new AudioCommand(), new NoAudioCommand(), new HighGpsCommand(), new BalancedGpsCommand(),
            new TakePhotoCommand() };

    @Override
    public void onReceive(Context context, Intent intent) {

        for (int i = 0; i < commands.length; i++) {
            if (commands[i].findCommand(context, intent)) return;
        }

        if (findNotifyCommand(context, intent)) return;
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

            for (int i = 0; i < tokens.length; i++) {
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
                    if ((NumberUtils.isCreatable(newTelegramId) && StringUtils.isNotEmpty(newTelegramId)) || StringUtils.isEmpty(newTelegramId)) {
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
                } else if (keyword.startsWith(RADIUS_COMMAND) || keyword.startsWith(NOTIFY_COMMAND) || keyword.startsWith(START_COMMAND)) {
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


    //---------------------------------- Commands classes --------------------------------------

    private static final class StartRouteTrackerServiceStartCommand extends AbstractCommand {

        public StartRouteTrackerServiceStartCommand() {
            super(START_COMMAND, "s", Finder.STARTS);
        }

        @Override
        protected boolean validateTokens() {
            return true;
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
            String phoneNumber = settings.getString("phoneNumber", "");
            String email = settings.getString("email", "");
            String telegramId = settings.getString("telegramId", "");

            boolean silentMode = false;
            if (commandTokens.length == 2 && (commandTokens[1].equalsIgnoreCase("silent") || commandTokens[1].equalsIgnoreCase("s"))) {
                silentMode = true;
            }

            RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, true, silentMode);

            settings.edit().putBoolean("motionDetectorRunning", true).commit();

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", START_COMMAND);
            context.startService(newIntent);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
            String phoneNumber = settings.getString("phoneNumber", "");
            String email = settings.getString("email", "");
            String telegramId = settings.getString("telegramId", "");

            RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, true, false);

            settings.edit().putBoolean("motionDetectorRunning", true).commit();

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("telegramId", telegramId);
            newIntent.putExtra("command", START_COMMAND);
            context.startService(newIntent);
        }
    }

    private static final class ResumeRouteTrackerServiceStartCommand extends AbstractCommand {

        public ResumeRouteTrackerServiceStartCommand() {
            super(RESUME_COMMAND, "rs", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
            String phoneNumber = settings.getString("phoneNumber", "");
            String email = settings.getString("email", "");
            String telegramId = settings.getString("telegramId", "");

            RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, false, false);

            settings.edit().putBoolean("motionDetectorRunning", true).commit();

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("notificationNumber", phoneNumber);
            newIntent.putExtra("email", email);
            newIntent.putExtra("telegramId", telegramId);
            newIntent.putExtra("command", RESUME_COMMAND);
            context.startService(newIntent);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
            String phoneNumber = settings.getString("phoneNumber", "");
            String email = settings.getString("email", "");
            String telegramId = settings.getString("telegramId", "");

            RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, false, false);

            settings.edit().putBoolean("motionDetectorRunning", true).commit();

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("telegramId", telegramId);
            newIntent.putExtra("command", RESUME_COMMAND);
            context.startService(newIntent);
        }
    }

    private static final class StopRouteTrackerServiceStartCommand extends AbstractCommand {

        public StopRouteTrackerServiceStartCommand() {
            super(STOP_COMMAND, "sp", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false);

            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("motionDetectorRunning", false).commit();

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", STOP_COMMAND);
            context.startService(newIntent);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false);

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            settings.edit().putBoolean("motionDetectorRunning", false).commit();
            String telegramId = settings.getString("telegramId", "");

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("telegramId", telegramId);
            newIntent.putExtra("command", STOP_COMMAND);
            context.startService(newIntent);
        }
    }

    private static final class ShareLocationCommand extends AbstractCommand {

        public ShareLocationCommand() {
            super(SHARE_COMMAND, "l", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            if (!Permissions.haveSendSMSAndLocationPermission(context)) {
                try {
                    Permissions.setPermissionNotification(context);
                } catch (Exception e) {
                    Toast.makeText(context, R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
                }
                Log.e(TAG, "Missing SMS and/or Locatoin permission");
                return;
            }
            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            context.startService(newIntent);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            String telegramId = PreferenceManager.getDefaultSharedPreferences(context).getString("telegramId", "");
            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("telegramId", telegramId);
            context.startService(newIntent);
        }
    }

    private static final class ShareRouteCommand extends AbstractCommand {

        public ShareRouteCommand() {
            super(ROUTE_COMMAND, "r", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            Intent routeTracingService = new Intent(context, RouteTrackingService.class);

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String title = settings.getString("routeTitle", "");

            if (StringUtils.isEmpty(title)) {
                title = "devicelocatorroute_" + Messenger.getDeviceId(context) + "_" + System.currentTimeMillis();
                settings.edit().putString("routeTitle", title).commit();
            }

            routeTracingService.putExtra(RouteTrackingService.COMMAND, RouteTrackingService.COMMAND_ROUTE);
            routeTracingService.putExtra("title", title);
            routeTracingService.putExtra("phoneNumber", sender);
            context.startService(routeTracingService);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String title = settings.getString("routeTitle", "");
            String telegramId = settings.getString("telegramId", "");

            if (StringUtils.isEmpty(title)) {
                title = "devicelocatorroute_" + Messenger.getDeviceId(context) + "_" + System.currentTimeMillis();
                settings.edit().putString("routeTitle", title).commit();
            }

            Intent routeTracingService = new Intent(context, RouteTrackingService.class);
            routeTracingService.putExtra(RouteTrackingService.COMMAND, RouteTrackingService.COMMAND_ROUTE);
            routeTracingService.putExtra("title", title);
            routeTracingService.putExtra("telegramId", telegramId);
            context.startService(routeTracingService);
        }
    }

    private static final class MuteCommand extends AbstractCommand {

        public MuteCommand() {
            super(MUTE_COMMAND, "m", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioMode.setRingerMode(AudioManager.RINGER_MODE_SILENT);

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", MUTE_COMMAND);
            context.startService(newIntent);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioMode.setRingerMode(AudioManager.RINGER_MODE_SILENT);

            Intent newIntent = new Intent(context, SmsSenderService.class);
            String telegramId = PreferenceManager.getDefaultSharedPreferences(context).getString("telegramId", "");
            newIntent.putExtra("telegramId", telegramId);
            newIntent.putExtra("command", MUTE_COMMAND);
            context.startService(newIntent);
        }
    }

    private static final class UnMuteCommand extends AbstractCommand {

        public UnMuteCommand() {
            super(UNMUTE_COMMAND, "um", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioMode.setRingerMode(AudioManager.RINGER_MODE_NORMAL);

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", UNMUTE_COMMAND);
            context.startService(newIntent);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioMode.setRingerMode(AudioManager.RINGER_MODE_NORMAL);

            Intent newIntent = new Intent(context, SmsSenderService.class);
            String telegramId = PreferenceManager.getDefaultSharedPreferences(context).getString("telegramId", "");
            newIntent.putExtra("telegramId", telegramId);
            newIntent.putExtra("command", UNMUTE_COMMAND);
            context.startService(newIntent);
        }
    }

    private static final class StartPhoneCallCommand extends AbstractCommand {

        public StartPhoneCallCommand() {
            super(CALL_COMMAND, "c", Finder.EQUALS);
        }


        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            initPhoneCall(sender, context);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            initPhoneCall(sender, context);
        }

        private void initPhoneCall(String sender, Context context) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Unable to initiate phone call due to missing permission");
                return;
            }
            Uri call = Uri.parse("tel:" + sender);
            Intent surf = new Intent(Intent.ACTION_CALL, call);
            //surf.setPackage("com.android.phone"); //use default phone
            //surf.setPackage("com.google.android.dialer");
            surf.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(surf);
        }
    }

    private static final class ChangeRouteTrackerServiceRadiusCommand extends AbstractCommand {
        public ChangeRouteTrackerServiceRadiusCommand() {
            super(RADIUS_COMMAND, "ra", Finder.STARTS);
        }

        @Override
        protected boolean validateTokens() {
            int radius = -1;
            if (commandTokens.length == 2) {
                try {
                    radius = Integer.parseInt(commandTokens[1]);
                } catch (Exception e) {
                    Log.e(TAG, "Wrong radius: " + commandTokens[1]);
                }
            }
            return radius > 0;
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            final int radius = Integer.parseInt(commandTokens[1]);
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String phoneNumber = settings.getString("phoneNumber", "");
            String email = settings.getString("email", "");
            String telegramId = settings.getString("telegramId", "");

            RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, phoneNumber, email, telegramId);

            settings.edit().putInt("radius", radius).commit();

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", RADIUS_COMMAND);
            context.startService(newIntent);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            final int radius = Integer.parseInt(commandTokens[1]);
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String phoneNumber = settings.getString("phoneNumber", "");
            String email = settings.getString("email", "");
            String telegramId = settings.getString("telegramId", "");

            RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, phoneNumber, email, telegramId);

            settings.edit().putInt("radius", radius).commit();

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("telegramId", telegramId);
            newIntent.putExtra("command", RADIUS_COMMAND);
            context.startService(newIntent);
        }
    }

    private static final class AudioCommand extends AbstractCommand {

        public AudioCommand() {
            super(AUDIO_COMMAND, "a", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useAudio", true).commit();

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", AUDIO_COMMAND);
            context.startService(newIntent);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String telegramId = settings.getString("telegramId", "");
            settings.edit().putBoolean("useAudio", true).commit();

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("telegramId", telegramId);
            newIntent.putExtra("command", AUDIO_COMMAND);
            context.startService(newIntent);
        }
    }

    private static final class NoAudioCommand extends AbstractCommand {

        public NoAudioCommand() {
            super(NOAUDIO_COMMAND, "na", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useAudio", false).commit();

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", NOAUDIO_COMMAND);
            context.startService(newIntent);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String telegramId = settings.getString("telegramId", "");
            settings.edit().putBoolean("useAudio", false).commit();

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("telegramId", telegramId);
            newIntent.putExtra("command", NOAUDIO_COMMAND);
            context.startService(newIntent);
        }
    }

    private static final class HighGpsCommand extends AbstractCommand {

        public HighGpsCommand() {
            super(GPS_HIGH_COMMAND, "g", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("gpsAccuracy", 1).commit();
            RouteTrackingServiceUtils.setGpsAccuracy(context, RouteTrackingService.COMMAND_GPS_HIGH);
            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", GPS_HIGH_COMMAND);
            context.startService(newIntent);

        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String telegramId = settings.getString("telegramId", "");
            settings.edit().putInt("gpsAccuracy", 1).commit();

            RouteTrackingServiceUtils.setGpsAccuracy(context, RouteTrackingService.COMMAND_GPS_HIGH);
            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("telegramId", telegramId);
            newIntent.putExtra("command", GPS_HIGH_COMMAND);
            context.startService(newIntent);
        }
    }

    private static final class BalancedGpsCommand extends AbstractCommand {

        public BalancedGpsCommand() {
            super(GPS_BALANCED_COMMAND, "gb", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("gpsAccuracy", 0).commit();
            RouteTrackingServiceUtils.setGpsAccuracy(context, RouteTrackingService.COMMAND_GPS_BALANCED);
            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", GPS_BALANCED_COMMAND);
            context.startService(newIntent);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String telegramId = settings.getString("telegramId", "");
            settings.edit().putInt("gpsAccuracy", 0).commit();

            RouteTrackingServiceUtils.setGpsAccuracy(context, RouteTrackingService.COMMAND_GPS_BALANCED);
            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("telegramId", telegramId);
            newIntent.putExtra("command", GPS_BALANCED_COMMAND);
            context.startService(newIntent);
        }
    }

    private static final class TakePhotoCommand extends AbstractCommand {

        public TakePhotoCommand() { super(TAKE_PHOTO_COMMAND, "p", Finder.EQUALS); }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("hiddenCamera", false)) {
                Intent cameraIntent = new Intent(context, HiddenCaptureImageService.class);
                context.startService(cameraIntent);
            }
            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("command", TAKE_PHOTO_COMMAND);
            context.startService(newIntent);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String telegramId = settings.getString("telegramId", "");
            boolean hiddenCamera = settings.getBoolean("hiddenCamera", false);
            if (hiddenCamera) {
                Intent cameraIntent = new Intent(context, HiddenCaptureImageService.class);
                context.startService(cameraIntent);
            }
            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("telegramId", telegramId);
            newIntent.putExtra("command", TAKE_PHOTO_COMMAND);
            context.startService(newIntent);
        }
    }
}