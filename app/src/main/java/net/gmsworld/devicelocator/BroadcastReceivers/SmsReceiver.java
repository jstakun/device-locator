package net.gmsworld.devicelocator.BroadcastReceivers;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
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
            new TakePhotoCommand(), new NotifiySettingsCommand() };

    @Override
    public void onReceive(Context context, Intent intent) {

        for (int i = 0; i < commands.length; i++) {
            if (commands[i].findCommand(context, intent)) return;
        }
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

            sendSmsNotification(context, sender, START_COMMAND);
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

            sendSocialNotification(context, START_COMMAND);
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
            sendSocialNotification(context, RESUME_COMMAND);
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
            sendSmsNotification(context, sender, STOP_COMMAND);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            RouteTrackingServiceUtils.stopRouteTrackingService(context, null, false);
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("motionDetectorRunning", false).commit();
            sendSocialNotification(context, STOP_COMMAND);
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
            sendSmsNotification(context, sender, null);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            sendSocialNotification(context, null);
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
            sendSmsNotification(context, sender, MUTE_COMMAND);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioMode.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            sendSocialNotification(context, MUTE_COMMAND);
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
            sendSmsNotification(context, sender, UNMUTE_COMMAND);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            final AudioManager audioMode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioMode.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            sendSocialNotification(context, UNMUTE_COMMAND);
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
            sendSmsNotification(context, sender, RADIUS_COMMAND);
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
            sendSocialNotification(context, RADIUS_COMMAND);
        }
    }

    private static final class AudioCommand extends AbstractCommand {

        public AudioCommand() {
            super(AUDIO_COMMAND, "a", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useAudio", true).commit();
            sendSmsNotification(context, sender, AUDIO_COMMAND);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useAudio", true).commit();
            sendSocialNotification(context, AUDIO_COMMAND);
        }
    }

    private static final class NoAudioCommand extends AbstractCommand {

        public NoAudioCommand() {
            super(NOAUDIO_COMMAND, "na", Finder.EQUALS);
        }

        @Override
        protected void onSmsCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useAudio", false).commit();
            sendSmsNotification(context, sender, NOAUDIO_COMMAND);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useAudio", false).commit();
            sendSocialNotification(context, NOAUDIO_COMMAND);
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
            sendSmsNotification(context, sender, GPS_HIGH_COMMAND);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("gpsAccuracy", 1).commit();
            RouteTrackingServiceUtils.setGpsAccuracy(context, RouteTrackingService.COMMAND_GPS_HIGH);
            sendSocialNotification(context, GPS_HIGH_COMMAND);
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
            sendSmsNotification(context, sender, GPS_BALANCED_COMMAND);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("gpsAccuracy", 0).commit();
            sendSocialNotification(context, GPS_BALANCED_COMMAND);
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
            sendSmsNotification(context, sender, TAKE_PHOTO_COMMAND);
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("hiddenCamera", false)) {
                Intent cameraIntent = new Intent(context, HiddenCaptureImageService.class);
                context.startService(cameraIntent);
            }
            sendSocialNotification(context, TAKE_PHOTO_COMMAND);
        }
    }

    private static final class NotifiySettingsCommand extends AbstractCommand {
        public NotifiySettingsCommand() {super(NOTIFY_COMMAND, "n", Finder.STARTS);}


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
                newIntent.putExtra("phoneNumber", sender);
                newIntent.putExtra("notificationNumber", phoneNumber);
                newIntent.putExtra("email", email);
                newIntent.putExtra("telegramId", telegramId);
                newIntent.putExtra("command", NOTIFY_COMMAND);
                context.startService(newIntent);
            }
        }

        @Override
        protected void onSmsSocialCommandFound(String sender, Context context) {
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
                newIntent.putExtra("notificationNumber", phoneNumber);
                newIntent.putExtra("email", email);
                newIntent.putExtra("telegramId", telegramId);
                newIntent.putExtra("command", NOTIFY_COMMAND);
                context.startService(newIntent);
            }
        }

        @Override
        protected boolean validateTokens() {
            if (commandTokens.length > 1) {
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
                    }
                }
                return true;
            } else {
                return false;
            }
        }
    }
}