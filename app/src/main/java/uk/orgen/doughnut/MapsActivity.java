package uk.orgen.doughnut;

import com.squareup.picasso.Target;
import java.util.Map;
import java.util.HashMap;
import java.io.File;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import android.provider.Settings.Secure;
import android.os.Handler;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.indooratlas.android.sdk.IALocation;
import com.indooratlas.android.sdk.IALocationListener;
import com.indooratlas.android.sdk.IALocationManager;
import com.indooratlas.android.sdk.IALocationRequest;
import com.indooratlas.android.sdk.IARegion;
import com.firebase.client.Firebase;
import com.indooratlas.android.sdk.resources.IAFloorPlan;
import com.indooratlas.android.sdk.resources.IALatLng;
import com.indooratlas.android.sdk.resources.IALocationListenerSupport;
import com.indooratlas.android.sdk.resources.IAResourceManager;
import com.indooratlas.android.sdk.resources.IAResult;
import com.indooratlas.android.sdk.resources.IAResultCallback;
import com.indooratlas.android.sdk.resources.IATask;
import com.firebase.client.ValueEventListener;
import com.firebase.client.FirebaseError;
import com.firebase.client.DataSnapshot;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

public class MapsActivity extends FragmentActivity {

    private static final int MAX_DIMENSION = 2048;
    private static final float[] colors = {BitmapDescriptorFactory.HUE_AZURE, BitmapDescriptorFactory.HUE_CYAN,
            BitmapDescriptorFactory.HUE_GREEN, BitmapDescriptorFactory.HUE_MAGENTA,
            BitmapDescriptorFactory.HUE_ORANGE, BitmapDescriptorFactory.HUE_RED,
            BitmapDescriptorFactory.HUE_ROSE, BitmapDescriptorFactory.HUE_VIOLET,
            BitmapDescriptorFactory.HUE_YELLOW};

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private Marker mMarker;
    private IALocationManager mIALocationManager;
    private GroundOverlay mGroundOverlay;
    private IAResourceManager mFloorPlanManager;
    private IATask<IAFloorPlan> mPendingAsyncResult;
    private IAFloorPlan mFloorPlan;
    private long mDownloadId;
    private DownloadManager mDownloadManager;
    private Target mLoadTarget;
    private boolean mCameraPositionNeedsUpdating;
    private IAResourceManager mResourceManager;
    private IATask<IAFloorPlan> mFetchFloorPlanTask;

    private Firebase fireRef;
    private String android_id;
    private String newId;

    private double prevx = 0;
    private double prevy = 0;

    private ValueEventListener listener;

    String TAG = "MapsActivity";

    private IALocationListener mListener = new IALocationListenerSupport() {

        /**
         * Location changed, move marker and camera position.
         */
        @Override
        public void onLocationChanged(IALocation location) {

            Log.d(TAG, "new location received with coordinates: " + location.getLatitude()
                    + "," + location.getLongitude());

            if (mMap == null) {
                // location received before map is initialized, ignoring update here
                return;
            }

            if (location.getLatitude() != prevx || location.getLongitude() != prevy) {

                prevx = location.getLatitude();
                prevy = location.getLongitude();

                Map<String, Double> pos = new HashMap<String, Double>();
                pos.put("x", prevx);
                pos.put("y", prevy);

                fireRef.child(android_id).setValue(pos);
            }

            if (mCameraPositionNeedsUpdating) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(),
                        location.getLongitude()), 17.5f));
                mCameraPositionNeedsUpdating = false;
            }
            fetchFloorPlan(newId);
        }
    };


    /**
     * Region listener that when:
     * <ul>
     * <li>region has entered; marks need to move camera and starts
     * loading floor plan bitmap</li>
     * <li>region has existed; clears marker</li>
     * </ul>.
     */
    private IARegion.Listener mRegionListener = new IARegion.Listener() {

        @Override
        public void onEnterRegion(IARegion region) {

            if (region.getType() == IARegion.TYPE_UNKNOWN) {
                Toast.makeText(MapsActivity.this, "Moved out of map",
                        Toast.LENGTH_LONG).show();
                return;
            }

            // entering new region, mark need to move camera
            mCameraPositionNeedsUpdating = true;

            newId = region.getId();

            Toast.makeText(MapsActivity.this, newId, Toast.LENGTH_SHORT).show();
            fetchFloorPlan(newId);

            // Add listener to firebase ref
            listener = fireRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    mMap.clear();
                    System.out.println("There are " + snapshot.getChildrenCount() + " people connected");
                    int i = 0;
                    for (DataSnapshot postSnapshot: snapshot.getChildren()) {
                        Double x = (Double) postSnapshot.child("x").getValue();
                        Double y = (Double) postSnapshot.child("y").getValue();
                        System.out.println("Position user " + String.valueOf(i) + ": " +
                                                String.valueOf(x) + " - " + String.valueOf(y));
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
        }

        @Override
        public void onExitRegion(IARegion region) {
            if (mMarker != null) {
                mMarker.remove();
                mMarker = null;
            }
            fireRef.removeEventListener(listener);
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        mIALocationManager = IALocationManager.create(this);
        mFloorPlanManager = IAResourceManager.create(this);
        mResourceManager =  IAResourceManager.create(this);

        android_id = Secure.getString(getContentResolver(), Secure.ANDROID_ID);

        if (mMap == null) {
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
        }

        Firebase.setAndroidContext(this);
        fireRef = new Firebase("https://donat.firebaseio.com/");
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
        }

        // start receiving location updates & monitor region changes
        mIALocationManager.requestLocationUpdates(IALocationRequest.create(), mListener);
        mIALocationManager.registerRegionListener(mRegionListener);

		Firebase.setAndroidContext(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mIALocationManager.removeLocationUpdates(mListener);
        mIALocationManager.unregisterRegionListener(mRegionListener);
    }

    @Override
    protected void onDestroy() {
        mIALocationManager.destroy();
        //fireRef.child(android_id).child("x").setValue(null);
        //fireRef.child(android_id).child("y").setValue(null);
        super.onDestroy();
    }

    /**
     * Sets bitmap of floor plan as ground overlay on Google Maps
     */
    private void setupGroundOverlay(IAFloorPlan floorPlan, Bitmap bitmap) {

        if (mGroundOverlay != null) {
            mGroundOverlay.remove();
        }

        if (mMap != null) {
            BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap);
            IALatLng iaLatLng = floorPlan.getCenter();
            LatLng center = new LatLng(iaLatLng.latitude, iaLatLng.longitude);
            GroundOverlayOptions fpOverlay = new GroundOverlayOptions()
                    .image(bitmapDescriptor)
                    .position(center, floorPlan.getWidthMeters(), floorPlan.getHeightMeters())
                    .bearing(floorPlan.getBearing());

            mGroundOverlay = mMap.addGroundOverlay(fpOverlay);
        }
    }

    /**
     * Download floor plan using Picasso library.
     */
    private void fetchFloorPlanBitmap(final IAFloorPlan floorPlan) {

        final String url = floorPlan.getUrl();

        if (mLoadTarget == null) {
            mLoadTarget = new Target() {

                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    Log.d(TAG, "onBitmap loaded with dimensions: " + bitmap.getWidth() + "x"
                            + bitmap.getHeight());
                    setupGroundOverlay(floorPlan, bitmap);
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {
                    // N/A
                }

                @Override
                public void onBitmapFailed(Drawable placeHolderDraweble) {
                    Toast.makeText(MapsActivity.this, "Failed to load bitmap",
                            Toast.LENGTH_SHORT).show();
                }
            };
        }

        RequestCreator request = Picasso.with(this).load(url);

        final int bitmapWidth = floorPlan.getBitmapWidth();
        final int bitmapHeight = floorPlan.getBitmapHeight();

        if (bitmapHeight > MAX_DIMENSION) {
            request.resize(0, MAX_DIMENSION);
        } else if (bitmapWidth > MAX_DIMENSION) {
            request.resize(MAX_DIMENSION, 0);
        }

        request.into(mLoadTarget);
    }


    /**
     * Fetches floor plan data from IndoorAtlas server.
     */
    private void fetchFloorPlan(String id) {

        // if there is already running task, cancel it
        cancelPendingNetworkCalls();

        final IATask<IAFloorPlan> task = mResourceManager.fetchFloorPlanWithId(id);

        task.setCallback(new IAResultCallback<IAFloorPlan>() {

            @Override
            public void onResult(IAResult<IAFloorPlan> result) {

                if (result.isSuccess() && result.getResult() != null) {
                    // retrieve bitmap for this floor plan metadata
                    fetchFloorPlanBitmap(result.getResult());
                } else {
                    // ignore errors if this task was already canceled
                    if (!task.isCancelled()) {
                        // do something with error
                        Toast.makeText(MapsActivity.this,
                                "loading floor plan failed: " + result.getError(), Toast.LENGTH_LONG)
                                .show();
                        // remove current ground overlay
                        if (mGroundOverlay != null) {
                            mGroundOverlay.remove();
                            mGroundOverlay = null;
                        }
                    }
                }
            }
        }, Looper.getMainLooper()); // deliver callbacks using main looper

        // keep reference to task so that it can be canceled if needed
        mFetchFloorPlanTask = task;
    }

    /**
     * Helper method to cancel current task if any.
     */
    private void cancelPendingNetworkCalls() {
        if (mFetchFloorPlanTask != null && !mFetchFloorPlanTask.isCancelled()) {
            mFetchFloorPlanTask.cancel();
        }
    }

}

