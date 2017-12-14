package net.gmsworld.devicelocator.BroadcastReceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.gmsworld.devicelocator.Utilities.Command;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Command.findCommandInSms(context, intent);
    }
}