package com.esj.automationhub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.List;

public final class GeofenceTriggerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError() || event.getGeofenceTransition() != Geofence.GEOFENCE_TRANSITION_ENTER) return;
        List<Geofence> triggering = event.getTriggeringGeofences();
        if (triggering == null) return;
        for (Geofence fence : triggering) {
            try { MainActivity.showGeofenceNotification(context, Long.parseLong(fence.getRequestId())); }
            catch (NumberFormatException ignored) { }
        }
    }
}
