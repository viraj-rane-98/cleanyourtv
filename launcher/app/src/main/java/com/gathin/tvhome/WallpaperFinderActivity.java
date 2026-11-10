package com.gathin.tvhome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Searches Wallhaven and applies the pictures as the private wallpaper overlay. */
public final class WallpaperFinderActivity extends Activity {
    private static final String TAG = "TvHome.WallpaperFinder";
    private static final String PREFS = "wallpaper_finder";
    private static final String RECENT = "recent";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private EditText query;
    private TextView status;
    private LinearLayout recent;
    private int generation;
    private boolean busy;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_wallpaper_finder);
        query = findViewById(R.id.finderQuery);
        status = findViewById(R.id.finderStatus);
        recent = findViewById(R.id.finderRecent);
        query.setOnEditorActionListener((v, actionId, event) -> {
            if (!searchAction(actionId, event)) return false;
            runSearch(query.getText().toString());
            return true;
        });
        findViewById(R.id.finderRestore).setOnClickListener(v -> restore());
        showRecent();
        query.requestFocus();
    }

    @Override
    protected void onDestroy() {
        generation++;
        worker.shutdownNow();
        super.onDestroy();
    }

    private static boolean searchAction(int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO
                || actionId == EditorInfo.IME_ACTION_DONE) return true;
        return event != null && event.getAction() == KeyEvent.ACTION_DOWN
                && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_SEARCH);
    }

    private void runSearch(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || busy) return;
        busy = true;
        int gen = ++generation;
        List<String> names = RecentSearches.remember(loadRecent(), name);
        saveRecent(names);
        showRecent();
        hideKeyboard();
        status.setText(R.string.wallpaper_finder_searching);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenW = metrics.widthPixels;
        int screenH = metrics.heightPixels;
        boolean online = online();
        File files = getFilesDir();
        File themeDir = Config.dir(this);
        worker.execute(() -> showOutcome(gen, search(name, online, files, themeDir, screenW, screenH, gen)));
    }

    private WallpaperSearch.Outcome search(String name, boolean online, File files, File themeDir,
                                            int screenW, int screenH, int gen) {
        if (!online) return WallpaperSearch.Outcome.OFFLINE;
        File root = WallpaperSets.root(files);
        WallpaperHttp http = new WallpaperHttp();
        WallpaperHttp.Text response = http.text(WallpaperSearch.url(name));
        if (!response.reached) return WallpaperSearch.Outcome.OFFLINE;
        List<WallpaperSearch.Hit> parsed = WallpaperSearch.parse(response.body);
        if (parsed == null) return WallpaperSearch.Outcome.FAILED;
        List<WallpaperSearch.Hit> hits = WallpaperSearch.select(parsed);
        if (hits.isEmpty()) return WallpaperSearch.Outcome.NONE;
        File staging = WallpaperSets.incoming(root);
        int saved = WallpaperDownload.save(hits, http::download, WallpaperJpeg::save, staging, screenW, screenH,
                (done, total) -> runOnUiThread(() -> {
                    if (gen != generation || isDestroyed()) return;
                    status.setText(getString(R.string.wallpaper_finder_progress, done, total));
                }));
        WallpaperSearch.Outcome outcome = WallpaperSearch.classify(true, true, hits.size(), saved);
        if (outcome != WallpaperSearch.Outcome.APPLIED) {
            WallpaperSets.deleteTree(staging);
            return outcome;
        }
        if (themeDir != null && !WallpaperSets.backupOnce(root,
                WallpaperSets.themeSources(themeDir, WallpaperSets.appNames(themeDir)))) {
            WallpaperSets.deleteTree(staging);
            return WallpaperSearch.Outcome.KEPT;
        }
        if (!WallpaperSets.commit(root, staging, WallpaperSets.CAP_BYTES)) {
            WallpaperSets.deleteTree(staging);
            return WallpaperSearch.Outcome.KEPT;
        }
        return WallpaperSearch.Outcome.APPLIED;
    }

    private void showOutcome(int gen, WallpaperSearch.Outcome outcome) {
        runOnUiThread(() -> {
            if (gen != generation || isDestroyed()) return;
            busy = false;
            Log.i(TAG, String.valueOf(outcome));
            if (outcome == WallpaperSearch.Outcome.APPLIED) bumpHome();
            status.setText(message(outcome));
        });
    }

    private int message(WallpaperSearch.Outcome outcome) {
        switch (outcome) {
            case OFFLINE: return R.string.wallpaper_finder_offline;
            case NONE: return R.string.wallpaper_finder_none;
            case FAILED: return R.string.wallpaper_finder_failed;
            case KEPT: return R.string.wallpaper_finder_kept;
            case APPLIED: return R.string.wallpaper_finder_applied;
            default: return R.string.wallpaper_finder_failed;
        }
    }

    private void restore() {
        if (busy) return;
        WallpaperSets.restore(WallpaperSets.root(getFilesDir()));
        bumpHome();
        status.setText(R.string.wallpaper_finder_restored);
    }

    private void bumpHome() {
        SharedPreferences choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private boolean online() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = manager.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(query.getWindowToken(), 0);
    }

    private List<String> loadRecent() {
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(RECENT, "");
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String line : raw.split("\n")) {
            if (!line.trim().isEmpty()) out.add(line);
        }
        return out;
    }

    private void saveRecent(List<String> names) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) body.append('\n');
            body.append(names.get(i));
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(RECENT, body.toString()).apply();
    }

    private void showRecent() {
        recent.removeAllViews();
        List<String> names = loadRecent();
        for (String name : names) {
            TextView row = (TextView) getLayoutInflater().inflate(R.layout.item_finder_recent, recent, false);
            row.setText(name);
            row.setOnClickListener(v -> {
                query.setText(name);
                runSearch(name);
            });
            recent.addView(row);
        }
    }
}
