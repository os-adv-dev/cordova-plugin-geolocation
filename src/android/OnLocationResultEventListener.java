package org.apache.cordova.geolocation;

import com.google.android.gms.location.LocationResult;

interface OnLocationResultEventListener {
    void onLocationResultSuccess(LocationContext locationContext, LocationResult result);
    void onLocationResultError(LocationContext locationContext, LocationError error);
    void onLocationResultSuccessHuawei(LocationContext locationContext, com.huawei.hms.location.LocationResult result);
}