package com.vishalsecure.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Color;
import android.graphics.Point;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class VideoPlayerActivity extends Activity
        implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;

    private TextView statusText;
    private TextView currentTimeText;
    private TextView totalTimeText;

    private Button playPauseButton;
    private Button rewindButton;
    private Button forwardButton;

    private SeekBar progressBar;

    private String videoUriString;

    private boolean surfaceReady = false;
    private boolean gestureMoved = false;
    private boolean controlsVisible = false;
    private boolean userSeeking = false;

    private int videoWidth = 0;
    private int videoHeight = 0;

    private float zoomFactor = 1.0f;
    private float playbackSpeed = 1.0f;

    private float downX;
    private float downY;
    private float initialDistance;

    private long lastTapTime = 0;

    private final Handler handler = new Handler();

    private final Runnable hideControlsRunnable =
            new Runnable() {
                @Override
                public void run() {
                    hideControls();
                }
            };

    private final Runnable progressRunnable =
            new Runnable() {
                @Override
                public void run() {
                    updateProgress();
                    handler.postDelayed(this, 500);
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        hideSystemUI();

        videoUriString =
                getIntent().getStringExtra("video_uri");

        if (videoUriString == null ||
                videoUriString.isEmpty()) {
            finish();
            return;
        }

        createPlayerUI();

        handler.post(progressRunnable);
    }

    private void hideSystemUI() {

        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
    }

    private void createPlayerUI() {

        final FrameLayout root =
                new FrameLayout(this);

        root.setBackgroundColor(Color.BLACK);

        // =====================================================
        // VIDEO SURFACE
        // IMPORTANT:
        // setZOrderMediaOverlay(true) intentionally removed
        // =====================================================

        surfaceView =
                new SurfaceView(this);

        surfaceView.setSecure(true);
        surfaceView.setKeepScreenOn(true);

        surfaceHolder =
                surfaceView.getHolder();

        surfaceHolder.addCallback(this);

        FrameLayout.LayoutParams surfaceParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        surfaceParams.gravity = Gravity.CENTER;

        root.addView(
                surfaceView,
                surfaceParams
        );

        // =====================================================
        // TOUCH AREA
        // =====================================================

        TextView touchView =
                new TextView(this);

        touchView.setBackgroundColor(
                Color.TRANSPARENT
        );

        FrameLayout.LayoutParams touchParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        root.addView(
                touchView,
                touchParams
        );

        // =====================================================
        // STATUS
        // =====================================================

        statusText =
                new TextView(this);

        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        statusText.setBackgroundColor(Color.TRANSPARENT);
        statusText.setVisibility(View.GONE);

        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        statusParams.gravity = Gravity.CENTER;

        root.addView(
                statusText,
                statusParams
        );

        // =====================================================
        // CURRENT TIME - LEFT
        // =====================================================

        currentTimeText =
                new TextView(this);

        currentTimeText.setTextColor(Color.WHITE);
        currentTimeText.setTextSize(14);
        currentTimeText.setGravity(Gravity.CENTER);
        currentTimeText.setShadowLayer(
                4,
                0,
                0,
                Color.BLACK
        );

        currentTimeText.setText("00:00");
        currentTimeText.setVisibility(View.GONE);

        FrameLayout.LayoutParams currentTimeParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        currentTimeParams.gravity =
                Gravity.BOTTOM | Gravity.LEFT;

        currentTimeParams.leftMargin = 25;
        currentTimeParams.bottomMargin = 72;

        root.addView(
                currentTimeText,
                currentTimeParams
        );

        // =====================================================
        // TOTAL TIME - RIGHT
        // =====================================================

        totalTimeText =
                new TextView(this);

        totalTimeText.setTextColor(Color.WHITE);
        totalTimeText.setTextSize(14);
        totalTimeText.setGravity(Gravity.CENTER);
        totalTimeText.setShadowLayer(
                4,
                0,
                0,
                Color.BLACK
        );

        totalTimeText.setText("00:00");
        totalTimeText.setVisibility(View.GONE);

        FrameLayout.LayoutParams totalTimeParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        totalTimeParams.gravity =
                Gravity.BOTTOM | Gravity.RIGHT;

        totalTimeParams.rightMargin = 25;
        totalTimeParams.bottomMargin = 72;

        root.addView(
                totalTimeText,
                totalTimeParams
        );

        // =====================================================
        // SEEK BAR
        // =====================================================

        progressBar =
                new SeekBar(this);

        progressBar.setMax(1000);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);

        FrameLayout.LayoutParams progressParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        45
                );

        progressParams.gravity =
                Gravity.BOTTOM;

        progressParams.leftMargin = 25;
        progressParams.rightMargin = 25;
        progressParams.bottomMargin = 38;

        root.addView(
                progressBar,
                progressParams
        );

        // =====================================================
        // REWIND 10 SEC
        // =====================================================

        rewindButton =
                new Button(this);

        rewindButton.setText("↶ 10");
        rewindButton.setTextColor(Color.WHITE);
        rewindButton.setTextSize(16);
        rewindButton.setBackgroundColor(
                Color.TRANSPARENT
        );

        rewindButton.setVisibility(View.GONE);

        FrameLayout.LayoutParams rewindParams =
                new FrameLayout.LayoutParams(
                        110,
                        60
                );

        rewindParams.gravity =
                Gravity.BOTTOM |
                        Gravity.CENTER_HORIZONTAL;

        rewindParams.rightMargin = 120;
        rewindParams.bottomMargin = 0;

        root.addView(
                rewindButton,
                rewindParams
        );

        rewindButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        seekBy(-10000);

                        showControls();
                    }
                }
        );

        // =====================================================
        // PLAY / PAUSE
        // =====================================================

        playPauseButton =
                new Button(this);

        playPauseButton.setText("▶");
        playPauseButton.setTextSize(24);
        playPauseButton.setTextColor(Color.WHITE);
        playPauseButton.setBackgroundColor(
                Color.TRANSPARENT
        );

        playPauseButton.setVisibility(View.GONE);

        FrameLayout.LayoutParams buttonParams =
                new FrameLayout.LayoutParams(
                        90,
                        60
                );

        buttonParams.gravity =
                Gravity.BOTTOM |
                        Gravity.CENTER_HORIZONTAL;

        buttonParams.bottomMargin = 0;

        root.addView(
                playPauseButton,
                buttonParams
        );

        playPauseButton.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {

                        if (mediaPlayer == null) {
                            return;
                        }

                        try {

                            if (mediaPlayer.isPlaying()) {

                                mediaPlayer.pause();

                                playPauseButton.setText(
                                        "▶"
                                );

                            } else {

                                mediaPlayer.start();

                                playPauseButton.setText(
                                        "❚❚"
                                );
                            }

                            showControls();

                        } catch (Exception ignored) {
                        }
                    }
                }
        );

        // =====================================================
        // FORWARD 10 SEC
        // =====================================================

        forwardButton =
                new Button(this);

        forwardButton.setText("10 ↷");
        forwardButton.setTextColor(Color.WHITE);
        forwardButton.setTextSize(16);
        forwardButton.setBackgroundColor(
                Color.TRANSPARENT
        );

        forwardButton.setVisibility(View.GONE);

        FrameLayout.LayoutParams forwardParams =
                new FrameLayout.LayoutParams(
                        110,
                        60
                );

        forwardParams.gravity =
                Gravity.BOTTOM |
                        Gravity.CENTER_HORIZONTAL;

        forwardParams.leftMargin = 120;
        forwardParams.bottomMargin = 0;

        root.addView(
                forwardButton,
                forwardParams
        );

        forwardButton.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {

                        seekBy(10000);

                        showControls();
                    }
                }
        );

        // =====================================================
        // SEEK BAR LISTENER
        // =====================================================

        progressBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser) {

                        if (fromUser &&
                                mediaPlayer != null) {

                            try {

                                int duration =
                                        mediaPlayer.getDuration();

                                if (duration > 0) {

                                    int position =
                                            (int) (
                                                    (progress /
                                                            1000.0f)
                                                            * duration
                                            );

                                    mediaPlayer.seekTo(
                                            position
                                    );

                                    showTime();
                                }

                            } catch (Exception ignored) {
                            }
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(
                            SeekBar seekBar) {

                        userSeeking = true;

                        showControls();
                    }

                    @Override
                    public void onStopTrackingTouch(
                            SeekBar seekBar) {

                        userSeeking = false;

                        showControls();
                    }
                }
        );

        // =====================================================
        // TOUCH / GESTURES
        // =====================================================

        touchView.setOnTouchListener(
                new View.OnTouchListener() {

                    @Override
                    public boolean onTouch(
                            View v,
                            MotionEvent event) {

                        switch (
                                event.getActionMasked()
                        ) {

                            case MotionEvent.ACTION_DOWN:

                                downX =
                                        event.getX();

                                downY =
                                        event.getY();

                                gestureMoved = false;

                                if (event.getPointerCount()
                                        == 1) {

                                    long now =
                                            System.currentTimeMillis();

                                    if (now -
                                            lastTapTime
                                            < 350) {

                                        changePlaybackSpeed();
                                    }

                                    lastTapTime = now;

                                    showControls();
                                }

                                return true;

                            case MotionEvent.ACTION_POINTER_DOWN:

                                if (event.getPointerCount()
                                        >= 2) {

                                    initialDistance =
                                            distance(event);

                                    gestureMoved = false;
                                }

                                return true;

                            case MotionEvent.ACTION_MOVE:

                                if (event.getPointerCount()
                                        >= 2) {

                                    float newDistance =
                                            distance(event);

                                    if (initialDistance > 0) {

                                        float scale =
                                                newDistance /
                                                        initialDistance;

                                        zoomFactor *= scale;

                                        if (zoomFactor < 1.0f) {
                                            zoomFactor = 1.0f;
                                        }

                                        if (zoomFactor > 3.0f) {
                                            zoomFactor = 3.0f;
                                        }

                                        initialDistance =
                                                newDistance;

                                        resizeVideo();

                                        gestureMoved = true;
                                    }

                                    return true;
                                }

                                if (event.getPointerCount()
                                        == 1) {

                                    float dx =
                                            event.getX() -
                                                    downX;

                                    float dy =
                                            event.getY() -
                                                    downY;

                                    if (Math.abs(dx) > 30 &&
                                            Math.abs(dx) >
                                                    Math.abs(dy)) {

                                        gestureMoved = true;

                                        seekBy(
                                                dx > 0
                                                        ? 10000
                                                        : -10000
                                        );

                                        downX =
                                                event.getX();
                                    }
                                }

                                return true;

                            case MotionEvent.ACTION_UP:

                                if (!gestureMoved) {

                                    showControls();
                                }

                                return true;
                        }

                        return true;
                    }
                }
        );

        setContentView(root);
    }

    // =====================================================
    // DISTANCE
    // =====================================================

    private float distance(
            MotionEvent event) {

        if (event.getPointerCount() < 2) {
            return 0;
        }

        float dx =
                event.getX(0) -
                        event.getX(1);

        float dy =
                event.getY(0) -
                        event.getY(1);

        return (float) Math.sqrt(
                dx * dx +
                        dy * dy
        );
    }

    // =====================================================
    // SEEK
    // =====================================================

    private void seekBy(int amount) {

        if (mediaPlayer == null) {
            return;
        }

        try {

            int current =
                    mediaPlayer.getCurrentPosition();

            int duration =
                    mediaPlayer.getDuration();

            int newPosition =
                    current + amount;

            if (newPosition < 0) {
                newPosition = 0;
            }

            if (newPosition > duration) {
                newPosition = duration;
            }

            mediaPlayer.seekTo(
                    newPosition
            );

            showTime();

            showControls();

        } catch (Exception ignored) {
        }
    }

    // =====================================================
    // PLAYBACK SPEED
    // =====================================================

    private void changePlaybackSpeed() {

        if (mediaPlayer == null) {
            return;
        }

        try {

            if (android.os.Build.VERSION.SDK_INT >= 23) {

                if (playbackSpeed == 1.0f) {

                    playbackSpeed = 1.5f;

                } else if (playbackSpeed == 1.5f) {

                    playbackSpeed = 2.0f;

                } else if (playbackSpeed == 2.0f) {

                    playbackSpeed = 0.5f;

                } else {

                    playbackSpeed = 1.0f;
                }

                mediaPlayer.setPlaybackParams(
                        mediaPlayer
                                .getPlaybackParams()
                                .setSpeed(
                                        playbackSpeed
                                )
                );
            }

            showControls();

        } catch (Exception ignored) {
        }
    }

    // =====================================================
    // SURFACE CREATED
    // =====================================================

    @Override
    public void surfaceCreated(
            SurfaceHolder holder) {

        surfaceReady = true;

        preparePlayer();
    }

    @Override
    public void surfaceChanged(
            SurfaceHolder holder,
            int format,
            int width,
            int height) {

        resizeVideo();
    }

    @Override
    public void surfaceDestroyed(
            SurfaceHolder holder) {

        surfaceReady = false;

        if (mediaPlayer != null) {

            try {

                mediaPlayer.setDisplay(
                        null
                );

            } catch (Exception ignored) {
            }
        }
    }

    // =====================================================
    // PREPARE PLAYER
    // =====================================================

    private void preparePlayer() {

        if (!surfaceReady) {
            return;
        }

        try {

            releasePlayer();

            mediaPlayer =
                    new MediaPlayer();

            mediaPlayer.setAudioStreamType(
                    AudioManager.STREAM_MUSIC
            );

            mediaPlayer.setDataSource(
                    this,
                    Uri.parse(videoUriString)
            );

            mediaPlayer.setDisplay(
                    surfaceHolder
            );

            mediaPlayer.setOnPreparedListener(
                    new MediaPlayer.OnPreparedListener() {

                        @Override
                        public void onPrepared(
                                MediaPlayer mp) {

                            videoWidth =
                                    mp.getVideoWidth();

                            videoHeight =
                                    mp.getVideoHeight();

                            zoomFactor = 1.0f;

                            resizeVideo();

                            statusText.setVisibility(
                                    View.GONE
                            );

                            mp.start();

                            playPauseButton.setText(
                                    "❚❚"
                            );

                            showControls();
                        }
                    }
            );

            mediaPlayer.setOnCompletionListener(
                    new MediaPlayer.OnCompletionListener() {

                        @Override
                        public void onCompletion(
                                MediaPlayer mp) {

                            playPauseButton.setText(
                                    "▶"
                            );

                            if (progressBar != null) {

                                progressBar.setProgress(
                                        1000
                                );
                            }

                            showControls();
                        }
                    }
            );

            mediaPlayer.setOnErrorListener(
                    new MediaPlayer.OnErrorListener() {

                        @Override
                        public boolean onError(
                                MediaPlayer mp,
                                int what,
                                int extra) {

                            statusText.setText(
                                    "Video चलाने में समस्या"
                            );

                            statusText.setVisibility(
                                    View.VISIBLE
                            );

                            return true;
                        }
                    }
            );

            statusText.setText(
                    "Video loading..."
            );

            statusText.setVisibility(
                    View.VISIBLE
            );

            mediaPlayer.prepareAsync();

        } catch (Exception e) {

            statusText.setText(
                    "Video open नहीं हो सकी"
            );

            statusText.setVisibility(
                    View.VISIBLE
            );
        }
    }

    // =====================================================
    // RESIZE VIDEO
    // =====================================================

    private void resizeVideo() {

        if (surfaceView == null ||
                videoWidth <= 0 ||
                videoHeight <= 0) {

            return;
        }

        int screenWidth;
        int screenHeight;

        try {

            Display display =
                    getWindowManager()
                            .getDefaultDisplay();

            Point size =
                    new Point();

            display.getRealSize(size);

            screenWidth = size.x;
            screenHeight = size.y;

        } catch (Exception e) {

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

        boolean landscape =
                screenWidth > screenHeight;

        int width;
        int height;

        if (landscape) {

            float widthRatio =
                    (float) screenWidth /
                            videoWidth;

            float heightRatio =
                    (float) screenHeight /
                            videoHeight;

            float scale =
                    Math.min(
                            widthRatio,
                            heightRatio
                    );

            scale *= zoomFactor;

            width =
                    (int) (
                            videoWidth *
                                    scale
                    );

            height =
                    (int) (
                            videoHeight *
                                    scale
                    );

        } else {

            int maxWidth =
                    (int) (
                            screenWidth *
                                    0.82f
                    );

            int maxHeight =
                    (int) (
                            screenHeight *
                                    0.65f
                    );

            float widthRatio =
                    (float) maxWidth /
                            videoWidth;

            float heightRatio =
                    (float) maxHeight /
                            videoHeight;

            float scale =
                    Math.min(
                            widthRatio,
                            heightRatio
                    );

            scale *= zoomFactor;

            width =
                    (int) (
                            videoWidth *
                                    scale
                    );

            height =
                    (int) (
                            videoHeight *
                                    scale
                    );
        }

        if (width < 1) {
            width = 1;
        }

        if (height < 1) {
            height = 1;
        }

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        width,
                        height
                );

        params.gravity =
                Gravity.CENTER;

        surfaceView.setLayoutParams(
                params
        );
    }

    // =====================================================
    // UPDATE PROGRESS
    // =====================================================

    private void updateProgress() {

        if (mediaPlayer == null ||
                progressBar == null) {

            return;
        }

        try {

            int duration =
                    mediaPlayer.getDuration();

            int current =
                    mediaPlayer.getCurrentPosition();

            if (duration > 0) {

                int progress =
                        (int) (
                                (current /
                                        (float) duration)
                                        * 1000
                        );

                if (!userSeeking &&
                        progressBar.getVisibility()
                                == View.VISIBLE) {

                    progressBar.setProgress(
                            progress
                    );
                }

                if (controlsVisible) {

                    showTime();
                }
            }

        } catch (Exception ignored) {
        }
    }

    // =====================================================
    // SHOW TIME
    // =====================================================

    private void showTime() {

        if (mediaPlayer == null ||
                currentTimeText == null ||
                totalTimeText == null) {

            return;
        }

        try {

            int current =
                    mediaPlayer.getCurrentPosition();

            int duration =
                    mediaPlayer.getDuration();

            currentTimeText.setText(
                    formatTime(current)
            );

            totalTimeText.setText(
                    formatTime(duration)
            );

        } catch (Exception ignored) {
        }
    }

    // =====================================================
    // FORMAT TIME
    // =====================================================

    private String formatTime(
            int milliseconds) {

        if (milliseconds < 0) {
            milliseconds = 0;
        }

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

    // =====================================================
    // SHOW CONTROLS
    // =====================================================

    private void showControls() {

        if (currentTimeText == null ||
                totalTimeText == null ||
                progressBar == null ||
                playPauseButton == null ||
                rewindButton == null ||
                forwardButton == null) {

            return;
        }

        controlsVisible = true;

        currentTimeText.setVisibility(
                View.VISIBLE
        );

        totalTimeText.setVisibility(
                View.VISIBLE
        );

        progressBar.setVisibility(
                View.VISIBLE
        );

        rewindButton.setVisibility(
                View.VISIBLE
        );

        playPauseButton.setVisibility(
                View.VISIBLE
        );

        forwardButton.setVisibility(
                View.VISIBLE
        );

        // =================================================
        // CONTROLS को सबसे ऊपर रखें
        // =================================================

        currentTimeText.bringToFront();
        totalTimeText.bringToFront();

        progressBar.bringToFront();

        rewindButton.bringToFront();
        playPauseButton.bringToFront();
        forwardButton.bringToFront();

        showTime();

        handler.removeCallbacks(
                hideControlsRunnable
        );

        handler.postDelayed(
                hideControlsRunnable,
                2000
        );
    }

    // =====================================================
    // HIDE CONTROLS
    // =====================================================

    private void hideControls() {

        if (currentTimeText == null ||
                totalTimeText == null ||
                progressBar == null ||
                playPauseButton == null ||
                rewindButton == null ||
                forwardButton == null) {

            return;
        }

        controlsVisible = false;

        currentTimeText.setVisibility(
                View.GONE
        );

        totalTimeText.setVisibility(
                View.GONE
        );

        progressBar.setVisibility(
                View.GONE
        );

        rewindButton.setVisibility(
                View.GONE
        );

        playPauseButton.setVisibility(
                View.GONE
        );

        forwardButton.setVisibility(
                View.GONE
        );
    }

    // =====================================================
    // RESUME
    // =====================================================

    @Override
    protected void onResume() {

        super.onResume();

        hideSystemUI();

        if (surfaceReady &&
                mediaPlayer == null) {

            preparePlayer();
        }
    }

    // =====================================================
    // WINDOW FOCUS
    // =====================================================

    @Override
    public void onWindowFocusChanged(
            boolean hasFocus) {

        super.onWindowFocusChanged(
                hasFocus
        );

        if (hasFocus) {
            hideSystemUI();
        }
    }

    // =====================================================
    // RELEASE PLAYER
    // =====================================================

    private void releasePlayer() {

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

    // =====================================================
    // PAUSE
    // =====================================================

    @Override
    protected void onPause() {

        super.onPause();

        if (mediaPlayer != null) {

            try {

                if (mediaPlayer.isPlaying()) {

                    mediaPlayer.pause();

                    if (playPauseButton != null) {

                        playPauseButton.setText(
                                "▶"
                        );
                    }
                }

            } catch (Exception ignored) {
            }
        }
    }

    // =====================================================
    // DESTROY
    // =====================================================

    @Override
    protected void onDestroy() {

        handler.removeCallbacks(
                progressRunnable
        );

        handler.removeCallbacks(
                hideControlsRunnable
        );

        releasePlayer();

        super.onDestroy();
    }
}
