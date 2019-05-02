package net.gmsworld.devicelocator;

import android.content.Intent;
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
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Permissions;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import java.util.ArrayList;
import java.util.Set;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnInfoWindowClickListener {

    private static final String TAG = MapsActivity.class.getSimpleName();

    private GoogleMap mMap;
    private UiSettings mUiSettings;
    private PreferencesUtils settings;

    private ArrayList<Device> devices;

    private String deviceImei = null;
    private int zoom = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        settings = new PreferencesUtils(this);

        deviceImei = getIntent().getStringExtra("imei");

        if (deviceImei == null) {
            deviceImei = Messenger.getDeviceId(this, false);
        } else {
            zoom = 15;
        }

        //TODO send locate command to all devices and update location on map view when received reply
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        if (mMap != null) {
            loadDeviceMarkers();
        }
    }

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

    private void loadDeviceMarkers() {
        mMap.clear();
        Set<String> deviceSet = settings.getStringSet(MainActivity.USER_DEVICES, null);
        LatLng center = null;
        if (deviceSet != null && !deviceSet.isEmpty()) {
            final LatLngBounds.Builder devicesBounds = new LatLngBounds.Builder();
            int i = 0;
            devices = new ArrayList<Device>();
            for (String device : deviceSet) {
                Device d = Device.fromString(device);
                if (d != null) {
                    devices.add(d);
                    String[] geo = d.geo.split(" ");
                    LatLng deviceMarker = new LatLng(Double.valueOf(geo[0]), Double.valueOf(geo[1]));
                    devicesBounds.include(deviceMarker);
                    MarkerOptions mo = null;
                    if (d.imei.equals(deviceImei)) {
                        center = deviceMarker;
                        mo = new MarkerOptions().zIndex(1.0f).position(deviceMarker).title("Click to send command to " + d.name).icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_small_red));
                    } else {
                        mo = new MarkerOptions().zIndex(0.0f).position(deviceMarker).title("Click to send command to " + d.name).icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_small));
                    }
                    Marker m = mMap.addMarker(mo);
                    m.setTag(i);
                    i++;
                }
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

}
