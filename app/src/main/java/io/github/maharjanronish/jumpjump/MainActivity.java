package io.github.maharjanronish.jumpjump;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import io.github.maharjanronish.jumpjump.core.Game;

/**
 * Single-activity host: fullscreen GL game + light native overlays for the
 * game-over card and the offline leaderboard. No AndroidX, no fragments,
 * no external libraries.
 */
public final class MainActivity extends Activity implements GameRenderer.Ui {

    private Game game;
    private GameRenderer renderer;
    private GameView gameView;
    private Sfx sfx;
    private Storage storage;

    private TextView hintView, finalScoreView, bestView, newBestView;
    private LinearLayout gameOverPanel, recordsPanel;
    private LinearLayout recordsRows;
    private Button soundButton;
    private final Random seedRandom = new Random();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        storage = new Storage(this);
        sfx = new Sfx(this);
        sfx.setSoundOn(storage.soundOn());

        game = new Game(seedRandom.nextLong());
        renderer = new GameRenderer(game, sfx, this);
        gameView = new GameView(this, renderer);
        FrameLayout container = findViewById(R.id.gl_container);
        container.addView(gameView, 0);

        hintView = findViewById(R.id.hint);
        finalScoreView = findViewById(R.id.final_score);
        bestView = findViewById(R.id.best_score);
        newBestView = findViewById(R.id.new_best);
        gameOverPanel = findViewById(R.id.game_over_panel);
        recordsPanel = findViewById(R.id.records_panel);
        recordsRows = findViewById(R.id.records_rows);
        soundButton = findViewById(R.id.sound_toggle);

        findViewById(R.id.play_again).setOnClickListener(v -> restart());
        findViewById(R.id.records_button).setOnClickListener(v -> showRecords());
        findViewById(R.id.records_back).setOnClickListener(v -> showGameOverPanel());
        soundButton.setOnClickListener(v -> toggleSound());

        if (!storage.firstRunDone()) {
            hintView.setVisibility(View.VISIBLE);
        }
        updateSoundLabel();
        hideSystemBars();
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // keep the soft lavender behind system bars before they hide
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
        }
    }

    // ---------------------------------------------------------------- fullscreen

    private void hideSystemBars() {
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = decor.getWindowInsetsController();
            if (c != null) {
                c.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                c.hide(WindowInsets.Type.systemBars());
            }
        } else {
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    // ---------------------------------------------------------------- game flow

    /** From the GL thread. */
    @Override public void onGameOver(final int score) {
        runOnUiThread(() -> showGameOver(score));
    }

    /** From the GL thread. */
    @Override public void onFirstJump() {
        runOnUiThread(() -> {
            hintView.setVisibility(View.GONE);
            storage.markFirstRunDone();
        });
    }

    private void showGameOver(int score) {
        int prevBest = storage.best();
        int rank = storage.addScore(score);
        int best = storage.best();

        finalScoreView.setText(String.valueOf(score));
        bestView.setText(getString(R.string.best_label, best));
        boolean newBest = score > prevBest && score > 0;
        newBestView.setVisibility(newBest ? View.VISIBLE : View.GONE);

        gameOverPanel.setVisibility(View.VISIBLE);
        recordsPanel.setVisibility(View.GONE);
        gameView.setPausedRendering(true);
    }

    private void restart() {
        gameOverPanel.setVisibility(View.GONE);
        recordsPanel.setVisibility(View.GONE);
        gameView.setPausedRendering(false);
        final long seed = seedRandom.nextLong();
        gameView.queueEvent(() -> renderer.resetGame(seed));
    }

    private void showRecords() {
        gameOverPanel.setVisibility(View.GONE);
        recordsPanel.setVisibility(View.VISIBLE);
        recordsRows.removeAllViews();

        List<Storage.Record> list = storage.records();
        if (list.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.no_records);
            empty.setTextColor(0xFF8A8595);
            empty.setTextSize(15);
            empty.setPadding(0, dp(16), 0, dp(16));
            recordsRows.addView(empty);
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        int rank = 1;
        for (Storage.Record r : list) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(4), dp(10), dp(4), dp(10));

            TextView rankV = new TextView(this);
            rankV.setText(String.format(Locale.getDefault(), "%2d.", rank++));
            rankV.setTextColor(0xFF56504A);
            rankV.setTextSize(16);
            rankV.setTypeface(null, Typeface_BOLD());
            row.addView(rankV, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 0.14f));

            TextView scoreV = new TextView(this);
            scoreV.setText(String.valueOf(r.score));
            scoreV.setTextColor(0xFF323136);
            scoreV.setTextSize(18);
            scoreV.setTypeface(null, Typeface_BOLD());
            row.addView(scoreV, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 0.34f));

            TextView dateV = new TextView(this);
            dateV.setText(fmt.format(new Date(r.timestamp)));
            dateV.setTextColor(0xFF8A8595);
            dateV.setTextSize(13);
            dateV.setGravity(Gravity.END);
            row.addView(dateV, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 0.52f));

            recordsRows.addView(row,
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private void showGameOverPanel() {
        recordsPanel.setVisibility(View.GONE);
        gameOverPanel.setVisibility(View.VISIBLE);
    }

    private void toggleSound() {
        boolean on = !storage.soundOn();
        storage.setSoundOn(on);
        sfx.setSoundOn(on);
        updateSoundLabel();
    }

    private void updateSoundLabel() {
        soundButton.setText(getString(R.string.sound_label, storage.soundOn()
                ? getString(R.string.sound_on) : getString(R.string.sound_off)));
    }

    private int Typeface_BOLD() { return android.graphics.Typeface.BOLD; }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override protected void onResume() {
        super.onResume();
        hideSystemBars();
        gameView.onResume();
    }

    @Override protected void onPause() {
        gameView.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        sfx.release();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (recordsPanel.getVisibility() == View.VISIBLE) {
            showGameOverPanel();
            return;
        }
        if (gameOverPanel.getVisibility() == View.VISIBLE) {
            restart();   // back on the game-over card = quick retry
            return;
        }
        super.onBackPressed();
    }
}
