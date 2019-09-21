package net.gmsworld.devicelocator.utilities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.services.SmsSenderService;

import org.apache.commons.lang3.StringUtils;

import java.io.File;

import static net.gmsworld.devicelocator.utilities.Messenger.CID_SEPARATOR;

/**
 * Created by jstakun on 10/25/17.
 */

public abstract class AbstractCommand {

    private static final String TAG = "DeviceCommand";

    public static final String AUDIT_FILE = "audit_log.bin";

    protected enum Finder {STARTS, EQUALS}

    private final String smsCommand;

    private final String smsShortCommand;

    private final Finder finder;

    String[] commandTokens;

    AbstractCommand(String smsCommand, String smsShortCommand, Finder finder) {
        this.smsCommand = smsCommand;
        this.smsShortCommand = smsShortCommand;
        this.finder = finder;
    }

    protected abstract void onSmsCommandFound(String sender, Context context);

    protected abstract void onSocialCommandFound(String sender, Context context);

    protected abstract void onAppCommandFound(String sender, Context context, Location location, Bundle extras);

    protected abstract void onAdmCommandFound(String sender, Context context);

    public String getOppositeCommand() { return null; }

    public String getLabel() { return StringUtils.capitalize(getSmsCommand().substring(0, getSmsCommand().length()-2)); }

    public boolean validateTokens() {
        return false;
    }

    public String getDefaultArgs() { return ""; }

    public int getConfirmation() { return -1; }

    public boolean canResend() { return false; }

    public final boolean hasParameters() {
        return finder.equals(Finder.STARTS);
    }

    public final boolean hasOppositeCommand() { return getOppositeCommand() != null; }

    protected final String getSmsCommand() {
        return smsCommand;
    }

    public final void setCommandTokens(String[] commandTokens) {
        this.commandTokens = commandTokens;
    }


    protected final boolean findSmsCommand(Context context, final String smsMessage, final String sender, final String pin, final boolean isPinRequired, final boolean hasSocialNotifiers) {
        int status = 0;
        //sms with sms notification
        if (StringUtils.startsWithIgnoreCase(smsMessage, smsCommand)) {
            status = findCommandInMessage(context, smsCommand, smsMessage, pin, sender, isPinRequired);
            auditCommand(context, smsCommand, sender, smsMessage);
        }
        if (status == 0 && StringUtils.startsWithIgnoreCase(smsMessage, smsShortCommand)) {
            status = findCommandInMessage(context, smsShortCommand, smsMessage, pin, sender, isPinRequired);
            auditCommand(context, smsShortCommand, sender, smsMessage);
        }
        if (status == 1) {
            onSmsCommandFound(sender, context);
            return true;
        } else if ((StringUtils.startsWithIgnoreCase(smsMessage, smsCommand + "t") || StringUtils.startsWithIgnoreCase(smsMessage, smsShortCommand + "t")) && hasSocialNotifiers) {
            //sms with social notification
            status = findCommandInMessage(context, smsCommand + "t", smsMessage, pin, sender, isPinRequired);
            if (status == 0 && StringUtils.startsWithIgnoreCase(smsMessage, smsShortCommand)) {
                status = findCommandInMessage(context, smsShortCommand + "t", smsMessage, pin, sender, isPinRequired);
            }
            if (status == 1) {
                onSocialCommandFound(sender, context);
                return true;
            }
        }
        Log.d(TAG, "No valid command found in sms message " + smsMessage);
        return false;
    }

    protected final int findSocialCommand(Context context, String message, String pin, String sender, boolean isPinRequired, boolean hasSocialNotifiers) {
        int foundCommand = 0;
        if ((StringUtils.startsWithIgnoreCase(message, smsCommand + "t") || StringUtils.startsWithIgnoreCase(message, smsShortCommand + "t")) && hasSocialNotifiers) {
            String[] ts = StringUtils.split(sender, CID_SEPARATOR);
            String s = sender;
            if (ts.length > 0) {
                s = ts[0];
            }
            auditCommand(context, smsCommand + "t", s, message);
            foundCommand = findKeyword(context, smsCommand + "t", message, pin, sender, isPinRequired);
            if (foundCommand == 1) {
                onSocialCommandFound(null, context);
            } else if (foundCommand == 0) {
                foundCommand = findKeyword(context, smsShortCommand + "t", message, pin, sender, isPinRequired);
                if (foundCommand == 1) {
                    onSocialCommandFound(null, context);
                }
            }
        }
        return foundCommand;
    }

    protected final int findAppCommand(Context context, String message, String sender, Location location, Bundle extras, String pin, boolean isPinRequired) {
        int foundCommand = 0;
        if (StringUtils.startsWithIgnoreCase(message, smsCommand + "app") || StringUtils.startsWithIgnoreCase(message, smsShortCommand + "app")) {
            String[] ts = StringUtils.split(sender, CID_SEPARATOR);
            String s = sender;
            if (ts.length > 0) {
                s = ts[0];
            }
            auditCommand(context, smsCommand + "app", s, message);
            foundCommand = findKeyword(context, smsCommand + "app", message, pin, sender, isPinRequired);
            if (foundCommand == 1) {
                onAppCommandFound(sender, context, location, extras);
            } else if (foundCommand == 0) {
                foundCommand = findKeyword(context, smsShortCommand + "app", message, pin, sender, isPinRequired);
                if (foundCommand == 1) {
                    onAppCommandFound(sender, context, location, extras);
                }
            }
        }
        return foundCommand;
    }

    protected final int findAdmCommand(Context context, String message, String sender, Location location, Bundle extras, String otp) {
        int foundCommand = 0;
        //Log.d(TAG, "Matching " + message + " with " + smsCommand + "adminapp");
        if (StringUtils.startsWithIgnoreCase(message, smsCommand + "admindlt") || StringUtils.startsWithIgnoreCase(message, smsShortCommand + "admindlt")) {
            String[] ts = StringUtils.split(sender, CID_SEPARATOR);
            String s = sender;
            if (ts.length > 0) {
                s = ts[0];
            }
            auditCommand(context, smsCommand + "admindlt", s, message);
            foundCommand = findKeyword(context, smsCommand + "admindlt", message, otp, sender, false);
            if (foundCommand == 1) {
                onAdmCommandFound(sender, context);
            } else if (foundCommand == 0) {
                foundCommand = findKeyword(context, smsShortCommand + "admindlt", message, otp, sender, false);
                if (foundCommand == 1) {
                    onAdmCommandFound(sender, context);
                }
            }
        }
        return foundCommand;
    }

    private int findKeyword(Context context, String keyword, String message, String pin, String sender, boolean isPinRequired) {
        if (finder.equals(Finder.STARTS)) {
            int foundCommand = findCommandInMessage(context, keyword, message, pin, sender, isPinRequired);
            if (foundCommand == 1) {
                if (!validateTokens()) {
                    foundCommand = 0;
                }
            }
            return foundCommand;
        } else { //if (finder.equals(Finder.EQUALS)) {
            return findCommandInMessage(context, keyword, message, pin, sender, isPinRequired);
        }
    }

    private int findCommandInMessage(Context context, String command, String message, String pin, String sender, boolean isPinRequired) {
        //<command><pin> <args> or <command> <pin> <args>
        Log.d(TAG, "Matching " + message + " with " + command + " and " + pin);
        int foundCommand = 0;
        if (finder == Finder.EQUALS || finder == Finder.STARTS) {
            commandTokens = message.split(" ");
            if (commandTokens.length >= 1) {
                if (StringUtils.equalsIgnoreCase(commandTokens[0], command + pin)) {
                    foundCommand = 1;
                }
                if (foundCommand == 0 && commandTokens.length >= 2) {
                    if (StringUtils.equalsIgnoreCase(commandTokens[0], command) && (!isPinRequired || StringUtils.equals(commandTokens[1], pin))) {
                        foundCommand = 1;
                    }
                }
                if (foundCommand == 0 && commandTokens.length >= 2 && StringUtils.equalsIgnoreCase(commandTokens[0], command) && StringUtils.isNumeric(commandTokens[1]) && !StringUtils.equals(commandTokens[1], pin)) {
                    sendSocialNotification(context, Command.INVALID_PIN, sender, commandTokens[0] + " " + commandTokens[1]);
                    Log.e(TAG, "Command " + commandTokens[0] + " with invalid Security PIN " + commandTokens[1] + " received!");
                    foundCommand = -1;
                } else if (foundCommand == 0 && StringUtils.startsWithIgnoreCase(commandTokens[0], command) && commandTokens[0].length() > pin.length() && StringUtils.isNumeric(StringUtils.substring(commandTokens[0], commandTokens[0].length() - pin.length()))) {
                    sendSocialNotification(context, Command.INVALID_PIN, sender, commandTokens[0]);
                    Log.e(TAG, "Command " + commandTokens[0] + " with invalid Security PIN received!");
                    foundCommand = -1;
                }
            }
        }
        return foundCommand;
    }

    static void sendSocialNotification(final Context context, final String command, final String sender, final String invalidCommand) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
        final String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");
        final String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
        Intent newIntent = new Intent(context, SmsSenderService.class);
        newIntent.putExtra("telegramId", telegramId);
        newIntent.putExtra("email", email);
        newIntent.putExtra("phoneNumber", phoneNumber);
        if (StringUtils.isNotEmpty(command)) {
            newIntent.putExtra("command", command);
        }
        if (StringUtils.isNotEmpty(sender)) {
            newIntent.putExtra("sender", sender);
        }
        newIntent.putExtra("source", "mobile");
        if (StringUtils.isNotEmpty(invalidCommand)) {
            newIntent.putExtra("invalidCommand", invalidCommand);
        }
        context.startService(newIntent);
    }

    void sendAppNotification(final Context context, final String command, final String sender) {
        if (sender != null) {
            Intent newIntent = new Intent(context, SmsSenderService.class);
            if (StringUtils.isNotEmpty(command)) {
                newIntent.putExtra("command", command);
            }
            newIntent.putExtra("app", sender);
            context.startService(newIntent);
        }
    }

    void sendSmsNotification(final Context context, final String sender, final String command) {
        Intent newIntent = new Intent(context, SmsSenderService.class);
        newIntent.putExtra("phoneNumber", sender);
        if (StringUtils.isNotEmpty(command)) {
            newIntent.putExtra("command", command);
        }
        context.startService(newIntent);
    }

    void sendAdmNotification(final Context context, final String command, final String sender, final String invalidCommand) {
        final String email = context.getString(R.string.app_email);
        final String telegramId = context.getString(R.string.app_telegram);
        Intent newIntent = new Intent(context, SmsSenderService.class);
        newIntent.putExtra("telegramId", telegramId);
        newIntent.putExtra("email", email);
        if (StringUtils.isNotEmpty(command)) {
            newIntent.putExtra("command", command);
        }
        if (StringUtils.isNotEmpty(sender)) {
            newIntent.putExtra("sender", sender);
        }
        newIntent.putExtra("source", "mobile");
        if (StringUtils.isNotEmpty(invalidCommand)) {
            newIntent.putExtra("invalidCommand", invalidCommand);
        }
        context.startService(newIntent);
    }

    private void auditCommand(Context context, final String command, final String from, final String message) {
        String auditLog = System.currentTimeMillis() + " ";
        if (StringUtils.isNotEmpty(from)) {
            auditLog += Messenger.CID_SEPARATOR + from + " ";
        }
        auditLog += command;
        if (StringUtils.isNotEmpty(message)) {
            auditLog += " " + message;
        }
        File auditFile = Files.getFilesDir(context, AUDIT_FILE, false);
        Files.appendLineToFileFromContextDir(auditFile, auditLog, 100, 10);
    }
}
