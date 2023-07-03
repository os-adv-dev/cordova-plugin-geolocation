package org.apache.cordova.geolocation;

import android.location.Location;


import java.util.List;

interface OnLocationResultEventListener {
    void onLocationResultSuccess(LocationContext locationContext, List<Location> locations);
    void onLocationResultError(LocationContext locationContext, LocationError error);
}