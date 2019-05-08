package net.gmsworld.devicelocator;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import net.gmsworld.devicelocator.model.Device;
import net.gmsworld.devicelocator.utilities.AbstractLocationManager;
import net.gmsworld.devicelocator.utilities.DevicesUtils;
import net.gmsworld.devicelocator.utilities.DistanceFormatter;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.Permissions;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import org.ocpsoft.prettytime.PrettyTime;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.config.LocationParams;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnInfoWindowClickListener, OnLocationUpdatedListener {

    private static final String TAG = MapsActivity.class.getSimpleName();

    private GoogleMap mMap;
    private UiSettings mUiSettings;
    private PreferencesUtils settings;

    private ArrayList<Device> devices;

    private String deviceImei = null;
    private int zoom = -1;

    private final PrettyTime pt = new PrettyTime();

    private Location bestLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        settings = new PreferencesUtils(this);

        deviceImei = getIntent().getStringExtra("imei");

        if (deviceImei == null) {
            deviceImei = Messenger.getDeviceId(this, false);
        } else {
            zoom = 15;
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        if (mMap != null) {
            loadDeviceMarkers();
        }
        try {
            if (SmartLocation.with(this).location().state().isAnyProviderAvailable()) {
                SmartLocation.with(this).location(new LocationGooglePlayServicesWithFallbackProvider(this))
                        .config(LocationParams.NAVIGATION)
                        .start(this);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        SmartLocation.with(this).location().stop();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.d(TAG, "onMapReady()");
        mMap = googleMap;

        if (Permissions.haveLocationPermission(this)) {
            mMap.setMyLocationEnabled(true);
        }

        mMap.setOnInfoWindowClickListener(this);

        mUiSettings = mMap.getUiSettings();
        mUiSettings.setZoomControlsEnabled(true);
        mUiSettings.setCompassEnabled(true);
        mUiSettings.setMyLocationButtonEnabled(false);
        mUiSettings.setScrollGesturesEnabled(true);
        mUiSettings.setZoomGesturesEnabled(true);
        mUiSettings.setTiltGesturesEnabled(true);
        mUiSettings.setRotateGesturesEnabled(true);

        loadDeviceMarkers();
    }

    public void loadDeviceMarkers() {
        mMap.clear();
        LatLng center = null;
        final LatLngBounds.Builder devicesBounds = new LatLngBounds.Builder();
        devices = DevicesUtils.buildDeviceList(settings);
        if (!devices.isEmpty()) {
            for (int i =0;i<devices.size(); i++) {
                Device d = devices.get(i);
                String[] geo = d.geo.split(" ");
                LatLng deviceMarker = new LatLng(Double.valueOf(geo[0]), Double.valueOf(geo[1]));
                devicesBounds.include(deviceMarker);

                long timestamp = Long.valueOf(geo[geo.length - 1]);
                String snippet = "Last seen " + pt.format(new Date(timestamp));
                Location location = SmartLocation.with(this).location(new LocationGooglePlayServicesWithFallbackProvider(this)).getLastLocation();
                if (location != null) {
                    Location deviceLocation = new Location("");
                    deviceLocation.setLatitude(Location.convert(geo[0]));
                    deviceLocation.setLongitude(Location.convert(geo[1]));

                    int dist = (int) location.distanceTo(deviceLocation);
                    if (dist <= 0) {
                        dist = 1;
                    }
                    snippet += " " + DistanceFormatter.format(dist) + " away";
                }

                MarkerOptions mo;
                if (d.imei.equals(deviceImei)) {
                    center = deviceMarker;
                    mo = new MarkerOptions().zIndex(1.0f).position(deviceMarker).title("Device " + d.name).snippet(snippet).icon(BitmapDescriptorFactory.fromResource(R.drawable.phoneok));
                } else {
                    mo = new MarkerOptions().zIndex(0.0f).position(deviceMarker).title("Device " + d.name).snippet(snippet).icon(BitmapDescriptorFactory.fromResource(R.drawable.phone));
                }
                Marker m = mMap.addMarker(mo);
                m.setTag(i);
            }

            LatLngBounds bounds = devicesBounds.build();
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;
            int padding = (int) (width * 0.12);
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding));
            //mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
            if (center != null && zoom > 0) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, zoom));
            } else if (center != null) {
                mMap.moveCamera(CameraUpdateFactory.newLatLng(center));
            }
        } else {
            Toast.makeText(this, "No devices registered! Please go to Devices tab and register your device.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        Intent intent = new Intent(this, CommandActivity.class);
        intent.putExtra("index", (int)marker.getTag());
        intent.putParcelableArrayListExtra("devices", devices);
        startActivity(intent);
    }

    @Override
    public void onLocationUpdated(Location location) {
        if (bestLocation == null) {
            bestLocation = location;
        }

        float accDiff = bestLocation.getAccuracy() - location.getAccuracy();
        float dist = location.distanceTo(bestLocation);

        if (!bestLocation.getProvider().equals(LocationManager.GPS_PROVIDER) || bestLocation.getProvider().equals(location.getProvider())) {
            if (location.hasAccuracy() && bestLocation.hasAccuracy() && location.getAccuracy() < bestLocation.getAccuracy()) {
                //Log.d(TAG, "Updating best location.");
                bestLocation = location;
            }
        }

        if (bestLocation.getAccuracy() < AbstractLocationManager.MAX_REASONABLE_ACCURACY) {
            if (dist > 1f || accDiff > 1f) {
                Log.d(TAG, "Sending new location with accuracy " + bestLocation.getAccuracy() + " and distance " + dist);
                sendGeo(this, settings, bestLocation);
            }
        } else {
            Log.d(TAG, "Accuracy is " + bestLocation.getAccuracy() + " more than max " + AbstractLocationManager.MAX_REASONABLE_ACCURACY + ", will check again.");
        }
    }

    private void sendGeo(final Context context, final PreferencesUtils settings, Location location) {
        if (Network.isNetworkAvailable(context)) {
            final String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN);
            final String geo = "geo:" + location.getLatitude() + " " + location.getLongitude() + " " + location.getAccuracy();
            final String content = "imei=" + Messenger.getDeviceId(context, false) + "&flex=" + geo;

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + tokenStr);

            Network.post(context, context.getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    DevicesUtils.loadDeviceList(context, settings, null);
                }
            });
        } else {
            Log.e(TAG, "No network available. Failed to send device location!");
        }
    }
}
