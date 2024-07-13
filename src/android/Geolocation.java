package org.apache.cordova.geolocation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.SparseArray;

import androidx.appcompat.app.AlertDialog;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.common.util.ArrayUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
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

import java.util.List;


public class Geolocation extends CordovaPlugin implements OnLocationResultEventListener {

    private SparseArray<LocationContext> locationContexts;
    private FusedLocationProviderClient fusedLocationClientGms;

    private com.huawei.hms.location.FusedLocationProviderClient fusedLocationClientHms;

    protected static final int REQUEST_CHECK_SETTINGS = 0x1;

    public static final String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};


    public enum CDVLocationAuthorizationStatus {
        AUTHORIZED(1),
        NOT_AUTHORIZED(2);

        private final int value;

        CDVLocationAuthorizationStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
    String checkLocationAuthorizationCallbackId;
    CallbackContext checkLocationCallbackContext;
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        locationContexts = new SparseArray<>();
        if(checkGMS()) {
            fusedLocationClientGms = LocationServices.getFusedLocationProviderClient(cordova.getActivity());
        } else if (checkHMS()) {
            fusedLocationClientHms = com.huawei.hms.location.LocationServices.getFusedLocationProviderClient(cordova.getActivity());
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.cordova.getActivity().runOnUiThread(() -> {
            try {
                if ("checkLocationAuthorization".equals(action)){
                    checkLocationAuthorization(callbackContext);
                } else if("getLocation".equals(action)) {
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

    public void checkLocationAuthorization(CallbackContext callbackContext) {
        boolean hasPermission = hasPermission();
        checkLocationAuthorizationCallbackId = callbackContext.getCallbackId();
        checkLocationCallbackContext = callbackContext;
        PluginResult result;
        if (hasPermission){
            result = new PluginResult(PluginResult.Status.OK, hasPermission?1:2);
        } else {
            PermissionHelper.requestPermissions(this, 1, permissions);
            result = new PluginResult(PluginResult.Status.OK, 2);
        }
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);

    }

    /**
     * Decide if getLocation will be executed from GMS services (Google Services) or HMS (Huawei Services)
     *
     * @param args arguments received from execute
     * @param callbackContext the callbackContext
     */
    private void flowGetLocationGmsOrHms(JSONArray args, CallbackContext callbackContext) throws JSONException {
        int id = args.getString(3).hashCode();
        LocationContext locationContext = new LocationContext(id, LocationContext.Type.RETRIEVAL, args, callbackContext, this, checkGMS() ? LocationContext.DeviceType.GOOGLE_SERVICES : LocationContext.DeviceType.HUAWEI_SERVICES);
        locationContexts.put(id, locationContext);

        if (hasPermission()) {
            if (checkGMS()) {
                getLocationGms(locationContext);
            } else if(checkHMS()) {
                getLocationHms(locationContext);
            } else {
                popUpGoogleOrHuaweiServicesAreNotPresent();
            }
        } else {
            PermissionHelper.requestPermissions(this, id, permissions);
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
        LocationContext lc = new LocationContext(id, LocationContext.Type.UPDATE, args, callbackContext, this,  checkGMS() ? LocationContext.DeviceType.GOOGLE_SERVICES : LocationContext.DeviceType.HUAWEI_SERVICES);
        locationContexts.put(id, lc);

        if (hasPermission()) {
            if (checkGMS()) {
                addWatchGms(lc);
            } else if(checkHMS()) {
                addWatchHms(lc);
            } else {
                popUpGoogleOrHuaweiServicesAreNotPresent();
            }
        } else {
            PermissionHelper.requestPermissions(this, id, permissions);
        }
    }

    /**
     * Decide if ClearWatch will be executed from GMS services (Google Services) or HMS (Huawei Services)
     *
     * @param args arguments received from execute
     * @param callbackContext the callbackContext
     */
    private void flowClearWatchGmsOrHms(JSONArray args, CallbackContext callbackContext) {
        String id = args.optString(0);

        if(id != null) {
            int requestId = id.hashCode();
            LocationContext lc = locationContexts.get(requestId);
            PluginResult result;

            if(lc == null) {
                result = new PluginResult(PluginResult.Status.ERROR, LocationError.WATCH_ID_NOT_FOUND.toJSON());
            } else {
                this.locationContexts.delete(requestId);
                if (checkGMS()) {
                    fusedLocationClientGms.removeLocationUpdates(lc.getLocationCallback());
                } else if (checkHMS()) {
                    fusedLocationClientHms.removeLocationUpdates(lc.getLocationCallbackHuawei());
                }
                result = new PluginResult(PluginResult.Status.OK);
            }
            callbackContext.sendPluginResult(result);
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
                if (lc.getType() == LocationContext.Type.UPDATE) {
                    if (checkGMS()) {
                        addWatchGms(lc);
                    } else if (checkHMS()) {
                        addWatchHms(lc);
                    } else {
                        popUpGoogleOrHuaweiServicesAreNotPresent();
                    }
                } else {
                    if (checkGMS()) {
                        getLocationGms(lc);
                    } else if (checkHMS()) {
                        getLocationHms(lc);
                    } else {
                        popUpGoogleOrHuaweiServicesAreNotPresent();
                    }
                }
            }

            if (checkLocationAuthorizationCallbackId != null){
                PluginResult result = new PluginResult(PluginResult.Status.OK, CDVLocationAuthorizationStatus.AUTHORIZED.value);
                result.setKeepCallback(true);
                checkLocationCallbackContext.sendPluginResult(result);
            }
        } else {
            if(lc != null){
                PluginResult result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION, LocationError.LOCATION_PERMISSION_DENIED.toJSON());
                lc.getCallbackContext().sendPluginResult(result);
                locationContexts.delete(lc.getId());
            }
            if (checkLocationAuthorizationCallbackId != null){
                PluginResult result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION, LocationError.LOCATION_PERMISSION_DENIED.toJSON());
                result.setKeepCallback(true);
                checkLocationCallbackContext.sendPluginResult(result);
            }
        }
    }

    /**
     * Get Location from Google Services inside
     * @param locationContext the locationContext
     */
    private void getLocationGms(LocationContext locationContext) {
        JSONArray args = locationContext.getExecuteArgs();
        long timeout = args.optLong(2);
        //Forcing the app to avoid the High Accuracy. To restore the standard plugin behavior, just uncomment the commented line and remove the following line
        //boolean enableHighAccuracy = args.optBoolean(0, false);
        boolean enableHighAccuracy = false;
        LocationRequest request = LocationRequest.create();
        request.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);


        request.setNumUpdates(1);

        // This is necessary to be able to get a response when location services are initially off and then turned on before this request.
        request.setInterval(0);

        //Forcing the app to avoid the High Accuracy. To restore the standard plugin behavior, uncomment the if statement below
        //if(enableHighAccuracy) {
        //    request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        //}

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
        //Forcing the app to avoid the High Accuracy. To restore the standard plugin behavior, just uncomment the commented line and remove the following line
        //boolean enableHighAccuracy = args.optBoolean(0, false);
        boolean enableHighAccuracy = false;
        com.huawei.hms.location.LocationRequest request = com.huawei.hms.location.LocationRequest.create();
        request.setPriority(com.huawei.hms.location.LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        request.setNumUpdates(1);

        // This is necessary to be able to get a response when location services are initially off and then turned on before this request.
        request.setInterval(0);

        //Forcing the app to avoid the High Accuracy. To restore the standard plugin behavior, uncomment the if statement below
        //if(enableHighAccuracy) {
        //    request.setPriority(com.huawei.hms.location.LocationRequest.PRIORITY_HIGH_ACCURACY);
        //}

        if(timeout != 0) {
            request.setExpirationDuration(timeout);
        }

        requestLocationUpdatesIfSettingsSatisfiedHms(locationContext, request);
    }

    /**
     * Add Watch Locations in devices which contains google services
     * @param locationContext the locationContext
     */
    private void addWatchGms(LocationContext locationContext) {
        JSONArray args = locationContext.getExecuteArgs();
        //boolean enableHighAccuracy = args.optBoolean(1, false);
        boolean enableHighAccuracy = false;
        long maximumAge = args.optLong(2, 5000);

        LocationRequest request = LocationRequest.create();
        request.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        request.setInterval(maximumAge);

        //if(enableHighAccuracy) {
        //   request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        //}

        requestLocationUpdatesIfSettingsSatisfiedGms(locationContext, request);
    }

    /**
     * Add Watch Locations in devices which doesn't contains google services (Huawei Devices)
     * @param locationContext the locationContext
     */
    private void addWatchHms(LocationContext locationContext) {
        JSONArray args = locationContext.getExecuteArgs();
        //boolean enableHighAccuracy = args.optBoolean(1, false);
        boolean enableHighAccuracy = false;
        long maximumAge = args.optLong(2, 5000);

        com.huawei.hms.location.LocationRequest request = com.huawei.hms.location.LocationRequest.create();
        request.setPriority(com.huawei.hms.location.LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        request.setInterval(maximumAge);

        //if(enableHighAccuracy) {
        //    request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        //}

        requestLocationUpdatesIfSettingsSatisfiedHms(locationContext, request);
    }

    @SuppressLint("MissingPermission")
    private void requestLocationUpdatesGms(LocationContext locationContext, LocationRequest request) {
        fusedLocationClientGms.requestLocationUpdates(request, locationContext.getLocationCallback(), null);
    }

    @SuppressLint("MissingPermission")
    private void requestLocationUpdatesHms(LocationContext locationContext, com.huawei.hms.location.LocationRequest request) {
        fusedLocationClientHms.requestLocationUpdates(request, locationContext.getLocationCallbackHuawei(),  Looper.getMainLooper());
    }

    @Override
    public void onLocationResultError(LocationContext locationContext, LocationError error) {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR, error.toJSON());

        if (locationContext.getType() == LocationContext.Type.UPDATE) {
            result.setKeepCallback(true);
        } else {
            locationContexts.delete(locationContext.getId());
        }

        locationContext.getCallbackContext().sendPluginResult(result);
    }

    @Override
    public void onLocationResultSuccess(LocationContext locationContext, List<Location> locations) {
        for (Location location : locations) {
            onLocationResult(location, locationContext);
        }
    }

    private void onLocationResult(Location location, LocationContext locationContext) {
        try {
            JSONObject locationObject = LocationUtils.locationToJSON(location);
            PluginResult result = new PluginResult(PluginResult.Status.OK, locationObject);
            setPluginResultData(result, locationContext);
        } catch (JSONException e) {
            PluginResult result = new PluginResult(PluginResult.Status.JSON_EXCEPTION, LocationError.SERIALIZATION_ERROR.toJSON());
            setPluginResultData(result, locationContext);
        }
    }

    private void setPluginResultData(PluginResult result, LocationContext locationContext) {
        if (locationContext.getType() == LocationContext.Type.UPDATE) {
            result.setKeepCallback(true);
        }
        else {
            locationContexts.delete(locationContext.getId());
        }

        locationContext.getCallbackContext().sendPluginResult(result);
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
    private boolean checkGMS() {
        GoogleApiAvailability gApi = GoogleApiAvailability.getInstance();
        int resultCode = gApi.isGooglePlayServicesAvailable(cordova.getActivity());
        return resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS;
    }

    /**
     * Display a dialog to inform the user that should have installed Google or Huawei Services to make it works
     */
    private void popUpGoogleOrHuaweiServicesAreNotPresent() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this.cordova.getContext());
        builder.setTitle("Error!")
                .setMessage("There aren't any Google or Huawei Services!")
                .setCancelable(false)
                .setPositiveButton("Ok", (dialog, id) -> dialog.dismiss());
        AlertDialog alert = builder.create();
        alert.show();
    }
}