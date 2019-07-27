package net.gmsworld.devicelocator;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
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

import net.gmsworld.devicelocator.fragments.RegisterDeviceDialogFragment;
import net.gmsworld.devicelocator.model.Device;
import net.gmsworld.devicelocator.services.CommandService;
import net.gmsworld.devicelocator.utilities.AbstractLocationManager;
import net.gmsworld.devicelocator.utilities.DevicesUtils;
import net.gmsworld.devicelocator.utilities.DistanceFormatter;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.Permissions;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import org.apache.commons.lang3.StringUtils;
import org.ocpsoft.prettytime.PrettyTime;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import androidx.fragment.app.FragmentActivity;
import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.config.LocationParams;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnInfoWindowClickListener, OnLocationUpdatedListener {

    private static final String TAG = MapsActivity.class.getSimpleName();

    private GoogleMap mMap;
    private PreferencesUtils settings;

    private ArrayList<Device> devices;

    private String deviceImei = null;

    private String thisDeviceImei = null;

    private float currentZoom = -1f;

    private final PrettyTime pt = new PrettyTime();

    private Location bestLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        settings = new PreferencesUtils(this);

        thisDeviceImei = Messenger.getDeviceId(this, false);

        deviceImei = getIntent().getStringExtra("imei");
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
        Log.d(TAG, "onPause()");
        SmartLocation.with(this).location().stop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent()");
        if (intent.hasExtra("imei")) {
            deviceImei = intent.getStringExtra("imei");
        }
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

        UiSettings mUiSettings = mMap.getUiSettings();
        mUiSettings.setZoomControlsEnabled(true);
        mUiSettings.setCompassEnabled(true);
        mUiSettings.setMyLocationButtonEnabled(true);
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
            int markerCount = 0;
            if (devices.size() > 1) {
                initLocateButton();
            }
            for (int i =0;i<devices.size(); i++) {
                Device d = devices.get(i);
                String[] geo = StringUtils.split(d.geo," ");
                if (geo != null && geo.length > 1) {
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
                        mo = new MarkerOptions().zIndex(1.0f).position(deviceMarker).title("Device " + d.name).snippet(snippet).icon(BitmapDescriptorFactory.fromResource(R.drawable.phoneok)).anchor(0.5f, 0.5f);
                    } else if (d.imei.equals(thisDeviceImei)) {
                        mo = new MarkerOptions().zIndex(0.5f).position(deviceMarker).title("Device " + d.name).snippet(snippet).icon(BitmapDescriptorFactory.fromResource(R.drawable.phoneidk)).anchor(0.5f, 0.5f);
                    } else {
                        mo = new MarkerOptions().zIndex(0.0f).position(deviceMarker).title("Device " + d.name).snippet(snippet).icon(BitmapDescriptorFactory.fromResource(R.drawable.phone)).anchor(0.5f, 0.5f);
                    }
                    Marker m = mMap.addMarker(mo);
                    m.setTag(i);
                    markerCount++;
                }
            }
            Log.d(TAG, "Loaded " + markerCount + " device markers to the map");

            if (markerCount > 0) {
                LatLngBounds bounds = devicesBounds.build();
                final int width = getResources().getDisplayMetrics().widthPixels;
                final int height = getResources().getDisplayMetrics().heightPixels;
                final int padding = (int) (width * 0.2);
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding));
                if (center != null) {
                    if (currentZoom > 0) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLng(center));
                    } else {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 14f));
                    }
                    currentZoom = mMap.getCameraPosition().zoom;
                }
            }
        } else {
            RegisterDeviceDialogFragment registerDeviceDialogFragment = new RegisterDeviceDialogFragment();
            registerDeviceDialogFragment.show(this.getFragmentManager(), RegisterDeviceDialogFragment.TAG);
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
            if (dist > 3f || accDiff > 1f) {
                Log.d(TAG, "Sending new location with accuracy " + bestLocation.getAccuracy() + ", distance " + dist + " and accuracy difference " + accDiff);
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
            final String content = "imei=" + thisDeviceImei + "&flex=" + geo;

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + tokenStr);

            Network.post(context, context.getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    DevicesUtils.loadDeviceList(context, settings, MapsActivity.this);
                }
            });
        } else {
            Log.e(TAG, "No network available. Failed to send device location!");
        }
    }

    private void initLocateButton() {
        final ImageButton locateButton = this.findViewById(R.id.locateDevices);
        locateButton.setVisibility(View.VISIBLE);

        locateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (devices != null && devices.size() > 1) {
                    Toast.makeText(MapsActivity.this, R.string.please_wait, Toast.LENGTH_LONG).show();
                    for (Device device : devices) {
                        if (!StringUtils.equals(device.imei, thisDeviceImei)) {
                            if (settings.contains(CommandActivity.PIN_PREFIX + device.imei)) {
                                //send locate command to deviceImei
                                String devicePin = settings.getEncryptedString(CommandActivity.PIN_PREFIX + device.imei);
                                Intent newIntent = new Intent(MapsActivity.this, CommandService.class);
                                newIntent.putExtra("command", "locate");
                                newIntent.putExtra("imei", device.imei);
                                newIntent.putExtra(MainActivity.DEVICE_NAME, device.name);
                                newIntent.putExtra("pin", devicePin);
                                newIntent.putExtra("args", "silent");
                                startService(newIntent);
                            } else {
                                Toast.makeText(MapsActivity.this, "Unable to send command to the device " + device.name + " because PIN is not saved!", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                }

            }
        });
    }
}
