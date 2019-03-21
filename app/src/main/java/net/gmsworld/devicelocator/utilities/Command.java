package net.gmsworld.devicelocator.utilities;

import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;
import android.util.Patterns;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.PinActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.RingingActivity;
import net.gmsworld.devicelocator.broadcastreceivers.DeviceAdminEventReceiver;
import net.gmsworld.devicelocator.broadcastreceivers.SmsReceiver;
import net.gmsworld.devicelocator.services.DlFirebaseMessagingService;
import net.gmsworld.devicelocator.services.HiddenCaptureImageService;
import net.gmsworld.devicelocator.services.RouteTrackingService;
import net.gmsworld.devicelocator.services.SmsSenderService;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Constructor;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

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
    public final static String SHARE_COMMAND = "locatedl"; //l share current location
    public final static String MUTE_COMMAND = "mutedl"; //m mute phone
    public final static String UNMUTE_COMMAND = "unmutedl"; //um unmute phone
    public final static String CALL_COMMAND = "calldl"; //c call to sender
    public final static String RADIUS_COMMAND = "radiusdl"; //ra change tracking radius, usage radiusdl x where is number of meters > 0
    public final static String GPS_HIGH_COMMAND = "gpshighdl"; //g set high gps accuracy
    public final static String GPS_BALANCED_COMMAND = "gpsbalancedl"; //gb set balanced gps accuracy
    public final static String NOTIFY_COMMAND = "notifydl"; //n set notification email, phone or telegram chat id usage notifydl p:x m:y t:z where x is mobile phone number, y is email address and z is Telegram chat or channel id.
    public final static String TAKE_PHOTO_COMMAND = "photodl"; //p if all permissions set take photo and send link
    public final static String PING_COMMAND = "pingdl"; //pg send ping to test connectivity
    public final static String HELLO_COMMAND = "hellodl"; //hl send ping to test connectivity
    public final static String RING_COMMAND = "ringdl"; //rn play ringtone
    public final static String RING_OFF_COMMAND = "ringoffdl"; //rn stop playing ringtone
    public final static String LOCK_SCREEN_COMMAND = "lockdl"; //ls lock screen now
    public final static String ABOUT_COMMAND = "aboutdl"; //ab send app version info
    public final static String CONFIG_COMMAND = "configdl"; //cf change app configuration
    public final static String PERIMETER_COMMAND = "perimeterdl"; //pm perimeter cloud message
    public final static String RESET_COMMAND = "resetdl"; //rt reset device to factory settings

    //private
    public final static String PIN_COMMAND = "pindl"; //send pin to notifiers (only when notifiers are set)
    public final static String MESSAGE_COMMAND = "messagedl"; //ms cloud message received from other devices
    public final static String AUDIO_COMMAND = "audiodl"; //a enable audio transmitter
    public final static String AUDIO_OFF_COMMAND = "noaudiodl"; //na disable audio transmitter

    //not a command
    public final static String LOCK_SCREEN_FAILED = "lockfail"; //this is not command
    public final static String RESET_FAILED = "resetfail"; //this is not command
    public final static String MUTE_FAILED = "mutefail"; //this is not command
    public final static String STOPPED_TRACKER = "stopped"; //this is not command
    public final static String INVALID_PIN = "invalidPin";
    public final static String INVALID_COMMAND = "invalidCommand";

    private static List<AbstractCommand> commands = null;

    public static String findCommandInSms(Context context, Bundle extras) {
        if (extras != null && extras.containsKey("pdus")) {
            Object[] pdus = (Object[]) extras.get("pdus");
            if (pdus != null) {
                Log.d(TAG, "Found " + pdus.length + " sms messages");
                for (int i = 0; i < pdus.length; i++) {
                    SmsMessage sms;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        String format = extras.getString("format");
                        sms = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                    } else {
                        sms = SmsMessage.createFromPdu((byte[]) pdus[i]);
                    }
                    if (sms != null) {
                        String smsMessage = StringUtils.trim(sms.getMessageBody());
                        if (StringUtils.isNotEmpty(smsMessage)) {
                            //Log.d(TAG, "Checking sms message " + smsMessage);
                            final PreferencesUtils prefs = new PreferencesUtils(context);
                            final String pin = prefs.getEncryptedString(PinActivity.DEVICE_PIN);
                            final boolean isPinRequired = prefs.getBoolean("settings_sms_without_pin", true);
                            final boolean hasSocialNotifiers = StringUtils.isNotEmpty(prefs.getString(MainActivity.NOTIFICATION_SOCIAL)) || StringUtils.isNotEmpty(prefs.getString(MainActivity.NOTIFICATION_EMAIL));
                            for (AbstractCommand c : getCommands()) {
                                if (c.findSmsCommand(context, smsMessage, sms.getOriginatingAddress(), pin, isPinRequired, hasSocialNotifiers)) {
                                    Log.d(TAG, "Found matching sms command " + c.getSmsCommand());
                                    return c.getSmsCommand();
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public static String findCommandInMessage(Context context, String message, String sender, Location location, Bundle extras) {
        final PreferencesUtils prefs = new PreferencesUtils(context);
        final String pin = prefs.getEncryptedString(PinActivity.DEVICE_PIN);
        final boolean isPinRequired = prefs.getBoolean("settings_sms_without_pin", true);
        final boolean hasSocialNotifiers = StringUtils.isNotEmpty(prefs.getString(MainActivity.NOTIFICATION_SOCIAL)) || StringUtils.isNotEmpty(prefs.getString(MainActivity.NOTIFICATION_EMAIL));
        for (AbstractCommand c : getCommands()) {
            if (c.findAppCommand(context, StringUtils.trim(message), sender, location, extras, pin, isPinRequired)) {
                Log.d(TAG, "Found matching cloud command");
                return c.getSmsCommand();
            } else if (c.findSocialCommand(context, StringUtils.trim(message), pin, isPinRequired, hasSocialNotifiers)) {
                Log.d(TAG, "Found matching social command");
                return c.getSmsCommand();
            }
        }
        Log.w(TAG, "Didn't found matching command " + message);
        return null;
    }

    private static List<AbstractCommand> getCommands() {
        if (commands == null) {
            Log.d(TAG, "Initializing commands...");
            commands = new ArrayList<>();
            try {
                Class<?>[] commandClasses = Command.class.getDeclaredClasses();
                for (Class<?> command : commandClasses) {
                    Constructor<?> constructor = command.getDeclaredConstructors()[0];
                    constructor.setAccessible(true);
                    AbstractCommand c = (AbstractCommand) constructor.newInstance();
                    Log.d(TAG, "Initialized command " + c.getClass().getName());
                    commands.add(c);
                }
                Log.d(TAG, "Done");
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
        return commands;
    }

    public static AbstractCommand getCommandByName(String name) {
        List<AbstractCommand> commands = getCommands();
        if (!StringUtils.endsWith(name, "dl")) {
            name = name + "dl";
        }
        for (AbstractCommand c : commands) {
            //Log.d(TAG, "Comparing " + name + " to " +  c.getSmsCommand() + "...");
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
            return (commandTokens == null || commandTokens.length == 1 || StringUtils.equalsAnyIgnoreCase(commandTokens[commandTokens.length-1], "s", "silent") || StringUtils.isNumeric(commandTokens[commandTokens.length-1]));
        }

        @Override
        public String getOppositeCommand() {
            return STOP_COMMAND;
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
            String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
            String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
            String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");

            RouteTrackingService.Mode mode = RouteTrackingService.Mode.Normal;
            if (commandTokens.length > 1 && (commandTokens[commandTokens.length-1].equalsIgnoreCase("silent") || commandTokens[commandTokens.length-1].equalsIgnoreCase("s"))) {
                mode = RouteTrackingService.Mode.Silent;
            }

            if (Permissions.haveLocationPermission(context)) {
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, null, true, mode);
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
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, null, true, RouteTrackingService.Mode.Normal);
                settings.edit().putBoolean("motionDetectorRunning", true).apply();
            } else {
                Log.e(TAG, "Unable to start route tracking service due to lack of Location permission");
            }

            sendSocialNotification(context, START_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
            String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
            String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
            String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");

            if (Permissions.haveLocationPermission(context)) {
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId,  sender,true, RouteTrackingService.Mode.Normal);
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
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId,  null,false, RouteTrackingService.Mode.Normal);
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
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, null, false, RouteTrackingService.Mode.Normal);
                settings.edit().putBoolean("motionDetectorRunning", true).apply();
            } else {
                Log.e(TAG, "Unable to start route tracking service due to lack of Location permission");
            }

            sendSocialNotification(context, RESUME_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
            String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
            String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
            String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");

            if (Permissions.haveLocationPermission(context)) {
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, sender,false, RouteTrackingService.Mode.Normal);
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

        public String getLabel() { return StringUtils.capitalize(getSmsCommand().substring(0, getSmsCommand().length()-2)); }

        @Override
        public String getDefaultArgs() {
            return "s";
        }

        @Override
        public String getOppositeCommand() {
            return START_COMMAND;
        }

        @Override
        public boolean validateTokens() {
            return (commandTokens == null || commandTokens.length == 1 || StringUtils.equalsAnyIgnoreCase(commandTokens[commandTokens.length-1], "s", "share") || StringUtils.isNumeric(commandTokens[commandTokens.length-1]));
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            if (GmsSmartLocationManager.getInstance().isEnabled()) {
                if (commandTokens.length > 1 && (commandTokens[commandTokens.length - 1].equalsIgnoreCase("share") || commandTokens[commandTokens.length - 1].equalsIgnoreCase("s"))) {
                    String title = RouteTrackingServiceUtils.getRouteId(context);
                    RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false, true, title, sender, null, null, null);
                } else {
                    RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false, false, null, null, null, null, null);
                }
            }
            if (settings.getBoolean("motionDetectorRunning", false)) {
                settings.edit().putBoolean("motionDetectorRunning", false).apply();
                sendSmsNotification(context, sender, STOP_COMMAND);
            } else {
                sendSmsNotification(context, sender, STOPPED_TRACKER);
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            if (GmsSmartLocationManager.getInstance().isEnabled()) {
                if (commandTokens.length > 1 && (commandTokens[commandTokens.length - 1].equalsIgnoreCase("share") || commandTokens[commandTokens.length - 1].equalsIgnoreCase("s"))) {
                    String title = RouteTrackingServiceUtils.getRouteId(context);
                    String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");
                    String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
                    RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false, true, title, sender, email, telegramId, null);
                } else {
                    RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false, false, null, null, null, null, null);
                }
            }
            if (settings.getBoolean("motionDetectorRunning", false)) {
                settings.edit().putBoolean("motionDetectorRunning", false).apply();
                sendSocialNotification(context, STOP_COMMAND);
            } else {
                sendSocialNotification(context, STOPPED_TRACKER);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            if (GmsSmartLocationManager.getInstance().isEnabled()) {
                if (commandTokens.length > 1 && (commandTokens[commandTokens.length - 1].equalsIgnoreCase("share") || commandTokens[commandTokens.length - 1].equalsIgnoreCase("s"))) {
                    String title = RouteTrackingServiceUtils.getRouteId(context);
                    String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");
                    String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
                    RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false, true, title, null, email, telegramId, sender);
                } else {
                    RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false, false, null, null, null, null, sender);
                }
            }
            if (settings.getBoolean("motionDetectorRunning", false)) {
                settings.edit().putBoolean("motionDetectorRunning", false).apply();
                sendAppNotification(context, STOP_COMMAND, sender);
            } else {
                sendAppNotification(context, STOPPED_TRACKER, sender);
            }
        }
    }

    private static final class ShareLocationCommand extends AbstractCommand {

        public ShareLocationCommand() {
            super(SHARE_COMMAND, "l", Finder.EQUALS);
        }

        @Override
        public boolean canResend() {
            return true;
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            if (!Permissions.haveSendSMSAndLocationPermission(context)) {
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
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
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
        public String getOppositeCommand() {
            if (!GmsSmartLocationManager.getInstance().isEnabled()) {
                return START_COMMAND;
            } else {
                return super.getOppositeCommand();
            }
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
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
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
        public String getOppositeCommand() {
            return UNMUTE_COMMAND;
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            if (mute(context)) {
                sendSmsNotification(context, sender, MUTE_COMMAND);
            } else {
                sendSmsNotification(context, sender, MUTE_FAILED);
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            if (mute(context)) {
                sendSocialNotification(context, MUTE_COMMAND);
            } else {
                sendSocialNotification(context, MUTE_FAILED);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            if (mute(context)) {
                sendAppNotification(context, MUTE_COMMAND, sender);
            } else {
                sendAppNotification(context, MUTE_FAILED, sender);
            }
        }

        private boolean mute(Context context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (notificationManager != null && !notificationManager.isNotificationPolicyAccessGranted()) {
                    return false;
                }
            }
            final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioMode != null) {
                audioMode.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                return true;
            } else {
                return false;
            }
        }
    }

    private static final class UnmuteCommand extends AbstractCommand {

        public UnmuteCommand() {
            super(UNMUTE_COMMAND, "um", Finder.EQUALS);
        }

        @Override
        public String getOppositeCommand() {
            return MUTE_COMMAND;
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
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioMode != null) {
                audioMode.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                sendAppNotification(context, UNMUTE_COMMAND, sender);
            }
        }
    }

    private static final class StartPhoneCallCommand extends AbstractCommand {

        public StartPhoneCallCommand() {
            super(CALL_COMMAND, "c", Finder.STARTS);
        }

        @Override
        public boolean validateTokens() {
            return (commandTokens == null || commandTokens.length == 1 || Patterns.PHONE.matcher(commandTokens[commandTokens.length-1]).matches() || StringUtils.isNumeric(commandTokens[commandTokens.length-1]));
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
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            String phoneNumber = extras.getString("args");
            if (phoneNumber != null && Patterns.PHONE.matcher(phoneNumber).matches() && SmsReceiver.contactExists(context, phoneNumber)) {
                if (!initPhoneCall(phoneNumber, context)) {
                    sendAppNotification(context, CALL_COMMAND, sender);
                }
            } else {
                sendAppNotification(context, CALL_COMMAND, sender);
            }
        }

        private boolean initPhoneCall(String sender, Context context) {
            if (Permissions.haveCallPhonePermission(context)) {
                try {
                    Log.d(TAG, "Calling " + sender + " ...");
                    Uri call = Uri.parse("tel:" + sender);
                    Intent callIntent = new Intent(Intent.ACTION_CALL, call);
                    //surf.setPackage("com.android.phone"); //use default phone
                    //surf.setPackage("com.google.android.dialer");
                    callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(callIntent);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    return false;
                }
            } else {
                Log.e(TAG, "Unable to initiate phone call due to missing permission!");
                return false;
            }
        }
    }

    private static final class ChangeRouteTrackerServiceRadiusCommand extends AbstractCommand {
        public ChangeRouteTrackerServiceRadiusCommand() {
            super(RADIUS_COMMAND, "ra", Finder.STARTS);
        }

        @Override
        public String getDefaultArgs() {
            return "100";
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
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
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
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
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
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
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
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(RouteTrackingService.GPS_ACCURACY, 1).apply();
            RouteTrackingServiceUtils.setGpsAccuracy(context, RouteTrackingService.COMMAND_GPS_HIGH);
            sendSmsNotification(context, sender, GPS_HIGH_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(RouteTrackingService.GPS_ACCURACY, 1).apply();
            RouteTrackingServiceUtils.setGpsAccuracy(context, RouteTrackingService.COMMAND_GPS_HIGH);
            sendSocialNotification(context, GPS_HIGH_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(RouteTrackingService.GPS_ACCURACY, 1).apply();
            sendAppNotification(context, GPS_HIGH_COMMAND, sender);
        }
    }

    private static final class BalancedGpsCommand extends AbstractCommand {

        public BalancedGpsCommand() {
            super(GPS_BALANCED_COMMAND, "gb", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(RouteTrackingService.GPS_ACCURACY, 0).apply();
            RouteTrackingServiceUtils.setGpsAccuracy(context, RouteTrackingService.COMMAND_GPS_BALANCED);
            sendSmsNotification(context, sender, GPS_BALANCED_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(RouteTrackingService.GPS_ACCURACY, 0).apply();
            sendSocialNotification(context, GPS_BALANCED_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(RouteTrackingService.GPS_ACCURACY, 0).apply();
            sendAppNotification(context, GPS_BALANCED_COMMAND, sender);
        }
    }

    private static final class TakePhotoCommand extends AbstractCommand {

        public TakePhotoCommand() { super(TAKE_PHOTO_COMMAND, "p", Finder.EQUALS); }

        @Override
        public boolean canResend() {
            return true;
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("hiddenCamera", false) && !HiddenCaptureImageService.isBusy()) {
                Intent cameraIntent = new Intent(context, HiddenCaptureImageService.class);
                cameraIntent.putExtra("sender", sender);
                context.startService(cameraIntent);
            }
            sendSmsNotification(context, sender, TAKE_PHOTO_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("hiddenCamera", false) && !HiddenCaptureImageService.isBusy()) {
                Intent cameraIntent = new Intent(context, HiddenCaptureImageService.class);
                context.startService(cameraIntent);
            }
            sendSocialNotification(context, TAKE_PHOTO_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("hiddenCamera", false) && !HiddenCaptureImageService.isBusy()) {
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
        public boolean validateTokens() {
            if (commandTokens != null && commandTokens.length > 1) {
                boolean hasValidToken = false;
                for (String token : commandTokens) {
                    Log.d(TAG, "Validating token " + token);
                    if (token.startsWith("m:")) {
                        String newEmailAddress = token.substring(2);
                        if (StringUtils.isNotEmpty(newEmailAddress) && !Patterns.EMAIL_ADDRESS.matcher(newEmailAddress).matches()) {
                            Log.d(TAG, "Invalid email");
                            return false;
                        } else {
                            hasValidToken = true;
                        }
                    } else if (token.startsWith("p:")) {
                        String newPhoneNumber = token.substring(2);
                        if (StringUtils.isNotEmpty(newPhoneNumber) && !Patterns.PHONE.matcher(newPhoneNumber).matches()) {
                            Log.d(TAG, "Invalid phone");
                            return false;
                        } else {
                            hasValidToken = true;
                        }
                    } else if (token.startsWith("t:")) {
                        String newTelegramId = token.substring(2);
                        if (StringUtils.isNotEmpty(newTelegramId) && !Messenger.isValidTelegramId(newTelegramId)) {
                            Log.d(TAG, "Invalid telegram");
                            return false;
                        } else {
                            hasValidToken = true;
                        }
                    } else {
                        //skip token
                    }
                }
                return hasValidToken;
            } else {
                return false;
            }
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            String email = null;
            String phoneNumber = null;
            String telegramId = null;
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

            for (String token : commandTokens) {
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
                    Messenger.sendEmailRegistrationRequest(context, email, 1);
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
                    Messenger.sendTelegramRegistrationRequest(context, telegramId, 1);
                } else {
                    telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");
                }
                editor.apply();

                int radius = settings.getInt("radius", 100);

                RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, phoneNumber, email, telegramId, null);

                sendSmsNotification(context, sender, NOTIFY_COMMAND);
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            String email = null;
            String phoneNumber = null;
            String telegramId = null;
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

            for (String token : commandTokens) {
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
                    Messenger.sendEmailRegistrationRequest(context, email, 1);
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
                    Messenger.sendTelegramRegistrationRequest(context, telegramId, 1);
                } else {
                    telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");
                }
                editor.apply();

                int radius = settings.getInt("radius", 100);

                RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, phoneNumber, email, telegramId, null);

                sendSocialNotification(context, NOTIFY_COMMAND);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            String email = null;
            String phoneNumber = null;
            String telegramId = null;
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

            for (String token : commandTokens) {
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
                    Messenger.sendEmailRegistrationRequest(context, email, 1);
                }
                if (phoneNumber != null) {
                    editor.putString(MainActivity.NOTIFICATION_PHONE_NUMBER, phoneNumber);
                }
                if (telegramId != null) {
                    editor.putString(MainActivity.NOTIFICATION_SOCIAL, telegramId);
                    Messenger.sendTelegramRegistrationRequest(context, telegramId, 1);
                }
                editor.apply();

                int radius = settings.getInt("radius", 100);

                RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, phoneNumber, email, telegramId, sender);

                sendAppNotification(context, NOTIFY_COMMAND, sender);
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
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            sendAppNotification(context, PING_COMMAND, sender);
        }
    }

    private static final class HelloCommand extends AbstractCommand {

        public HelloCommand() { super(HELLO_COMMAND, "hl", Finder.EQUALS); }

        @Override
        public boolean canResend() {
            return true;
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            sendSmsNotification(context, sender, HELLO_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            sendSocialNotification(context, HELLO_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            sendAppNotification(context, HELLO_COMMAND, sender);
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
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            sendAppNotification(context, ABOUT_COMMAND, sender);
        }
    }

    private static final class LockScreenCommand extends AbstractCommand {

        public LockScreenCommand() { super(LOCK_SCREEN_COMMAND, "ls", Finder.EQUALS); }

        @Override
        public boolean canResend() {
            return true;
        }

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
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
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
                    PreferenceManager.getDefaultSharedPreferences(context).edit().remove("pinFailedCount").remove("pinVerificationMillis").apply();
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
        public String getOppositeCommand() {
            return RING_COMMAND;
        }

        @Override
        public String getLabel() {
            if (ringtone != null) {
                return "Stop ringing";
            } else {
                return super.getLabel();
            }
        }

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
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
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
                //TODO show/hide ringing activity
                Intent ringIntent = new Intent(context, RingingActivity.class);
                if (ringtone != null) {
                    ringIntent.setAction(RING_COMMAND);
                } else {
                    ringIntent.setAction(RING_OFF_COMMAND);
                }
                ringIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(ringIntent);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    private static final class MessageCommand extends AbstractCommand {

        public MessageCommand() { super(MESSAGE_COMMAND, "ms", Finder.STARTS); }

        @Override
        public boolean validateTokens() {
            return (commandTokens.length >= 2);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            //message is received only from the cloud
            Log.e(TAG, "This method shouldn't be called!");
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            //message is received only from the cloud
            Log.e(TAG, "This method shouldn't be called!");
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            showMessageNotification(context, location, extras);
        }

        private void showMessageNotification(Context context, Location location, Bundle extras) {
            String message = null;
            try {
                if (commandTokens.length > 2) {
                    message = URLDecoder.decode(StringUtils.join(commandTokens, " ", 1, commandTokens.length), "UTF-8");
                } else if (commandTokens.length == 2) {
                    message = URLDecoder.decode(commandTokens[1], "UTF-8");
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
            if (StringUtils.isNotEmpty(message)) {
                if (StringUtils.containsIgnoreCase(message, "perimeter")) {
                    String[] tokens = StringUtils.split(message, "\n");
                    if (tokens.length > 1 && StringUtils.isNumeric(tokens[tokens.length-1])) {
                        int perimeter = Integer.valueOf(tokens[tokens.length - 1]);
                        Location lastLocation = SmartLocation.with(context).location(new LocationGooglePlayServicesWithFallbackProvider(context)).getLastLocation();
                        if (lastLocation != null && (System.currentTimeMillis() - lastLocation.getTime() < 10 * 60 * 1000)) { //10 min
                            int distance = (int) location.distanceTo(lastLocation);
                            if (distance <= perimeter) {
                                message = StringUtils.join(tokens, "\n", 0, tokens.length-1);
                            } else {
                                message = null;
                            }
                        } else {
                            message = null;
                        }
                    } else {
                        message = null;
                    }
                }
                if (message != null) {
                    NotificationUtils.showMessageNotification(context, message, location, extras);
                }
            }
        }
    }

    private static final class PerimeterCommand extends AbstractCommand {

        public PerimeterCommand() {
            super(PERIMETER_COMMAND, "pm", Finder.STARTS);
        }

        public boolean validateTokens() {
            int radius = -1;
            if (commandTokens != null && commandTokens.length > 1) {
                try {
                    radius = Integer.parseInt(commandTokens[commandTokens.length-1]);
                } catch (Exception e) {
                    Log.e(TAG, "Wrong perimeter radius: " + commandTokens[commandTokens.length-1]);
                }
            }
            return radius > 0;
        }

        @Override
        public String getDefaultArgs() { return "500"; }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            //message is received only from the cloud
            Log.e(TAG, "This method shouldn't be called!");
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            //message is received only from the cloud
            Log.e(TAG, "This method shouldn't be called!");
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            if (Permissions.haveLocationPermission(context)) {
                final int perimeter = Integer.parseInt(commandTokens[1]);
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                final int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, null, null, null,  sender,true, RouteTrackingService.Mode.Perimeter);
                settings.edit().putBoolean("motionDetectorRunning", true).putInt("perimeter", perimeter).apply();
            } else {
                Log.e(TAG, "Unable to start route tracking service due to lack of Location permission");
            }
        }
    }

    private static final class ConfigCommand extends AbstractCommand {
        public ConfigCommand() { super(CONFIG_COMMAND, "cf", Finder.STARTS); }

        @Override
        public boolean validateTokens() {
            if (commandTokens != null && commandTokens.length > 1) {
                for (int i = 1; i < commandTokens.length; i++) {
                    String token = commandTokens[i];
                    if (!StringUtils.equalsAnyIgnoreCase(token, "lm:on", "lm:off", "gpsm:on", "gpsm:off", "mapm:on", "mapm:off", "gpsb:on", "gpsb:off", "gpsh:on", "gpsh:off", "nt:on") && !token.startsWith("dn:")) {
                        //location message on/off
                        //Gps message on/off
                        //Google maps link message on/off
                        //Gpsbalance/Gpshigh on/off
                        //Notification test on
                        //Device name
                        Log.d(TAG, "Invalid config param: " + token);
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            applyConfigChange(context);
            sendSmsNotification(context, sender, CONFIG_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            applyConfigChange(context);
            sendSocialNotification(context, CONFIG_COMMAND);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            applyConfigChange(context);
            sendAppNotification(context, CONFIG_COMMAND, sender);
        }

        private void applyConfigChange(Context context) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

            for (String token : commandTokens) {
                if (token.equalsIgnoreCase("lm:on")) {
                    //location message on
                    settings.edit().putBoolean(SmsSenderService.SEND_ACKNOWLEDGE_MESSAGE, true).apply();
                } else if (token.equalsIgnoreCase("lm:off")) {
                    //location message off
                    settings.edit().putBoolean(SmsSenderService.SEND_ACKNOWLEDGE_MESSAGE, false).apply();
                } else if (token.equalsIgnoreCase("gpsm:on")) {
                    //Gps message on
                    settings.edit().putBoolean(SmsSenderService.SEND_LOCATION_MESSAGE, true).apply();
                } else if (token.equalsIgnoreCase("gpsm:off")) {
                    //Gps message off
                    settings.edit().putBoolean(SmsSenderService.SEND_LOCATION_MESSAGE, false).apply();
                } else if (token.equalsIgnoreCase("mapm:on")) {
                    //Maps link message on
                    settings.edit().putBoolean(SmsSenderService.SEND_MAP_LINK_MESSAGE, true).apply();
                } else if (token.equalsIgnoreCase("mapm:off")) {
                    //Maps link message off
                    settings.edit().putBoolean(SmsSenderService.SEND_MAP_LINK_MESSAGE, false).apply();
                } else if (token.equalsIgnoreCase("gpsb:on")) {
                    //Gpsbalance on
                    settings.edit().putInt(RouteTrackingService.GPS_ACCURACY, 0).apply();
                } else if (token.equalsIgnoreCase("gpsb:off")) {
                    //Gpsbalance off
                    settings.edit().putInt(RouteTrackingService.GPS_ACCURACY, 1).apply();
                } else if (token.equalsIgnoreCase("gpsh:on")) {
                    //Gpshigh on
                    settings.edit().putInt(RouteTrackingService.GPS_ACCURACY, 1).apply();
                } else if (token.equalsIgnoreCase("gpsh:off")) {
                    //Gpshigh off
                    settings.edit().putInt(RouteTrackingService.GPS_ACCURACY, 0).apply();
                } else if (token.equalsIgnoreCase("nt:on")) {
                    //start Notification test
                    final String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
                    final String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
                    final String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");

                    if (StringUtils.isNotEmpty(phoneNumber) || StringUtils.isNotEmpty(email) || StringUtils.isNotEmpty(telegramId)) {
                        if (StringUtils.isNotEmpty(phoneNumber)) {
                            Intent newIntent = new Intent(context, SmsSenderService.class);
                            newIntent.putExtra("phoneNumber", phoneNumber);
                            newIntent.putExtra("command", Command.HELLO_COMMAND);
                            context.startService(newIntent);
                        }
                        if (StringUtils.isNotEmpty(email) || StringUtils.isNotEmpty(telegramId)) {
                            Intent newIntent = new Intent(context, SmsSenderService.class);
                            newIntent.putExtra("telegramId", telegramId);
                            newIntent.putExtra("email", email);
                            newIntent.putExtra("command", Command.HELLO_COMMAND);
                            context.startService(newIntent);
                        }
                    }
                } else if (token.startsWith("dn:")) {
                    //Device name
                    String newDeviceName = token.substring(3);
                    if (!StringUtils.equals(settings.getString(MainActivity.DEVICE_NAME, ""), newDeviceName)) {
                        String normalizedDeviceName = StringUtils.trimToEmpty(newDeviceName).replace(' ', '-');
                        if (DlFirebaseMessagingService.sendRegistrationToServer(context, settings.getString(MainActivity.USER_LOGIN, ""), normalizedDeviceName, true)) {
                            settings.edit().putString(MainActivity.DEVICE_NAME, normalizedDeviceName).apply();
                        } else {
                            Log.e(TAG, "Failed to register device on server");
                        }
                    }
                }
            }
        }
    }

    private static final class ResetDeviceCommand extends AbstractCommand {

        public ResetDeviceCommand() { super(RESET_COMMAND, "rt", Finder.EQUALS); }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            DevicePolicyManager deviceManger = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            final ComponentName compName = new ComponentName(context, DeviceAdminEventReceiver.class);
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("allowReset", false) && deviceManger != null && deviceManger.isAdminActive(compName)) {
                try {
                    sendSmsNotification(context, sender, RESET_COMMAND);
                    deviceManger.wipeData(0);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    sendSmsNotification(context, sender, RESET_FAILED);
                }
            } else {
                sendSmsNotification(context, sender, RESET_FAILED);
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            DevicePolicyManager deviceManger = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            final ComponentName compName = new ComponentName(context, DeviceAdminEventReceiver.class);
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("allowReset", false) && deviceManger != null && deviceManger.isAdminActive(compName)) {
                try {
                    sendSocialNotification(context, RESET_COMMAND);
                    deviceManger.wipeData(0);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    sendSocialNotification(context, RESET_FAILED);
                }
            } else {
                sendSocialNotification(context, RESET_FAILED);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            DevicePolicyManager deviceManger = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            final ComponentName compName = new ComponentName(context, DeviceAdminEventReceiver.class);
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("allowReset", false) && deviceManger != null && deviceManger.isAdminActive(compName)) {
                try {
                    sendAppNotification(context, RESET_COMMAND, sender);
                    deviceManger.wipeData(0);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    sendAppNotification(context, RESET_FAILED, sender);
                }
            } else {
                sendAppNotification(context, RESET_FAILED, sender);
            }
        }

        @Override
        public int getConfirmation() {
            return R.string.reset_confirmation;
        }

        @Override
        public boolean canResend() { return true; }
    }
}
