package com.vishalsecure.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

public class VideoPlayerActivity extends Activity {

    private VideoView videoView;

    private TextView statusText;
    private TextView timeText;

    private Button backButton;
    private Button playPauseButton;
    private Button forwardButton;
    private Button closeButton;

    private String videoUriString;

    private boolean prepared = false;

    private final Handler handler = new Handler();

    private final Runnable updateProgress = new Runnable() {
        @Override
        public void run() {

            if (videoView != null && prepared) {
                try {

                    int position = videoView.getCurrentPosition();
                    int duration = videoView.getDuration();

                    timeText.setText(
                            formatTime(position)
                                    + " / "
                                    + formatTime(duration)
                    );

                    if (videoView.isPlaying()) {
                        playPauseButton.setText("PAUSE");
                    } else {
                        playPauseButton.setText("PLAY");
                    }

                } catch (Exception e) {
                    showError(
                            "Progress Error:\n"
                                    + e.getClass().getSimpleName()
                                    + "\n"
                                    + String.valueOf(e.getMessage())
                    );
                }
            }

            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Screenshot / screen-record protection
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        videoUriString =
                getIntent().getStringExtra("video_uri");

        if (videoUriString == null
                || videoUriString.trim().isEmpty()) {

            showErrorAndClose(
                    "Video URI नहीं मिला।"
            );

            return;
        }

        buildScreen();

        handler.post(updateProgress);
    }

    private void buildScreen() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setBackgroundColor(
                Color.BLACK
        );

        // ---------------- VIDEO AREA ----------------

        LinearLayout videoArea =
                new LinearLayout(this);

        videoArea.setOrientation(
                LinearLayout.VERTICAL
        );

        videoArea.setGravity(
                Gravity.CENTER
        );

        videoArea.setBackgroundColor(
                Color.BLACK
        );

        videoView =
                new VideoView(this);

        videoView.setBackgroundColor(
                Color.BLACK
        );

        videoView.setKeepScreenOn(true);

        videoArea.addView(
                videoView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        root.addView(
                videoArea,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1.0f
                )
        );

        // ---------------- STATUS ----------------

        statusText =
                new TextView(this);

        statusText.setText(
                "वीडियो तैयार हो रहा है..."
        );

        statusText.setTextColor(
                Color.WHITE
        );

        statusText.setTextSize(14);

        statusText.setGravity(
                Gravity.CENTER
        );

        statusText.setBackgroundColor(
                Color.BLACK
        );

        root.addView(
                statusText,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(35)
                )
        );

        // ---------------- CONTROLS ----------------

        LinearLayout controls =
                new LinearLayout(this);

        controls.setOrientation(
                LinearLayout.HORIZONTAL
        );

        controls.setGravity(
                Gravity.CENTER
        );

        controls.setBackgroundColor(
                Color.BLACK
        );

        backButton =
                new Button(this);

        backButton.setText("−10s");

        playPauseButton =
                new Button(this);

        playPauseButton.setText("PLAY");

        forwardButton =
                new Button(this);

        forwardButton.setText("+10s");

        closeButton =
                new Button(this);

        closeButton.setText("CLOSE");

        controls.addView(
                backButton,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        controls.addView(
                playPauseButton,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        controls.addView(
                forwardButton,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        controls.addView(
                closeButton,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        root.addView(
                controls,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        // ---------------- TIME ----------------

        timeText =
                new TextView(this);

        timeText.setText(
                "00:00 / 00:00"
        );

        timeText.setTextColor(
                Color.WHITE
        );

        timeText.setTextSize(14);

        timeText.setGravity(
                Gravity.CENTER
        );

        timeText.setBackgroundColor(
                Color.BLACK
        );

        root.addView(
                timeText,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(30)
                )
        );

        setContentView(root);

        // ---------------- BUTTON ACTIONS ----------------

        backButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        seekBackward();
                    }
                }
        );

        playPauseButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        togglePlayPause();
                    }
                }
        );

        forwardButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        seekForward();
                    }
                }
        );

        closeButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                }
        );

        // ---------------- VIDEO ERROR ----------------

        videoView.setOnErrorListener(
                new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(
                            MediaPlayer mp,
                            int what,
                            int extra
                    ) {

                        String message =
                                "VIDEO PLAYBACK ERROR\n\n"
                                        + "what = "
                                        + what
                                        + "\n"
                                        + "extra = "
                                        + extra;

                        showError(message);

                        return true;
                    }
                }
        );

        // ---------------- VIDEO PREPARED ----------------

        videoView.setOnPreparedListener(
                new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(
                            MediaPlayer mp
                    ) {

                        prepared = true;

                        statusText.setText(
                                "वीडियो तैयार है"
                        );

                        statusText.setVisibility(
                                View.GONE
                        );

                        try {
                            mp.setScreenOnWhilePlaying(
                                    true
                            );
                        } catch (Exception ignored) {
                        }

                        try {
                            mp.setAudioStreamType(
                                    AudioManager.STREAM_MUSIC
                            );
                        } catch (Exception ignored) {
                        }

                        try {
                            videoView.start();

                            playPauseButton.setText(
                                    "PAUSE"
                            );

                        } catch (Exception e) {

                            showError(
                                    "START ERROR\n\n"
                                            + e.getClass()
                                            .getSimpleName()
                                            + "\n"
                                            + String.valueOf(
                                            e.getMessage()
                                    )
                            );
                        }
                    }
                }
        );

        // ---------------- VIDEO COMPLETION ----------------

        videoView.setOnCompletionListener(
                new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(
                            MediaPlayer mp
                    ) {

                        playPauseButton.setText(
                                "PLAY"
                        );
                    }
                }
        );

        // ---------------- SET VIDEO ----------------

        try {

            Uri videoUri =
                    Uri.parse(videoUriString);

            videoView.setVideoURI(
                    videoUri
            );

        } catch (Exception e) {

            showErrorAndClose(
                    "VIDEO URI ERROR\n\n"
                            + e.getClass()
                            .getSimpleName()
                            + "\n"
                            + String.valueOf(
                            e.getMessage()
                    )
            );
        }
    }

    private void togglePlayPause() {

        if (videoView == null) {
            return;
        }

        if (!prepared) {

            showError(
                    "वीडियो अभी तैयार नहीं है।"
            );

            return;
        }

        try {

            if (videoView.isPlaying()) {

                videoView.pause();

                playPauseButton.setText(
                        "PLAY"
                );

            } else {

                videoView.start();

                playPauseButton.setText(
                        "PAUSE"
                );
            }

        } catch (Exception e) {

            showError(
                    "PLAY / PAUSE ERROR\n\n"
                            + e.getClass()
                            .getSimpleName()
                            + "\n"
                            + String.valueOf(
                            e.getMessage()
                    )
            );
        }
    }

    private void seekBackward() {

        if (videoView == null || !prepared) {
            return;
        }

        try {

            int current =
                    videoView.getCurrentPosition();

            int newPosition =
                    Math.max(
                            0,
                            current - 10000
                    );

            videoView.seekTo(
                    newPosition
            );

        } catch (Exception e) {

            showError(
                    "BACKWARD ERROR\n\n"
                            + e.getClass()
                            .getSimpleName()
                            + "\n"
                            + String.valueOf(
                            e.getMessage()
                    )
            );
        }
    }

    private void seekForward() {

        if (videoView == null || !prepared) {
            return;
        }

        try {

            int current =
                    videoView.getCurrentPosition();

            int duration =
                    videoView.getDuration();

            int newPosition =
                    Math.min(
                            duration,
                            current + 10000
                    );

            videoView.seekTo(
                    newPosition
            );

        } catch (Exception e) {

            showError(
                    "FORWARD ERROR\n\n"
                            + e.getClass()
                            .getSimpleName()
                            + "\n"
                            + String.valueOf(
                            e.getMessage()
                    )
            );
        }
    }

    private String formatTime(
            int milliseconds
    ) {

        int totalSeconds =
                milliseconds / 1000;

        int hours =
                totalSeconds / 3600;

        int minutes =
                (totalSeconds % 3600) / 60;

        int seconds =
                totalSeconds % 60;

        if (hours > 0) {

            return String.format(
                    "%02d:%02d:%02d",
                    hours,
                    minutes,
                    seconds
            );

        } else {

            return String.format(
                    "%02d:%02d",
                    minutes,
                    seconds
            );
        }
    }

    private void showError(
            String message
    ) {

        try {

            new AlertDialog.Builder(this)
                    .setTitle(
                            "Vishal Secure - Error"
                    )
                    .setMessage(message)
                    .setPositiveButton(
                            "OK",
                            null
                    )
                    .setCancelable(true)
                    .show();

        } catch (Exception ignored) {
        }
    }

    private void showErrorAndClose(
            String message
    ) {

        try {

            new AlertDialog.Builder(this)
                    .setTitle(
                            "Vishal Secure"
                    )
                    .setMessage(message)
                    .setPositiveButton(
                            "OK",
                            new android.content.DialogInterface
                                    .OnClickListener() {
                                @Override
                                public void onClick(
                                        android.content.DialogInterface dialog,
                                        int which
                                ) {
                                    finish();
                                }
                            }
                    )
                    .setCancelable(false)
                    .show();

        } catch (Exception e) {

            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (videoView != null) {

            try {

                if (videoView.isPlaying()) {
                    videoView.pause();
                }

            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onDestroy() {

        handler.removeCallbacks(
                updateProgress
        );

        if (videoView != null) {

            try {
                videoView.stopPlayback();
            } catch (Exception ignored) {
            }
        }

        super.onDestroy();
    }

    private int dp(int value) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return (int)
                (value * density + 0.5f);
    }
}
