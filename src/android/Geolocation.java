package org.apache.cordova.geolocation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.SparseArray;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.common.util.ArrayUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.huawei.hms.api.HuaweiApiAvailability;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class Geolocation extends CordovaPlugin implements OnLocationResultEventListener {

    private SparseArray<LocationContext> locationContexts;
    private FusedLocationProviderClient fusedLocationClientGms;

    private com.huawei.hms.location.FusedLocationProviderClient fusedLocationProviderClientHms;

    protected static final int REQUEST_CHECK_SETTINGS = 0x1;

    public static final String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        locationContexts = new SparseArray<>();
        if(checkGMS()) {
            fusedLocationClientGms = LocationServices.getFusedLocationProviderClient(cordova.getActivity());
        } else if (checkHMS()) {
            fusedLocationProviderClientHms = com.huawei.hms.location.LocationServices.getFusedLocationProviderClient(cordova.getActivity());
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.cordova.getActivity().runOnUiThread(() -> {
            try {
                if ("getLocation".equals(action)) {
                    flowGetLocationGmsOrHms(args, callbackContext);
                } else if ("addWatch".equals(action)) {
                    flowAddWatchGmsOrHms(args, callbackContext);
                } else if ("clearWatch".equals(action)) {
                    flowClearWatchGmsOrHms(args, callbackContext);
                }
            } catch (Exception ex) {
                callbackContext.error(ex.getMessage());
            }
        });

        return true;
    }

    /**
     * Decide if getLocation will be executed from GMS services (Google Services) or HMS (Huawei Services)
     *
     * @param args arguments received from execute
     * @param callbackContext the callbackContext
     */
    private void flowGetLocationGmsOrHms(JSONArray args, CallbackContext callbackContext) throws JSONException {
        int id = args.getString(3).hashCode();

        if (checkGMS()) {
            LocationContext lc = new LocationContext(id, LocationContext.Type.RETRIEVAL, args, callbackContext, this, LocationContext.DeviceType.GOOGLE_SERVICES);
            locationContexts.put(id, lc);
            if (hasPermission()) {
                getLocationGms(lc);
            } else {
                PermissionHelper.requestPermissions(this, id, permissions);
            }
        } else if (checkHMS()) {
            LocationContext lc = new LocationContext(id, LocationContext.Type.RETRIEVAL, args, callbackContext, this, LocationContext.DeviceType.HUAWEI_SERVICES);
            locationContexts.put(id, lc);
            if (hasPermission()) {
                getLocationHms(lc);
            } else {
                PermissionHelper.requestPermissions(this, id, permissions);
            }
        }
    }

    /**
     * Decide if AddWatch will be executed from GMS services (Google Services) or HMS (Huawei Services)
     *
     * @param args arguments received from execute
     * @param callbackContext the callbackContext
     */
    private void flowAddWatchGmsOrHms(JSONArray args, CallbackContext callbackContext) throws JSONException {
        int id = args.getString(0).hashCode();

        if (checkGMS()) {
            LocationContext lc = new LocationContext(id, LocationContext.Type.UPDATE, args, callbackContext, this, LocationContext.DeviceType.GOOGLE_SERVICES);
            locationContexts.put(id, lc);
            if (hasPermission()) {
                addWatch(lc);
            } else {
                PermissionHelper.requestPermissions(this, id, permissions);
            }
        } else if (checkHMS()) {
            LocationContext lc = new LocationContext(id, LocationContext.Type.UPDATE, args, callbackContext, this, LocationContext.DeviceType.HUAWEI_SERVICES);
            locationContexts.put(id, lc);
            if (hasPermission()) {
                addWatchHuawei(lc);
            } else {
                PermissionHelper.requestPermissions(this, id, permissions);
            }
        }
    }

    /**
     * Decide if ClearWatch will be executed from GMS services (Google Services) or HMS (Huawei Services)
     *
     * @param args arguments received from execute
     * @param callbackContext the callbackContext
     */
    private void flowClearWatchGmsOrHms(JSONArray args, CallbackContext callbackContext) {
        if (checkGMS()) {
            clearWatch(args, callbackContext);
        } else if (checkHMS()) {
            clearWatchHMS(args, callbackContext);
        }
    }

    private boolean hasPermission() {
        for (String permission : permissions) {
            if (!PermissionHelper.hasPermission(this, permission)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        // In case a permission request is cancelled, the permissions and grantResults arrays are empty.
        // We must exit immediately to avoid calling getLocation erroneously.
        if(permissions == null || permissions.length == 0) {
            return;
        }

        LocationContext lc = locationContexts.get(requestCode);

        //if we are granted either ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION
        if (ArrayUtils.contains(grantResults, PackageManager.PERMISSION_GRANTED)) {
            if (lc != null) {
                switch (lc.getType()) {
                    case UPDATE:
                        if(checkGMS()) {
                            addWatch(lc);
                        } else if (checkHMS()) {
                            addWatchHuawei(lc);
                        }
                        break;
                    default:
                        if(checkGMS()) {
                            getLocationGms(lc);
                        } else if (checkHMS()) {
                            getLocationHms(lc);
                        }
                        break;
                }
            }
        } else {
            if(lc != null){
                PluginResult result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION, LocationError.LOCATION_PERMISSION_DENIED.toJSON());
                lc.getCallbackContext().sendPluginResult(result);
                locationContexts.delete(lc.getId());
            }
        }
    }

    private void getLocationGms(LocationContext locationContext) {
        JSONArray args = locationContext.getExecuteArgs();
        long timeout = args.optLong(2);
        boolean enableHighAccuracy = args.optBoolean(0, false);
        LocationRequest request = LocationRequest.create();

        request.setNumUpdates(1);

        // This is necessary to be able to get a response when location services are initially off and then turned on before this request.
        request.setInterval(0);

        if(enableHighAccuracy) {
            request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        }

        if(timeout != 0) {
            request.setExpirationDuration(timeout);
        }

        requestLocationUpdatesIfSettingsSatisfiedGms(locationContext, request);
    }

    /**
     * Get Location from Huawei Devices / No Google Services inside
     * @param locationContext the locationContext
     */
    private void getLocationHms(LocationContext locationContext) {
        JSONArray args = locationContext.getExecuteArgs();
        long timeout = args.optLong(2);
        boolean enableHighAccuracy = args.optBoolean(0, false);
        com.huawei.hms.location.LocationRequest request = com.huawei.hms.location.LocationRequest.create();

        request.setNumUpdates(1);

        // This is necessary to be able to get a response when location services are initially off and then turned on before this request.
        request.setInterval(0);

        if(enableHighAccuracy) {
            request.setPriority(com.huawei.hms.location.LocationRequest.PRIORITY_HIGH_ACCURACY);
        }

        if(timeout != 0) {
            request.setExpirationDuration(timeout);
        }

        requestLocationUpdatesIfSettingsSatisfiedHms(locationContext, request);
    }

    /**
     * Add Watch Locations in devices which contains google services
     * @param locationContext the locationContext
     */
    private void addWatch(LocationContext locationContext) {
        JSONArray args = locationContext.getExecuteArgs();
        boolean enableHighAccuracy = args.optBoolean(1, false);
        long maximumAge = args.optLong(2, 5000);

        LocationRequest request = LocationRequest.create();

        request.setInterval(maximumAge);

        if(enableHighAccuracy) {
            request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        }

        requestLocationUpdatesIfSettingsSatisfiedGms(locationContext, request);
    }

    /**
     * Add Watch Locations in devices which doesn't contains google services (Huawei Devices)
     * @param locationContext the locationContext
     */
    private void addWatchHuawei(LocationContext locationContext) {
        JSONArray args = locationContext.getExecuteArgs();
        boolean enableHighAccuracy = args.optBoolean(1, false);
        long maximumAge = args.optLong(2, 5000);

        com.huawei.hms.location.LocationRequest request = com.huawei.hms.location.LocationRequest.create();

        request.setInterval(maximumAge);

        if(enableHighAccuracy) {
            request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        }

        requestLocationUpdatesIfSettingsSatisfiedHms(locationContext, request);
    }

    @SuppressLint("MissingPermission")
    private void requestLocationUpdatesGms(LocationContext locationContext, LocationRequest request) {
        fusedLocationClientGms.requestLocationUpdates(request, locationContext.getLocationCallback(), null);
    }

    @SuppressLint("MissingPermission")
    private void requestLocationUpdatesHms(LocationContext locationContext, com.huawei.hms.location.LocationRequest request) {
        if (fusedLocationProviderClientHms != null) {
            fusedLocationProviderClientHms.requestLocationUpdates(request, locationContext.getLocationCallbackHuawei(),  Looper.getMainLooper());
        }
    }

    /**
     * Clear Watch Locations in devices which contains google services
     * @param callbackContext the callbackContext
     */
    private void clearWatch(JSONArray args, CallbackContext callbackContext) {
        String id = args.optString(0);

        if(id != null) {
            int requestId = id.hashCode();
            LocationContext lc = locationContexts.get(requestId);

            if(lc == null) {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, LocationError.WATCH_ID_NOT_FOUND.toJSON());
                callbackContext.sendPluginResult(result);
            }
            else {
                this.locationContexts.delete(requestId);
                fusedLocationClientGms.removeLocationUpdates(lc.getLocationCallback());

                PluginResult result = new PluginResult(PluginResult.Status.OK);
                callbackContext.sendPluginResult(result);
            }
        }
    }

    /**
     * Clear Watch Locations in devices which doesn't contains google services (Huawei Devices)
     * @param callbackContext the callbackContext
     */
    private void clearWatchHMS(JSONArray args, CallbackContext callbackContext) {
        String id = args.optString(0);

        if(id != null) {
            int requestId = id.hashCode();
            LocationContext lc = locationContexts.get(requestId);

            if(lc == null) {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, LocationError.WATCH_ID_NOT_FOUND.toJSON());
                callbackContext.sendPluginResult(result);
            }
            else {
                this.locationContexts.delete(requestId);
                fusedLocationProviderClientHms.removeLocationUpdates(lc.getLocationCallbackHuawei());

                PluginResult result = new PluginResult(PluginResult.Status.OK);
                callbackContext.sendPluginResult(result);
            }
        }
    }

    @Override
    public void onLocationResultSuccess(LocationContext locationContext, LocationResult locationResult) {
        for (Location location : locationResult.getLocations()) {
            try {
                JSONObject locationObject = LocationUtils.locationToJSON(location);
                PluginResult result = new PluginResult(PluginResult.Status.OK, locationObject);

                if (locationContext.getType() == LocationContext.Type.UPDATE) {
                    result.setKeepCallback(true);
                }
                else {
                    locationContexts.delete(locationContext.getId());
                }

                locationContext.getCallbackContext().sendPluginResult(result);

            } catch (JSONException e) {
                PluginResult result = new PluginResult(PluginResult.Status.JSON_EXCEPTION, LocationError.SERIALIZATION_ERROR.toJSON());

                if (locationContext.getType() == LocationContext.Type.UPDATE) {
                    result.setKeepCallback(true);
                }
                else {
                    locationContexts.delete(locationContext.getId());
                }

                locationContext.getCallbackContext().sendPluginResult(result);
            }
        }
    }

    @Override
    public void onLocationResultError(LocationContext locationContext, LocationError error) {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR, error.toJSON());

        if (locationContext.getType() == LocationContext.Type.UPDATE) {
            result.setKeepCallback(true);
        }
        else {
            locationContexts.delete(locationContext.getId());
        }

        locationContext.getCallbackContext().sendPluginResult(result);
    }

    @Override
    public void onLocationResultSuccessHuawei(LocationContext locationContext, com.huawei.hms.location.LocationResult locationResult) {
        for (Location location : locationResult.getLocations()) {
            try {
                JSONObject locationObject = LocationUtils.locationToJSON(location);
                PluginResult result = new PluginResult(PluginResult.Status.OK, locationObject);

                if (locationContext.getType() == LocationContext.Type.UPDATE) {
                    result.setKeepCallback(true);
                }
                else {
                    locationContexts.delete(locationContext.getId());
                }

                locationContext.getCallbackContext().sendPluginResult(result);

            } catch (JSONException e) {
                PluginResult result = new PluginResult(PluginResult.Status.JSON_EXCEPTION, LocationError.SERIALIZATION_ERROR.toJSON());
                if (locationContext.getType() == LocationContext.Type.UPDATE) {
                    result.setKeepCallback(true);
                }
                else {
                    locationContexts.delete(locationContext.getId());
                }

                locationContext.getCallbackContext().sendPluginResult(result);
            }
        }
    }

    private void requestLocationUpdatesIfSettingsSatisfiedGms(final LocationContext locationContext, final LocationRequest request) {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(request);
        SettingsClient client = LocationServices.getSettingsClient(cordova.getActivity());
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        OnSuccessListener<LocationSettingsResponse> checkLocationSettingsOnSuccess = locationSettingsResponse -> {
            // All location settings are satisfied. The client can initialize location requests here.
            requestLocationUpdatesGms(locationContext, request);
        };

        OnFailureListener checkLocationSettingsOnFailure = e -> {
            PluginResult result;
            if (e instanceof ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult(). We should do this but it is not working
                    // so for now we simply call for location updates directly, after presenting the dialog
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    resolvable.startResolutionForResult(cordova.getActivity(),
                            REQUEST_CHECK_SETTINGS);
                    requestLocationUpdatesGms(locationContext, request);
                } catch (IntentSender.SendIntentException sendEx) {
                    // Ignore the error.
                }
            }
            else {
                result = new PluginResult(PluginResult.Status.ERROR, LocationError.LOCATION_SETTINGS_ERROR.toJSON());
                locationContext.getCallbackContext().sendPluginResult(result);
                locationContexts.remove(locationContext.getId());
            }
        };

        task.addOnSuccessListener(checkLocationSettingsOnSuccess);
        task.addOnFailureListener(checkLocationSettingsOnFailure);
    }

    private void requestLocationUpdatesIfSettingsSatisfiedHms(final LocationContext locationContext, final com.huawei.hms.location.LocationRequest request) {
        com.huawei.hms.location.LocationSettingsRequest.Builder builder = new com.huawei.hms.location.LocationSettingsRequest.Builder();
        builder.addLocationRequest(request);
        com.huawei.hms.location.SettingsClient client = com.huawei.hms.location.LocationServices.getSettingsClient(cordova.getActivity());
        com.huawei.hmf.tasks.Task<com.huawei.hms.location.LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        com.huawei.hmf.tasks.OnSuccessListener<com.huawei.hms.location.LocationSettingsResponse> checkLocationSettingsOnSuccess = new com.huawei.hmf.tasks.OnSuccessListener<com.huawei.hms.location.LocationSettingsResponse>() {
            @Override
            public void onSuccess(com.huawei.hms.location.LocationSettingsResponse locationSettingsResponse) {
                // All location settings are satisfied. The client can initialize location requests here.
                requestLocationUpdatesHms(locationContext, request);
            }
        };

        com.huawei.hmf.tasks.OnFailureListener checkLocationSettingsOnFailure = e -> {
            PluginResult result;
            if (e instanceof com.huawei.hms.common.ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult(). We should do this but it is not working
                    // so for now we simply call for location updates directly, after presenting the dialog
                    com.huawei.hms.common.ResolvableApiException resolvable = (com.huawei.hms.common.ResolvableApiException) e;
                    resolvable.startResolutionForResult(cordova.getActivity(),
                            REQUEST_CHECK_SETTINGS);
                    requestLocationUpdatesHms(locationContext, request);
                } catch (IntentSender.SendIntentException sendEx) {
                    // Ignore the error.
                }
            }
            else {
                result = new PluginResult(PluginResult.Status.ERROR, LocationError.LOCATION_SETTINGS_ERROR.toJSON());
                locationContext.getCallbackContext().sendPluginResult(result);
                locationContexts.remove(locationContext.getId());
            }
        };

        task.addOnSuccessListener(checkLocationSettingsOnSuccess);
        task.addOnFailureListener(checkLocationSettingsOnFailure);
    }

    /**
     * Check if we are running in devices with Huawei Services
     * @return true if contains, otherwise false
     */
    private boolean checkHMS() {
        HuaweiApiAvailability hApi = HuaweiApiAvailability.getInstance();
        int resultCode = hApi.isHuaweiMobileServicesAvailable(cordova.getActivity());
        return resultCode == com.huawei.hms.api.ConnectionResult.SUCCESS;
    }

    /**
     * Check if we are running in devices with Google Services
     * @return true if contains, otherwise false
     */
    private  boolean checkGMS() {
        GoogleApiAvailability gApi = GoogleApiAvailability.getInstance();
        int resultCode = gApi.isGooglePlayServicesAvailable(cordova.getActivity());
        return resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS;
    }
}