package com.esj.automationhub;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int REQUEST_AUDIO = 41;
    private static final int REQUEST_FINE_LOCATION = 42;
    private static final int REQUEST_BLUETOOTH = 43;
    private static final int REQUEST_NOTIFICATIONS = 44;
    private static final int REQUEST_BACKGROUND_LOCATION = 45;
    private static final String PREFS = "automation_hub";
    private static final String ROUTINES_KEY = "routines_v1";
    private static final int BG = 0xFF080A0F;
    private static final int SURFACE = 0xFF11141B;
    private static final int SURFACE_HIGH = 0xFF1A1E28;
    private static final int TEXT = 0xFFF4F1FA;
    private static final int MUTED = 0xFFA7AAB8;
    private static final int PURPLE = 0xFFB48CFF;
    private static final String CHANNEL_ID = "automation_triggers";

    private final ArrayList<Routine> routines = new ArrayList<>();
    private LinearLayout routineList;
    private SpeechRecognizer speechRecognizer;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long pendingRoutineId = -1L;
    private long lastShakeAt;
    private boolean listening;
    private EditText bluetoothPickerInput;
    private static final String[] ACTION_IDS = {"wifi", "dnd", "alarm", "location", "bluetooth", "display"};
    private static final String[] ACTION_LABELS = {
            "Open Wi-Fi settings", "Open Do Not Disturb settings", "Set a 7:00 AM alarm",
            "Open location settings", "Open Bluetooth settings", "Open display settings"
    };
    private static final String[] TRIGGER_IDS = {
            Routine.TAP, Routine.VOICE, Routine.GEOFENCE, Routine.BLUETOOTH, Routine.SHAKE, Routine.DOUBLE_TAP
    };
    private static final String[] TRIGGER_LABELS = {
            "Tap this routine", "Say a voice phrase", "Arrive at a location (geofence)",
            "Paired Bluetooth device connects", "Shake the phone (while app is open)", "Double-tap the header (while app is open)"
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        loadRoutines();
        buildScreen();
        runNotificationRoutine(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        runNotificationRoutine(intent);
    }

    private void runNotificationRoutine(Intent intent) {
        if (intent == null || !intent.hasExtra(TriggerNotifications.EXTRA_ROUTINE_ID)) return;
        long id = intent.getLongExtra(TriggerNotifications.EXTRA_ROUTINE_ID, -1L);
        for (Routine r : routines) if (r.id == id) {
            intent.removeExtra(TriggerNotifications.EXTRA_ROUTINE_ID);
            executeRoutine(r);
            return;
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(22), dp(10), dp(22), dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Automation Hub", 29, TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(60), 1));
        TextView settings = text("⚙", 28, TEXT, false);
        settings.setGravity(Gravity.CENTER);
        settings.setContentDescription("Settings and help");
        settings.setBackground(round(SURFACE, dp(18), 0));
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        settings.setOnClickListener(v -> showAbout());
        GestureDetector headerGestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { activateGesture(Routine.DOUBLE_TAP); return true; }
        });
        header.setClickable(true);
        header.setOnClickListener(v -> { });
        header.setOnTouchListener((v, event) -> {
            boolean handled = headerGestures.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP && handled) v.performClick();
            return handled;
        });
        root.addView(header);

        LinearLayout voiceBar = new LinearLayout(this);
        voiceBar.setGravity(Gravity.CENTER_VERTICAL);
        voiceBar.setPadding(dp(14), 0, dp(12), 0);
        voiceBar.setBackground(round(SURFACE_HIGH, dp(18), 0));
        TextView mic = text("♩", 21, PURPLE, true);
        mic.setGravity(Gravity.CENTER);
        voiceBar.addView(mic, new LinearLayout.LayoutParams(dp(36), dp(44)));
        TextView voiceLabel = text("Speak a trigger", 15, TEXT, true);
        voiceBar.addView(voiceLabel, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView voiceHint = text(listening ? "Listening…" : "Tap to listen", 13, MUTED, false);
        voiceHint.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        voiceBar.addView(voiceHint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        voiceBar.setOnClickListener(v -> startVoiceRecognition(null));
        LinearLayout.LayoutParams voiceParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        voiceParams.topMargin = dp(10);
        root.addView(voiceBar, voiceParams);

        FrameLayout content = new FrameLayout(this);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        contentParams.topMargin = dp(14);
        contentParams.bottomMargin = dp(8);
        root.addView(content, contentParams);
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, dp(3), 0, dp(84));
        TextView intro = text("Your routines", 20, TEXT, true);
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        introParams.bottomMargin = dp(13);
        page.addView(intro, introParams);
        routineList = new LinearLayout(this);
        routineList.setOrientation(LinearLayout.VERTICAL);
        page.addView(routineList);
        scroll.addView(page);
        content.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView add = text("+", 34, 0xFFFFFFFF, false);
        add.setGravity(Gravity.CENTER);
        add.setElevation(dp(8));
        add.setBackground(round(PURPLE, dp(28), 0));
        add.setContentDescription("Create automation");
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(dp(62), dp(62), Gravity.END | Gravity.BOTTOM);
        addParams.setMargins(0, 0, dp(8), dp(8));
        content.addView(add, addParams);
        add.setOnClickListener(v -> showRoutineEditor(null));
        root.addView(buildBottomBar());
        setContentView(root);
        renderRoutines();
    }

    private View buildBottomBar() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(7), dp(8), dp(7));
        nav.setBackground(round(SURFACE, dp(30), 0xFF252933));
        String[][] items = {{"⌂", "Home"}, {"ϟ", "Automations"}, {"▦", "More"}};
        for (int i = 0; i < items.length; i++) {
            final int selected = i;
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER);
            item.setOrientation(LinearLayout.VERTICAL);
            TextView icon = text(items[i][0], 23, i == 0 ? PURPLE : MUTED, true);
            TextView label = text(items[i][1], 12, i == 0 ? PURPLE : MUTED, i == 0);
            item.addView(icon);
            item.addView(label);
            nav.addView(item, new LinearLayout.LayoutParams(0, dp(57), 1));
            item.setOnClickListener(v -> {
                if (selected == 2) showAbout();
                else if (selected == 1) Toast.makeText(this, routines.size() + " automations are ready", Toast.LENGTH_SHORT).show();
                else Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show();
            });
        }
        return nav;
    }

    private void renderRoutines() {
        if (routineList == null) return;
        routineList.removeAllViews();
        for (Routine routine : routines) routineList.addView(makeRoutineCard(routine));
        if (routines.isEmpty()) {
            TextView empty = text("No routines yet. Tap + to create your first automation.", 15, MUTED, false);
            empty.setPadding(dp(18), dp(24), dp(18), dp(24));
            routineList.addView(empty);
        }
    }

    private View makeRoutineCard(Routine routine) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(14), dp(15));
        card.setBackground(round(SURFACE, dp(25), 0xFF242832));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = text(routine.icon(), 30, routine.accent(), true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(routine.tileColor(), dp(22), 0));
        top.addView(icon, new LinearLayout.LayoutParams(dp(70), dp(70)));
        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14), 0, dp(4), 0);
        info.addView(text(routine.title, 19, TEXT, true));
        TextView trigger = text(routine.triggerSummary(), 14, MUTED, false);
        trigger.setMaxLines(2);
        trigger.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams triggerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        triggerParams.topMargin = dp(4);
        info.addView(trigger, triggerParams);
        top.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView more = text("⋮", 24, MUTED, true);
        more.setGravity(Gravity.CENTER);
        more.setContentDescription("Edit or delete " + routine.title);
        top.addView(more, new LinearLayout.LayoutParams(dp(30), dp(52)));
        more.setOnClickListener(v -> showRoutineMenu(more, routine));
        card.addView(top);
        LinearLayout taskLine = new LinearLayout(this);
        taskLine.setGravity(Gravity.CENTER_VERTICAL);
        taskLine.setPadding(dp(84), dp(11), 0, 0);
        TextView dot = text("●", 10, routine.accent(), true);
        TextView task = text(routine.actionLabel(), 13, MUTED, false);
        task.setMaxLines(1);
        task.setEllipsize(android.text.TextUtils.TruncateAt.END);
        taskLine.addView(dot);
        LinearLayout.LayoutParams taskParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        taskParams.leftMargin = dp(8);
        taskLine.addView(task, taskParams);
        card.addView(taskLine);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(13);
        card.setContentDescription(routine.title + ". " + routine.triggerSummary() + ". " + routine.actionLabel());
        card.setOnClickListener(v -> {
            if (Routine.VOICE.equals(routine.trigger)) startVoiceRecognition(routine);
            else if (Routine.TAP.equals(routine.trigger)) executeRoutine(routine);
            else if (Routine.GEOFENCE.equals(routine.trigger)) {
                ensureLocationPermissions();
                Toast.makeText(this, "Geofence is armed when background location is allowed", Toast.LENGTH_LONG).show();
            } else if (Routine.BLUETOOTH.equals(routine.trigger)) {
                ensureBluetoothPermission();
                Toast.makeText(this, "Bluetooth trigger runs when the paired device connects", Toast.LENGTH_LONG).show();
            } else Toast.makeText(this, "Gesture triggers work while Automation Hub is open", Toast.LENGTH_SHORT).show();
        });
        return card;
    }

    private void showRoutineMenu(View anchor, Routine routine) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Edit").setOnMenuItemClickListener(item -> { showRoutineEditor(routine); return true; });
        menu.getMenu().add("Activate now").setOnMenuItemClickListener(item -> { executeRoutine(routine); return true; });
        if (Routine.GEOFENCE.equals(routine.trigger)) {
            menu.getMenu().add("Set up location trigger").setOnMenuItemClickListener(item -> { ensureLocationPermissions(); return true; });
        } else if (Routine.BLUETOOTH.equals(routine.trigger)) {
            menu.getMenu().add("Check Bluetooth permission").setOnMenuItemClickListener(item -> { ensureBluetoothPermission(); return true; });
        }
        menu.getMenu().add("Delete").setOnMenuItemClickListener(item -> {
            routines.remove(routine);
            saveRoutines();
            renderRoutines();
            syncGeofences();
            Toast.makeText(this, "Automation deleted", Toast.LENGTH_SHORT).show();
            return true;
        });
        menu.show();
    }

    @SuppressLint("SetTextI18n")
    private void showRoutineEditor(Routine existing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(10), dp(22), dp(4));
        EditText titleInput = input(existing == null ? "" : existing.title, "Name your automation");
        Spinner triggerSpinner = new Spinner(this);
        ArrayAdapter<String> triggerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, TRIGGER_LABELS);
        triggerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        triggerSpinner.setAdapter(triggerAdapter);
        EditText triggerValueInput = input(existing == null ? "" : existing.triggerValue, "Voice phrase, coordinates or device");
        TextView triggerHint = text("", 12, MUTED, false);
        Button pairedButton = new Button(this);
        pairedButton.setText("Choose a paired Bluetooth device");
        EditText radiusInput = input(existing == null ? "150" : String.valueOf((int) existing.radiusMeters), "Radius in meters");
        Spinner actionSpinner = new Spinner(this);
        ArrayAdapter<String> actionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ACTION_LABELS);
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actionSpinner.setAdapter(actionAdapter);
        if (existing != null) {
            for (int i = 0; i < TRIGGER_IDS.length; i++) if (TRIGGER_IDS[i].equals(existing.trigger)) triggerSpinner.setSelection(i);
            for (int i = 0; i < ACTION_IDS.length; i++) if (ACTION_IDS[i].equals(existing.action)) actionSpinner.setSelection(i);
        }
        form.addView(titleInput, fieldParams());
        form.addView(text("Start when", 13, MUTED, true));
        form.addView(triggerSpinner, fieldParams());
        form.addView(triggerValueInput, fieldParams());
        form.addView(triggerHint);
        form.addView(pairedButton);
        form.addView(radiusInput, fieldParams());
        form.addView(text("Then do", 13, MUTED, true));
        form.addView(actionSpinner, fieldParams());
        Runnable updateTriggerControls = () -> {
            String trigger = TRIGGER_IDS[Math.max(0, triggerSpinner.getSelectedItemPosition())];
            boolean needsValue = Routine.VOICE.equals(trigger) || Routine.GEOFENCE.equals(trigger) || Routine.BLUETOOTH.equals(trigger);
            triggerValueInput.setVisibility(needsValue ? View.VISIBLE : View.GONE);
            pairedButton.setVisibility(Routine.BLUETOOTH.equals(trigger) ? View.VISIBLE : View.GONE);
            radiusInput.setVisibility(Routine.GEOFENCE.equals(trigger) ? View.VISIBLE : View.GONE);
            if (Routine.VOICE.equals(trigger)) {
                triggerValueInput.setHint("Words to recognize (e.g. Good morning)");
                triggerHint.setText("Uses Android's built-in speech recognition only after you tap to start listening.");
            } else if (Routine.GEOFENCE.equals(trigger)) {
                triggerValueInput.setHint("Latitude, longitude (e.g. 37.4219, -122.0840)");
                triggerHint.setText("Enter a center point. Background location and notifications are needed; Android may require Allow all the time.");
            } else if (Routine.BLUETOOTH.equals(trigger)) {
                triggerValueInput.setHint("Paired device name or MAC address");
                triggerHint.setText("Classic Bluetooth connect events are matched to this paired device.");
            } else if (Routine.SHAKE.equals(trigger)) triggerHint.setText("Shake detection is active only while the app is open.");
            else if (Routine.DOUBLE_TAP.equals(trigger)) triggerHint.setText("Double-tap the Automation Hub header while the app is open.");
            else triggerHint.setText("Tap the routine card to run it.");
        };
        triggerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateTriggerControls.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        pairedButton.setOnClickListener(v -> choosePairedBluetoothDevice(triggerValueInput));
        updateTriggerControls.run();
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Create automation" : "Edit automation")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(existing == null ? "Create" : "Save", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String name = titleInput.getText().toString().trim();
            String value = triggerValueInput.getText().toString().trim();
            String trigger = TRIGGER_IDS[Math.max(0, triggerSpinner.getSelectedItemPosition())];
            if (name.isEmpty()) { titleInput.setError("Add a name"); return; }
            double latitude = existing == null ? 0 : existing.latitude;
            double longitude = existing == null ? 0 : existing.longitude;
            float radius = 150f;
            if (Routine.VOICE.equals(trigger) && value.isEmpty()) { triggerValueInput.setError("Add the phrase to listen for"); return; }
            if (Routine.BLUETOOTH.equals(trigger) && value.isEmpty()) { triggerValueInput.setError("Choose or enter a paired device"); return; }
            if (Routine.GEOFENCE.equals(trigger)) {
                String[] coordinates = value.split(",");
                if (coordinates.length != 2) { triggerValueInput.setError("Enter latitude, longitude"); return; }
                try {
                    latitude = Double.parseDouble(coordinates[0].trim());
                    longitude = Double.parseDouble(coordinates[1].trim());
                    radius = Float.parseFloat(radiusInput.getText().toString().trim());
                    if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180 || radius < 50 || radius > 10000) throw new NumberFormatException();
                } catch (NumberFormatException error) { triggerValueInput.setError("Use valid coordinates and a radius from 50 to 10000 m"); return; }
            }
            String action = ACTION_IDS[Math.max(0, actionSpinner.getSelectedItemPosition())];
            if (existing == null) routines.add(new Routine(System.currentTimeMillis(), name, action, trigger, value, latitude, longitude, radius));
            else {
                existing.title = name;
                existing.action = action;
                existing.trigger = trigger;
                existing.triggerValue = value;
                existing.latitude = latitude;
                existing.longitude = longitude;
                existing.radiusMeters = radius;
            }
            saveRoutines();
            renderRoutines();
            dialog.dismiss();
            syncGeofences();
            ensureTriggerSetup(trigger);
            Toast.makeText(this, existing == null ? "Automation created" : "Automation saved", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private void choosePairedBluetoothDevice(EditText target) {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            bluetoothPickerInput = target;
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH);
            return;
        }
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) { Toast.makeText(this, "Bluetooth is not available on this device", Toast.LENGTH_SHORT).show(); return; }
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            ArrayList<BluetoothDevice> devices = new ArrayList<>(bonded);
            Collections.sort(devices, Comparator.comparing(device -> safeBluetoothName(device)));
            if (devices.isEmpty()) { Toast.makeText(this, "Pair a device in Android Bluetooth settings first", Toast.LENGTH_LONG).show(); return; }
            String[] labels = new String[devices.size()];
            for (int i = 0; i < devices.size(); i++) labels[i] = safeBluetoothName(devices.get(i)) + "  ·  " + devices.get(i).getAddress();
            new AlertDialog.Builder(this).setTitle("Choose paired device").setItems(labels, (d, which) -> target.setText(devices.get(which).getAddress())).setNegativeButton("Cancel", null).show();
        } catch (SecurityException e) { Toast.makeText(this, "Allow Nearby devices access to choose a paired device", Toast.LENGTH_LONG).show(); }
    }

    private String safeBluetoothName(BluetoothDevice device) {
        try { return device.getName() == null ? "Bluetooth device" : device.getName(); }
        catch (SecurityException e) { return "Bluetooth device"; }
    }

    private EditText input(String value, String hint) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setText(value);
        edit.setHint(hint);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(MUTED);
        edit.setPadding(dp(12), dp(8), dp(12), dp(8));
        edit.setBackground(round(SURFACE_HIGH, dp(12), 0));
        return edit;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        p.bottomMargin = dp(11);
        return p;
    }

    private void startVoiceRecognition(Routine expected) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "No speech recognition service is available on this device", Toast.LENGTH_LONG).show();
            return;
        }
        pendingRoutineId = expected == null ? -1L : expected.id;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return;
        }
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { listening = true; Toast.makeText(MainActivity.this, "Listening for your trigger…", Toast.LENGTH_SHORT).show(); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { listening = false; }
            @Override public void onError(int error) {
                listening = false;
                String message = error == SpeechRecognizer.ERROR_NO_MATCH ? "I didn't catch a matching phrase" : "Voice input stopped. Tap Speak a trigger to try again.";
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
            @Override public void onResults(Bundle results) {
                listening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) matchAndRun(matches, pendingRoutineId);
            }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, expected == null ? "Say one of your automation phrases" : "Say: " + expected.triggerValue);
        speechRecognizer.startListening(intent);
    }

    private void matchAndRun(ArrayList<String> matches, long expectedId) {
        for (String match : matches) {
            String spoken = normalize(match);
            for (Routine routine : routines) {
                if (!Routine.VOICE.equals(routine.trigger)) continue;
                if (expectedId != -1L && routine.id != expectedId) continue;
                String trigger = normalize(routine.triggerValue);
                if (!trigger.isEmpty() && (spoken.equals(trigger) || spoken.contains(trigger) || trigger.contains(spoken))) {
                    Toast.makeText(this, "Matched “" + routine.title + "”", Toast.LENGTH_SHORT).show();
                    executeRoutine(routine);
                    return;
                }
            }
        }
        Toast.makeText(this, "No matching voice automation. Try saying one of your saved phrases.", Toast.LENGTH_LONG).show();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").trim().replaceAll("\\s+", " ");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQUEST_AUDIO) {
            if (granted) {
                Routine expected = null;
                if (pendingRoutineId != -1L) for (Routine r : routines) if (r.id == pendingRoutineId) expected = r;
                startVoiceRecognition(expected);
            } else Toast.makeText(this, "Microphone access is needed for voice triggers", Toast.LENGTH_LONG).show();
        } else if (requestCode == REQUEST_FINE_LOCATION) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) ensureLocationPermissions();
            else if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this).setTitle("Precise location required")
                        .setMessage("Geofence triggers need Precise location. Change this app's Location permission in Android Settings, then return here.")
                        .setPositiveButton("Open app settings", (d, w) -> openAppSettings()).setNegativeButton("Cancel", null).show();
            } else Toast.makeText(this, "Location permission is needed to set up geofences", Toast.LENGTH_LONG).show();
        } else if (requestCode == REQUEST_BACKGROUND_LOCATION) {
            if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                syncGeofences();
                requestNotificationPermission();
            } else Toast.makeText(this, "Allow all the time location access to enable background geofences", Toast.LENGTH_LONG).show();
        } else if (requestCode == REQUEST_BLUETOOTH) {
            if (granted && bluetoothPickerInput != null) {
                EditText input = bluetoothPickerInput;
                bluetoothPickerInput = null;
                choosePairedBluetoothDevice(input);
            } else if (granted) requestNotificationPermission();
            else Toast.makeText(this, "Nearby devices permission is needed to choose a paired device", Toast.LENGTH_LONG).show();
        } else if (requestCode == REQUEST_NOTIFICATIONS && !granted) {
            Toast.makeText(this, "Android notifications are needed to alert you when a background trigger matches", Toast.LENGTH_LONG).show();
        }
    }

    private void ensureTriggerSetup(String trigger) {
        if (Routine.GEOFENCE.equals(trigger)) {
            ensureLocationPermissions();
        } else if (Routine.BLUETOOTH.equals(trigger)) {
            ensureBluetoothPermission();
            if (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) requestNotificationPermission();
        } else if (Routine.VOICE.equals(trigger) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Tap Speak a trigger to grant microphone access when you're ready", Toast.LENGTH_LONG).show();
        }
    }

    private void ensureBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH);
        }
    }

    private void ensureLocationPermissions() {
        if (Build.VERSION.SDK_INT < 29) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_FINE_LOCATION);
            else { syncGeofences(); requestNotificationPermission(); }
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_FINE_LOCATION);
            return;
        }
        if (Build.VERSION.SDK_INT >= 30 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setTitle("Allow background location")
                    .setMessage("For arrival automations to work when Automation Hub is closed, set Location access to ‘Allow all the time’ in Android app permissions. You can change it later in system Settings.")
                    .setNegativeButton("Not now", null)
                    .setPositiveButton("Open app settings", (d, w) -> openAppSettings())
                    .show();
            return;
        }
        if (Build.VERSION.SDK_INT == 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQUEST_BACKGROUND_LOCATION);
            return;
        }
        syncGeofences();
        requestNotificationPermission();
    }

    private void openAppSettings() {
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        catch (ActivityNotFoundException e) { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
    }

    private boolean hasBackgroundLocation() {
        return Build.VERSION.SDK_INT < 29
                || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void syncGeofences() {
        TriggerNotifications.registerGeofences(this, false);
    }

    private void activateGesture(String type) {
        for (Routine routine : routines) if (type.equals(routine.trigger)) { executeRoutine(routine); return; }
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float x = event.values[0], y = event.values[1], z = event.values[2];
        double magnitude = Math.sqrt(x * x + y * y + z * z);
        long now = System.currentTimeMillis();
        if (magnitude > 18.0 && now - lastShakeAt > 1800) {
            lastShakeAt = now;
            activateGesture(Routine.SHAKE);
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private void executeRoutine(Routine routine) {
        try {
            Intent intent;
            switch (routine.action) {
                case "wifi": intent = Build.VERSION.SDK_INT >= 29 ? new Intent(Settings.Panel.ACTION_WIFI) : new Intent(Settings.ACTION_WIFI_SETTINGS); break;
                case "dnd": intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS); break;
                case "alarm": intent = new Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, 7)
                        .putExtra(AlarmClock.EXTRA_MINUTES, 0).putExtra(AlarmClock.EXTRA_MESSAGE, routine.title)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, false); break;
                case "location": intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS); break;
                case "bluetooth": intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS); break;
                case "display": intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS); break;
                default: Toast.makeText(this, "This action isn't supported", Toast.LENGTH_SHORT).show(); return;
            }
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            Toast.makeText(this, "Android couldn't open that screen on this device", Toast.LENGTH_LONG).show();
        }
    }

    private void showAbout() {
        new AlertDialog.Builder(this).setTitle("Automation Hub")
                .setMessage("Create routines that run Android system tasks from a tap, a spoken phrase, a geofence arrival, a paired Bluetooth connection, or an in-app gesture. Voice recognition starts only after you tap to listen. Shake and double-tap triggers work while this app is open. Geofence and Bluetooth triggers alert you with a notification; tap it to run the task.\n\nThis interface uses Material 3 with a fixed dark palette. Material You dynamic color extraction is not enabled. Android keeps control of protected settings and may ask you to confirm changes in the system screen.")
                .setPositiveButton("Got it", null).show();
    }

    private void loadRoutines() {
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(ROUTINES_KEY, null);
        if (saved != null) {
            try {
                JSONArray array = new JSONArray(saved);
                for (int i = 0; i < array.length(); i++) routines.add(Routine.fromJson(array.getJSONObject(i), i));
                return;
            } catch (Exception ignored) { routines.clear(); }
        }
        routines.add(new Routine(1, "Good Morning", "wifi", Routine.VOICE, "Good Morning", 0, 0, 150));
        routines.add(new Routine(2, "Study Mode", "dnd", Routine.TAP, "", 0, 0, 150));
        routines.add(new Routine(3, "Bedtime", "alarm", Routine.VOICE, "Good Night", 0, 0, 150));
        routines.add(new Routine(4, "Leave Home", "location", Routine.TAP, "", 0, 0, 150));
        saveRoutines();
    }

    private void saveRoutines() {
        JSONArray array = new JSONArray();
        for (Routine r : routines) array.put(r.toJson());
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(ROUTINES_KEY, array.toString()).apply();
    }

    static ArrayList<Routine> readRoutines(android.content.Context context) {
        ArrayList<Routine> result = new ArrayList<>();
        String saved = context.getSharedPreferences(PREFS, MODE_PRIVATE).getString(ROUTINES_KEY, null);
        if (saved == null) return result;
        try {
            JSONArray array = new JSONArray(saved);
            for (int i = 0; i < array.length(); i++) result.add(Routine.fromJson(array.getJSONObject(i), i));
        } catch (Exception ignored) { }
        return result;
    }

    static void showGeofenceNotification(android.content.Context context, long routineId) {
        for (Routine routine : readRoutines(context)) if (routine.id == routineId) {
            TriggerNotifications.notifyRoutine(context, routine, "You arrived at the saved location");
            return;
        }
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        if (stroke != 0) shape.setStroke(dp(1), stroke);
        return shape;
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        if (routines.stream().anyMatch(r -> Routine.GEOFENCE.equals(r.trigger)) && hasBackgroundLocation()
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            syncGeofences();
            requestNotificationPermission();
        } else if (routines.stream().anyMatch(r -> Routine.GEOFENCE.equals(r.trigger))) {
            TriggerNotifications.invalidateGeofenceRegistration(this);
        }
    }

    @Override protected void onPause() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
        super.onDestroy();
    }
}
