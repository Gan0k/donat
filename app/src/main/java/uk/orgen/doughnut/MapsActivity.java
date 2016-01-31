package uk.orgen.doughnut;

import java.util.Random;
import java.util.Map;
import java.util.HashMap;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.os.Handler;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.indooratlas.android.sdk.IALocation;
import com.indooratlas.android.sdk.IALocationListener;
import com.indooratlas.android.sdk.IALocationManager;
import com.indooratlas.android.sdk.IALocationRequest;
import com.indooratlas.android.sdk.IARegion;
import com.firebase.client.Firebase;
import com.firebase.client.ValueEventListener;
import com.firebase.client.FirebaseError;
import com.firebase.client.DataSnapshot;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,IALocationListener {

    private static final float[] colors = {BitmapDescriptorFactory.HUE_AZURE, BitmapDescriptorFactory.HUE_CYAN,
                                            BitmapDescriptorFactory.HUE_GREEN, BitmapDescriptorFactory.HUE_MAGENTA,
                                            BitmapDescriptorFactory.HUE_ORANGE, BitmapDescriptorFactory.HUE_RED,
                                            BitmapDescriptorFactory.HUE_ROSE, BitmapDescriptorFactory.HUE_VIOLET,
                                            BitmapDescriptorFactory.HUE_YELLOW};

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private Marker mMarker;
    private IALocationManager mIALocationManager;
    private Firebase fireRef;
    private String android_id;
    private Runnable runnable;
    private Handler handler;

    String TAG = "MapsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mIALocationManager = IALocationManager.create(this);

        String floorPlanId = "my-floor-plan-id";
        if (!TextUtils.isEmpty(floorPlanId)) {
            final IALocation FLOOR_PLAN_ID = IALocation.from(IARegion.floorPlan(floorPlanId));
            mIALocationManager.setLocation(FLOOR_PLAN_ID);
        }

		Firebase.setAndroidContext(this);
        fireRef = new Firebase("https://donat.firebaseio.com/");
        android_id = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                Random r = new Random();
                Map<String, Double> pos = new HashMap<String, Double>();
                pos.put("x", new Double(r.nextDouble()));
                pos.put("y", new Double(r.nextDouble()));
                fireRef.child(android_id).setValue(pos);
                handler.postDelayed(this, 1000);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIALocationManager.requestLocationUpdates(IALocationRequest.create(), this);
        handler.postDelayed(runnable,1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIALocationManager.removeLocationUpdates(this);
        handler.removeCallbacks(runnable);
    }

    @Override
    protected void onDestroy() {
        mIALocationManager.destroy();
        handler.removeCallbacks(runnable);
        super.onDestroy();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Add listener to firebase ref
        fireRef.addValueEventListener(new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                  mMap.clear();
                  System.out.println("There are " + snapshot.getChildrenCount() + " people connected");
                  int i = 0;
                  for (DataSnapshot postSnapshot: snapshot.getChildren()) {
                      Double x = (Double) postSnapshot.child("x").getValue();
                      Double y = (Double) postSnapshot.child("y").getValue();
                      System.out.println(String.valueOf(x) + " - " + String.valueOf(y));
                      mMap.addMarker(new MarkerOptions().position(new LatLng(x,y))
                                    .icon(BitmapDescriptorFactory.defaultMarker(colors[i])));
                      ++i;
                  }
              }
              @Override
              public void onCancelled(FirebaseError firebaseError) {
                  System.out.println("The read failed: " + firebaseError.getMessage());
              }
        });

        // Start handler to broadcast position
        handler.postDelayed(runnable, 1000);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        mMap.setMyLocationEnabled(true);

        /* Add a marker in Sydney and move the camera
        LatLng sydney = new LatLng(-34, 151);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney)); */
    }

    public void onLocationChanged(IALocation location) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        if (mMarker == null) {
            if (mMap != null) {
                mMarker = mMap.addMarker(new MarkerOptions().position(latLng)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17.0f));
            }
        } else {
            mMarker.setPosition(latLng);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // N/A
    }

}

