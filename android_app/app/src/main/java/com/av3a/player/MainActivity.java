package com.av3a.player;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.IOException;
import java.util.Arrays;

public class MainActivity extends Activity {
    private static final int PICK_MEDIA_REQUEST = 1001;

    private TextView statusView;
    private VideoView videoView;
    private Uri currentUri;
    private boolean videoLoaded;
    private boolean playWhenPrepared;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));

        TextView title = new TextView(this);
        title.setText("AV3A Android Player");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        Button pickButton = new Button(this);
        pickButton.setText("Select MP4 test file");
        pickButton.setOnClickListener(v -> pickMedia());
        root.addView(pickButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        videoView = new VideoView(this);
        configureVideoView();
        root.addView(videoView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);

        Button startButton = createControlButton("Start");
        startButton.setOnClickListener(v -> startPlayback());
        controls.addView(startButton, controlLayoutParams(0));

        Button pauseButton = createControlButton("Pause");
        pauseButton.setOnClickListener(v -> pausePlayback());
        controls.addView(pauseButton, controlLayoutParams(dp(8)));

        Button stopButton = createControlButton("Stop");
        stopButton.setOnClickListener(v -> stopPlayback());
        controls.addView(stopButton, controlLayoutParams(dp(8)));

        root.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        statusView = new TextView(this);
        statusView.setTextSize(13);
        statusView.setText(buildStartupStatus());

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(statusView);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));

        setContentView(root);
    }

    private void pickMedia() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
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

        stopPlayback();
        currentUri = uri;
        appendStatus("Selected: " + uri);
    }

    private void configureVideoView() {
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            if (playWhenPrepared) {
                videoView.start();
                appendStatus("Playback started");
            } else {
                appendStatus("Media prepared");
            }
        });
        videoView.setOnCompletionListener(mp -> {
            playWhenPrepared = false;
            appendStatus("Playback completed");
        });
        videoView.setOnErrorListener((mp, what, extra) -> {
            playWhenPrepared = false;
            videoLoaded = false;
            appendStatus("System player failed: what=" + what + ", extra=" + extra);
            return false;
        });
    }

    private void startPlayback() {
        if (currentUri == null) {
            appendStatus("Select an MP4 file first");
            return;
        }

        playWhenPrepared = true;
        if (!videoLoaded) {
            videoView.setVideoURI(currentUri);
            videoLoaded = true;
            appendStatus("Preparing media");
            return;
        }

        videoView.start();
        appendStatus("Playback resumed");
    }

    private void pausePlayback() {
        playWhenPrepared = false;
        if (videoView.isPlaying()) {
            videoView.pause();
            appendStatus("Playback paused");
            return;
        }
        appendStatus("Playback is not running");
    }

    private void stopPlayback() {
        playWhenPrepared = false;
        if (videoLoaded || videoView.isPlaying()) {
            videoView.stopPlayback();
            videoLoaded = false;
            appendStatus("Playback stopped");
        }
    }

    private String buildStartupStatus() {
        StringBuilder builder = new StringBuilder();
        builder.append("ABI: ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append('\n');
        builder.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" / API ").append(Build.VERSION.SDK_INT).append("\n\n");
        builder.append("Native libraries packaged:\n");
        builder.append(checkLibrary("AVS3AudioDec"));
        builder.append(checkLibrary("av3a_binaural_render"));
        builder.append(checkLibrary("avutil"));
        builder.append(checkLibrary("swresample"));
        builder.append(checkLibrary("avcodec"));
        builder.append(checkLibrary("avformat"));
        builder.append(checkLibrary("swscale"));
        builder.append(checkLibrary("avfilter"));
        builder.append('\n');
        builder.append("Assets:\n");
        builder.append(checkAsset("model.bin"));
        builder.append('\n');
        builder.append("Note: this APK packages the AV3A and FFmpeg native outputs. The current screen uses Android VideoView for smoke testing; JNI/FFmpeg playback can be connected next.\n");
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

    private void appendStatus(String text) {
        statusView.append("\n" + text);
    }

    private Button createControlButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams controlLayoutParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.leftMargin = leftMargin;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
