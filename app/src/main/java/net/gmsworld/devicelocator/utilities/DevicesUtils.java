package net.gmsworld.devicelocator.utilities;

import android.app.Activity;
import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.MapsActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.model.Device;
import net.gmsworld.devicelocator.services.DlFirebaseMessagingService;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DevicesUtils {

    public static final String USER_DEVICES = "userDevices";
    public static final String CURRENT_DEVICE_ID = "currentDeviceId";

    private static final String TAG = DevicesUtils.class.getSimpleName();

    public static void loadDeviceList(final Context context, final PreferencesUtils settings, final Activity callerActivity) {
        if (Network.isNetworkAvailable(context)) {
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN);
            final Map<String, String> headers = new HashMap<>();
            headers.put("X-GMS-AppId", "2");
            headers.put("X-GMS-Scope", "dl");
            if (StringUtils.isNotEmpty(tokenStr)) {
                String userLogin = settings.getString(MainActivity.USER_LOGIN);
                if (StringUtils.isNotEmpty(userLogin)) {
                    headers.put("Authorization", "Bearer " + tokenStr);
                    String content = "username=" + userLogin + "&action=list";
                    Network.post(context, context.getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String results, int responseCode, String url) {
                            if (responseCode == 200 && StringUtils.startsWith(results, "{")) {
                                JsonElement reply = new JsonParser().parse(results);
                                JsonArray devices = reply.getAsJsonObject().get("devices").getAsJsonArray();
                                boolean thisDeviceOnList = false;
                                if (devices.size() > 0) {
                                    ArrayList<Device> userDevices = new ArrayList<>();
                                    final String imei = Messenger.getDeviceId(context, false);
                                    for (JsonElement d : devices) {
                                        JsonObject deviceObject = d.getAsJsonObject();
                                        if (StringUtils.isNotEmpty(deviceObject.get("token").getAsString())) {
                                            Device device = new Device();
                                            if (deviceObject.has("name")) {
                                                device.name = deviceObject.get("name").getAsString();
                                            }
                                            device.imei = deviceObject.get("imei").getAsString();
                                            device.creationDate = deviceObject.get("creationDate").getAsString();
                                            if (deviceObject.has("geo")) {
                                                device.geo = deviceObject.get("geo").getAsString();
                                            }
                                            if (StringUtils.equals(device.imei, imei)) {
                                                thisDeviceOnList = true;
                                            }
                                            userDevices.add(device);
                                        }
                                    }
                                    if (thisDeviceOnList) {
                                        Set<String> deviceSet = new HashSet<>();
                                        for (Device device : userDevices) {
                                            deviceSet.add(device.toString());
                                        }
                                        PreferenceManager.getDefaultSharedPreferences(context).edit().putStringSet(DevicesUtils.USER_DEVICES, deviceSet).apply();
                                        if (callerActivity != null) {
                                            if (callerActivity instanceof MainActivity) {
                                                ((MainActivity)callerActivity).populateDeviceList(userDevices);
                                            } else if (callerActivity instanceof MapsActivity) {
                                                ((MapsActivity)callerActivity).loadDeviceMarkers();
                                            }
                                        }
                                    } else if (callerActivity != null && callerActivity instanceof MainActivity) {
                                        final TextView deviceListEmpty = ((MainActivity)callerActivity).findViewById(R.id.deviceListEmpty);
                                        deviceListEmpty.setText(R.string.devices_list_empty);
                                    }
                                } else if (callerActivity != null && callerActivity instanceof MainActivity) {
                                    final TextView deviceListEmpty = ((MainActivity)callerActivity).findViewById(R.id.deviceListEmpty);
                                    deviceListEmpty.setText(R.string.devices_list_empty);
                                }
                                if (!thisDeviceOnList) {
                                    //this device has been removed from other device
                                    PreferenceManager.getDefaultSharedPreferences(context).edit().remove(MainActivity.USER_LOGIN).remove(DevicesUtils.USER_DEVICES).apply();
                                    if (callerActivity != null && callerActivity instanceof MainActivity) {
                                        ((MainActivity)callerActivity).initUserLoginInput(true, false);
                                    }
                                }
                            } else if (callerActivity != null && callerActivity instanceof MainActivity) {
                                final TextView deviceListEmpty = ((MainActivity)callerActivity).findViewById(R.id.deviceListEmpty);
                                deviceListEmpty.setText(R.string.devices_list_loading_failed);
                            }
                        }
                    });
                } else {
                    Log.e(TAG, "User login is unset. No device list will be loaded");
                }
            } else {
                String queryString = "scope=dl&user=" + Messenger.getDeviceId(context, false);
                Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            Messenger.getToken(context, results);
                            loadDeviceList(context, settings,  callerActivity);
                        } else {
                            Log.d(TAG, "Failed to receive token: " + results);
                        }
                    }
                });
            }
        }
    }

    public static void deleteDevice(final Context context, final PreferencesUtils settings, final String deviceId) {
        final String content = "imei=" + deviceId + "&action=delete";
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + settings.getString(DeviceLocatorApp.GMS_TOKEN));
        Network.post(context, context.getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
            @Override
            public void onGetFinish(String results, int responseCode, String url) {
                if (responseCode == 200) {
                    Log.d(TAG, "Device " + deviceId + " has been removed!");
                    PreferenceManager.getDefaultSharedPreferences(context).edit().remove(DevicesUtils.USER_DEVICES).remove(CURRENT_DEVICE_ID).apply();
                }
            }
        });
    }

    public static void registerDevice(Context context, PreferencesUtils settings) {
        final String userLogin = settings.getString(MainActivity.USER_LOGIN);
        if (StringUtils.isNotEmpty(userLogin)) {
            Toast.makeText(context, "Synchronizing device...", Toast.LENGTH_LONG).show();
        }
        if (DlFirebaseMessagingService.sendRegistrationToServer(context, userLogin, settings.getString(MainActivity.DEVICE_NAME), true)) {
            //delete old device
            if (settings.contains(CURRENT_DEVICE_ID)) {
                deleteDevice(context, settings, settings.getString(CURRENT_DEVICE_ID));
            }
        } else {
            Toast.makeText(context, "Your device can't be registered at the moment!", Toast.LENGTH_LONG).show();
        }
    }

    public static ArrayList<Device> buildDeviceList(PreferencesUtils settings) {
        Set<String> deviceSet = settings.getStringSet(DevicesUtils.USER_DEVICES, null);
        ArrayList<Device> userDevices = new ArrayList<>();
        if (deviceSet != null && !deviceSet.isEmpty()) {
            for (String device : deviceSet) {
                Device d = Device.fromString(device);
                if (d != null) {
                    userDevices.add(d);
                }
            }
        }
        return userDevices;
    }
}