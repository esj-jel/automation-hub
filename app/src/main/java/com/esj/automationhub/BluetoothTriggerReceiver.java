package com.esj.automationhub;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Locale;

public final class BluetoothTriggerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) return;
        if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
        BluetoothDevice device;
        try {
            if (Build.VERSION.SDK_INT >= 33) device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
            else device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        } catch (SecurityException e) { return; }
        if (device == null) return;
        String address;
        String name;
        try {
            address = device.getAddress();
            name = device.getName();
        } catch (SecurityException e) { return; }
        for (Routine routine : MainActivity.readRoutines(context)) {
            if (!Routine.BLUETOOTH.equals(routine.trigger)) continue;
            String configured = routine.triggerValue == null ? "" : routine.triggerValue.trim();
            boolean matchesAddress = address != null && configured.equalsIgnoreCase(address);
            boolean matchesName = name != null && configured.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT));
            if (matchesAddress || matchesName) TriggerNotifications.notifyRoutine(context, routine, "Bluetooth device connected: " + (name == null ? "paired device" : name));
        }
    }
}
