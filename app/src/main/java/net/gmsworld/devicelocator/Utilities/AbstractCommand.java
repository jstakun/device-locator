package net.gmsworld.devicelocator.Utilities;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;

/**
 * Created by jstakun on 10/25/17.
 */

public abstract class AbstractCommand {

    protected static String TAG = "SmsCommand";

    protected enum Finder {STARTS, EQUALS};

    private String smsCommand = null;

    private String smsShortCommand= null;

    private Finder finder;

    protected String[] commandTokens;

    protected AbstractCommand(String smsCommand, String smsShortCommand, Finder finder) {
        this.smsCommand = smsCommand;
        this.smsShortCommand = smsShortCommand;
        this.finder = finder;
    }

    protected abstract void onSmsCommandFound(String sender, Context context);

    protected abstract void onSmsSocialCommandFound(String sender, Context context);

    protected boolean validateTokens() {
        return false;
    }

    public boolean findCommand(Context context, Intent intent) {
        String sender = null;
        if (StringUtils.isNotEmpty(smsCommand)) {
            sender = getSenderAddress(context, intent, smsCommand);
        }
        if (sender == null && StringUtils.isNotEmpty(smsShortCommand)) {
            sender = getSenderAddress(context, intent, smsShortCommand);
        }
        if (sender != null) {
            onSmsCommandFound(sender, context);
            return true;
        } else if (StringUtils.isNotEmpty(smsCommand ) &&  StringUtils.isNotEmpty(PreferenceManager.getDefaultSharedPreferences(context).getString("telegramId", ""))) {
            sender = getSenderAddress(context, intent, smsCommand + "t");
            if (sender == null && StringUtils.isNotEmpty(smsShortCommand)) {
                sender = getSenderAddress(context, intent, smsShortCommand + "t");
            }
            if (sender != null) {
                onSmsSocialCommandFound(sender, context);
                return true;
            }
        }
        return false;
    }

    private ArrayList<SmsMessage> getMessagesWithKeywordEquals(String keyword, Bundle bundle) {
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
                }
            }
        }
        return list;
    }

    private ArrayList<SmsMessage> getMessagesWithKeywordStarts(String keyword, Bundle bundle) {
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

                commandTokens = sms.getMessageBody().split(" ");
                if (commandTokens != null  && commandTokens.length > 0 && StringUtils.equalsIgnoreCase(commandTokens[0], keyword) && validateTokens()) {
                    list.add(sms);
                }
            }
        }
        return list;
    }

    private String getSenderAddress(Context context, Intent intent, String command) {
        try {
            String token = PreferenceManager.getDefaultSharedPreferences(context).getString("token", "");
            command += token;
            ArrayList<SmsMessage> list = null;
            switch (finder) {
                case EQUALS:
                    list = getMessagesWithKeywordEquals(command, intent.getExtras());
                    break;
                case STARTS:
                    list =  getMessagesWithKeywordStarts(command, intent.getExtras());
                    break;
                default:
                    Log.d(TAG, "No command finder set");
                    break;
            }
            if (list != null && list.size() > 0) {
                return list.get(0).getOriginatingAddress();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return null;
    }
}
