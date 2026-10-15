package com.esj.automationhub;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_AUDIO = 41;
    private static final String PREFS = "automation_hub";
    private static final String ROUTINES_KEY = "routines_v1";
    private static final int BG = 0xFF080A0F;
    private static final int SURFACE = 0xFF11141B;
    private static final int SURFACE_HIGH = 0xFF1A1E28;
    private static final int TEXT = 0xFFF4F1FA;
    private static final int MUTED = 0xFFA7AAB8;
    private static final int PURPLE = 0xFFB48CFF;

    private final ArrayList<Routine> routines = new ArrayList<>();
    private LinearLayout routineList;
    private SpeechRecognizer speechRecognizer;
    private long pendingRoutineId = -1L;
    private boolean listening;

    private static final String[] ACTION_IDS = {
            "wifi", "dnd", "alarm", "location", "bluetooth", "display"
    };
    private static final String[] ACTION_LABELS = {
            "Open Wi-Fi settings", "Open Do Not Disturb settings", "Set a 7:00 AM alarm",
            "Open location settings", "Open Bluetooth settings", "Open display settings"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        loadRoutines();
        buildScreen();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(22), dp(10), dp(22), dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = text("Automation Hub", 29, TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(60), 1));
        TextView settings = text("⚙", 28, TEXT, false);
        settings.setGravity(Gravity.CENTER);
        settings.setContentDescription("Settings and help");
        settings.setBackground(round(SURFACE, dp(18), 0));
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        settings.setOnClickListener(v -> showAbout());
        root.addView(header);

        LinearLayout voiceBar = new LinearLayout(this);
        voiceBar.setGravity(Gravity.CENTER_VERTICAL);
        voiceBar.setOrientation(LinearLayout.HORIZONTAL);
        voiceBar.setPadding(dp(14), 0, dp(12), 0);
        voiceBar.setBackground(round(SURFACE_HIGH, dp(18), 0));
        TextView mic = text("●", 19, PURPLE, true);
        mic.setGravity(Gravity.CENTER);
        voiceBar.addView(mic, new LinearLayout.LayoutParams(dp(32), dp(44)));
        TextView voiceLabel = text("Speak a trigger", 15, TEXT, true);
        voiceBar.addView(voiceLabel, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView voiceHint = text(listening ? "Listening…" : "Tap to listen", 13, MUTED, false);
        voiceHint.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
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
        scroll.setFillViewport(false);
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
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(dp(62), dp(62), Gravity.RIGHT | Gravity.BOTTOM);
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
        nav.setOrientation(LinearLayout.HORIZONTAL);
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
        for (Routine routine : routines) {
            routineList.addView(makeRoutineCard(routine));
        }
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
        top.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(dp(70), dp(70));
        TextView icon = text(routine.icon(), 30, routine.accent(), true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(routine.tileColor(), dp(22), 0));
        top.addView(icon, tileParams);

        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14), 0, dp(4), 0);
        TextView name = text(routine.title, 19, TEXT, true);
        TextView trigger = text(routine.phrase.isEmpty() ? "Tap to activate" : "Say  “" + routine.phrase + "”", 14, MUTED, false);
        trigger.setMaxLines(1);
        trigger.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(name);
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
        taskLine.setOrientation(LinearLayout.HORIZONTAL);
        taskLine.setPadding(dp(84), dp(11), 0, 0);
        TextView dot = text("●", 10, routine.accent(), true);
        TextView task = text(actionLabel(routine.action), 13, MUTED, false);
        task.setMaxLines(1);
        task.setEllipsize(android.text.TextUtils.TruncateAt.END);
        taskLine.addView(dot);
        LinearLayout.LayoutParams taskParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        taskParams.leftMargin = dp(8);
        taskLine.addView(task, taskParams);
        card.addView(taskLine);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(13);
        routineList.addView(card, cardParams);
        card.setOnClickListener(v -> {
            if (routine.phrase.isEmpty()) executeRoutine(routine);
            else startVoiceRecognition(routine);
        });
        card.setContentDescription(routine.title + ". " + (routine.phrase.isEmpty() ? "Tap to activate. " : "Say " + routine.phrase + ". ") + actionLabel(routine.action));
        return card;
    }

    private void showRoutineMenu(View anchor, Routine routine) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Edit").setOnMenuItemClickListener(item -> { showRoutineEditor(routine); return true; });
        menu.getMenu().add("Delete").setOnMenuItemClickListener(item -> {
            routines.remove(routine);
            saveRoutines();
            renderRoutines();
            Toast.makeText(this, "Automation deleted", Toast.LENGTH_SHORT).show();
            return true;
        });
        menu.show();
    }

    private void showRoutineEditor(Routine existing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(10), dp(22), dp(4));
        EditText titleInput = input(existing == null ? "Routine name" : existing.title, "Name your automation");
        EditText phraseInput = input(existing == null ? "" : existing.phrase, "Voice phrase (leave blank for tap)");
        Spinner actionSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ACTION_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actionSpinner.setAdapter(adapter);
        if (existing != null) {
            for (int i = 0; i < ACTION_IDS.length; i++) if (ACTION_IDS[i].equals(existing.action)) actionSpinner.setSelection(i);
        }
        form.addView(titleInput, fieldParams());
        form.addView(phraseInput, fieldParams());
        TextView hint = text("When you say the phrase (or tap the routine), Automation Hub opens the chosen Android screen. Voice is only listened for after you tap.", 13, MUTED, false);
        hint.setPadding(0, dp(2), 0, dp(10));
        form.addView(hint);
        form.addView(actionSpinner, fieldParams());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Create automation" : "Edit automation")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(existing == null ? "Create" : "Save", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String name = titleInput.getText().toString().trim();
            String phrase = phraseInput.getText().toString().trim();
            if (name.isEmpty()) {
                titleInput.setError("Add a name");
                return;
            }
            int selected = actionSpinner.getSelectedItemPosition();
            String action = ACTION_IDS[Math.max(0, selected)];
            if (existing == null) routines.add(new Routine(System.currentTimeMillis(), name, phrase, action));
            else {
                existing.title = name;
                existing.phrase = phrase;
                existing.action = action;
            }
            saveRoutines();
            renderRoutines();
            dialog.dismiss();
            Toast.makeText(this, existing == null ? "Automation created" : "Automation saved", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
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
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
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
                if (matches == null || matches.isEmpty()) return;
                matchAndRun(matches, pendingRoutineId);
            }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, expected == null ? "Say one of your automation phrases" : "Say: " + expected.phrase);
        speechRecognizer.startListening(intent);
    }

    private void matchAndRun(ArrayList<String> matches, long expectedId) {
        for (String match : matches) {
            String spoken = normalize(match);
            for (Routine routine : routines) {
                if (routine.phrase.isEmpty()) continue;
                if (expectedId != -1L && routine.id != expectedId) continue;
                String trigger = normalize(routine.phrase);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Routine expected = null;
            if (pendingRoutineId != -1L) for (Routine r : routines) if (r.id == pendingRoutineId) expected = r;
            startVoiceRecognition(expected);
        } else if (requestCode == REQUEST_AUDIO) {
            Toast.makeText(this, "Microphone access is needed for voice triggers", Toast.LENGTH_LONG).show();
        }
    }

    private void executeRoutine(Routine routine) {
        try {
            Intent intent;
            switch (routine.action) {
                case "wifi":
                    if (android.os.Build.VERSION.SDK_INT >= 29) intent = new Intent(Settings.Panel.ACTION_WIFI);
                    else intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                    break;
                case "dnd":
                    intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    break;
                case "alarm":
                    intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                            .putExtra(AlarmClock.EXTRA_HOUR, 7)
                            .putExtra(AlarmClock.EXTRA_MINUTES, 0)
                            .putExtra(AlarmClock.EXTRA_MESSAGE, routine.title)
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                    break;
                case "location":
                    intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    break;
                case "bluetooth":
                    intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    break;
                case "display":
                    intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
                    break;
                default:
                    Toast.makeText(this, "This action isn't supported", Toast.LENGTH_SHORT).show();
                    return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            Toast.makeText(this, "Android couldn't open that screen on this device", Toast.LENGTH_LONG).show();
        }
    }

    private String actionLabel(String action) {
        for (int i = 0; i < ACTION_IDS.length; i++) if (ACTION_IDS[i].equals(action)) return ACTION_LABELS[i];
        return "Choose a task";
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("Automation Hub")
                .setMessage("Create a routine with a spoken phrase or tap trigger and an Android system action. Voice recognition starts only after you tap Speak a trigger or a voice-triggered routine. This app uses a fixed Material 3 dark palette; it does not use Material You dynamic colors.\n\nAndroid may require confirmation or a permission in the destination screen for some actions. This app opens those screens rather than silently changing protected settings.")
                .setPositiveButton("Got it", null)
                .show();
    }

    private void loadRoutines() {
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(ROUTINES_KEY, null);
        if (saved != null) {
            try {
                JSONArray array = new JSONArray(saved);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.getJSONObject(i);
                    routines.add(new Routine(o.optLong("id", System.currentTimeMillis() + i), o.optString("title"), o.optString("phrase"), o.optString("action", "wifi")));
                }
                return;
            } catch (Exception ignored) { routines.clear(); }
        }
        routines.add(new Routine(1, "Good Morning", "Good Morning", "wifi"));
        routines.add(new Routine(2, "Study Mode", "", "dnd"));
        routines.add(new Routine(3, "Bedtime", "Good Night", "alarm"));
        routines.add(new Routine(4, "Leave Home", "", "location"));
        saveRoutines();
    }

    private void saveRoutines() {
        JSONArray array = new JSONArray();
        for (Routine r : routines) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", r.id);
                o.put("title", r.title);
                o.put("phrase", r.phrase);
                o.put("action", r.action);
                array.put(o);
            } catch (Exception ignored) { }
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(ROUTINES_KEY, array.toString()).apply();
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

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        super.onDestroy();
    }

    private static class Routine {
        final long id;
        String title;
        String phrase;
        String action;
        Routine(long id, String title, String phrase, String action) {
            this.id = id;
            this.title = title;
            this.phrase = phrase;
            this.action = action;
        }
        int accent() {
            if ("wifi".equals(action)) return 0xFFC19BFF;
            if ("dnd".equals(action)) return 0xFFFF5E9F;
            if ("alarm".equals(action)) return 0xFF85B6FF;
            if ("location".equals(action)) return 0xFF71D69D;
            return 0xFF8FD5D0;
        }
        int tileColor() {
            if ("wifi".equals(action)) return 0xFF28213C;
            if ("dnd".equals(action)) return 0xFF38202F;
            if ("alarm".equals(action)) return 0xFF1D2B42;
            if ("location".equals(action)) return 0xFF193427;
            return 0xFF1E3538;
        }
        String icon() {
            if ("wifi".equals(action)) return "♫";
            if ("dnd".equals(action)) return "▦";
            if ("alarm".equals(action)) return "☾";
            if ("location".equals(action)) return "⌂";
            if ("bluetooth".equals(action)) return "ᛒ";
            return "☼";
        }
    }
}
