package net.gmsworld.devicelocator;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

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
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.services.CommandService;
import net.gmsworld.devicelocator.utilities.AbstractLocationManager;
import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.DevicesUtils;
import net.gmsworld.devicelocator.utilities.DistanceFormatter;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.Permissions;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;
import net.gmsworld.devicelocator.utilities.Toaster;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import androidx.fragment.app.FragmentActivity;
import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.config.LocationParams;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

public class RouteActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnInfoWindowClickListener, OnLocationUpdatedListener {

    private static final String TAG = RouteActivity.class.getSimpleName();

    private PreferencesUtils settings;
    private GoogleMap mMap;
    private Location bestLocation;

    private String deviceImei = null, routeId = null, thisDeviceImei = null, now = null, deviceName = null;
    private Integer time;
    private Double distance;

    private final List<LatLng> routePoints = new ArrayList<>();
    private final Handler handler = new Handler();
    private final Runnable r = new Runnable() {
        @Override
        public void run() {
            getRoutePoints(listener);
        }
    };

    private Toaster toaster;

    private final Network.OnGetFinishListener listener = new Network.OnGetFinishListener() {
        @Override
        public void onGetFinish(String results, int responseCode, String url) {
            if (responseCode == 200 && results.startsWith("{")) {
                JsonElement reply = JsonParser.parseString(results);
                if (reply != null) {
                    try {
                        JsonArray features = reply.getAsJsonObject().getAsJsonArray("features");
                        if (features.size() > 0) {
                            JsonElement properties = features.get(0).getAsJsonObject().getAsJsonObject("properties");
                            if (properties.getAsJsonObject().has("distance") && properties.getAsJsonObject().has("time")) {
                                distance = properties.getAsJsonObject().get("distance").getAsDouble();
                                time = properties.getAsJsonObject().get("time").getAsInt();
                            }
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
                                    loadMarkers(false);
                                }
                            }
                        } else {
                            Log.d(TAG, "No route points found!");
                            toaster.showActivityToast("No route points saved yet!");
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

        settings = new PreferencesUtils(this);

        toaster = new Toaster(this);

        Intent intent = getIntent();
        String action = null;
        if (intent != null) {
            action = intent.getAction();
        }

        thisDeviceImei = Messenger.getDeviceId(this, false);

        if (StringUtils.equals(action,Intent.ACTION_VIEW)) {
            Uri data = intent.getData();
            String[] tokens = StringUtils.split(data.getPath(), "/");
            if (tokens.length >= 3) {
                deviceImei = tokens[1];
                routeId = tokens[2];
                if (tokens.length >= 4 && StringUtils.equals(tokens[3], "now")) {
                    now = "true";
                }
            }
        } else {
            deviceImei = intent.getStringExtra("imei");
            routeId = intent.getStringExtra("routeId");
            now = intent.getStringExtra("now");
            deviceName = intent.getStringExtra("deviceName");
        }

        if (StringUtils.isEmpty(deviceName)) {
            deviceName = deviceImei;
        }

        getRoutePoints(listener);

        initShareRouteButton();

        FirebaseAnalytics.getInstance(this).logEvent("route_activity", new Bundle());
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(r);
        SmartLocation.with(this).location().stop();
        toaster.cancel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(r);
        if (StringUtils.equals(now, "true") && StringUtils.isNotEmpty(deviceImei) && StringUtils.isNotEmpty(routeId)) {
            handler.post(r);
        }
        if (Permissions.haveLocationPermission(this)) {
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
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent()");
        if (intent.hasExtra("imei") && intent.hasExtra("routeId")) {
            deviceImei = intent.getStringExtra("imei");
            routeId = getIntent().getStringExtra("routeId");
            now = getIntent().getStringExtra("now");
            deviceName = getIntent().getStringExtra("deviceName");
            if (StringUtils.isEmpty(deviceName)) {
                deviceName = deviceImei;
            }
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
        mUiSettings.setMapToolbarEnabled(false);

        loadMarkers(true);
    }

    private void getRoutePoints(Network.OnGetFinishListener onGetFinishListener) {
        if (Network.isNetworkAvailable(this)) {
            if (StringUtils.isNotEmpty(deviceImei) && StringUtils.isNotEmpty(routeId)) {
                String queryString = "route=" + "device_locator_route_" + deviceImei + "_" + routeId;
                if (StringUtils.equals(now, "true")) {
                    queryString += "&now=true";
                } else {
                    toaster.showActivityToast(R.string.please_wait);
                }
                //Log.d(TAG, "Calling " + queryString);
                Network.get(this, getString(R.string.routeProviderUrl) + "?" + queryString, null, onGetFinishListener);
            } else {
                toaster.showActivityToast(R.string.internal_error);
            }
        } else {
            toaster.showActivityToast(R.string.no_network_error);
        }

        handler.removeCallbacks(r);
        if (StringUtils.equals(now, "true")) {
            handler.postDelayed(r, 10000);
        }
    }

    private void initShareRouteButton() {
        final ImageButton shareButton = this.findViewById(R.id.shareRoute);

        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String routeUrl = getString(R.string.showRouteUrl) + "/" + deviceImei + "/" + routeId;
                if (StringUtils.equals(now, "true")) {
                    routeUrl += "/now";
                }
                try {
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, "Follow device " + deviceName + " location here: " + routeUrl);
                    sendIntent.putExtra(Intent.EXTRA_HTML_TEXT, "Follow device <a href=\"" + getString(R.string.deviceUrl) + "/" + deviceImei + "\">" + deviceName + "</a> location <a href=\"" + routeUrl + "\">here</a>...");
                    sendIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.message, deviceName) + " - route map link");
                    sendIntent.setType("text/html");
                    startActivity(sendIntent);
                } catch (Exception e) {
                    toaster.showActivityToast(R.string.internal_error);
                }
            }
        });
    }

    private void loadMarkers(boolean zoom) {
        if (mMap != null && !routePoints.isEmpty()) {
            mMap.clear();
            final LatLngBounds.Builder routePointsBounds = new LatLngBounds.Builder();

            if (routePoints.size() > 1) {
                Marker m = mMap.addMarker(new MarkerOptions().position(routePoints.get(0)).icon(BitmapDescriptorFactory.fromResource(R.drawable.route_start)).anchor(0.5f, 0.5f));
                m.setTag("first");
                for (int i = 0; i < routePoints.size() - 1; i++) {
                    mMap.addPolyline(new PolylineOptions().add(routePoints.get(i), routePoints.get(i + 1)).width(12).color(Color.RED));
                    mMap.addMarker(new MarkerOptions().position(routePoints.get(i + 1)).icon(BitmapDescriptorFactory.fromResource(R.drawable.red_ball)).anchor(0.5f, 0.5f));
                    routePointsBounds.include(routePoints.get(i));
                }
                routePointsBounds.include(routePoints.get(routePoints.size()-1));
            }

            MarkerOptions mo;
            final String title = "Device location";
            String snippet = "Click to stop device tracing";
            if (!StringUtils.equals(now, "true") && time != null && distance != null) {
                float avg = (float)(distance / (time / 1000d)); // meters / second
                snippet = "Distance: " + DistanceFormatter.format(distance.intValue()) + ", avg. speed: " +  Messenger.getSpeed(this, avg) + ", time: " + DateUtils.formatElapsedTime(time/1000);
            } else if (!StringUtils.equals(now, "true")) {
                snippet = "";
            }
            if (deviceImei.equals(thisDeviceImei)) {
                mo = new MarkerOptions().zIndex(1.0f).position(routePoints.get(routePoints.size() - 1)).title(title).snippet(snippet).icon(BitmapDescriptorFactory.fromResource(R.drawable.phoneok)).anchor(0.5f, 0.5f);
            } else {
                mo = new MarkerOptions().zIndex(0.0f).position(routePoints.get(routePoints.size() - 1)).title(title).snippet(snippet).icon(BitmapDescriptorFactory.fromResource(R.drawable.phoneidk)).anchor(0.5f, 0.5f);
            }
            Marker m = mMap.addMarker(mo);
            m.setTag("last");

            if (zoom) {
                try {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(routePoints.get(routePoints.size() - 1), 14f));
                } catch (Exception e) {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(routePoints.get(routePoints.size() - 1), 14f));
                }
            } else if (routePoints.size() > 1) {
                LatLngBounds bounds = routePointsBounds.build();
                final int width = getResources().getDisplayMetrics().widthPixels;
                final int height = getResources().getDisplayMetrics().heightPixels;
                final int padding = (int) (width * 0.2);
                try {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding));
                } catch (Exception e) {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding));
                }
            } else if (routePoints.size() == 1) {
                try {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(routePoints.get(routePoints.size() - 1), 14f));
                } catch (Exception e) {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(routePoints.get(routePoints.size() - 1), 14f));
                }
            }
        }
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        if (StringUtils.equals(now, "true")) {
            Log.d(TAG, "Device tracing will be stopped...");
            Intent newIntent = new Intent(this, CommandService.class);
            newIntent.putExtra("command", Command.STOP_COMMAND);
            newIntent.putExtra("imei", deviceImei);
            startService(newIntent);
        }
    }

    @Override
    public void onLocationUpdated(Location location) {
        if (mMap != null && routePoints.isEmpty()) {
            try {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 14f));
            } catch (Exception e) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 14f));
            }
        }

        if (bestLocation == null) {
            bestLocation = location;
        }

        float accDiff = bestLocation.getAccuracy() - location.getAccuracy();
        boolean isAccBetter = location.getAccuracy() <= bestLocation.getAccuracy();
        float dist = location.distanceTo(bestLocation);

        if (!bestLocation.getProvider().equals(LocationManager.GPS_PROVIDER) || bestLocation.getProvider().equals(location.getProvider())) {
            if (location.hasAccuracy() && bestLocation.hasAccuracy() && isAccBetter) {
                //Log.d(TAG, "Updating best location.");
                bestLocation = location;
            }
        }

        if (bestLocation.getAccuracy() < AbstractLocationManager.MAX_REASONABLE_ACCURACY) {
            final boolean needUpdateLocation = System.currentTimeMillis() - settings.getLong(Messenger.LOCATION_SENT_MILLIS) > AlarmManager.INTERVAL_HOUR;
            if (isAccBetter && (needUpdateLocation || dist > 3f || accDiff > 2f)) {
                Log.d(TAG, "Sending new location with accuracy " + bestLocation.getAccuracy() + ", distance " + dist + " and accuracy difference " + accDiff);
                DevicesUtils.sendGeo(this, settings, thisDeviceImei, bestLocation, toaster);
            }
        } else {
            Log.d(TAG, "Accuracy is " + bestLocation.getAccuracy() + " more than max " + AbstractLocationManager.MAX_REASONABLE_ACCURACY + ", will check again.");
        }
    }
}
