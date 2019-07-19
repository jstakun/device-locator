package net.gmsworld.devicelocator;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.services.CommandService;
import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.Permissions;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class RouteActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnInfoWindowClickListener {

    private static final String TAG = RouteActivity.class.getSimpleName();

    private GoogleMap mMap;

    private String deviceImei = null, routeId = null, thisDeviceImei = null, now = null;

    List<LatLng> routePoints = new ArrayList<LatLng>();

    private Handler handler = new Handler();

    private Runnable r = new Runnable() {
        @Override
        public void run() {
            getRoutePoints(listener);
        }
    };

    private Network.OnGetFinishListener listener = new Network.OnGetFinishListener() {
        @Override
        public void onGetFinish(String results, int responseCode, String url) {
            if (responseCode == 200 && results.startsWith("{")) {
                JsonElement reply = new JsonParser().parse(results);
                if (reply != null) {
                    try {
                        JsonArray features = reply.getAsJsonObject().getAsJsonArray("features");
                        if (features.size() > 0) {
                            JsonElement geometry = features.get(0).getAsJsonObject().getAsJsonObject("geometry");
                            JsonArray coords = geometry.getAsJsonObject().getAsJsonArray("coordinates");
                            if (coords.size() > routePoints.size()) {
                                routePoints.clear();
                                for (int i = 0; i < coords.size(); i++) {
                                    JsonArray point = coords.get(i).getAsJsonArray();
                                    double lat = point.get(1).getAsDouble();
                                    double lng = point.get(0).getAsDouble();
                                    routePoints.add(new LatLng(lat, lng));
                                }
                                if (mMap != null) {
                                    loadMarkers();
                                }
                            }
                        } else {
                            Log.d(TAG, "No route points found!");
                            Toast.makeText(RouteActivity.this, "No route points found!", Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        deviceImei = getIntent().getStringExtra("imei");

        thisDeviceImei = Messenger.getDeviceId(this, false);

        routeId = getIntent().getStringExtra("routeId");

        now = getIntent().getStringExtra("now");

        getRoutePoints(listener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (StringUtils.equals(now, "true")) {
            handler.removeCallbacks(r);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (StringUtils.equals(now, "true") && StringUtils.isNotEmpty(deviceImei) && StringUtils.isNotEmpty(routeId)) {
            handler.removeCallbacks(r);
            handler.post(r);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent()");
        if (intent.hasExtra("imei") && intent.hasExtra("routeId")) {
            deviceImei = intent.getStringExtra("imei");
            routeId = getIntent().getStringExtra("routeId");
            now = getIntent().getStringExtra("now");
            if (mMap != null) {
                mMap.clear();
            }
            routePoints.clear();
            getRoutePoints(listener);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
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

        loadMarkers();
    }

    public void getRoutePoints(Network.OnGetFinishListener onGetFinishListener) {
        if (Network.isNetworkAvailable(this)) {
            Toast.makeText(this, R.string.please_wait, Toast.LENGTH_LONG).show();
            String queryString = "route=" + "device_locator_route_" + deviceImei + "_" + routeId;
            if (StringUtils.equals(now, "true")) {
                queryString += "&now=true";
            }
            Log.d(TAG, "Calling " + queryString);
            Network.get(this, getString(R.string.routeProviderUrl) + "?" + queryString, null, onGetFinishListener);
        } else {
            Toast.makeText(this, R.string.no_network_error, Toast.LENGTH_LONG).show();
        }

        if (StringUtils.equals(now, "true")) {
            handler.removeCallbacks(r);
            handler.postDelayed(r, 10000);
        }
    }

    private void loadMarkers() {
        if (mMap != null && !routePoints.isEmpty()) {
            mMap.clear();

            if (routePoints.size() > 1) {
                Marker m = mMap.addMarker(new MarkerOptions().position(routePoints.get(0)).icon(BitmapDescriptorFactory.fromResource(R.drawable.red_ball)));
                m.setTag("first");
                for (int i = 0; i < routePoints.size() - 1; i++) {
                    Polyline line = mMap.addPolyline(new PolylineOptions()
                            .add(routePoints.get(i), routePoints.get(i + 1))
                            .width(12)
                            .color(Color.RED));
                    mMap.addMarker(new MarkerOptions().position(routePoints.get(i + 1)).icon(BitmapDescriptorFactory.fromResource(R.drawable.red_ball)));
                }
            }

            MarkerOptions mo;
            if (deviceImei.equals(thisDeviceImei)) {
                mo = new MarkerOptions().zIndex(1.0f).position(routePoints.get(routePoints.size() - 1)).title("Device location").snippet("Click to stop device tracing").icon(BitmapDescriptorFactory.fromResource(R.drawable.phoneok));
            } else {
                mo = new MarkerOptions().zIndex(0.0f).position(routePoints.get(routePoints.size() - 1)).title("Device location").snippet("Click to stop device tracing").icon(BitmapDescriptorFactory.fromResource(R.drawable.phoneidk));
            }
            Marker m = mMap.addMarker(mo);
            m.setTag("last");

            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(routePoints.get(routePoints.size() - 1), 14f));
        }
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        Log.d(TAG, "Device tracing will be stopped...");
        Intent newIntent = new Intent(this, CommandService.class);
        newIntent.putExtra("command", Command.STOP_COMMAND);
        newIntent.putExtra("imei", deviceImei);
        startService(newIntent);
    }
}
