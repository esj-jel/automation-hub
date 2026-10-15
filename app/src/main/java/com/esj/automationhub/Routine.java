package com.esj.automationhub;

import org.json.JSONObject;

final class Routine {
    static final String TAP = "tap";
    static final String VOICE = "voice";
    static final String GEOFENCE = "geofence";
    static final String BLUETOOTH = "bluetooth";
    static final String SHAKE = "shake";
    static final String DOUBLE_TAP = "double_tap";

    final long id;
    String title;
    String action;
    String trigger;
    String triggerValue;
    double latitude;
    double longitude;
    float radiusMeters;

    Routine(long id, String title, String action, String trigger, String triggerValue,
            double latitude, double longitude, float radiusMeters) {
        this.id = id;
        this.title = title;
        this.action = action;
        this.trigger = trigger;
        this.triggerValue = triggerValue;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters;
    }

    static Routine fromJson(JSONObject o, int index) {
        String oldPhrase = o.optString("phrase", "");
        String trigger = o.optString("trigger", oldPhrase.isEmpty() ? TAP : VOICE);
        String value = o.optString("triggerValue", oldPhrase);
        return new Routine(o.optLong("id", System.currentTimeMillis() + index),
                o.optString("title", "Automation"), o.optString("action", "wifi"), trigger,
                value, o.optDouble("latitude", 0), o.optDouble("longitude", 0),
                (float) o.optDouble("radiusMeters", 150));
    }

    JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("title", title);
            o.put("action", action);
            o.put("trigger", trigger);
            o.put("triggerValue", triggerValue);
            // Keep the legacy field for compatibility with prior app builds.
            o.put("phrase", VOICE.equals(trigger) ? triggerValue : "");
            o.put("latitude", latitude);
            o.put("longitude", longitude);
            o.put("radiusMeters", radiusMeters);
        } catch (Exception ignored) { }
        return o;
    }

    String triggerSummary() {
        switch (trigger) {
            case VOICE: return triggerValue.isEmpty() ? "Say a phrase" : "Say “" + triggerValue + "”";
            case GEOFENCE: return String.format(java.util.Locale.getDefault(), "Arrive · %.0fm radius", radiusMeters);
            case BLUETOOTH: return triggerValue.isEmpty() ? "Bluetooth device connects" : "When “" + triggerValue + "” connects";
            case SHAKE: return "Shake phone · app open";
            case DOUBLE_TAP: return "Double-tap the header · app open";
            default: return "Tap to activate";
        }
    }

    String actionLabel() {
        switch (action) {
            case "wifi": return "Wi-Fi settings";
            case "dnd": return "Do Not Disturb settings";
            case "alarm": return "Set a 7:00 AM alarm";
            case "location": return "Location settings";
            case "bluetooth": return "Bluetooth settings";
            case "display": return "Display settings";
            default: return "Choose a task";
        }
    }

    int accent() {
        switch (action) {
            case "wifi": return 0xFFC19BFF;
            case "dnd": return 0xFFFF5E9F;
            case "alarm": return 0xFF85B6FF;
            case "location": return 0xFF71D69D;
            default: return 0xFF8FD5D0;
        }
    }

    int tileColor() {
        switch (action) {
            case "wifi": return 0xFF28213C;
            case "dnd": return 0xFF38202F;
            case "alarm": return 0xFF1D2B42;
            case "location": return 0xFF193427;
            default: return 0xFF1E3538;
        }
    }

    String icon() {
        switch (action) {
            case "wifi": return "⌁";
            case "dnd": return "▦";
            case "alarm": return "☾";
            case "location": return "⌂";
            case "bluetooth": return "ᛒ";
            default: return "☼";
        }
    }
}
