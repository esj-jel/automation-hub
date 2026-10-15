package com.esj.automationhub;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;

final class TriggerNotifications {
    static final String EXTRA_ROUTINE_ID = "automation_hub_routine_id";
    static final String CHANNEL_ID = "automation_triggers";

    private TriggerNotifications() { }

    static PendingIntent geofencePendingIntent(Context context) {
        Intent intent = new Intent(context, GeofenceTriggerReceiver.class);
        return PendingIntent.getBroadcast(context, 2601, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void registerGeofences(Context context) { registerGeofences(context, false); }

    static void registerGeofences(Context context, boolean force) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        if (Build.VERSION.SDK_INT >= 29 && context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        android.content.SharedPreferences registration = context.getSharedPreferences("automation_hub_geofences", Context.MODE_PRIVATE);
        ArrayList<Geofence> fences = new ArrayList<>();
        StringBuilder signature = new StringBuilder();
        for (Routine routine : MainActivity.readRoutines(context)) {
            if (!Routine.GEOFENCE.equals(routine.trigger)) continue;
            signature.append(routine.id).append(':').append(routine.latitude).append(':').append(routine.longitude).append(':').append(routine.radiusMeters).append(';');
            fences.add(new Geofence.Builder().setRequestId(String.valueOf(routine.id))
                    .setCircularRegion(routine.latitude, routine.longitude, routine.radiusMeters)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER).build());
        }
        String fingerprint = signature.toString();
        if (!force && fingerprint.equals(registration.getString("signature", null))) return;
        com.google.android.gms.location.GeofencingClient client = LocationServices.getGeofencingClient(context);
        PendingIntent pendingIntent = geofencePendingIntent(context);
        client.removeGeofences(pendingIntent).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) return;
            if (fences.isEmpty()) {
                registration.edit().putString("signature", fingerprint).apply();
                return;
            }
            GeofencingRequest request = new GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER).addGeofences(fences).build();
            try {
                client.addGeofences(request, pendingIntent)
                        .addOnSuccessListener(ignored -> registration.edit().putString("signature", fingerprint).apply())
                        .addOnFailureListener(ignored -> registration.edit().remove("signature").apply());
            } catch (SecurityException ignored) { registration.edit().remove("signature").apply(); }
        });
    }

    static void invalidateGeofenceRegistration(Context context) {
        context.getSharedPreferences("automation_hub_geofences", Context.MODE_PRIVATE).edit().remove("signature").apply();
    }

    static void notifyRoutine(Context context, Routine routine, String detail) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Automation triggers", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Alerts when a background routine trigger matches");
            manager.createNotificationChannel(channel);
        }
        Intent launch = new Intent(context, MainActivity.class)
                .putExtra(EXTRA_ROUTINE_ID, routine.id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(context, (int) (routine.id & 0x7fffffff), launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(routine.title)
                .setContentText(detail + " · Tap to run " + routine.actionLabel())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(detail + ". Tap this notification to open the Android screen for " + routine.actionLabel() + "."))
                .setContentIntent(content)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        try { NotificationManagerCompat.from(context).notify((int) (routine.id & 0x7fffffff), notification); }
        catch (SecurityException ignored) { }
    }
}
