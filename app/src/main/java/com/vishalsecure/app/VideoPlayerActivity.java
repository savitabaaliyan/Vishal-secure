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
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class VideoPlayerActivity extends Activity
        implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;

    private TextView statusText;
    private TextView timeText;

    private Button playPauseButton;
    private Button closeButton;

    private SeekBar seekBar;

    private LinearLayout controlPanel;

    private View touchOverlay;

    private String videoUriString;

    private boolean surfaceReady = false;
    private boolean controlsVisible = true;
    private boolean userSeeking = false;

    private final Handler handler = new Handler();

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();

            if (mediaPlayer != null) {
                handler.postDelayed(this, 500);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        videoUriString =
                getIntent().getStringExtra("video_uri");

        if (videoUriString == null ||
                videoUriString.trim().isEmpty()) {

            showErrorAndClose("Video file नहीं मिली।");
            return;
        }

        buildPlayerScreen();
    }

    private void buildPlayerScreen() {

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // VIDEO SURFACE
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        surfaceView = new SurfaceView(this);

        surfaceView.setSecure(true);
        surfaceView.setKeepScreenOn(true);

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        FrameLayout.LayoutParams surfaceParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        surfaceParams.gravity = Gravity.CENTER;

        root.addView(surfaceView, surfaceParams);

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // STATUS TEXT
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        statusText = new TextView(this);

        statusText.setText("Video loading...");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        statusText.setBackgroundColor(Color.TRANSPARENT);

        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        statusParams.gravity = Gravity.CENTER;

        root.addView(statusText, statusParams);

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // TOUCH OVERLAY
        // वीडियो पर touch करके controls hide/show
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        touchOverlay = new View(this);

        touchOverlay.setBackgroundColor(Color.TRANSPARENT);
        touchOverlay.setClickable(true);

        FrameLayout.LayoutParams touchParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        touchParams.gravity = Gravity.CENTER;

        root.addView(touchOverlay, touchParams);

        touchOverlay.setOnClickListener(v -> {

            if (controlPanel == null) {
                return;
            }

            if (controlsVisible) {

                controlPanel.setVisibility(View.GONE);
                controlsVisible = false;

            } else {

                controlPanel.setVisibility(View.VISIBLE);
                controlsVisible = true;
            }
        });

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CONTROL PANEL
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        controlPanel = new LinearLayout(this);

        controlPanel.setOrientation(
                LinearLayout.VERTICAL
        );

        controlPanel.setGravity(Gravity.CENTER);

        // ज्यादा dark ताकि controls साफ दिखें
        controlPanel.setBackgroundColor(
                Color.argb(235, 0, 0, 0)
        );

        controlPanel.setPadding(
                dp(12),
                dp(8),
                dp(12),
                dp(10)
        );

        FrameLayout.LayoutParams panelParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        panelParams.gravity =
                Gravity.BOTTOM;

        root.addView(controlPanel, panelParams);

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // TIME TEXT
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        timeText = new TextView(this);

        timeText.setText("00:00 / 00:00");
        timeText.setTextColor(Color.WHITE);
        timeText.setTextSize(15);
        timeText.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams timeParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        timeParams.bottomMargin = dp(4);

        controlPanel.addView(
                timeText,
                timeParams
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // SEEK BAR
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        seekBar = new SeekBar(this);

        seekBar.setMax(1000);

        LinearLayout.LayoutParams seekParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(42)
                );

        controlPanel.addView(
                seekBar,
                seekParams
        );

        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser) {

                        if (fromUser &&
                                mediaPlayer != null &&
                                mediaPlayer.isPlaying()) {

                            int duration =
                                    mediaPlayer.getDuration();

                            int position =
                                    (int) (
                                            duration *
                                            (progress / 1000.0)
                                    );

                            timeText.setText(
                                    formatTime(position)
                                            + " / "
                                            + formatTime(duration)
                            );
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(
                            SeekBar seekBar) {

                        userSeeking = true;
                    }

                    @Override
                    public void onStopTrackingTouch(
                            SeekBar seekBar) {

                        if (mediaPlayer != null) {

                            try {

                                int duration =
                                        mediaPlayer.getDuration();

                                int position =
                                        (int) (
                                                duration *
                                                (seekBar.getProgress()
                                                        / 1000.0)
                                        );

                                mediaPlayer.seekTo(position);

                            } catch (Exception ignored) {
                            }
                        }

                        userSeeking = false;
                    }
                }
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // BUTTON ROW
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        LinearLayout buttonRow =
                new LinearLayout(this);

        buttonRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        buttonRow.setGravity(
                Gravity.CENTER
        );

        controlPanel.addView(
                buttonRow,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(58)
                )
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // PLAY / PAUSE
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        playPauseButton =
                new Button(this);

        playPauseButton.setText("PLAY");
        playPauseButton.setTextColor(Color.WHITE);
        playPauseButton.setTextSize(16);
        playPauseButton.setAllCaps(false);

        playPauseButton.setBackgroundColor(
                Color.DKGRAY
        );

        LinearLayout.LayoutParams playParams =
                new LinearLayout.LayoutParams(
                        dp(120),
                        dp(50)
                );

        playParams.setMargins(
                dp(5),
                dp(2),
                dp(10),
                dp(2)
        );

        buttonRow.addView(
                playPauseButton,
                playParams
        );

        playPauseButton.setOnClickListener(v -> {

            if (mediaPlayer == null) {
                return;
            }

            try {

                if (mediaPlayer.isPlaying()) {

                    mediaPlayer.pause();

                    playPauseButton.setText(
                            "PLAY"
                    );

                } else {

                    mediaPlayer.start();

                    playPauseButton.setText(
                            "PAUSE"
                    );
                }

            } catch (Exception ignored) {
            }
        });

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CLOSE BUTTON
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        closeButton = new Button(this);

        closeButton.setText("CLOSE");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTextSize(16);
        closeButton.setAllCaps(false);

        closeButton.setBackgroundColor(
                Color.DKGRAY
        );

        LinearLayout.LayoutParams closeParams =
                new LinearLayout.LayoutParams(
                        dp(120),
                        dp(50)
                );

        closeParams.setMargins(
                dp(10),
                dp(2),
                dp(5),
                dp(2)
        );

        buttonRow.addView(
                closeButton,
                closeParams
        );

        closeButton.setOnClickListener(
                v -> finish()
        );

        setContentView(root);
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SURFACE CREATED
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    public void surfaceCreated(
            SurfaceHolder holder) {

        surfaceReady = true;

        startVideo();
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SURFACE CHANGED
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    public void surfaceChanged(
            SurfaceHolder holder,
            int format,
            int width,
            int height) {

        surfaceHolder = holder;

        if (mediaPlayer != null) {

            try {

                mediaPlayer.setDisplay(
                        surfaceHolder
                );

            } catch (Exception ignored) {
            }
        }

        adjustVideoSize();
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SURFACE DESTROYED
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    public void surfaceDestroyed(
            SurfaceHolder holder) {

        surfaceReady = false;

        releasePlayer();
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // START VIDEO
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void startVideo() {

        if (!surfaceReady ||
                surfaceHolder == null ||
                videoUriString == null) {

            return;
        }

        releasePlayer();

        try {

            Uri videoUri =
                    Uri.parse(videoUriString);

            mediaPlayer =
                    new MediaPlayer();

            mediaPlayer.setAudioStreamType(
                    AudioManager.STREAM_MUSIC
            );

            mediaPlayer.setScreenOnWhilePlaying(
                    true
            );

            mediaPlayer.setDisplay(
                    surfaceHolder
            );

            mediaPlayer.setOnVideoSizeChangedListener(
                    (mp, width, height) -> {

                        adjustVideoSize();
                    }
            );

            mediaPlayer.setOnPreparedListener(
                    mp -> {

                        try {

                            mp.setDisplay(
                                    surfaceHolder
                            );

                            adjustVideoSize();

                            int duration =
                                    mp.getDuration();

                            seekBar.setMax(1000);
                            seekBar.setProgress(0);

                            timeText.setText(
                                    "00:00 / "
                                            + formatTime(duration)
                            );

                            mp.start();

                            playPauseButton.setText(
                                    "PAUSE"
                            );

                            statusText.setVisibility(
                                    View.GONE
                            );

                            handler.removeCallbacks(
                                    progressRunnable
                            );

                            handler.post(
                                    progressRunnable
                            );

                        } catch (Exception e) {

                            showError(
                                    "Video चलाने में समस्या हुई।"
                            );
                        }
                    }
            );

            mediaPlayer.setOnCompletionListener(
                    mp -> {

                        playPauseButton.setText(
                                "PLAY"
                        );

                        if (seekBar != null) {
                            seekBar.setProgress(1000);
                        }

                        if (timeText != null) {

                            timeText.setText(
                                    formatTime(
                                            mp.getDuration()
                                    )
                                            + " / "
                                            + formatTime(
                                            mp.getDuration()
                                    )
                            );
                        }

                        if (statusText != null) {

                            statusText.setText(
                                    "Video समाप्त"
                            );

                            statusText.setVisibility(
                                    View.VISIBLE
                            );
                        }
                    }
            );

            mediaPlayer.setOnErrorListener(
                    (mp, what, extra) -> {

                        showError(
                                "यह video इस device पर play नहीं हो सकी।"
                        );

                        return true;
                    }
            );

            mediaPlayer.setDataSource(
                    this,
                    videoUri
            );

            mediaPlayer.prepareAsync();

        } catch (Exception e) {

            showError(
                    "Video खोलने में समस्या हुई।"
            );
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // VIDEO SIZE / ASPECT RATIO
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void adjustVideoSize() {

        if (surfaceView == null ||
                mediaPlayer == null) {

            return;
        }

        try {

            int videoWidth =
                    mediaPlayer.getVideoWidth();

            int videoHeight =
                    mediaPlayer.getVideoHeight();

            if (videoWidth <= 0 ||
                    videoHeight <= 0) {

                return;
            }

            int screenWidth =
                    getWindow()
                            .getDecorView()
                            .getWidth();

            int screenHeight =
                    getWindow()
                            .getDecorView()
                            .getHeight();

            if (screenWidth <= 0 ||
                    screenHeight <= 0) {

                return;
            }

            float videoRatio =
                    (float) videoWidth
                            / videoHeight;

            float screenRatio =
                    (float) screenWidth
                            / screenHeight;

            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams)
                            surfaceView.getLayoutParams();

            if (videoRatio > screenRatio) {

                params.width =
                        screenWidth;

                params.height =
                        (int) (
                                screenWidth
                                        / videoRatio
                        );

            } else {

                params.height =
                        screenHeight;

                params.width =
                        (int) (
                                screenHeight
                                        * videoRatio
                        );
            }

            params.gravity =
                    Gravity.CENTER;

            surfaceView.setLayoutParams(
                    params
            );

        } catch (Exception ignored) {
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // UPDATE PROGRESS
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void updateProgress() {

        if (mediaPlayer == null ||
                seekBar == null ||
                timeText == null) {

            return;
        }

        try {

            int duration =
                    mediaPlayer.getDuration();

            int position =
                    mediaPlayer.getCurrentPosition();

            if (duration > 0 &&
                    !userSeeking) {

                int progress =
                        (int) (
                                (position * 1000L)
                                        / duration
                        );

                seekBar.setProgress(
                        progress
                );
            }

            timeText.setText(
                    formatTime(position)
                            + " / "
                            + formatTime(duration)
            );

        } catch (Exception ignored) {
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // TIME FORMAT
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private String formatTime(
            int milliseconds) {

        int totalSeconds =
                milliseconds / 1000;

        int minutes =
                totalSeconds / 60;

        int seconds =
                totalSeconds % 60;

        return String.format(
                java.util.Locale.getDefault(),
                "%02d:%02d",
                minutes,
                seconds
        );
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // PAUSE
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    protected void onPause() {

        super.onPause();

        if (mediaPlayer != null) {

            try {

                if (mediaPlayer.isPlaying()) {

                    mediaPlayer.pause();

                    if (playPauseButton != null) {

                        playPauseButton.setText(
                                "PLAY"
                        );
                    }
                }

            } catch (Exception ignored) {
            }
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // RESUME
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    protected void onResume() {

        super.onResume();

        if (surfaceReady &&
                mediaPlayer != null) {

            try {

                if (!mediaPlayer.isPlaying()) {

                    mediaPlayer.start();

                    if (playPauseButton != null) {

                        playPauseButton.setText(
                                "PAUSE"
                        );
                    }
                }

            } catch (Exception ignored) {
            }
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // RELEASE PLAYER
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void releasePlayer() {

        handler.removeCallbacks(
                progressRunnable
        );

        if (mediaPlayer != null) {

            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {
            }

            try {
                mediaPlayer.reset();
            } catch (Exception ignored) {
            }

            try {
                mediaPlayer.release();
            } catch (Exception ignored) {
            }

            mediaPlayer = null;
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ERROR
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void showError(
            String message) {

        runOnUiThread(() -> {

            if (statusText != null) {

                statusText.setText(
                        message
                );

                statusText.setVisibility(
                        View.VISIBLE
                );
            }
        });
    }

    private void showErrorAndClose(
            String message) {

        new AlertDialog.Builder(this)
                .setTitle("Vishal Secure")
                .setMessage(message)
                .setPositiveButton(
                        "OK",
                        (dialog, which) -> finish()
                )
                .setCancelable(false)
                .show();
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // DESTROY
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    protected void onDestroy() {

        releasePlayer();

        super.onDestroy();
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // DP
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private int dp(int value) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return (int) (
                value * density + 0.5f
        );
    }
}
