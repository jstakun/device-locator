package net.gmsworld.devicelocator.utilities;

import android.annotation.SuppressLint;
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
import net.gmsworld.devicelocator.services.HiddenCaptureImageService;
import net.gmsworld.devicelocator.services.RouteTrackingService;
import net.gmsworld.devicelocator.services.ScreenStatusService;
import net.gmsworld.devicelocator.services.SmsSenderService;

import org.acra.ACRA;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
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
    public final static String RING_COMMAND = "ringdl"; //ro play ringtone
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
    public final static String LOCK_SCREEN_FAILED = "lockfail";
    public final static String RESET_FAILED = "resetfail";
    public final static String RINGER_MODE_FAILED = "ringerfail";
    public final static String STOPPED_TRACKER = "stopped";
    protected final static String INVALID_PIN = "invalidPin";
    protected final static String INVALID_COMMAND = "invalidCommand";
    public final static String ALARM_COMMAND = "alarm";

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
                        final String smsMessage = StringUtils.trim(sms.getMessageBody());
                        if (StringUtils.isNotEmpty(smsMessage)) {
                            //Log.d(TAG, "Checking sms message " + smsMessage);
                            final PreferencesUtils prefs = new PreferencesUtils(context);
                            final String pin = prefs.getEncryptedString(PinActivity.DEVICE_PIN);
                            final boolean isPinRequired = prefs.getBoolean("settings_sms_without_pin", true);
                            final boolean hasSocialNotifiers = StringUtils.isNotEmpty(prefs.getString(MainActivity.NOTIFICATION_SOCIAL)) || StringUtils.isNotEmpty(prefs.getString(MainActivity.NOTIFICATION_EMAIL));
                            for (AbstractCommand c : getCommands()) {
                                if (c.findSmsCommand(context, smsMessage, sms.getOriginatingAddress(), pin, isPinRequired, hasSocialNotifiers)) {
                                    Log.d(TAG, "Found matching command " + c.getSmsCommand() + " in sms message " + smsMessage);
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

    public static String findCommandInMessage(Context context, String message, String replyTo, Location location, Bundle extras, String pinRead) {
        if (StringUtils.containsIgnoreCase(message, "admindlt")) {
            //adm command
            Log.d(TAG, "Looking for adm command...");
            findAdmCommandInMessage(context, message, replyTo, location, extras, pinRead);
        } else {
            final PreferencesUtils prefs = new PreferencesUtils(context);
            final String pin = prefs.getEncryptedString(PinActivity.DEVICE_PIN);
            final boolean isPinRequired = prefs.getBoolean("settings_sms_without_pin", true);
            final boolean hasSocialNotifiers = StringUtils.isNotEmpty(prefs.getString(MainActivity.NOTIFICATION_SOCIAL)) || StringUtils.isNotEmpty(prefs.getString(MainActivity.NOTIFICATION_EMAIL));
            int foundCommand;
            for (AbstractCommand c : getCommands()) {
                foundCommand = c.findAppCommand(context, StringUtils.trim(message), replyTo, location, extras, pin, isPinRequired);
                if (foundCommand == 1) {
                    Log.d(TAG, "Found matching cloud command");
                    return c.getSmsCommand();
                } else if (foundCommand == -1) {
                    //invalid pin
                    return null;
                } else {
                    foundCommand = c.findSocialCommand(context, StringUtils.trim(message), pin, replyTo, extras, isPinRequired, hasSocialNotifiers);
                    if (foundCommand == 1) {
                        Log.d(TAG, "Found matching social command");
                        return c.getSmsCommand();
                    } else if (foundCommand == -1) {
                        //invalid pin
                        return null;
                    }
                }
            }
            //invalid command
            final String commandName = message.split("dl")[0];
            Log.d(TAG, "Invalid command " + commandName + " found in message!");
            if (StringUtils.isNotEmpty(replyTo)) {
                final String msg = "Invalid command " + commandName + " sent to device " + Messenger.getDeviceId(context, true);
                Messenger.sendCloudMessage(context, null, replyTo, msg, commandName, 1, 2000, new HashMap<String, String>());
            }
            AbstractCommand.sendSocialNotification(context, INVALID_COMMAND, replyTo, commandName);
        }
        return null;
    }

    private static void findAdmCommandInMessage(final Context context, final String message, final String sender, final Location location, final Bundle extras, final String otp) {
        final String deviceId = Messenger.getDeviceId(context, false);
        final String content = "key=" + deviceId + "&value=" + otp;
        Network.post(context, context.getString(R.string.otpUrl), content, null, null, new Network.OnGetFinishListener() {
            @Override
            public void onGetFinish(String results, int responseCode, String url) {
                if (responseCode == 200) {
                    Log.d(TAG, "Otp has been verified successfully!");
                    for (AbstractCommand c : getCommands()) {
                        if (c.findAdmCommand(context, StringUtils.trim(message), sender, extras, otp) == 1) {
                            Log.d(TAG, "Found matching adm command");
                            break;
                        }
                    }
                }
            }
        });
    }


    private static List<AbstractCommand> getCommands() {
        if (commands == null) {
            Log.d(TAG, "Initializing commands...");
            commands = new ArrayList<>();
            try {
                Class<?>[] commandClasses = Command.class.getDeclaredClasses();
                for (Class<?> command : commandClasses) {
                    //filter out abstract classes
                    if (!Modifier.isAbstract(command.getModifiers())) {
                        Constructor<?> constructor = command.getDeclaredConstructors()[0];
                        constructor.setAccessible(true);
                        AbstractCommand c = (AbstractCommand) constructor.newInstance();
                        Log.d(TAG, "Initialized command " + c.getClass().getName());
                        commands.add(c);
                    }
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
            return (commandTokens == null || commandTokens.length == 1 || StringUtils.equalsAnyIgnoreCase(commandTokens[commandTokens.length - 1], "s", "silent", "screen") || StringUtils.isNumeric(commandTokens[commandTokens.length - 1]));
        }

        @Override
        public String getOppositeCommand() {
            return STOP_COMMAND;
        }

        private void startTracker(final Context context) {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            final int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);

            RouteTrackingService.Mode mode = RouteTrackingService.Mode.Normal;
            if (commandTokens.length > 1 && (commandTokens[commandTokens.length - 1].equalsIgnoreCase("silent") || commandTokens[commandTokens.length - 1].equalsIgnoreCase("s"))) {
                mode = RouteTrackingService.Mode.Silent;
            } else if (commandTokens.length > 1 && commandTokens[commandTokens.length - 1].equalsIgnoreCase("screen")) {
                mode = RouteTrackingService.Mode.Screen;
            }

            //TODO testing
            if (mode == RouteTrackingService.Mode.Screen) {
                ScreenStatusService.initService(context);
            } else if (Permissions.haveLocationPermission(context)) {
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, null, true, mode);
                settings.edit().putBoolean("motionDetectorRunning", true).apply();
            } else {
                Log.e(TAG, "Unable to start route tracking service due to lack of Location permission");
            }
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            startTracker(context);
            sendSmsNotification(context, sender, START_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            startTracker(context);
            sendSocialNotification(context, START_COMMAND, sender, null);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            startTracker(context);
            sendAppNotification(context, START_COMMAND, sender, extras.getString("language"));
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            startTracker(context);
            sendAdmNotification(context, START_COMMAND, sender, null);
        }
    }

    private static final class ResumeRouteTrackerServiceStartCommand extends AbstractCommand {

        public ResumeRouteTrackerServiceStartCommand() {
            super(RESUME_COMMAND, "rs", Finder.EQUALS);
        }

        private void resumeTracker(Context context) {
            PreferencesUtils settings = new PreferencesUtils(context);
            final int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);

            if (Permissions.haveLocationPermission(context)) {
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, null, false, RouteTrackingService.Mode.Normal);
                settings.setBoolean("motionDetectorRunning", true);
            } else {
                Log.e(TAG, "Unable to start route tracking service due to lack of Location permission");
            }
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            resumeTracker(context);
            Bundle extras = new Bundle();
            extras.putString("phoneNumber", sender);
            SmsSenderService.initService(context, true, true, true, null, RESUME_COMMAND, null, null, extras);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            resumeTracker(context);
            sendSocialNotification(context, RESUME_COMMAND, sender, null);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            resumeTracker(context);
            sendAppNotification(context, RESUME_COMMAND, sender, extras.getString("language"));
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            resumeTracker(context);
            sendAdmNotification(context, RESUME_COMMAND, sender, null);
        }
    }

    private static final class StopRouteTrackerServiceStartCommand extends AbstractCommand {

        public StopRouteTrackerServiceStartCommand() {
            super(STOP_COMMAND, "sp", Finder.STARTS);
        }

        public String getLabel() {
            return StringUtils.capitalize(getSmsCommand().substring(0, getSmsCommand().length() - 2));
        }

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
            return (commandTokens == null || commandTokens.length == 1 || StringUtils.equalsAnyIgnoreCase(commandTokens[commandTokens.length - 1], "s", "share", "screen") || StringUtils.isNumeric(commandTokens[commandTokens.length - 1]));
        }

        private void stopTracker(Context context) {
            //TODO testing
            if (commandTokens.length > 1 && commandTokens[commandTokens.length - 1].equalsIgnoreCase("screen")) {
                ScreenStatusService.stopService(context);
            } else if (GmsSmartLocationManager.getInstance().isEnabled()) {
                if (commandTokens.length > 1 && (commandTokens[commandTokens.length - 1].equalsIgnoreCase("share") || commandTokens[commandTokens.length - 1].equalsIgnoreCase("s"))) {
                    final String title = RouteTrackingServiceUtils.getRouteId(context);
                    RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false, true, title, null);
                } else {
                    RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false, false, null, null);
                }
            }
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            PreferencesUtils settings = new PreferencesUtils(context);
            stopTracker(context);
            if (settings.getBoolean("motionDetectorRunning", false)) {
                settings.setBoolean("motionDetectorRunning", false);
                sendSmsNotification(context, sender, STOP_COMMAND);
            } else {
                sendSmsNotification(context, sender, STOPPED_TRACKER);
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            PreferencesUtils settings = new PreferencesUtils(context);
            stopTracker(context);
            if (settings.getBoolean("motionDetectorRunning", false)) {
                settings.setBoolean("motionDetectorRunning", false);
                sendSocialNotification(context, STOP_COMMAND, sender, null);
            } else {
                sendSocialNotification(context, STOPPED_TRACKER, sender, null);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            PreferencesUtils settings = new PreferencesUtils(context);
            stopTracker(context);
            if (settings.getBoolean("motionDetectorRunning", false)) {
                settings.setBoolean("motionDetectorRunning", false);
                sendAppNotification(context, STOP_COMMAND, sender, extras.getString("language"));
            } else {
                sendAppNotification(context, STOPPED_TRACKER, sender, extras.getString("language"));
            }
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            PreferencesUtils settings = new PreferencesUtils(context);
            stopTracker(context);
            if (settings.getBoolean("motionDetectorRunning", false)) {
                settings.setBoolean("motionDetectorRunning", false);
                sendAdmNotification(context, STOP_COMMAND, sender, null);
            } else {
                sendAdmNotification(context, STOPPED_TRACKER, sender, null);
            }
        }
    }

    private static final class ShareLocationCommand extends AbstractCommand {

        public ShareLocationCommand() {
            super(SHARE_COMMAND, "l", Finder.STARTS);
        }

        @Override
        public String getDefaultArgs() {
            return "now";
        }

        @Override
        public boolean validateTokens() {
            int interval = -1;
            if (commandTokens != null && commandTokens.length > 1) {
                final String intervalStr = commandTokens[commandTokens.length - 1];
                if (StringUtils.endsWithIgnoreCase(intervalStr, "now")) {
                    interval = 0;
                } else {
                    try {
                        interval = Integer.parseInt(intervalStr);
                    } catch (Exception e) {
                        Log.e(TAG, "Wrong interval: " + intervalStr);
                        interval = 0;
                    }
                }
            } else {
                interval = 0;
            }
            return interval >= 0 && interval <= 24;
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
                if (commandTokens != null && commandTokens.length > 1) {
                    String intervalStr = commandTokens[commandTokens.length - 1];
                    if (StringUtils.equalsIgnoreCase(intervalStr, "now")) {
                        sendSmsNotification(context, sender, null); //don't set SHARE_COMMAND here!
                    } else if (StringUtils.isNumeric(intervalStr)) {
                        int interval = 0;
                        try {
                            interval = Integer.parseInt(intervalStr);
                        } catch (Exception e) {
                            Log.e(TAG, "Wrong interval: " + intervalStr);
                        }
                        if (interval == 0) {
                            LocationAlarmUtils.cancel(context);
                        } else {
                            if (interval <= 24 && interval > 0) {
                                PreferencesUtils settings = new PreferencesUtils(context);
                                settings.setInt(LocationAlarmUtils.ALARM_INTERVAL, interval);
                                settings.setBoolean(LocationAlarmUtils.ALARM_SETTINGS, true);
                                LocationAlarmUtils.initWhenDown(context, true);
                                sendSmsNotification(context, sender, ALARM_COMMAND);
                            } else {
                                sendSmsNotification(context, sender, null); //don't set SHARE_COMMAND here!
                            }
                        }
                    }
                } else {
                    sendSmsNotification(context, sender, null); //don't set SHARE_COMMAND here!
                }
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            if (!Permissions.haveLocationPermission(context)) {
                Log.e(TAG, "Missing Location permission");
                sendSocialNotification(context, SHARE_COMMAND, sender, null);
            } else {
                if (commandTokens != null && commandTokens.length > 1) {
                    String intervalStr = commandTokens[commandTokens.length - 1];
                    if (StringUtils.equalsIgnoreCase(intervalStr, "now")) {
                        sendSocialNotification(context, null, sender, null); //don't set SHARE_COMMAND here!
                    } else {
                        int interval = 0;
                        try {
                            interval = Integer.parseInt(intervalStr);
                        } catch (Exception e) {
                            Log.e(TAG, "Wrong interval: " + intervalStr);
                        }
                        if (interval == 0) {
                            LocationAlarmUtils.cancel(context);
                        } else if (interval <= 24 && interval > 0) {
                            PreferencesUtils settings = new PreferencesUtils(context);
                            settings.setInt(LocationAlarmUtils.ALARM_INTERVAL, interval);
                            settings.setBoolean(LocationAlarmUtils.ALARM_SETTINGS, true);
                            LocationAlarmUtils.initWhenDown(context, true);
                            sendSocialNotification(context, ALARM_COMMAND, sender, null);
                        } else {
                            sendSocialNotification(context, null, sender, null); //don't set SHARE_COMMAND here!
                        }
                    }
                } else {
                    sendSocialNotification(context, null, sender, null); //don't set SHARE_COMMAND here!
                }
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            if (!Permissions.haveLocationPermission(context)) {
                Log.e(TAG, "Missing Location permission");
                sendAppNotification(context, SHARE_COMMAND, sender, extras.getString("language"));
            } else {
                if (commandTokens != null && commandTokens.length > 1) {
                    String intervalStr = commandTokens[commandTokens.length - 1];
                    if (StringUtils.equalsIgnoreCase(intervalStr, "now")) {
                        sendAppNotification(context, null, sender, extras.getString("language")); //don't set SHARE_COMMAND here!
                    } else {
                        int interval = 0;
                        try {
                            interval = Integer.parseInt(intervalStr);
                        } catch (Exception e) {
                            Log.e(TAG, "Wrong interval: " + intervalStr);
                        }
                        if (interval == 0) {
                            LocationAlarmUtils.cancel(context);
                        } else if (interval <= 24 && interval > 0) {
                            PreferencesUtils settings = new PreferencesUtils(context);
                            settings.setInt(LocationAlarmUtils.ALARM_INTERVAL, interval);
                            settings.setBoolean(LocationAlarmUtils.ALARM_SETTINGS, true);
                            LocationAlarmUtils.initWhenDown(context, true);
                            sendAppNotification(context, ALARM_COMMAND, sender, extras.getString("language"));
                        } else {
                            sendAppNotification(context, null, sender, extras.getString("language")); //don't set SHARE_COMMAND here!
                        }
                    }
                } else {
                    sendAppNotification(context, null, sender, extras.getString("language")); //don't set SHARE_COMMAND here!
                }
            }
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            if (!Permissions.haveLocationPermission(context)) {
                Log.e(TAG, "Missing Location permission");
                sendAdmNotification(context, SHARE_COMMAND, sender, null);
            } else {
                if (commandTokens != null && commandTokens.length > 1) {
                    String intervalStr = commandTokens[commandTokens.length - 1];
                    if (StringUtils.equalsIgnoreCase(intervalStr, "now")) {
                        sendAdmNotification(context, null, sender, null); //don't set SHARE_COMMAND here!
                    } else {
                        int interval = 0;
                        try {
                            interval = Integer.parseInt(intervalStr);
                        } catch (Exception e) {
                            Log.e(TAG, "Wrong interval: " + intervalStr);
                        }
                        if (interval == 0) {
                            LocationAlarmUtils.cancel(context);
                        } else if (interval <= 24 && interval > 0) {
                            PreferencesUtils settings = new PreferencesUtils(context);
                            settings.setInt(LocationAlarmUtils.ALARM_INTERVAL, interval);
                            settings.setBoolean(LocationAlarmUtils.ALARM_SETTINGS, true);
                            LocationAlarmUtils.initWhenDown(context, true);
                            sendAdmNotification(context, ALARM_COMMAND, sender, null);
                        } else {
                            sendAdmNotification(context, null, sender, null); //don't set SHARE_COMMAND here!
                        }
                    }

                } else {
                    sendAdmNotification(context, null, sender, null); //don't set SHARE_COMMAND here!
                }
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
            Bundle extras = new Bundle();
            extras.putString("phoneNumber", sender);
            RouteTrackingServiceUtils.shareRoute(context, extras);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            RouteTrackingServiceUtils.shareRoute(context, null);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            extras.putString("app", sender);
            RouteTrackingServiceUtils.shareRoute(context, extras);
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            Bundle extras = new Bundle();
            extras.putString("telegramId", context.getString(R.string.app_telegram));
            extras.putString("email", context.getString(R.string.app_email));
            RouteTrackingServiceUtils.shareRoute(context, extras);
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
            if (setRingerMode(context, AudioManager.RINGER_MODE_SILENT)) {
                sendSmsNotification(context, sender, MUTE_COMMAND);
            } else {
                sendSmsNotification(context, sender, RINGER_MODE_FAILED);
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            if (setRingerMode(context, AudioManager.RINGER_MODE_SILENT)) {
                sendSocialNotification(context, MUTE_COMMAND, sender, null);
            } else {
                sendSocialNotification(context, RINGER_MODE_FAILED, sender, null);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            if (setRingerMode(context, AudioManager.RINGER_MODE_SILENT)) {
                sendAppNotification(context, MUTE_COMMAND, sender, extras.getString("language"));
            } else {
                sendAppNotification(context, RINGER_MODE_FAILED, sender, extras.getString("language"));
            }
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            if (setRingerMode(context, AudioManager.RINGER_MODE_SILENT)) {
                sendAdmNotification(context, MUTE_COMMAND, sender, null);
            } else {
                sendAdmNotification(context, RINGER_MODE_FAILED, sender, null);
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
            if (setRingerMode(context, AudioManager.RINGER_MODE_NORMAL)) {
                sendSmsNotification(context, sender, UNMUTE_COMMAND);
            } else {
                sendSmsNotification(context, sender, RINGER_MODE_FAILED);
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            if (setRingerMode(context, AudioManager.RINGER_MODE_NORMAL)) {
                sendSocialNotification(context, UNMUTE_COMMAND, sender, null);
            } else {
                sendSocialNotification(context, RINGER_MODE_FAILED, sender, null);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            if (setRingerMode(context, AudioManager.RINGER_MODE_NORMAL)) {
                sendAppNotification(context, UNMUTE_COMMAND, sender, extras.getString("language"));
            } else {
                sendAppNotification(context, RINGER_MODE_FAILED, sender, extras.getString("language"));
            }
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            if (setRingerMode(context, AudioManager.RINGER_MODE_NORMAL)) {
                sendAdmNotification(context, UNMUTE_COMMAND, sender, null);
            } else {
                sendAdmNotification(context, RINGER_MODE_FAILED, sender, null);
            }
        }
    }

    private static final class StartPhoneCallCommand extends AbstractCommand {

        public StartPhoneCallCommand() {
            super(CALL_COMMAND, "c", Finder.STARTS);
        }

        @Override
        public boolean validateTokens() {
            //return (commandTokens == null || commandTokens.length == 1 || Patterns.PHONE.matcher(commandTokens[commandTokens.length - 1]).matches() || StringUtils.isNumeric(commandTokens[commandTokens.length - 1]));
            return (commandTokens != null && commandTokens.length >= 1 && Patterns.PHONE.matcher(commandTokens[commandTokens.length - 1]).matches());
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            if (!initPhoneCall(sender, context)) {
                sendSmsNotification(context, sender, CALL_COMMAND);
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            final String phoneNumber = PreferenceManager.getDefaultSharedPreferences(context).getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
            if (StringUtils.isEmpty(phoneNumber) || !initPhoneCall(phoneNumber, context)) {
                Log.e(TAG, "Failed to init phone call to: " + phoneNumber);
                sendSocialNotification(context, CALL_COMMAND, sender, null);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            final String phoneNumber = extras.getString("args");
            if (StringUtils.isNotEmpty(phoneNumber) && Patterns.PHONE.matcher(phoneNumber).matches() && SmsReceiver.contactExists(context, phoneNumber)) {
                if (!initPhoneCall(phoneNumber, context)) {
                    sendAppNotification(context, CALL_COMMAND, sender, extras.getString("language"));
                }
            } else {
                sendAppNotification(context, CALL_COMMAND, sender, extras.getString("language"));
            }
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            final String phoneNumber = PreferenceManager.getDefaultSharedPreferences(context).getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
            if (StringUtils.isNotEmpty(phoneNumber) && !initPhoneCall(phoneNumber, context)) {
                sendAdmNotification(context, CALL_COMMAND, sender, null);
            }
        }

        @SuppressLint("MissingPermission")
        private boolean initPhoneCall(String sender, Context context) {
            if (AppUtils.getInstance().hasTelephonyFeature(context) && Permissions.haveCallPhonePermission(context)) {
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
                Log.e(TAG, "Unable to initiate phone call due to missing permission or lack of telephony service!");
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
                    radius = Integer.parseInt(commandTokens[commandTokens.length - 1]);
                } catch (Exception e) {
                    Log.e(TAG, "Wrong radius: " + commandTokens[commandTokens.length - 1]);
                }
            }
            return radius >= MainActivity.MIN_RADIUS && radius <= MainActivity.MAX_RADIUS;
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            int radius = Integer.parseInt(commandTokens[commandTokens.length - 1]);
            if (radius < MainActivity.MIN_RADIUS) {
                radius = MainActivity.MIN_RADIUS;
            } else if (radius > MainActivity.MAX_RADIUS) {
                radius = MainActivity.MAX_RADIUS;
            }
            PreferencesUtils settings = new PreferencesUtils(context);
            settings.setInt("radius", radius);
            RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, null);
            sendSmsNotification(context, sender, RADIUS_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            int radius = Integer.parseInt(commandTokens[commandTokens.length - 1]);
            if (radius < MainActivity.MIN_RADIUS) {
                radius = MainActivity.MIN_RADIUS;
            } else if (radius > MainActivity.MAX_RADIUS) {
                radius = MainActivity.MAX_RADIUS;
            }
            PreferencesUtils settings = new PreferencesUtils(context);
            settings.setInt("radius", radius);
            RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, null);
            sendSocialNotification(context, RADIUS_COMMAND, sender, null);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            int radius = Integer.parseInt(commandTokens[commandTokens.length - 1]);
            if (radius < MainActivity.MIN_RADIUS) {
                radius = MainActivity.MIN_RADIUS;
            } else if (radius > MainActivity.MAX_RADIUS) {
                radius = MainActivity.MAX_RADIUS;
            }
            PreferencesUtils settings = new PreferencesUtils(context);
            settings.setInt("radius", radius);
            RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, sender);
            sendAppNotification(context, RADIUS_COMMAND, sender, extras.getString("language"));
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            int radius = Integer.parseInt(commandTokens[commandTokens.length - 1]);
            if (radius < MainActivity.MIN_RADIUS) {
                radius = MainActivity.MIN_RADIUS;
            } else if (radius > MainActivity.MAX_RADIUS) {
                radius = MainActivity.MAX_RADIUS;
            }
            PreferencesUtils settings = new PreferencesUtils(context);
            settings.setInt("radius", radius);
            RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, null);
            sendSocialNotification(context, RADIUS_COMMAND, sender, null);
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
            sendSocialNotification(context, AUDIO_COMMAND, sender, null);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useAudio", true).apply();
            sendAppNotification(context, AUDIO_COMMAND, sender, extras.getString("language"));
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useAudio", true).apply();
            sendAdmNotification(context, AUDIO_COMMAND, sender, null);
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
            sendSocialNotification(context, AUDIO_OFF_COMMAND, sender, null);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useAudio", false).apply();
            sendAppNotification(context, AUDIO_OFF_COMMAND, sender, extras.getString("language"));
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useAudio", false).apply();
            sendAdmNotification(context, AUDIO_OFF_COMMAND, sender, null);
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
            sendSocialNotification(context, GPS_HIGH_COMMAND, sender, null);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(RouteTrackingService.GPS_ACCURACY, 1).apply();
            sendAppNotification(context, GPS_HIGH_COMMAND, sender, extras.getString("language"));
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(RouteTrackingService.GPS_ACCURACY, 1).apply();
            RouteTrackingServiceUtils.setGpsAccuracy(context, RouteTrackingService.COMMAND_GPS_HIGH);
            sendAdmNotification(context, GPS_HIGH_COMMAND, sender, null);
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
            sendSocialNotification(context, GPS_BALANCED_COMMAND, sender, null);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(RouteTrackingService.GPS_ACCURACY, 0).apply();
            sendAppNotification(context, GPS_BALANCED_COMMAND, sender, extras.getString("language"));
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(RouteTrackingService.GPS_ACCURACY, 0).apply();
            sendAdmNotification(context, GPS_BALANCED_COMMAND, sender, null);
        }
    }

    private static final class TakePhotoCommand extends AbstractCommand {

        public TakePhotoCommand() {
            super(TAKE_PHOTO_COMMAND, "p", Finder.EQUALS);
        }

        @Override
        public boolean canResend() {
            return true;
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(HiddenCaptureImageService.STATUS, false) && HiddenCaptureImageService.isNotBusy()) {
                Intent cameraIntent = new Intent(context, HiddenCaptureImageService.class);
                cameraIntent.putExtra("sender", sender);
                context.startService(cameraIntent);
            }
            sendSmsNotification(context, sender, TAKE_PHOTO_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(HiddenCaptureImageService.STATUS, false) && HiddenCaptureImageService.isNotBusy()) {
                Intent cameraIntent = new Intent(context, HiddenCaptureImageService.class);
                context.startService(cameraIntent);
            }
            sendSocialNotification(context, TAKE_PHOTO_COMMAND, sender, null);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(HiddenCaptureImageService.STATUS, false) && HiddenCaptureImageService.isNotBusy()) {
                Intent cameraIntent = new Intent(context, HiddenCaptureImageService.class);
                cameraIntent.putExtra("app", sender);
                context.startService(cameraIntent);
            }
            sendAppNotification(context, TAKE_PHOTO_COMMAND, sender, extras.getString("language"));
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(HiddenCaptureImageService.STATUS, false) && HiddenCaptureImageService.isNotBusy()) {
                Intent cameraIntent = new Intent(context, HiddenCaptureImageService.class);
                context.startService(cameraIntent);
            }
            sendAdmNotification(context, TAKE_PHOTO_COMMAND, sender, null);
        }
    }

    private static final class NotifySettingsCommand extends AbstractCommand {

        public NotifySettingsCommand() {
            super(NOTIFY_COMMAND, "n", Finder.STARTS);
        }

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
                    } //else {
                        //skip token
                    //}
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
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = settings.edit();
                if (email != null) {
                    editor.putString(MainActivity.NOTIFICATION_EMAIL, email);
                    Messenger.sendEmailRegistrationRequest(context, email, true, 1);
                }
                if (phoneNumber != null) {
                    editor.putString(MainActivity.NOTIFICATION_PHONE_NUMBER, phoneNumber);
                }
                if (telegramId != null) {
                    editor.putString(MainActivity.NOTIFICATION_SOCIAL, telegramId);
                    Messenger.sendTelegramRegistrationRequest(context, telegramId, 1);
                }
                editor.apply();

                final int radius = settings.getInt("radius", 100);

                RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, null);

                sendSmsNotification(context, sender, NOTIFY_COMMAND);
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            String email = null;
            String phoneNumber = null;
            String telegramId = null;

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
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = settings.edit();
                if (email != null) {
                    editor.putString(MainActivity.NOTIFICATION_EMAIL, email);
                    Messenger.sendEmailRegistrationRequest(context, email, true, 1);
                }
                if (phoneNumber != null) {
                    editor.putString(MainActivity.NOTIFICATION_PHONE_NUMBER, phoneNumber);
                }
                if (telegramId != null) {
                    editor.putString(MainActivity.NOTIFICATION_SOCIAL, telegramId);
                    Messenger.sendTelegramRegistrationRequest(context, telegramId, 1);
                }
                editor.apply();

                final int radius = settings.getInt("radius", 100);

                RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, null);

                sendSocialNotification(context, NOTIFY_COMMAND, sender, null);
            }
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            String email = null;
            String phoneNumber = null;
            String telegramId = null;

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
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = settings.edit();
                if (email != null) {
                    editor.putString(MainActivity.NOTIFICATION_EMAIL, email);
                    Messenger.sendEmailRegistrationRequest(context, email, true, 1);
                }
                if (phoneNumber != null) {
                    editor.putString(MainActivity.NOTIFICATION_PHONE_NUMBER, phoneNumber);
                }
                if (telegramId != null) {
                    editor.putString(MainActivity.NOTIFICATION_SOCIAL, telegramId);
                    Messenger.sendTelegramRegistrationRequest(context, telegramId, 1);
                }
                editor.apply();

                final int radius = settings.getInt("radius", 100);

                RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, null);

                sendAdmNotification(context, NOTIFY_COMMAND, sender, null);
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
                    Messenger.sendEmailRegistrationRequest(context, email, true, 1);
                }
                if (phoneNumber != null) {
                    editor.putString(MainActivity.NOTIFICATION_PHONE_NUMBER, phoneNumber);
                }
                if (telegramId != null) {
                    editor.putString(MainActivity.NOTIFICATION_SOCIAL, telegramId);
                    Messenger.sendTelegramRegistrationRequest(context, telegramId, 1);
                }
                editor.apply();

                final int radius = settings.getInt("radius", 100);

                RouteTrackingServiceUtils.resetRouteTrackingService(context, null, false, radius, sender);

                sendAppNotification(context, NOTIFY_COMMAND, sender, extras.getString("language"));
            }
        }
    }

    private static final class PingCommand extends AbstractCommand {

        public PingCommand() {
            super(PING_COMMAND, "pg", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            sendSmsNotification(context, sender, PING_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            sendSocialNotification(context, PING_COMMAND, sender, null);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            sendAppNotification(context, PING_COMMAND, sender, extras.getString("language"));
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            sendAdmNotification(context, PING_COMMAND, sender, null);
        }
    }

    private static final class HelloCommand extends AbstractCommand {

        public HelloCommand() {
            super(HELLO_COMMAND, "hl", Finder.EQUALS);
        }

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
            sendSocialNotification(context, HELLO_COMMAND, sender, null);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            sendAppNotification(context, HELLO_COMMAND, sender, extras.getString("language"));
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            sendAdmNotification(context, HELLO_COMMAND, sender, null);
        }
    }

    private static final class AboutCommand extends AbstractCommand {

        public AboutCommand() {
            super(ABOUT_COMMAND, "ab", Finder.STARTS);
        }

        @Override
        public boolean validateTokens() {
            return (commandTokens == null || commandTokens.length == 1 || StringUtils.equalsAnyIgnoreCase(commandTokens[commandTokens.length - 1], "l", "log"));
        }

        private void sendLog() {
            if (commandTokens.length >= 1 && (commandTokens[commandTokens.length - 1].equalsIgnoreCase("log") || commandTokens[commandTokens.length - 1].equalsIgnoreCase("l"))) {
                ACRA.getErrorReporter().handleSilentException(null);
            }
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            sendLog();
            sendSmsNotification(context, sender, ABOUT_COMMAND);
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            sendLog();
            sendSocialNotification(context, ABOUT_COMMAND, sender, null);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            sendLog();
            sendAppNotification(context, ABOUT_COMMAND, sender, extras.getString("language"));
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            sendLog();
            sendAdmNotification(context, ABOUT_COMMAND, sender, null);
        }
    }

    private static final class LockScreenCommand extends AbstractCommand {

        public LockScreenCommand() {
            super(LOCK_SCREEN_COMMAND, "ls", Finder.EQUALS);
        }

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
                sendSocialNotification(context, LOCK_SCREEN_COMMAND, sender, null);
            } else {
                sendSocialNotification(context, LOCK_SCREEN_FAILED, sender, null);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            if (lockScreenNow(context)) {
                sendAppNotification(context, LOCK_SCREEN_COMMAND, sender, extras.getString("language"));
            } else {
                sendAppNotification(context, LOCK_SCREEN_FAILED, sender, extras.getString("language"));
            }
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            if (lockScreenNow(context)) {
                sendAdmNotification(context, LOCK_SCREEN_COMMAND, sender, null);
            } else {
                sendAdmNotification(context, LOCK_SCREEN_FAILED, sender, null);
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

    private static abstract class AbstractRingCommand extends AbstractCommand {

        private static Ringtone ringtone = null;

        AbstractRingCommand(String smsCommand, String smsShortCommand, Finder finder) {
            super(smsCommand, smsShortCommand, finder);
        }

        protected static boolean playBeep(Context context) {
            try {
                if (ringtone == null) {
                    final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    final Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                    ringtone = RingtoneManager.getRingtone(context, ringtoneUri);
                    if (!ringtone.isPlaying()) {
                        audioMode.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        audioMode.setStreamVolume(AudioManager.STREAM_RING, audioMode.getStreamMaxVolume(AudioManager.STREAM_RING), AudioManager.FLAG_SHOW_UI);
                        ringtone.play();
                        Log.d(TAG, "Ringtone " + ringtoneUri.toString() + " should be playing now");
                    }
                    final Intent ringIntent = new Intent(context, RingingActivity.class);
                    ringIntent.setAction(RING_COMMAND);
                    ringIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(ringIntent);
                }
                return true;
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                return false;
            }
        }

        protected static boolean stopBeep(Context context) {
            try {
                if (ringtone != null) {
                    if (ringtone.isPlaying()) {
                        ringtone.stop();
                        ringtone = null;
                        Log.d(TAG, "Ringtone should stop playing now");
                    }
                    final Intent ringIntent = new Intent(context, RingingActivity.class);
                    ringIntent.setAction(RING_OFF_COMMAND);
                    ringIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(ringIntent);
                }
                return true;
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                return false;
            }
        }
    }

    private static final class RingCommand extends AbstractRingCommand {

        public RingCommand() {
            super(RING_COMMAND, "rn", Finder.EQUALS);
        }

        @Override
        public String getOppositeCommand() {
            return RING_OFF_COMMAND;
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            if (playBeep(context)) {
                sendSmsNotification(context, sender, RING_COMMAND);
            }
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            if (playBeep(context)) {
                sendSocialNotification(context, RING_COMMAND, sender, null);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            if (playBeep(context)) {
                sendAppNotification(context, RING_COMMAND, sender, extras.getString("language"));
            }
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            if (playBeep(context)) {
                sendAdmNotification(context, RING_COMMAND, sender, null);
            }
        }
    }


    private static final class RingOffCommand extends AbstractRingCommand {

        public RingOffCommand() {
            super(RING_OFF_COMMAND, "ro", Finder.EQUALS);
        }

        @Override
        public String getOppositeCommand() {
            return RING_COMMAND;
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            if (stopBeep(context)) {
                sendSmsNotification(context, sender, RING_OFF_COMMAND);
            }
        }

        @Override
        public String getLabel() {
            return "Stop ringing";
        }

        @Override
        protected void onSocialCommandFound(String sender, Context context) {
            if (stopBeep(context)) {
                sendSocialNotification(context, RING_OFF_COMMAND, sender, null);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            if (stopBeep(context)) {
                sendAppNotification(context, RING_OFF_COMMAND, sender, extras.getString("language"));
            }
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            if (stopBeep(context)) {
                sendAdmNotification(context, RING_OFF_COMMAND, sender, null);
            }
        }
    }

    private static final class MessageCommand extends AbstractCommand {

        public MessageCommand() {
            super(MESSAGE_COMMAND, "ms", Finder.STARTS);
        }

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

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            //message is received only from the cloud
            Log.e(TAG, "This method shouldn't be called!");
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
                if (StringUtils.containsIgnoreCase(message, "perimeter")) { //check if sender device is in perimeter
                    String[] tokens = StringUtils.split(message, "\n");
                    if (tokens.length > 1 && StringUtils.isNumeric(tokens[tokens.length - 1])) {
                        int perimeter = Integer.valueOf(tokens[tokens.length - 1]);
                        Location lastLocation = SmartLocation.with(context).location(new LocationGooglePlayServicesWithFallbackProvider(context)).getLastLocation();
                        if (lastLocation != null && (System.currentTimeMillis() - lastLocation.getTime() < 10 * 60 * 1000)) { //10 min
                            int distance = (int) location.distanceTo(lastLocation);
                            if (distance <= perimeter) {
                                message = StringUtils.join(tokens, "\n", 0, tokens.length - 1);
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
                    radius = Integer.parseInt(commandTokens[commandTokens.length - 1]);
                } catch (Exception e) {
                    Log.e(TAG, "Wrong perimeter radius: " + commandTokens[commandTokens.length - 1]);
                }
            }
            return radius >= MainActivity.MIN_RADIUS && radius <= MainActivity.MAX_RADIUS;
        }

        @Override
        public String getDefaultArgs() {
            return "500";
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
            if (Permissions.haveLocationPermission(context)) {
                PreferencesUtils settings = new PreferencesUtils(context);
                final int radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, sender, true, RouteTrackingService.Mode.Perimeter);
                settings.setBoolean("motionDetectorRunning", true);
                int perimeter = Integer.parseInt(commandTokens[1]);
                if (radius < MainActivity.MIN_RADIUS) {
                    perimeter = MainActivity.MIN_RADIUS;
                } else if (radius > MainActivity.MAX_RADIUS) {
                    perimeter = MainActivity.MAX_RADIUS;
                }
                settings.setInt("perimeter", perimeter);
            } else {
                Log.e(TAG, "Unable to start perimeter service due to lack of Location permission");
            }
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            //message is received only from the cloud
            Log.e(TAG, "This method shouldn't be called!");
        }
    }

    private static final class ConfigCommand extends AbstractCommand {
        public ConfigCommand() {
            super(CONFIG_COMMAND, "cf", Finder.STARTS);
        }

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
            sendSocialNotification(context, CONFIG_COMMAND, sender, null);
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            applyConfigChange(context);
            sendAppNotification(context, CONFIG_COMMAND, sender, extras.getString("language"));
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            applyConfigChange(context);
            sendAdmNotification(context, CONFIG_COMMAND, sender, null);
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
                    SmsSenderService.initService(context, true, true, true, null, Command.HELLO_COMMAND, null, null, null);
                } else if (token.startsWith("dn:")) {
                    //Device name
                    String newDeviceName = token.substring(3);
                    if (!StringUtils.equals(settings.getString(MainActivity.DEVICE_NAME, ""), newDeviceName)) {
                        String normalizedDeviceName = StringUtils.trimToEmpty(newDeviceName).replace(' ', '-');
                        if (Messenger.sendRegistrationToServer(context, settings.getString(MainActivity.USER_LOGIN, ""), normalizedDeviceName, true)) {
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

        public ResetDeviceCommand() {
            super(RESET_COMMAND, "rt", Finder.EQUALS);
        }

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
                    sendSocialNotification(context, RESET_COMMAND, sender, null);
                    deviceManger.wipeData(0);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    sendSocialNotification(context, RESET_FAILED, sender, null);
                }
            } else {
                sendSocialNotification(context, RESET_FAILED, sender, null);
            }
        }

        @Override
        protected void onAppCommandFound(String sender, Context context, Location location, Bundle extras) {
            DevicePolicyManager deviceManger = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            final ComponentName compName = new ComponentName(context, DeviceAdminEventReceiver.class);
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("allowReset", false) && deviceManger != null && deviceManger.isAdminActive(compName)) {
                try {
                    sendAppNotification(context, RESET_COMMAND, sender, extras.getString("language"));
                    deviceManger.wipeData(0);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    sendAppNotification(context, RESET_FAILED, sender, extras.getString("language"));
                }
            } else {
                sendAppNotification(context, RESET_FAILED, sender, extras.getString("language"));
            }
        }

        @Override
        protected void onAdmCommandFound(String sender, Context context) {
            DevicePolicyManager deviceManger = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            final ComponentName compName = new ComponentName(context, DeviceAdminEventReceiver.class);
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("allowReset", false) && deviceManger != null && deviceManger.isAdminActive(compName)) {
                try {
                    sendAdmNotification(context, RESET_COMMAND, sender, null);
                    deviceManger.wipeData(0);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    sendAdmNotification(context, RESET_FAILED, sender, null);
                }
            } else {
                sendAdmNotification(context, RESET_FAILED, sender, null);
            }
        }

        @Override
        public int getConfirmation() {
            return R.string.reset_confirmation;
        }

        @Override
        public boolean canResend() {
            return true;
        }
    }
}
