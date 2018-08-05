package net.gmsworld.devicelocator.Utilities;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Patterns;

import net.gmsworld.devicelocator.BroadcastReceivers.DeviceAdminEventReceiver;
import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.Services.HiddenCaptureImageService;
import net.gmsworld.devicelocator.Services.RouteTrackingService;
import net.gmsworld.devicelocator.Services.SmsSenderService;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jstakun on 12/14/17.
 */

public class Command {

    private static final String TAG = Command.class.getSimpleName();

    //public
    public final static String RESUME_COMMAND = "resumedl"; //rs resume route tracking
    public final static String START_COMMAND = "startdl"; //s start route tracking and delete old route points if exists
    public final static String STOP_COMMAND = "stopdl"; //sp stop route tracking
    public final static String ROUTE_COMMAND = "routedl"; //r share currently recorded route
    public final static String SHARE_COMMAND = "locatedl"; //l hare current location via sms
    public final static String MUTE_COMMAND = "mutedl"; //m mute phone
    public final static String UNMUTE_COMMAND = "unmutedl"; //um unmute phone
    public final static String CALL_COMMAND = "calldl"; //c call to sender
    public final static String RADIUS_COMMAND = "radiusdl"; //ra change tracking radius, usage radiusdl x where is number of meters > 0
    public final static String GPS_HIGH_COMMAND = "gpshighdl"; //g set high gps accuracy
    public final static String GPS_BALANCED_COMMAND = "gpsbalancedl"; //gb set balanced gps accuracy
    public final static String NOTIFY_COMMAND = "notifydl"; //n set notification email, phone or telegram chat id usage notifydl p:x m:y t:z where x is mobile phone number, y is email address and z is Telegram chat or channel id.
    public final static String AUDIO_COMMAND = "audiodl"; //a enable audio transmitter
    public final static String AUDIO_OFF_COMMAND = "noaudiodl"; //na disable audio transmitter
    public final static String TAKE_PHOTO_COMMAND = "photodl"; //p if all permissions set take photo and send link
    public final static String PING_COMMAND = "pingdl"; //pg send ping to test connectivity
    public final static String RING_COMMAND = "ringdl"; //rn play ringtone
    public final static String RING_OFF_COMMAND = "ringoffdl"; //rn stop playing ringtone
    public final static String LOCK_SCREEN_COMMAND = "lockdl"; //ls lock screen now

    //private
    public final static String PIN_COMMAND = "pindl"; //send pin to notifiers (only when notifiers are set)
    public final static String ABOUT_COMMAND = "aboutdl"; //send app version info
    public final static String MESSAGE_COMMAND = "messagedl"; //cloud message received from other devices

    public final static String LOCK_SCREEN_FAILED = "lockfail"; //this is not command

    private static List<AbstractCommand> commands = null;

    public static String findCommandInSms(Context context, Intent intent) {
        for (AbstractCommand c : getCommands()) {
            if (c.findSmsCommand(context, intent)) {
                Log.d(TAG, "Found matching command");
                return c.getSmsCommand();
            }
        }
        return null;
    }

    public static String findCommandInMessage(Context context, String message, String sender) {
        for (AbstractCommand c : getCommands()) {
            if (c.findSocialCommand(context, message)) {
                Log.d(TAG, "Found matching social command");
                return c.getSmsCommand();
            } else if (c.findAppCommand(context, message, sender)) {
                Log.d(TAG, "Found matching app command");
                return c.getSmsCommand();
            }
        }
        return null;
    }

    private static List<AbstractCommand> getCommands() {
        Log.d(TAG, "Initializing commands...");
        if (commands == null) {
            commands = new ArrayList<AbstractCommand>();
            try {
                Class<?>[] commandClasses = Command.class.getDeclaredClasses();
                for (Class<?> command : commandClasses) {
                    Constructor<?> constructor = command.getDeclaredConstructors()[0];
                    constructor.setAccessible(true);
                    AbstractCommand c = (AbstractCommand) constructor.newInstance();
                    Log.d(TAG, "Initialized command " + c.getClass().getName());
                    commands.add(c);
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
        Log.d(TAG, "Done");
        return commands;
    }

    public static AbstractCommand getCommandByName(String name) {
        List<AbstractCommand> commands = getCommands();
        if (!StringUtils.endsWith("dl", name)) {
            name = name + "dl";
        }
        for (AbstractCommand c : commands) {
            if (StringUtils.equalsIgnoreCase(name, c.getSmsCommand())) {
                return c;
            }
        }
        return null;
    }

    //---------------------------------- Commands classes --------------------------------------

    private static final class StartRouteTrackerServiceStartCommand extends AbstractCommand {

        public StartRouteTrackerServiceStartCommand() {
            super(START_COMMAND, "s", Finder.STARTS);
        }

        @Override
        public boolean validateTokens() {
            return true;
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
            String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
            String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
            String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");

            boolean silentMode = false;
            if (commandTokens.length > 1 && (commandTokens[commandTokens.length-1].equalsIgnoreCase("silent") || commandTokens[commandTokens.length-1].equalsIgnoreCase("s"))) {
                silentMode = true;
            }

            if (Permissions.haveLocationPermission(context)) {
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, null, true, silentMode);
                settings.edit().putBoolean("motionDetectorRunning", true).apply();
            } else {
                Log.e(TAG, "Unable to start route tracking service due to lack of Location permission");
            }

            sendSmsNotification(context, sender, START_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
            String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
            String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
            String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");

            if (Permissions.haveLocationPermission(context)) {
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, null, true, false);
                settings.edit().putBoolean("motionDetectorRunning", true).apply();
            } else {
                Log.e(TAG, "Unable to start route tracking service due to lack of Location permission");
            }

            sendSocialNotification(context, START_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
            String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
            String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
            String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");

            if (Permissions.haveLocationPermission(context)) {
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId,  sender,true, false);
                settings.edit().putBoolean("motionDetectorRunning", true).apply();
            } else {
                Log.e(TAG, "Unable to start route tracking service due to lack of Location permission");
            }

            sendAppNotification(context, START_COMMAND, sender);
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
            String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
            String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
            String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");

            if (Permissions.haveLocationPermission(context)) {
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId,  null,false, false);
                settings.edit().putBoolean("motionDetectorRunning", true).apply();
            } else {
                Log.e(TAG, "Unable to start route tracking service due to lack of Location permission");
            }

            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", sender);
            newIntent.putExtra("notificationNumber", phoneNumber);
            newIntent.putExtra("email", email);
            newIntent.putExtra("telegramId", telegramId);
            newIntent.putExtra("command", RESUME_COMMAND);
            context.startService(newIntent);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
            String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
            String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
            String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");

            if (Permissions.haveLocationPermission(context)) {
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, null, false, false);
                settings.edit().putBoolean("motionDetectorRunning", true).apply();
            } else {
                Log.e(TAG, "Unable to start route tracking service due to lack of Location permission");
            }

            sendSocialNotification(context, RESUME_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
            String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
            String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
            String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");

            if (Permissions.haveLocationPermission(context)) {
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, sender,false, false);
                settings.edit().putBoolean("motionDetectorRunning", true).apply();
            } else {
                Log.e(TAG, "Unable to start route tracking service due to lack of Location permission");
            }

            sendAppNotification(context, RESUME_COMMAND, sender);
        }
    }

    private static final class StopRouteTrackerServiceStartCommand extends AbstractCommand {

        public StopRouteTrackerServiceStartCommand() {
            super(STOP_COMMAND, "sp", Finder.STARTS);
        }

        @Override
        public boolean validateTokens() {
            return true;
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            if (commandTokens.length > 1 && (commandTokens[commandTokens.length-1].equalsIgnoreCase("share") || commandTokens[commandTokens.length-1].equalsIgnoreCase("s"))) {
                String title = RouteTrackingServiceUtils.getRouteId(context);
                RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false, true, title, sender, null, null, null);
            } else {
                RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false, false, null, null, null, null, null);
            }
            settings.edit().putBoolean("motionDetectorRunning", false).apply();
            sendSmsNotification(context, sender, STOP_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            if (commandTokens.length > 1 && (commandTokens[commandTokens.length-1].equalsIgnoreCase("share") || commandTokens[commandTokens.length-1].equalsIgnoreCase("s"))) {
                String title = RouteTrackingServiceUtils.getRouteId(context);
                String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");
                String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");

                RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false, true, title, sender, email, telegramId, null);
            } else {
                RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false, false, null, null, null, null, null);
            }
            settings.edit().putBoolean("motionDetectorRunning", false).apply();
            sendSocialNotification(context, STOP_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            if (commandTokens.length > 1 && (commandTokens[commandTokens.length-1].equalsIgnoreCase("share") || commandTokens[commandTokens.length-1].equalsIgnoreCase("s"))) {
                String title = RouteTrackingServiceUtils.getRouteId(context);
                String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");
                String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");

                RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false, true, title, sender, email, telegramId, sender);
            } else {
                RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false, false, null, null, null, null, sender);
            }
            settings.edit().putBoolean("motionDetectorRunning", false).apply();
            sendAppNotification(context, STOP_COMMAND, sender);
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
                    Log.e(TAG, e.getMessage(), e);
                }
                Log.e(TAG, "Missing SMS and/or Location permission");
                sendSmsNotification(context, sender, SHARE_COMMAND);
            } else {
                sendSmsNotification(context, sender, null); //don't set SHARE_COMMAND here!
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            if (!Permissions.haveLocationPermission(context)) {
                sendSocialNotification(context, SHARE_COMMAND);
            } else {
                sendSocialNotification(context, null); //don't set SHARE_COMMAND here!
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            if (!Permissions.haveLocationPermission(context)) {
                sendAppNotification(context, SHARE_COMMAND, sender);
            } else {
                sendAppNotification(context, null, sender); //don't set SHARE_COMMAND here!
            }
        }
    }

    private static final class ShareRouteCommand extends AbstractCommand {

        public ShareRouteCommand() {
            super(ROUTE_COMMAND, "r", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            Intent routeTracingService = new Intent(context, RouteTrackingService.class);
            routeTracingService.putExtra(RouteTrackingService.COMMAND, RouteTrackingService.COMMAND_ROUTE);
            routeTracingService.putExtra("phoneNumber", sender);
            context.startService(routeTracingService);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            Intent routeTracingService = new Intent(context, RouteTrackingService.class);
            routeTracingService.putExtra(RouteTrackingService.COMMAND, RouteTrackingService.COMMAND_ROUTE);
            routeTracingService.putExtra("telegramId", settings.getString(MainActivity.NOTIFICATION_SOCIAL, ""));
            routeTracingService.putExtra("email", settings.getString(MainActivity.NOTIFICATION_EMAIL, ""));
            context.startService(routeTracingService);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            Intent routeTracingService = new Intent(context, RouteTrackingService.class);
            routeTracingService.putExtra(RouteTrackingService.COMMAND, RouteTrackingService.COMMAND_ROUTE);
            routeTracingService.putExtra("app", sender);
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
            if (audioMode != null) {
                audioMode.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                sendSmsNotification(context, sender, MUTE_COMMAND);
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioMode != null) {
                audioMode.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                sendSocialNotification(context, MUTE_COMMAND);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioMode != null) {
                audioMode.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                sendAppNotification(context, MUTE_COMMAND, sender);
            }
        }
    }

    private static final class UnmuteCommand extends AbstractCommand {

        public UnmuteCommand() {
            super(UNMUTE_COMMAND, "um", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioMode != null) {
                audioMode.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                sendSmsNotification(context, sender, UNMUTE_COMMAND);
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioMode != null) {
                audioMode.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                sendSocialNotification(context, UNMUTE_COMMAND);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioMode != null) {
                audioMode.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                sendAppNotification(context, UNMUTE_COMMAND, sender);
            }
        }
    }

    private static final class StartPhoneCallCommand extends AbstractCommand {

        public StartPhoneCallCommand() {
            super(CALL_COMMAND, "c", Finder.EQUALS);
        }


        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            if (!initPhoneCall(sender, context)) {
                sendSmsNotification(context, sender, CALL_COMMAND);
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            sendSocialNotification(context, CALL_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            sendAppNotification(context, CALL_COMMAND, sender);
        }

        private boolean initPhoneCall(String sender, Context context) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                try {
                    Uri call = Uri.parse("tel:" + sender);
                    Intent surf = new Intent(Intent.ACTION_CALL, call);
                    //surf.setPackage("com.android.phone"); //use default phone
                    //surf.setPackage("com.google.android.dialer");
                    surf.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(surf);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    return false;
                }
            } else {
                Log.e(TAG, "Unable to initiate phone call due to missing permission");
                return false;
            }
        }
    }

    private static final class ChangeRouteTrackerServiceRadiusCommand extends AbstractCommand {
        public ChangeRouteTrackerServiceRadiusCommand() {
            super(RADIUS_COMMAND, "ra", Finder.STARTS);
        }

        @Override
        public boolean validateTokens() {
            int radius = -1;
            if (commandTokens != null && commandTokens.length > 1) {
                try {
                    radius = Integer.parseInt(commandTokens[commandTokens.length-1]);
                } catch (Exception e) {
                    Log.e(TAG, "Wrong radius: " + commandTokens[commandTokens.length-1]);
                }
            }
            return radius > 0;
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            final int radius = Integer.parseInt(commandTokens[commandTokens.length-1]);
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
            String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
            String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");
            RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, phoneNumber, email, telegramId, null);
            settings.edit().putInt("radius", radius).apply();
            sendSmsNotification(context, sender, RADIUS_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            final int radius = Integer.parseInt(commandTokens[1]);
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
            String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
            String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");
            RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, phoneNumber, email, telegramId, null);
            settings.edit().putInt("radius", radius).apply();
            sendSocialNotification(context, RADIUS_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            final int radius = Integer.parseInt(commandTokens[1]);
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
            String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
            String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");
            RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, phoneNumber, email, telegramId, sender);
            settings.edit().putInt("radius", radius).apply();
            sendAppNotification(context, RADIUS_COMMAND, sender);
        }
    }

    private static final class AudioCommand extends AbstractCommand {

        public AudioCommand() {
            super(AUDIO_COMMAND, "a", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useAudio", true).apply();
            sendSmsNotification(context, sender, AUDIO_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useAudio", true).apply();
            sendSocialNotification(context, AUDIO_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useAudio", true).apply();
            sendSocialNotification(context, AUDIO_COMMAND);
        }
    }

    private static final class NoAudioCommand extends AbstractCommand {

        public NoAudioCommand() {
            super(AUDIO_OFF_COMMAND, "na", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useAudio", false).apply();
            sendSmsNotification(context, sender, AUDIO_OFF_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useAudio", false).apply();
            sendSocialNotification(context, AUDIO_OFF_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useAudio", false).apply();
            sendSocialNotification(context, AUDIO_OFF_COMMAND);
        }
    }

    private static final class HighGpsCommand extends AbstractCommand {

        public HighGpsCommand() {
            super(GPS_HIGH_COMMAND, "g", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("gpsAccuracy", 1).apply();
            RouteTrackingServiceUtils.setGpsAccuracy(context, RouteTrackingService.COMMAND_GPS_HIGH);
            sendSmsNotification(context, sender, GPS_HIGH_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("gpsAccuracy", 1).apply();
            RouteTrackingServiceUtils.setGpsAccuracy(context, RouteTrackingService.COMMAND_GPS_HIGH);
            sendSocialNotification(context, GPS_HIGH_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("gpsAccuracy", 1).apply();
            sendAppNotification(context, GPS_HIGH_COMMAND, sender);
        }
    }

    private static final class BalancedGpsCommand extends AbstractCommand {

        public BalancedGpsCommand() {
            super(GPS_BALANCED_COMMAND, "gb", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("gpsAccuracy", 0).apply();
            RouteTrackingServiceUtils.setGpsAccuracy(context, RouteTrackingService.COMMAND_GPS_BALANCED);
            sendSmsNotification(context, sender, GPS_BALANCED_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("gpsAccuracy", 0).apply();
            sendSocialNotification(context, GPS_BALANCED_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("gpsAccuracy", 0).apply();
            sendAppNotification(context, GPS_BALANCED_COMMAND, sender);
        }
    }

    private static final class TakePhotoCommand extends AbstractCommand {

        public TakePhotoCommand() { super(TAKE_PHOTO_COMMAND, "p", Finder.EQUALS); }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("hiddenCamera", false)) {
                Intent cameraIntent = new Intent(context, HiddenCaptureImageService.class);
                cameraIntent.putExtra("sender", sender);
                context.startService(cameraIntent);
            }
            sendSmsNotification(context, sender, TAKE_PHOTO_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("hiddenCamera", false)) {
                Intent cameraIntent = new Intent(context, HiddenCaptureImageService.class);
                context.startService(cameraIntent);
            }
            sendSocialNotification(context, TAKE_PHOTO_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("hiddenCamera", false)) {
                Intent cameraIntent = new Intent(context, HiddenCaptureImageService.class);
                cameraIntent.putExtra("app", sender);
                context.startService(cameraIntent);
            }
            sendAppNotification(context, TAKE_PHOTO_COMMAND, sender);
        }
    }

    private static final class NotifySettingsCommand extends AbstractCommand {

        public NotifySettingsCommand() {super(NOTIFY_COMMAND, "n", Finder.STARTS);}

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            String email = null;
            String phoneNumber = null;
            String telegramId = null;
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

            for (int i = 0; i < commandTokens.length; i++) {
                String token = commandTokens[i];
                if (token.startsWith("m:")) {
                    email = token.substring(2);
                } else if (token.startsWith("p:")) {
                    phoneNumber = token.substring(2);
                } else if (token.startsWith("t:")) {
                    telegramId = token.substring(2);
                }
            }

            if (telegramId != null || phoneNumber != null || email != null) {
                SharedPreferences.Editor editor = settings.edit();
                if (email != null) {
                    editor.putString(MainActivity.NOTIFICATION_EMAIL, email);
                } else {
                    email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
                }
                if (phoneNumber != null) {
                    editor.putString(MainActivity.NOTIFICATION_PHONE_NUMBER, phoneNumber);
                } else {
                    phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
                }
                if (telegramId != null) {
                    editor.putString(MainActivity.NOTIFICATION_SOCIAL, telegramId);
                } else {
                    telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");
                }
                editor.apply();

                int radius = settings.getInt("radius", 100);

                RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, phoneNumber, email, telegramId, null);

                Intent newIntent = new Intent(context, SmsSenderService.class);
                newIntent.putExtra("phoneNumber", sender);
                newIntent.putExtra("notificationNumber", phoneNumber);
                newIntent.putExtra("email", email);
                newIntent.putExtra("telegramId", telegramId);
                newIntent.putExtra("command", NOTIFY_COMMAND);
                context.startService(newIntent);
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            String email = null;
            String phoneNumber = null;
            String telegramId = null;
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

            for (int i = 0; i < commandTokens.length; i++) {
                String token = commandTokens[i];
                if (token.startsWith("m:")) {
                    email = token.substring(2);
                } else if (token.startsWith("p:")) {
                    phoneNumber = token.substring(2);
                } else if (token.startsWith("t:")) {
                    telegramId = token.substring(2);
                }
            }

            if (telegramId != null || phoneNumber != null || email != null) {
                SharedPreferences.Editor editor = settings.edit();
                if (email != null) {
                    editor.putString(MainActivity.NOTIFICATION_EMAIL, email);
                } else {
                    email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
                }
                if (phoneNumber != null) {
                    editor.putString(MainActivity.NOTIFICATION_PHONE_NUMBER, phoneNumber);
                } else {
                    phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
                }
                if (telegramId != null) {
                    editor.putString(MainActivity.NOTIFICATION_SOCIAL, telegramId);
                } else {
                    telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");
                }
                editor.apply();

                int radius = settings.getInt("radius", 100);

                RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, phoneNumber, email, telegramId, null);

                Intent newIntent = new Intent(context, SmsSenderService.class);
                newIntent.putExtra("notificationNumber", phoneNumber);
                newIntent.putExtra("email", email);
                newIntent.putExtra("telegramId", telegramId);
                newIntent.putExtra("command", NOTIFY_COMMAND);
                context.startService(newIntent);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            String email = null;
            String phoneNumber = null;
            String telegramId = null;
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

            for (int i = 0; i < commandTokens.length; i++) {
                String token = commandTokens[i];
                if (token.startsWith("m:")) {
                    email = token.substring(2);
                } else if (token.startsWith("p:")) {
                    phoneNumber = token.substring(2);
                } else if (token.startsWith("t:")) {
                    telegramId = token.substring(2);
                }
            }

            if (telegramId != null || phoneNumber != null || email != null) {
                SharedPreferences.Editor editor = settings.edit();
                if (email != null) {
                    editor.putString(MainActivity.NOTIFICATION_EMAIL, email);
                } else {
                    email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
                }
                if (phoneNumber != null) {
                    editor.putString(MainActivity.NOTIFICATION_PHONE_NUMBER, phoneNumber);
                } else {
                    phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
                }
                if (telegramId != null) {
                    editor.putString(MainActivity.NOTIFICATION_SOCIAL, telegramId);
                } else {
                    telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");
                }
                editor.apply();

                int radius = settings.getInt("radius", 100);

                RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, phoneNumber, email, telegramId, sender);

                Intent newIntent = new Intent(context, SmsSenderService.class);
                newIntent.putExtra("app", "app");
                newIntent.putExtra("command", NOTIFY_COMMAND);
                context.startService(newIntent);
            }
        }

        @Override
        public boolean validateTokens() {
            if (commandTokens != null && commandTokens.length > 1) {
                for (int i = 0; i < commandTokens.length; i++) {
                    String token = commandTokens[i];
                    if (token.startsWith("m:")) {
                        String newEmailAddress = token.substring(2);
                        if (StringUtils.isNotEmpty(newEmailAddress) && !Patterns.EMAIL_ADDRESS.matcher(newEmailAddress).matches()) {
                            return false;
                        }
                    } else if (token.startsWith("p:")) {
                        String newPhoneNumber = token.substring(2);
                        if (StringUtils.isNotEmpty(newPhoneNumber) && !Patterns.PHONE.matcher(newPhoneNumber).matches()) {
                            return false;
                        }
                    } else if (token.startsWith("t:")) {
                        String newTelegramId = token.substring(2);
                        if (StringUtils.isNotEmpty(newTelegramId) && !NumberUtils.isCreatable(newTelegramId)) {
                            return false;
                        }
                    } else {
                        //invalid token
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }
    }

    private static final class PingCommand extends AbstractCommand {

        public PingCommand() { super(PING_COMMAND, "pg", Finder.EQUALS); }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            sendSmsNotification(context, sender, PING_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            sendSocialNotification(context, PING_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            sendAppNotification(context, PING_COMMAND, sender);
        }
    }

    private static final class AboutCommand extends AbstractCommand {

        public AboutCommand() { super(ABOUT_COMMAND, "ab", Finder.EQUALS); }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            sendSmsNotification(context, sender, ABOUT_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            sendSocialNotification(context, ABOUT_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            sendAppNotification(context, ABOUT_COMMAND, sender);
        }
    }

    private static final class LockScreenCommand extends AbstractCommand {

        public LockScreenCommand() { super(LOCK_SCREEN_COMMAND, "ls", Finder.EQUALS); }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            if (lockScreenNow(context)) {
                sendSmsNotification(context, sender, LOCK_SCREEN_COMMAND);
            } else {
                sendSmsNotification(context, sender, LOCK_SCREEN_FAILED);
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            if (lockScreenNow(context)) {
                sendSocialNotification(context, LOCK_SCREEN_COMMAND);
            } else {
                sendSocialNotification(context, LOCK_SCREEN_FAILED);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            if (lockScreenNow(context)) {
                sendAppNotification(context, LOCK_SCREEN_COMMAND, sender);
            } else {
                sendAppNotification(context, LOCK_SCREEN_FAILED, sender);
            }
        }

        private boolean lockScreenNow(Context context) {
            DevicePolicyManager deviceManger = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            final ComponentName compName = new ComponentName(context, DeviceAdminEventReceiver.class);
            if (deviceManger != null && deviceManger.isAdminActive(compName)) {
                try {
                    deviceManger.lockNow();
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    private static final class RingCommand extends AbstractCommand {

        Ringtone ringtone = null;
        int currentMode = -1;
        int currentVolume = -1;

        public RingCommand() { super(RING_COMMAND, "rn", Finder.EQUALS); }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            playBeep(context);
            if (ringtone != null) {
                sendSmsNotification(context, sender, RING_COMMAND);
            } else {
                sendSmsNotification(context, sender, RING_OFF_COMMAND);
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            playBeep(context);
            if (ringtone != null) {
                sendSocialNotification(context, RING_COMMAND);
            } else {
                sendSocialNotification(context, RING_OFF_COMMAND);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            playBeep(context);
            if (ringtone != null) {
                sendAppNotification(context, RING_COMMAND, sender);
            } else {
                sendAppNotification(context, RING_OFF_COMMAND, sender);
            }
        }

        private void playBeep(Context context) {
            try {
                final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (ringtone == null && audioMode != null) {
                    currentMode = audioMode.getRingerMode();
                    if (currentMode != AudioManager.RINGER_MODE_NORMAL) {
                        audioMode.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    }
                    currentVolume = audioMode.getStreamVolume(AudioManager.STREAM_RING);
                    if (currentVolume < audioMode.getStreamMaxVolume(AudioManager.STREAM_RING)) {
                        audioMode.setStreamVolume(AudioManager.STREAM_RING, audioMode.getStreamMaxVolume(AudioManager.STREAM_RING), AudioManager.FLAG_SHOW_UI);
                    }
                    //RingtoneManager.TYPE_ALARM    RingtoneManager.TYPE_RINGTONE
                    Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                    ringtone = RingtoneManager.getRingtone(context, ringtoneUri);
                    ringtone.play();
                    Log.d(TAG, "Ringtone " + ringtoneUri.toString() + " should be playing now");
                } else if (audioMode != null) {
                    ringtone.stop();
                    ringtone = null;
                    Log.d(TAG, "Ringtone should stop playing now");
                    if (currentMode != audioMode.getMode() && currentMode != -1) {
                        audioMode.setRingerMode(currentMode);
                        currentMode = -1;
                    }
                    if (currentVolume != audioMode.getStreamVolume(AudioManager.STREAM_RING) && currentVolume != -1) {
                        audioMode.setStreamVolume(AudioManager.STREAM_RING, currentVolume, AudioManager.FLAG_SHOW_UI);
                        currentVolume = -1;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    private static final class MessageCommand extends AbstractCommand {

        public MessageCommand() { super(MESSAGE_COMMAND, "ms", Finder.STARTS); }

        @Override
        public boolean validateTokens() {
            return true;
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            showMessageNotification(context);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            showMessageNotification(context);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context) {
            showMessageNotification(context);
        }

        private void showMessageNotification(Context context) {
            String message = StringUtils.join(commandTokens, " ", 1, commandTokens.length);
            int notificationId = (int)System.currentTimeMillis();
            if (StringUtils.startsWithIgnoreCase(message, Messenger.ROUTE_MESSAGE_PREFIX)) {
                notificationId = RouteTrackingService.NOTIFICATION_ROUTE_ID;
            }
            Notification notification =  NotificationUtils.buildMessageNotification(context, notificationId, message);
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(notificationId, notification);
            }
        }
    }
}
