package com.av3a.player;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_MEDIA_REQUEST = 1001;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            progressHandler.postDelayed(this, 500);
        }
    };

    private TextView statusBadge;
    private TextView fileNameView;
    private TextView emptyVideoHint;
    private TextView currentTimeView;
    private TextView durationView;
    private TextView logView;
    private ScrollView logScrollView;
    private SeekBar progressBar;
    private VideoView videoView;
    private Uri currentUri;
    private boolean videoLoaded;
    private boolean playWhenPrepared;
    private boolean userSeeking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
        configureVideoView();
        appendLog(buildStartupStatus());
        progressHandler.post(progressRunnable);
    }

    @Override
    protected void onDestroy() {
        progressHandler.removeCallbacks(progressRunnable);
        super.onDestroy();
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackgroundColor(color("#0B0F14"));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);

        TextView title = text("AV3A Player", 24, "#F8FAFC", Typeface.BOLD);
        TextView subtitle = text("测试版2 · Binaural render test build", 12, "#94A3B8", Typeface.NORMAL);
        titleGroup.addView(title);
        titleGroup.addView(subtitle);
        header.addView(titleGroup, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusBadge = text("Idle", 12, "#99F6E4", Typeface.BOLD);
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setBackground(roundRect("#123B35", dp(18), "#1F8A78", 1));
        header.addView(statusBadge, new LinearLayout.LayoutParams(dp(74), dp(34)));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button pickButton = createPrimaryButton("选择 MP4 文件");
        pickButton.setOnClickListener(v -> pickMedia());
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        pickParams.topMargin = dp(14);
        root.addView(pickButton, pickParams);

        fileNameView = text("未选择文件", 13, "#CBD5E1", Typeface.NORMAL);
        fileNameView.setSingleLine(true);
        LinearLayout.LayoutParams fileParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fileParams.topMargin = dp(10);
        root.addView(fileNameView, fileParams);

        FrameLayout videoFrame = new FrameLayout(this);
        videoFrame.setBackground(roundRect("#020617", dp(10), "#1E293B", 1));
        videoFrame.setPadding(dp(1), dp(1), dp(1), dp(1));

        videoView = new VideoView(this);
        videoView.setBackgroundColor(Color.BLACK);
        videoFrame.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyVideoHint = text("选择文件后点击 Start 开始播放", 15, "#64748B", Typeface.NORMAL);
        emptyVideoHint.setGravity(Gravity.CENTER);
        videoFrame.addView(emptyVideoHint, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        videoParams.topMargin = dp(12);
        root.addView(videoFrame, videoParams);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        currentTimeView = text("00:00", 12, "#CBD5E1", Typeface.NORMAL);
        durationView = text("00:00", 12, "#CBD5E1", Typeface.NORMAL);
        durationView.setGravity(Gravity.RIGHT);
        timeRow.addView(currentTimeView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        timeRow.addView(durationView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = dp(10);
        root.addView(timeRow, timeParams);

        progressBar = new SeekBar(this);
        progressBar.setMax(0);
        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTimeView.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (videoLoaded) {
                    videoView.seekTo(seekBar.getProgress());
                }
                userSeeking = false;
            }
        });
        root.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);

        Button startButton = createControlButton("Start", "#14B8A6", "#042F2E");
        startButton.setOnClickListener(v -> startPlayback());
        controls.addView(startButton, controlLayoutParams(0));

        Button pauseButton = createControlButton("Pause", "#334155", "#0F172A");
        pauseButton.setOnClickListener(v -> pausePlayback());
        controls.addView(pauseButton, controlLayoutParams(dp(8)));

        Button stopButton = createControlButton("Stop", "#7F1D1D", "#1F0A0A");
        stopButton.setOnClickListener(v -> stopPlayback());
        controls.addView(stopButton, controlLayoutParams(dp(8)));

        root.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        logView = text("", 12, "#CBD5E1", Typeface.NORMAL);
        logView.setPadding(dp(12), dp(10), dp(12), dp(10));
        logView.setBackground(roundRect("#111827", dp(8), "#243244", 1));

        logScrollView = new ScrollView(this);
        logScrollView.addView(logView);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
        logParams.topMargin = dp(12);
        root.addView(logScrollView, logParams);

        return root;
    }

    private void pickMedia() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType("video/mp4");
        startActivityForResult(intent, PICK_MEDIA_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_MEDIA_REQUEST || resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();
        if (uri == null) {
            return;
        }

        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (RuntimeException ignored) {
            // Some providers do not offer persistable permissions.
        }

        stopPlayback();
        currentUri = uri;
        fileNameView.setText(resolveDisplayName(uri));
        emptyVideoHint.setVisibility(View.VISIBLE);
        setStatus("Ready");
        appendLog("Selected: " + uri);
    }

    private void configureVideoView() {
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            int duration = Math.max(0, videoView.getDuration());
            progressBar.setMax(duration);
            durationView.setText(formatTime(duration));
            emptyVideoHint.setVisibility(View.GONE);
            if (playWhenPrepared) {
                videoView.start();
                setStatus("Playing");
                appendLog("Playback started");
            } else {
                setStatus("Ready");
                appendLog("Media prepared");
            }
        });
        videoView.setOnCompletionListener(mp -> {
            playWhenPrepared = false;
            setStatus("Done");
            appendLog("Playback completed");
        });
        videoView.setOnErrorListener((mp, what, extra) -> {
            playWhenPrepared = false;
            videoLoaded = false;
            emptyVideoHint.setVisibility(View.VISIBLE);
            setStatus("Error");
            appendLog("System player failed: what=" + what + ", extra=" + extra);
            return false;
        });
    }

    private void startPlayback() {
        if (currentUri == null) {
            appendLog("Select an MP4 file first");
            return;
        }

        playWhenPrepared = true;
        if (!videoLoaded) {
            videoView.setVideoURI(currentUri);
            videoLoaded = true;
            setStatus("Loading");
            appendLog("Preparing media");
            return;
        }

        videoView.start();
        emptyVideoHint.setVisibility(View.GONE);
        setStatus("Playing");
        appendLog("Playback resumed");
    }

    private void pausePlayback() {
        playWhenPrepared = false;
        if (videoView.isPlaying()) {
            videoView.pause();
            setStatus("Paused");
            appendLog("Playback paused");
            return;
        }
        appendLog("Playback is not running");
    }

    private void stopPlayback() {
        playWhenPrepared = false;
        if (videoLoaded || videoView.isPlaying()) {
            videoView.stopPlayback();
            videoLoaded = false;
            progressBar.setProgress(0);
            currentTimeView.setText("00:00");
            durationView.setText("00:00");
            emptyVideoHint.setVisibility(View.VISIBLE);
            setStatus("Stopped");
            appendLog("Playback stopped");
        }
    }

    private void updateProgress() {
        if (!videoLoaded || userSeeking) {
            return;
        }

        int duration = videoView.getDuration();
        int current = videoView.getCurrentPosition();
        if (duration > 0) {
            progressBar.setMax(duration);
            durationView.setText(formatTime(duration));
            progressBar.setProgress(Math.max(0, current));
            currentTimeView.setText(formatTime(current));
        }
    }

    private String buildStartupStatus() {
        StringBuilder builder = new StringBuilder();
        builder.append("Device ABI: ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append('\n');
        builder.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" / API ").append(Build.VERSION.SDK_INT).append("\n\n");
        builder.append("Packaged native libraries:\n");
        String[] libraries = {
                "ijksdl",
                "ijkffmpeg",
                "ijkplayer",
                "avs3a_decoder",
                "avs3a_renderer",
                "RtsSDK",
                "SennheiserAmbeoDecoder",
                "wsrtcsdk"
        };
        for (String library : libraries) {
            builder.append(checkLibrary(library));
        }
        builder.append('\n');
        builder.append("Assets:\n");
        builder.append(checkAsset("model.bin"));
        builder.append('\n');
        builder.append("Note: this screen is a polished APK smoke test shell. Native IJK/AV3A libraries are packaged for loading verification; dedicated JNI playback can be connected next.\n");
        return builder.toString();
    }

    private String checkLibrary(String name) {
        try {
            System.loadLibrary(name);
            return "  OK  lib" + name + ".so\n";
        } catch (UnsatisfiedLinkError error) {
            return "  ERR lib" + name + ".so: " + error.getMessage() + "\n";
        }
    }

    private String checkAsset(String name) {
        try {
            getAssets().open(name).close();
            return "  OK  " + name + "\n";
        } catch (IOException error) {
            return "  ERR " + name + ": " + error.getMessage() + "\n";
        }
    }

    private String resolveDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (RuntimeException ignored) {
            // Fall back to the URI path below.
        }
        String path = uri.getLastPathSegment();
        return path == null ? uri.toString() : path;
    }

    private String formatTime(int millis) {
        int totalSeconds = Math.max(0, millis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void appendLog(String text) {
        if (logView.length() > 0) {
            logView.append("\n");
        }
        logView.append(text);
        logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void setStatus(String text) {
        statusBadge.setText(text);
    }

    private TextView text(String value, int sp, String color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color(color));
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private Button createPrimaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(roundRect("#0F766E", dp(8), "#2DD4BF", 1));
        return button;
    }

    private Button createControlButton(String text, String fill, String stroke) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(roundRect(fill, dp(8), stroke, 1));
        return button;
    }

    private LinearLayout.LayoutParams controlLayoutParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.leftMargin = leftMargin;
        return params;
    }

    private GradientDrawable roundRect(String fill, int radius, String stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(fill));
        drawable.setCornerRadius(radius);
        drawable.setStroke(strokeWidth, color(stroke));
        return drawable;
    }

    private int color(String hex) {
        return Color.parseColor(hex);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
