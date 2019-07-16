package net.gmsworld.devicelocator;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.Permissions;

import java.util.ArrayList;
import java.util.List;

public class RouteActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = RouteActivity.class.getSimpleName();

    private GoogleMap mMap;

    private String deviceImei = null, routeId = null;

    List<LatLng> routePoints = new ArrayList<LatLng>();

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

        routeId = getIntent().getStringExtra("routeId");

        getRoutePoints(listener);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (Permissions.haveLocationPermission(this)) {
            mMap.setMyLocationEnabled(true);
        }

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
            String queryString = "route=" + "device_locator_route_" + deviceImei + "_" + routeId + "&now=true";
            Log.d(TAG, "Calling " + queryString);
            Network.get(this, getString(R.string.routeProviderUrl) + "?" + queryString, null, onGetFinishListener);
        } else {
            Toast.makeText(this, R.string.no_network_error, Toast.LENGTH_LONG).show();
        }
    }

    private void loadMarkers() {
        if (mMap != null && !routePoints.isEmpty()) {
            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(routePoints.get(0)).title("Route start point"));

            if (routePoints.size() > 1) {
                for (int i=0;i<routePoints.size()-1;i++) {
                    Polyline line = mMap.addPolyline(new PolylineOptions()
                            .add(routePoints.get(i), routePoints.get(i+1))
                            .width(5)
                            .color(Color.RED));
                }

                mMap.addMarker(new MarkerOptions().position(routePoints.get(routePoints.size()-1)).title("Current route end point"));
            }

            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(routePoints.get(routePoints.size()-1), 14f));
        }
    }
}
