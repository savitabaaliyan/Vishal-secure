package com.vishalsecure.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

public class VideoPlayerActivity extends Activity
        implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;

    private TextView statusText;
    private TextView timeText;
    private Button playPauseButton;

    private String videoUriString;

    private boolean surfaceReady = false;
    private boolean controlsVisible = false;
    private boolean gestureMoved = false;

    private int videoWidth = 0;
    private int videoHeight = 0;

    private float zoomFactor = 1.0f;
    private float playbackSpeed = 1.0f;

    private float downX;
    private float downY;
    private float initialDistance;

    private long lastTapTime = 0;

    private final Handler handler = new Handler();

    private final Runnable hideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            hideControls();
        }
    };

    private final Runnable hideStatusRunnable = new Runnable() {
        @Override
        public void run() {
            if (statusText != null) {
                statusText.setVisibility(View.GONE);
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

        buildScreen();
    }

    private void buildScreen() {

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        surfaceView = new SurfaceView(this);
        surfaceView.setSecure(true);
        surfaceView.setKeepScreenOn(true);

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        FrameLayout.LayoutParams videoParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        videoParams.gravity = Gravity.CENTER;

        root.addView(surfaceView, videoParams);

        /*
         * Touch layer
         */
        View touchView = new View(this);
        touchView.setBackgroundColor(Color.TRANSPARENT);

        FrameLayout.LayoutParams touchParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        root.addView(touchView, touchParams);

        /*
         * Status / Speed
         */
        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(15);
        statusText.setGravity(Gravity.CENTER);
        statusText.setBackgroundColor(
                Color.argb(150, 0, 0, 0)
        );
        statusText.setPadding(18, 8, 18, 8);
        statusText.setVisibility(View.GONE);

        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        statusParams.gravity =
                Gravity.TOP | Gravity.CENTER_HORIZONTAL;

        statusParams.topMargin = 25;

        root.addView(statusText, statusParams);

        /*
         * Time
         */
        timeText = new TextView(this);
        timeText.setTextColor(Color.WHITE);
        timeText.setTextSize(15);
        timeText.setGravity(Gravity.CENTER);
        timeText.setBackgroundColor(
                Color.argb(170, 0, 0, 0)
        );
        timeText.setPadding(20, 8, 20, 8);
        timeText.setVisibility(View.GONE);

        FrameLayout.LayoutParams timeParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        timeParams.gravity =
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;

        timeParams.bottomMargin = 75;

        root.addView(timeText, timeParams);

        /*
         * Play / Pause
         */
        playPauseButton = new Button(this);
        playPauseButton.setText("▶");
        playPauseButton.setTextSize(22);
        playPauseButton.setVisibility(View.GONE);

        playPauseButton.setOnClickListener(
                v -> {

                    togglePlay();

                    showControls();
                }
        );

        FrameLayout.LayoutParams buttonParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        buttonParams.gravity =
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;

        buttonParams.bottomMargin = 10;

        root.addView(playPauseButton, buttonParams);

        /*
         * Touch handling
         */
        touchView.setOnTouchListener(
                new View.OnTouchListener() {

                    @Override
                    public boolean onTouch(
                            View v,
                            MotionEvent event) {

                        switch (event.getActionMasked()) {

                            case MotionEvent.ACTION_DOWN:

                                downX = event.getX();
                                downY = event.getY();

                                gestureMoved = false;

                                if (event.getPointerCount() == 2) {

                                    initialDistance =
                                            distance(event);
                                }

                                return true;

                            case MotionEvent.ACTION_POINTER_DOWN:

                                if (event.getPointerCount() == 2) {

                                    initialDistance =
                                            distance(event);
                                }

                                return true;

                            case MotionEvent.ACTION_MOVE:

                                return handleMove(event);

                            case MotionEvent.ACTION_UP:

                                handleTouchUp(event);

                                return true;
                        }

                        return true;
                    }
                }
        );

        setContentView(root);

        hideSystemBars();
    }

    /*
     * Show controls
     */
    private void showControls() {

        controlsVisible = true;

        if (timeText != null) {
            timeText.setVisibility(View.VISIBLE);
        }

        if (playPauseButton != null) {
            playPauseButton.setVisibility(View.VISIBLE);
        }

        handler.removeCallbacks(
                hideControlsRunnable
        );

        handler.postDelayed(
                hideControlsRunnable,
                2500
        );
    }

    /*
     * Hide controls
     */
    private void hideControls() {

        controlsVisible = false;

        if (timeText != null) {
            timeText.setVisibility(View.GONE);
        }

        if (playPauseButton != null) {
            playPauseButton.setVisibility(View.GONE);
        }
    }

    /*
     * Play / Pause
     */
    private void togglePlay() {

        if (mediaPlayer == null) {
            return;
        }

        try {

            if (mediaPlayer.isPlaying()) {

                mediaPlayer.pause();

                playPauseButton.setText("▶");

            } else {

                mediaPlayer.start();

                playPauseButton.setText("❚❚");

                updateProgress();
            }

            showTime();

        } catch (Exception ignored) {
        }
    }

    /*
     * Touch movement
     */
    private boolean handleMove(MotionEvent event) {

        if (event.getPointerCount() == 2) {

            float currentDistance =
                    distance(event);

            if (initialDistance > 0) {

                float ratio =
                        currentDistance /
                        initialDistance;

                if (ratio > 1.05f) {

                    zoomFactor += 0.02f;

                    if (zoomFactor > 2.0f) {
                        zoomFactor = 2.0f;
                    }

                    applyZoom();

                    gestureMoved = true;

                } else if (ratio < 0.95f) {

                    zoomFactor -= 0.02f;

                    if (zoomFactor < 0.6f) {
                        zoomFactor = 0.6f;
                    }

                    applyZoom();

                    gestureMoved = true;
                }

                initialDistance =
                        currentDistance;
            }

            return true;
        }

        float x = event.getX();
        float y = event.getY();

        float dx = x - downX;
        float dy = y - downY;

        if (Math.abs(dx) > 50 &&
                Math.abs(dx) > Math.abs(dy)) {

            gestureMoved = true;

            if (dx > 0) {

                seek(10000);

            } else {

                seek(-10000);
            }

            downX = x;

            showTime();
        }

        return true;
    }

    /*
     * Touch release
     */
    private void handleTouchUp(MotionEvent event) {

        if (!gestureMoved) {

            long now =
                    System.currentTimeMillis();

            /*
             * Double tap = speed
             */
            if (now - lastTapTime < 350) {

                changeSpeed();

                lastTapTime = 0;

                showControls();

                return;
            }

            /*
             * Single tap
             */
            lastTapTime = now;

            showTime();
            showControls();
        }
    }

    /*
     * Show time
     */
    private void showTime() {

        if (mediaPlayer == null ||
                timeText == null) {
            return;
        }

        try {

            int current =
                    mediaPlayer.getCurrentPosition();

            int duration =
                    mediaPlayer.getDuration();

            timeText.setText(
                    formatTime(current)
                            + " / "
                            + formatTime(duration)
            );

            timeText.setVisibility(
                    View.VISIBLE
            );

        } catch (Exception ignored) {
        }
    }

    /*
     * Distance between fingers
     */
    private float distance(MotionEvent event) {

        if (event.getPointerCount() < 2) {
            return 0;
        }

        float x =
                event.getX(0) -
                event.getX(1);

        float y =
                event.getY(0) -
                event.getY(1);

        return (float)
                Math.sqrt(
                        x * x + y * y
                );
    }

    /*
     * Seek
     */
    private void seek(int amount) {

        if (mediaPlayer == null) {
            return;
        }

        try {

            int current =
                    mediaPlayer.getCurrentPosition();

            int duration =
                    mediaPlayer.getDuration();

            int target =
                    current + amount;

            if (target < 0) {
                target = 0;
            }

            if (target > duration) {
                target = duration;
            }

            mediaPlayer.seekTo(target);

        } catch (Exception ignored) {
        }
    }

    /*
     * Speed
     */
    private void changeSpeed() {

        if (mediaPlayer == null) {
            return;
        }

        playbackSpeed += 0.25f;

        if (playbackSpeed > 2.0f) {
            playbackSpeed = 0.5f;
        }

        try {

            if (android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.M) {

                mediaPlayer.setPlaybackParams(
                        mediaPlayer
                                .getPlaybackParams()
                                .setSpeed(playbackSpeed)
                );
            }

            statusText.setText(
                    playbackSpeed + "x"
            );

            statusText.setVisibility(
                    View.VISIBLE
            );

            handler.removeCallbacks(
                    hideStatusRunnable
            );

            handler.postDelayed(
                    hideStatusRunnable,
                    1500
            );

        } catch (Exception ignored) {
        }
    }

    @Override
    public void surfaceCreated(
            SurfaceHolder holder) {

        surfaceReady = true;

        startVideo();
    }

    @Override
    public void surfaceChanged(
            SurfaceHolder holder,
            int format,
            int width,
            int height) {

        if (mediaPlayer != null) {

            try {

                mediaPlayer.setDisplay(
                        holder
                );

            } catch (Exception ignored) {
            }

            zoomFactor = 1.0f;

            applyVideoFit();
        }
    }

    @Override
    public void surfaceDestroyed(
            SurfaceHolder holder) {

        surfaceReady = false;

        releasePlayer();
    }

    /*
     * Start video
     */
    private void startVideo() {

        if (!surfaceReady ||
                videoUriString == null) {
            return;
        }

        releasePlayer();

        try {

            mediaPlayer = new MediaPlayer();

            mediaPlayer.setAudioStreamType(
                    AudioManager.STREAM_MUSIC
            );

            mediaPlayer.setDisplay(
                    surfaceHolder
            );

            mediaPlayer.setOnVideoSizeChangedListener(
                    (mp, width, height) -> {

                        videoWidth = width;
                        videoHeight = height;

                        applyVideoFit();
                    }
            );

            mediaPlayer.setOnPreparedListener(
                    mp -> {

                        videoWidth =
                                mp.getVideoWidth();

                        videoHeight =
                                mp.getVideoHeight();

                        zoomFactor = 1.0f;
                        playbackSpeed = 1.0f;

                        applyVideoFit();

                        statusText.setVisibility(
                                View.GONE
                        );

                        hideControls();

                        mp.start();

                        playPauseButton.setText(
                                "❚❚"
                        );

                        updateProgress();
                    }
            );

            mediaPlayer.setOnCompletionListener(
                    mp -> {

                        playPauseButton.setText(
                                "▶"
                        );

                        showTime();
                        showControls();
                    }
            );

            mediaPlayer.setOnErrorListener(
                    (mp, what, extra) -> {

                        statusText.setText(
                                "Video playback error"
                        );

                        statusText.setVisibility(
                                View.VISIBLE
                        );

                        return true;
                    }
            );

            Uri uri =
                    Uri.parse(videoUriString);

            mediaPlayer.setDataSource(
                    this,
                    uri
            );

            statusText.setText(
                    "Loading..."
            );

            statusText.setVisibility(
                    View.VISIBLE
            );

            mediaPlayer.prepareAsync();

        } catch (Exception e) {

            statusText.setText(
                    "Unable to play video"
            );

            statusText.setVisibility(
                    View.VISIBLE
            );
        }
    }

    /*
     * VIDEO FIT
     *
     * Landscape:
     * उपलब्ध पूरी screen के अंदर
     * वीडियो का maximum possible size।
     *
     * कोई crop नहीं।
     */
    private void applyVideoFit() {

        if (surfaceView == null ||
                videoWidth <= 0 ||
                videoHeight <= 0) {
            return;
        }

        int screenWidth =
                getResources()
                        .getDisplayMetrics()
                        .widthPixels;

        int screenHeight =
                getResources()
                        .getDisplayMetrics()
                        .heightPixels;

        float videoRatio =
                (float) videoWidth /
                (float) videoHeight;

        /*
         * पहले पूरी screen को target मानें।
         */
        int width = screenWidth;

        int height =
                (int) (width / videoRatio);

        /*
         * अगर calculated height
         * screen से बड़ी है,
         * तो height के हिसाब से fit करें।
         */
        if (height > screenHeight) {

            height = screenHeight;

            width =
                    (int) (height * videoRatio);
        }

        /*
         * Portrait में थोड़ा छोटा center video
         */
        if (screenWidth <= screenHeight) {

            width =
                    (int) (width * 0.82f);

            height =
                    (int) (width / videoRatio);

            if (height > (int)(screenHeight * 0.65f)) {

                height =
                        (int)(screenHeight * 0.65f);

                width =
                        (int)(height * videoRatio);
            }
        }

        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams)
                        surfaceView.getLayoutParams();

        params.width = width;
        params.height = height;

        params.gravity =
                Gravity.CENTER;

        surfaceView.setLayoutParams(
                params
        );
    }

    /*
     * Zoom
     */
    private void applyZoom() {

        if (surfaceView == null ||
                videoWidth <= 0 ||
                videoHeight <= 0) {
            return;
        }

        int screenWidth =
                getResources()
                        .getDisplayMetrics()
                        .widthPixels;

        int screenHeight =
                getResources()
                        .getDisplayMetrics()
                        .heightPixels;

        float videoRatio =
                (float) videoWidth /
                (float) videoHeight;

        int width = screenWidth;

        int height =
                (int) (width / videoRatio);

        if (height > screenHeight) {

            height = screenHeight;

            width =
                    (int) (height * videoRatio);
        }

        if (screenWidth <= screenHeight) {

            width =
                    (int)(width * 0.82f);

            height =
                    (int)(width / videoRatio);

            if (height > (int)(screenHeight * 0.65f)) {

                height =
                        (int)(screenHeight * 0.65f);

                width =
                        (int)(height * videoRatio);
            }
        }

        width =
                (int)(width * zoomFactor);

        height =
                (int)(height * zoomFactor);

        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams)
                        surfaceView.getLayoutParams();

        params.width = width;
        params.height = height;

        params.gravity =
                Gravity.CENTER;

        surfaceView.setLayoutParams(
                params
        );
    }

    /*
     * Progress
     */
    private void updateProgress() {

        handler.postDelayed(
                new Runnable() {

                    @Override
                    public void run() {

                        if (mediaPlayer != null) {

                            try {

                                if (mediaPlayer.isPlaying() &&
                                        timeText.getVisibility()
                                                == View.VISIBLE) {

                                    int current =
                                            mediaPlayer
                                                    .getCurrentPosition();

                                    int duration =
                                            mediaPlayer
                                                    .getDuration();

                                    timeText.setText(
                                            formatTime(current)
                                                    + " / "
                                                    + formatTime(duration)
                                    );
                                }

                            } catch (Exception ignored) {
                            }
                        }

                        handler.postDelayed(
                                this,
                                500
                        );
                    }
                },
                500
        );
    }

    /*
     * Format time
     */
    private String formatTime(int ms) {

        int total =
                ms / 1000;

        int hours =
                total / 3600;

        int minutes =
                (total % 3600) / 60;

        int seconds =
                total % 60;

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

    /*
     * Fullscreen
     */
    private void hideSystemBars() {

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

    @Override
    public void onWindowFocusChanged(
            boolean hasFocus) {

        super.onWindowFocusChanged(
                hasFocus
        );

        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    protected void onPause() {

        super.onPause();

        if (mediaPlayer != null) {

            try {

                if (mediaPlayer.isPlaying()) {

                    mediaPlayer.pause();

                    playPauseButton.setText(
                            "▶"
                    );
                }

            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        hideSystemBars();
    }

    /*
     * Release
     */
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

    @Override
    protected void onDestroy() {

        handler.removeCallbacksAndMessages(null);

        releasePlayer();

        super.onDestroy();
    }
}
