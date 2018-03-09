package net.gmsworld.devicelocator.BroadcastReceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.telephony.SmsMessage;
import android.util.Log;

import net.gmsworld.devicelocator.Utilities.Command;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = SmsReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {

        boolean proceed = true;

        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("settings_sms_contacts", false)) {
            proceed = false;
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get("pdus");
                for (int i = 0; i < pdus.length; i++) {
                    SmsMessage sms;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        String format = bundle.getString("format");
                        sms = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                    } else {
                        sms = SmsMessage.createFromPdu((byte[]) pdus[i]);
                    }
                    if (contactExists(context, sms.getOriginatingAddress())) {
                        proceed = true;
                        break;
                    }
                }
            }
        }

        if (proceed) {
            Command.findCommandInSms(context, intent);
        }
    }

    private static boolean contactExists(Context context, String number) {
        Uri lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        String[] mPhoneNumberProjection = { ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.NUMBER, ContactsContract.PhoneLookup.DISPLAY_NAME };
        Cursor cur = context.getContentResolver().query(lookupUri,mPhoneNumberProjection, null, null, null);
        try {
            if (cur.moveToFirst()) {
                Log.d(TAG, "Sender found in contact list");
                return true;
            }
        } finally {
            if (cur != null)
                cur.close();
        }
        Log.d(TAG, "Sender doesn't exists on contact list!");
        return false;
    }


}