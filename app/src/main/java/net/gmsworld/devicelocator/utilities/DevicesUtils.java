package net.gmsworld.devicelocator.utilities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

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
    public static final String CURRENT_DEVICE_ID = "currentDeviceId";

    private static boolean isSyncingDevices = false;

    private static final String TAG = DevicesUtils.class.getSimpleName();

    //TODO replace this interface with broadcast
    public interface DeviceLoadListener {
        void onDeviceListLoaded(ArrayList<Device> userDevices);
        void onDeviceLoadError(int messageId);
        void onDeviceRemoved();
    }

    public static void loadDeviceList(final Context context, final PreferencesUtils settings, final DeviceLoadListener deviceLoadListener) {
        if (Network.isNetworkAvailable(context)) {
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN);
            final Map<String, String> headers = new HashMap<>();
            if (StringUtils.isNotEmpty(tokenStr)) {
                String userLogin = settings.getString(MainActivity.USER_LOGIN);
                if (StringUtils.isNotEmpty(userLogin) && !isSyncingDevices) {
                    isSyncingDevices = true;
                    headers.put("Authorization", "Bearer " + tokenStr);
                    final String content = "username=" + userLogin + "&action=list";
                    Network.post(context, context.getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String results, int responseCode, String url) {
                            isSyncingDevices = false;
                            if (responseCode == 200 && StringUtils.startsWith(results, "{")) {
                                JsonObject reply = new JsonParser().parse(results).getAsJsonObject();
                                JsonArray devices = null;
                                if (reply.has("devices")) {
                                    devices = reply.get("devices").getAsJsonArray();
                                }
                                boolean thisDeviceOnList = false;
                                if (devices != null && devices.size() > 0) {
                                    ArrayList<Device> userDevices = new ArrayList<>();
                                    final String imei = Messenger.getDeviceId(context, false);
                                    for (JsonElement d : devices) {
                                        JsonObject deviceObject = d.getAsJsonObject();
                                        if (deviceObject.has("imei")) {
                                            final String deviceImei = deviceObject.get("imei").getAsString();
                                            String token = null;
                                            if (deviceObject.has("token")) {
                                                token = deviceObject.get("token").getAsString();
                                            }
                                            if (StringUtils.isNotEmpty(token) || StringUtils.equals(deviceImei, imei)) {
                                                Device device = new Device();
                                                device.imei = deviceImei;
                                                if (deviceObject.has("creationDate")) {
                                                    device.creationDate = deviceObject.get("creationDate").getAsString();
                                                }
                                                if (deviceObject.has("name")) {
                                                    device.name = deviceObject.get("name").getAsString();
                                                }
                                                if (deviceObject.has("geo")) {
                                                    device.geo = deviceObject.get("geo").getAsString();
                                                }
                                                if (StringUtils.equals(device.imei, imei)) {
                                                    thisDeviceOnList = true;
                                                }
                                                userDevices.add(device);
                                            }
                                        }
                                    }
                                    if (thisDeviceOnList) {
                                        setUserDevices(settings, userDevices, context);
                                        if (deviceLoadListener != null) {
                                            deviceLoadListener.onDeviceListLoaded(userDevices);
                                        } else {
                                            Intent broadcastIntent = new Intent();
                                            Log.d(TAG, "Sending UI Update Broadcast");
                                            broadcastIntent.setAction(Command.UPDATE_UI_ACTION);
                                            context.sendBroadcast(broadcastIntent);
                                        }
                                    } else if (deviceLoadListener != null) {
                                        deviceLoadListener.onDeviceLoadError(R.string.devices_list_empty);
                                    }
                                } else if (deviceLoadListener != null) {
                                    deviceLoadListener.onDeviceLoadError(R.string.devices_list_empty);
                                }
                                if (!thisDeviceOnList) {
                                    //this device has been removed from other device
                                    settings.remove(MainActivity.USER_LOGIN, DevicesUtils.USER_DEVICES);
                                    if (deviceLoadListener != null) {
                                        deviceLoadListener.onDeviceRemoved();
                                    }
                                }
                            } else if (deviceLoadListener != null) {
                                deviceLoadListener.onDeviceLoadError(R.string.devices_list_loading_failed);
                            }
                        }
                    });
                } else if (StringUtils.isEmpty(userLogin)) {
                    Log.e(TAG, "User login is unset. No device list will be loaded");
                } else {
                    Log.d(TAG, "Device sync in progress");
                }
            } else {
                String queryString = "scope=dl&user=" + Messenger.getDeviceId(context, false);
                Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            Messenger.getToken(context, results);
                            loadDeviceList(context, settings,  deviceLoadListener);
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
                    settings.remove(DevicesUtils.USER_DEVICES, CURRENT_DEVICE_ID);
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

    public static void updateDevice(List<Device> devices, Device device, Context context) {
        PreferencesUtils settings = new PreferencesUtils(context);
        if (devices == null) {
            devices = buildDeviceList(settings);
        }
        if (StringUtils.isEmpty(device.name)) {
            device.name = getDeviceName(devices, device.imei);
        }
        for (int i=0;i<devices.size();i++) {
            if (devices.get(i).imei.equals(device.imei)) {
                devices.remove(i);
                break;
            }
        }
        devices.add(device);
        setUserDevices(settings, devices, context);
        Intent broadcastIntent = new Intent();
        Log.d(TAG, "Sending UI Update Broadcast");
        broadcastIntent.setAction(Command.UPDATE_UI_ACTION);
        context.sendBroadcast(broadcastIntent);
    }

    private static void setUserDevices(PreferencesUtils settings, List<Device> userDevices, Context context) {
        Set<String> deviceSet = new HashSet<>();
        for (Device device : userDevices) {
            deviceSet.add(device.toString());
        }
        settings.setStringSet(USER_DEVICES, deviceSet);
        //settings.setLong(USER_DEVICES_TIMESTAMP, System.currentTimeMillis());
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

    public static void sendGeo(final Activity context, final PreferencesUtils settings, final String thisDeviceImei, final Location location, final boolean silent) {
        if (Network.isNetworkAvailable(context)) {
            final String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN);
            if (StringUtils.isNotEmpty(tokenStr)) {
                final String geo = location.getLatitude() + " " + location.getLongitude() + " " + location.getAccuracy();
                final String content = "imei=" + thisDeviceImei + "&flex=" + "geo:" + geo;
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenStr);
                Network.post(context, context.getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            settings.setLong(Messenger.LOCATION_SENT_MILLIS, System.currentTimeMillis());
                            List<Device> devices = buildDeviceList(settings);
                            int pos = getDevicePosition(devices, thisDeviceImei);
                            if (pos >= 0 && pos < devices.size()) {
                                Device d = devices.get(pos);
                                d.geo = geo + " " + System.currentTimeMillis();
                                updateDevice(devices, d, context);
                            }
                            //
                            if (!silent) {
                                Toaster.showToast(context, "Location refreshed");
                            }
                        }
                    }
                });
            }
        } else {
            Log.e(TAG, "No network available. Failed to send device location!");
        }
    }
}
