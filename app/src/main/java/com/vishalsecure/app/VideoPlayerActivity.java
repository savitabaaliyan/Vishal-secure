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

    private Button back10Button;
    private Button playPauseButton;
    private Button forward10Button;
    private Button closeButton;

    private SeekBar seekBar;

    private LinearLayout controlPanel;

    private View touchOverlay;

    private String videoUriString;

    private boolean surfaceReady = false;
    private boolean controlsVisible = true;
    private boolean userSeeking = false;

    private int videoWidth = 0;
    private int videoHeight = 0;

    private final Handler handler = new Handler();

    private final Runnable progressRunnable =
            new Runnable() {
                @Override
                public void run() {

                    updateProgress();

                    if (mediaPlayer != null) {
                        handler.postDelayed(
                                this,
                                500
                        );
                    }
                }
            };

    @Override
    protected void onCreate(
            Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        videoUriString =
                getIntent().getStringExtra(
                        "video_uri"
                );

        if (videoUriString == null ||
                videoUriString.trim().isEmpty()) {

            showErrorAndClose(
                    "Video file नहीं मिली।"
            );

            return;
        }

        buildPlayerScreen();
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // PLAYER SCREEN
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void buildPlayerScreen() {

        FrameLayout root =
                new FrameLayout(this);

        root.setBackgroundColor(
                Color.BLACK
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // VIDEO
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        surfaceView =
                new SurfaceView(this);

        surfaceView.setSecure(true);
        surfaceView.setKeepScreenOn(true);

        surfaceHolder =
                surfaceView.getHolder();

        surfaceHolder.addCallback(this);

        FrameLayout.LayoutParams
                surfaceParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        surfaceParams.gravity =
                Gravity.CENTER;

        root.addView(
                surfaceView,
                surfaceParams
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // STATUS
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        statusText =
                new TextView(this);

        statusText.setText(
                "Video loading..."
        );

        statusText.setTextColor(
                Color.WHITE
        );

        statusText.setTextSize(17);

        statusText.setGravity(
                Gravity.CENTER
        );

        statusText.setBackgroundColor(
                Color.TRANSPARENT
        );

        FrameLayout.LayoutParams
                statusParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        statusParams.gravity =
                Gravity.CENTER;

        root.addView(
                statusText,
                statusParams
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // TOUCH OVERLAY
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        touchOverlay =
                new View(this);

        touchOverlay.setBackgroundColor(
                Color.TRANSPARENT
        );

        touchOverlay.setClickable(true);

        FrameLayout.LayoutParams
                touchParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        touchParams.gravity =
                Gravity.CENTER;

        root.addView(
                touchOverlay,
                touchParams
        );

        touchOverlay.setOnClickListener(
                v -> toggleControls()
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CONTROL PANEL
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        controlPanel =
                new LinearLayout(this);

        controlPanel.setOrientation(
                LinearLayout.VERTICAL
        );

        controlPanel.setGravity(
                Gravity.CENTER
        );

        controlPanel.setBackgroundColor(
                Color.argb(
                        240,
                        0,
                        0,
                        0
                )
        );

        controlPanel.setPadding(
                dp(10),
                dp(8),
                dp(10),
                dp(10)
        );

        FrameLayout.LayoutParams
                panelParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        panelParams.gravity =
                Gravity.BOTTOM;

        root.addView(
                controlPanel,
                panelParams
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // TIME
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        timeText =
                new TextView(this);

        timeText.setText(
                "00:00 / 00:00"
        );

        timeText.setTextColor(
                Color.WHITE
        );

        timeText.setTextSize(15);

        timeText.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams
                timeParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        timeParams.bottomMargin =
                dp(3);

        controlPanel.addView(
                timeText,
                timeParams
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // SEEK BAR
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        seekBar =
                new SeekBar(this);

        seekBar.setMax(1000);

        LinearLayout.LayoutParams
                seekParams =
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
                            SeekBar bar,
                            int progress,
                            boolean fromUser) {

                        if (fromUser &&
                                mediaPlayer != null) {

                            try {

                                int duration =
                                        mediaPlayer.getDuration();

                                int position =
                                        (int) (
                                                duration *
                                                (progress / 1000.0)
                                        );

                                timeText.setText(
                                        formatTime(
                                                position
                                        )
                                                + " / "
                                                + formatTime(
                                                duration
                                        )
                                );

                            } catch (Exception ignored) {
                            }
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(
                            SeekBar bar) {

                        userSeeking = true;
                    }

                    @Override
                    public void onStopTrackingTouch(
                            SeekBar bar) {

                        if (mediaPlayer != null) {

                            try {

                                int duration =
                                        mediaPlayer.getDuration();

                                int position =
                                        (int) (
                                                duration *
                                                (
                                                        bar.getProgress()
                                                                / 1000.0
                                                )
                                        );

                                mediaPlayer.seekTo(
                                        position
                                );

                                hideFinishedMessage();

                            } catch (Exception ignored) {
                            }
                        }

                        userSeeking = false;
                    }
                }
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // BUTTON ROW
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

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

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // -10 SEC
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        back10Button =
                createControlButton(
                        "−10 SEC"
                );

        buttonRow.addView(
                back10Button,
                buttonParams()
        );

        back10Button.setOnClickListener(
                v -> seekBy(-10000)
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // PLAY / PAUSE
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        playPauseButton =
                createControlButton(
                        "PLAY"
                );

        buttonRow.addView(
                playPauseButton,
                buttonParams()
        );

        playPauseButton.setOnClickListener(
                v -> togglePlayPause()
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // +10 SEC
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        forward10Button =
                createControlButton(
                        "+10 SEC"
                );

        buttonRow.addView(
                forward10Button,
                buttonParams()
        );

        forward10Button.setOnClickListener(
                v -> seekBy(10000)
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CLOSE
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        closeButton =
                createControlButton(
                        "CLOSE"
                );

        buttonRow.addView(
                closeButton,
                buttonParams()
        );

        closeButton.setOnClickListener(
                v -> finish()
        );

        setContentView(root);
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // CONTROL BUTTON DESIGN
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private Button createControlButton(
            String text) {

        Button button =
                new Button(this);

        button.setText(text);

        button.setTextColor(
                Color.WHITE
        );

        button.setTextSize(14);

        button.setAllCaps(false);

        button.setGravity(
                Gravity.CENTER
        );

        button.setBackgroundColor(
                Color.DKGRAY
        );

        return button;
    }

    private LinearLayout.LayoutParams
    buttonParams() {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1
                );

        params.setMargins(
                dp(3),
                dp(2),
                dp(3),
                dp(2)
        );

        return params;
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // HIDE / SHOW CONTROLS
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void toggleControls() {

        if (controlPanel == null) {
            return;
        }

        if (controlsVisible) {

            controlPanel.setVisibility(
                    View.GONE
            );

            controlsVisible = false;

        } else {

            controlPanel.setVisibility(
                    View.VISIBLE
            );

            controlsVisible = true;
        }
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
                    Uri.parse(
                            videoUriString
                    );

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

                        videoWidth = width;
                        videoHeight = height;

                        adjustVideoSize();
                    }
            );

            mediaPlayer.setOnPreparedListener(
                    mp -> {

                        try {

                            mp.setDisplay(
                                    surfaceHolder
                            );

                            videoWidth =
                                    mp.getVideoWidth();

                            videoHeight =
                                    mp.getVideoHeight();

                            adjustVideoSize();

                            int duration =
                                    mp.getDuration();

                            seekBar.setProgress(
                                    0
                            );

                            timeText.setText(
                                    "00:00 / "
                                            + formatTime(
                                            duration
                                    )
                            );

                            // पुराना "Video समाप्त" हटायें
                            hideFinishedMessage();

                            mp.start();

                            playPauseButton.setText(
                                    "PAUSE"
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

                            seekBar.setProgress(
                                    1000
                            );
                        }

                        if (timeText != null) {

                            int duration =
                                    mp.getDuration();

                            timeText.setText(
                                    formatTime(
                                            duration
                                    )
                                            + " / "
                                            + formatTime(
                                            duration
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
    // PLAY / PAUSE
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void togglePlayPause() {

        if (mediaPlayer == null) {
            return;
        }

        try {

            int duration =
                    mediaPlayer.getDuration();

            int position =
                    mediaPlayer.getCurrentPosition();

            // वीडियो खत्म हो चुकी है
            // तो शुरुआत से फिर चलायें
            if (duration > 0 &&
                    position >= duration - 500) {

                mediaPlayer.seekTo(0);

                seekBar.setProgress(0);

                timeText.setText(
                        "00:00 / "
                                + formatTime(
                                duration
                        )
                );

                hideFinishedMessage();

                mediaPlayer.start();

                playPauseButton.setText(
                        "PAUSE"
                );

                return;
            }

            if (mediaPlayer.isPlaying()) {

                mediaPlayer.pause();

                playPauseButton.setText(
                        "PLAY"
                );

            } else {

                hideFinishedMessage();

                mediaPlayer.start();

                playPauseButton.setText(
                        "PAUSE"
                );
            }

        } catch (Exception ignored) {
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 10 SECOND SEEK
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void seekBy(int milliseconds) {

        if (mediaPlayer == null) {
            return;
        }

        try {

            int duration =
                    mediaPlayer.getDuration();

            int current =
                    mediaPlayer.getCurrentPosition();

            int target =
                    current + milliseconds;

            if (target < 0) {
                target = 0;
            }

            if (target > duration) {
                target = duration;
            }

            mediaPlayer.seekTo(
                    target
            );

            hideFinishedMessage();

            if (duration > 0) {

                int progress =
                        (int) (
                                target * 1000L
                                        / duration
                        );

                seekBar.setProgress(
                        progress
                );
            }

            timeText.setText(
                    formatTime(target)
                            + " / "
                            + formatTime(duration)
            );

            // अगर 10 सेकंड आगे करने पर end पर पहुंचे
            if (target >= duration &&
                    duration > 0) {

                playPauseButton.setText(
                        "PLAY"
                );
            }

        } catch (Exception ignored) {
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // VIDEO ZOOM / FULL SCREEN
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void adjustVideoSize() {

        if (surfaceView == null ||
                videoWidth <= 0 ||
                videoHeight <= 0) {

            return;
        }

        try {

            View rootView =
                    surfaceView.getRootView();

            int screenWidth =
                    rootView.getWidth();

            int screenHeight =
                    rootView.getHeight();

            if (screenWidth <= 0 ||
                    screenHeight <= 0) {

                screenWidth =
                        getResources()
                                .getDisplayMetrics()
                                .widthPixels;

                screenHeight =
                        getResources()
                                .getDisplayMetrics()
                                .heightPixels;
            }

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
                            surfaceView
                                    .getLayoutParams();

            /*
             * ZOOM-TO-FILL:
             *
             * वीडियो screen को पूरा भरेगी।
             * जरूरत पड़ने पर किनारों से थोड़ा हिस्सा
             * crop होगा।
             */

            if (videoRatio > screenRatio) {

                // वीडियो ज्यादा चौड़ी है
                params.height =
                        screenHeight;

                params.width =
                        (int) (
                                screenHeight
                                        * videoRatio
                        );

            } else {

                // वीडियो ज्यादा लंबी है
                params.width =
                        screenWidth;

                params.height =
                        (int) (
                                screenWidth
                                        / videoRatio
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
    // PROGRESS
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
                                position * 1000L
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
    // HIDE FINISHED MESSAGE
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void hideFinishedMessage() {

        if (statusText != null) {

            statusText.setVisibility(
                    View.GONE
            );
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

                    int duration =
                            mediaPlayer.getDuration();

                    int position =
                            mediaPlayer.getCurrentPosition();

                    // केवल तब auto-resume करें
                    // जब video पूरी तरह समाप्त न हुई हो
                    if (duration > 0 &&
                            position < duration - 500) {

                        mediaPlayer.start();

                        if (playPauseButton != null) {

                            playPauseButton.setText(
                                    "PAUSE"
                            );
                        }
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
                .setTitle(
                        "Vishal Secure"
                )
                .setMessage(message)
                .setPositiveButton(
                        "OK",
                        (dialog, which) ->
                                finish()
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
