package net.gmsworld.devicelocator.utilities;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.model.Device;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DevicesUtils {

    public static final String USER_DEVICES = "userDevices";
    public static final String USER_DEVICES_TIMESTAMP = "userDevicesTimestamp";
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
                                        PreferenceManager.getDefaultSharedPreferences(context).edit().putStringSet(USER_DEVICES, deviceSet).putLong(USER_DEVICES_TIMESTAMP, System.currentTimeMillis()).apply();
                                        //TODO refactor this code to use interface
                                        if (callerActivity != null) {
                                            if (callerActivity instanceof MainActivity) {
                                                ((MainActivity)callerActivity).populateDeviceList(userDevices);
                                            } //else if (callerActivity instanceof MapsActivity) {
                                              //  ((MapsActivity)callerActivity).loadDeviceMarkers(false);
                                            //}
                                        }
                                    } else if (callerActivity instanceof MainActivity) {
                                        final TextView deviceListEmpty = ((MainActivity)callerActivity).findViewById(R.id.deviceListEmpty);
                                        deviceListEmpty.setText(R.string.devices_list_empty);
                                    }
                                } else if (callerActivity instanceof MainActivity) {
                                    final TextView deviceListEmpty = ((MainActivity)callerActivity).findViewById(R.id.deviceListEmpty);
                                    deviceListEmpty.setText(R.string.devices_list_empty);
                                }
                                if (!thisDeviceOnList) {
                                    //this device has been removed from other device
                                    PreferenceManager.getDefaultSharedPreferences(context).edit().remove(MainActivity.USER_LOGIN).remove(DevicesUtils.USER_DEVICES).remove(DevicesUtils.USER_DEVICES_TIMESTAMP).apply();
                                    if (callerActivity instanceof MainActivity) {
                                        ((MainActivity)callerActivity).initUserLoginInput(true, false);
                                    }
                                }
                            } else if (callerActivity instanceof MainActivity) {
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
                    PreferenceManager.getDefaultSharedPreferences(context).edit().remove(DevicesUtils.USER_DEVICES).remove(DevicesUtils.USER_DEVICES_TIMESTAMP).remove(CURRENT_DEVICE_ID).apply();
                }
            }
        });
    }

    public static void registerDevice(Activity context, PreferencesUtils settings, Toaster toaster) {
        final String userLogin = settings.getString(MainActivity.USER_LOGIN);
        if (StringUtils.isNotEmpty(userLogin)) {
            toaster.showActivityToast(R.string.devices_list_loading);
        }
        String deviceName = settings.getString(MainActivity.DEVICE_NAME);
        if (StringUtils.isEmpty(deviceName)) {
            deviceName = Messenger.getDefaultDeviceName();
            settings.setString(MainActivity.DEVICE_NAME, deviceName);
        }
        if (Messenger.sendRegistrationToServer(context, userLogin, deviceName, true)) {
            //delete old device
            if (settings.contains(CURRENT_DEVICE_ID)) {
                deleteDevice(context, settings, settings.getString(CURRENT_DEVICE_ID));
            }
        } else {
            toaster.showActivityToast("Your device can't be registered at the moment!");
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

    public static String getDeviceName(List<Device> devices, String deviceId) {
        for (Device d : devices) {
            if (d.imei.equals(deviceId)) {
                return d.name;
            }
        }
        return deviceId;
    }

    public static int getDevicePosition(List<Device> devices, String deviceId) {
        for (int i=0;i<devices.size();i++) {
            Device d = devices.get(i);
            if (d.imei.equals(deviceId)) {
                return i;
            }
        }
        return -1;
    }

    public static void sendGeo(final Activity context, final PreferencesUtils settings, final String thisDeviceImei, final Location location) {
        if (Network.isNetworkAvailable(context)) {
            final String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN);
            final String geo = "geo:" + location.getLatitude() + " " + location.getLongitude() + " " + location.getAccuracy();
            final String content = "imei=" + thisDeviceImei + "&flex=" + geo;

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + tokenStr);

            Network.post(context, context.getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    loadDeviceList(context, settings, context);
                }
            });
        } else {
            Log.e(TAG, "No network available. Failed to send device location!");
        }
    }

}
