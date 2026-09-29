package com.vishalsecure.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
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

    private FrameLayout rootLayout;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private TextView statusText;

    private Button playPauseButton;
    private SeekBar progressBar;

    private String videoUriString;

    private boolean surfaceReady = false;
    private boolean playerPrepared = false;
    private boolean controlsVisible = false;
    private boolean userSeeking = false;

    private int videoWidth = 0;
    private int videoHeight = 0;

    private float zoomFactor = 1.0f;
    private float initialDistance = 0.0f;

    // Double-tap detection
    private long lastTapTime = 0;
    private float lastTapX = 0;
    private float lastTapY = 0;

    private static final long DOUBLE_TAP_TIME = 350;
    private static final long SEEK_MS = 10000;

    private final Handler handler = new Handler();

    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {

            if (mediaPlayer != null && playerPrepared) {

                try {
                    int current = mediaPlayer.getCurrentPosition();
                    int duration = mediaPlayer.getDuration();

                    if (!userSeeking) {
                        progressBar.setMax(duration);
                        progressBar.setProgress(current);
                        showTime(current, duration);
                    }

                } catch (Exception ignored) {
                }
            }

            handler.postDelayed(this, 500);
        }
    };

    private final Runnable hideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            hideControls();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().setStatusBarColor(Color.BLACK);

        videoUriString = getIntent().getStringExtra("video_uri");

        if (videoUriString == null) {
            finish();
            return;
        }

        buildUI();

        handler.post(updateProgressRunnable);
    }

    private void buildUI() {

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        setContentView(rootLayout);

        // ============================================================
        // VIDEO SURFACE
        // ============================================================

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

        rootLayout.addView(surfaceView, videoParams);

        // ============================================================
        // TOUCH LAYER
        // ============================================================

        TextView touchView = new TextView(this);

        touchView.setBackgroundColor(Color.TRANSPARENT);

        FrameLayout.LayoutParams touchParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        rootLayout.addView(touchView, touchParams);

        touchView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getActionMasked()) {

                    case MotionEvent.ACTION_DOWN:

                        float tapX = event.getX();
                        float tapY = event.getY();

                        long now = System.currentTimeMillis();

                        // ------------------------------------------------
                        // DOUBLE TAP
                        // ------------------------------------------------

                        if (now - lastTapTime <= DOUBLE_TAP_TIME) {

                            float screenWidth = v.getWidth();

                            // Double tap LEFT = 10 sec back
                            if (tapX < screenWidth / 2.0f) {

                                seekRelative(-SEEK_MS);

                            }
                            // Double tap RIGHT = 10 sec forward
                            else {

                                seekRelative(SEEK_MS);
                            }

                            lastTapTime = 0;

                            return true;
                        }

                        // First tap
                        lastTapTime = now;
                        lastTapX = tapX;
                        lastTapY = tapY;

                        showControls();

                        return true;

                    case MotionEvent.ACTION_POINTER_DOWN:

                        if (event.getPointerCount() >= 2) {

                            initialDistance =
                                    distanceBetweenFingers(event);
                        }

                        return true;

                    case MotionEvent.ACTION_MOVE:

                        if (event.getPointerCount() >= 2 &&
                                initialDistance > 0) {

                            float currentDistance =
                                    distanceBetweenFingers(event);

                            float scale =
                                    currentDistance / initialDistance;

                            zoomFactor =
                                    zoomFactor * scale;

                            if (zoomFactor < 1.0f) {
                                zoomFactor = 1.0f;
                            }

                            if (zoomFactor > 3.0f) {
                                zoomFactor = 3.0f;
                            }

                            initialDistance =
                                    currentDistance;

                            applyZoom();
                        }

                        return true;

                    case MotionEvent.ACTION_POINTER_UP:

                        initialDistance = 0;

                        return true;

                    case MotionEvent.ACTION_UP:

                        initialDistance = 0;

                        return true;
                }

                return true;
            }
        });

        // ============================================================
        // CURRENT TIME
        // ============================================================

        currentTimeText = new TextView(this);

        currentTimeText.setText("00:00");
        currentTimeText.setTextColor(Color.WHITE);
        currentTimeText.setTextSize(14);
        currentTimeText.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout.LayoutParams currentParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        currentParams.gravity =
                Gravity.START | Gravity.BOTTOM;

        currentParams.leftMargin = 12;
        currentParams.bottomMargin = 72;

        rootLayout.addView(
                currentTimeText,
                currentParams
        );

        // ============================================================
        // TOTAL TIME
        // ============================================================

        totalTimeText = new TextView(this);

        totalTimeText.setText("00:00");
        totalTimeText.setTextColor(Color.WHITE);
        totalTimeText.setTextSize(14);
        totalTimeText.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout.LayoutParams totalParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        totalParams.gravity =
                Gravity.END | Gravity.BOTTOM;

        totalParams.rightMargin = 12;
        totalParams.bottomMargin = 72;

        rootLayout.addView(
                totalTimeText,
                totalParams
        );

        // ============================================================
        // TIMELINE
        // ============================================================

        progressBar = new SeekBar(this);

        progressBar.setMax(1000);
        progressBar.setProgress(0);

        FrameLayout.LayoutParams progressParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        progressParams.gravity = Gravity.BOTTOM;

        progressParams.leftMargin = 8;
        progressParams.rightMargin = 8;
        progressParams.bottomMargin = 38;

        rootLayout.addView(
                progressBar,
                progressParams
        );

        progressBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser) {

                        if (fromUser &&
                                mediaPlayer != null &&
                                playerPrepared) {

                            showTime(
                                    progress,
                                    mediaPlayer.getDuration()
                            );
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

                        if (mediaPlayer != null &&
                                playerPrepared) {

                            try {

                                mediaPlayer.seekTo(
                                        seekBar.getProgress()
                                );

                            } catch (Exception ignored) {
                            }
                        }

                        userSeeking = false;

                        showControls();
                    }
                }
        );

        // ============================================================
        // PLAY / PAUSE BUTTON
        // ============================================================

        playPauseButton = new Button(this);

        playPauseButton.setText("▶");
        playPauseButton.setTextColor(Color.WHITE);
        playPauseButton.setTextSize(25);

        // हल्का पारदर्शी गोल background
        GradientDrawable buttonBackground =
                new GradientDrawable();

        buttonBackground.setShape(
                GradientDrawable.OVAL
        );

        buttonBackground.setColor(
                Color.argb(210, 70, 70, 70)
        );

        buttonBackground.setStroke(
                2,
                Color.WHITE
        );

        playPauseButton.setBackground(
                buttonBackground
        );

        playPauseButton.setVisibility(
                View.GONE
        );

        FrameLayout.LayoutParams playParams =
                new FrameLayout.LayoutParams(
                        75,
                        75
                );

        playParams.gravity =
                Gravity.CENTER_HORIZONTAL |
                Gravity.BOTTOM;

        playParams.bottomMargin = 85;

        rootLayout.addView(
                playPauseButton,
                playParams
        );

        playPauseButton.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {

                        if (mediaPlayer == null ||
                                !playerPrepared) {

                            return;
                        }

                        try {

                            if (mediaPlayer.isPlaying()) {

                                mediaPlayer.pause();

                                playPauseButton.setText("▶");

                            } else {

                                mediaPlayer.start();

                                playPauseButton.setText("❚❚");
                            }

                            showControls();

                        } catch (Exception ignored) {
                        }
                    }
                }
        );

        // ============================================================
        // STATUS TEXT
        // ============================================================

        statusText = new TextView(this);

        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(15);
        statusText.setGravity(Gravity.CENTER);

        statusText.setVisibility(
                View.GONE
        );

        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        statusParams.gravity = Gravity.CENTER;

        rootLayout.addView(
                statusText,
                statusParams
        );
    }

    // ================================================================
    // 10 SECOND SEEK
    // ================================================================

    private void seekRelative(long amount) {

        if (mediaPlayer == null ||
                !playerPrepared) {

            return;
        }

        try {

            int current =
                    mediaPlayer.getCurrentPosition();

            int duration =
                    mediaPlayer.getDuration();

            long newPosition =
                    current + amount;

            if (newPosition < 0) {
                newPosition = 0;
            }

            if (newPosition > duration) {
                newPosition = duration;
            }

            mediaPlayer.seekTo(
                    (int) newPosition
            );

            progressBar.setProgress(
                    (int) newPosition
            );

            showTime(
                    (int) newPosition,
                    duration
            );

        } catch (Exception ignored) {
        }
    }

    // ================================================================
    // SURFACE CALLBACKS
    // ================================================================

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
    }

    @Override
    public void surfaceDestroyed(
            SurfaceHolder holder) {

        surfaceReady = false;
        playerPrepared = false;

        releasePlayer();
    }

    // ================================================================
    // PREPARE PLAYER
    // ================================================================

    private void preparePlayer() {

        if (!surfaceReady) {
            return;
        }

        releasePlayer();

        try {

            mediaPlayer =
                    new MediaPlayer();

            playerPrepared = false;

            mediaPlayer.setAudioStreamType(
                    AudioManager.STREAM_MUSIC
            );

            Uri videoUri =
                    Uri.parse(videoUriString);

            mediaPlayer.setDataSource(
                    this,
                    videoUri
            );

            mediaPlayer.setDisplay(
                    surfaceHolder
            );

            mediaPlayer.setOnPreparedListener(
                    new MediaPlayer.OnPreparedListener() {

                        @Override
                        public void onPrepared(
                                MediaPlayer mp) {

                            playerPrepared = true;

                            try {

                                videoWidth =
                                        mp.getVideoWidth();

                                videoHeight =
                                        mp.getVideoHeight();

                                int duration =
                                        mp.getDuration();

                                progressBar.setMax(
                                        duration
                                );

                                progressBar.setProgress(
                                        0
                                );

                                showTime(
                                        0,
                                        duration
                                );

                                applyZoom();

                                mp.start();

                                playPauseButton.setText(
                                        "❚❚"
                                );

                                showControls();

                            } catch (Exception ignored) {
                            }
                        }
                    }
            );

            mediaPlayer.setOnCompletionListener(
                    new MediaPlayer.OnCompletionListener() {

                        @Override
                        public void onCompletion(
                                MediaPlayer mp) {

                            try {

                                playPauseButton.setText(
                                        "▶"
                                );

                                progressBar.setProgress(
                                        progressBar.getMax()
                                );

                                showTime(
                                        progressBar.getMax(),
                                        progressBar.getMax()
                                );

                                showControls();

                            } catch (Exception ignored) {
                            }
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

                            playerPrepared = false;

                            statusText.setText(
                                    "Video playback error"
                            );

                            statusText.setVisibility(
                                    View.VISIBLE
                            );

                            return true;
                        }
                    }
            );

            mediaPlayer.prepareAsync();

        } catch (Exception e) {

            playerPrepared = false;

            statusText.setText(
                    "Unable to play video"
            );

            statusText.setVisibility(
                    View.VISIBLE
            );
        }
    }

    // ================================================================
    // CONTROLS
    // ================================================================

    private void showControls() {

        controlsVisible = true;

        playPauseButton.bringToFront();
        currentTimeText.bringToFront();
        totalTimeText.bringToFront();
        progressBar.bringToFront();

        playPauseButton.setVisibility(
                View.VISIBLE
        );

        currentTimeText.setVisibility(
                View.VISIBLE
        );

        totalTimeText.setVisibility(
                View.VISIBLE
        );

        progressBar.setVisibility(
                View.VISIBLE
        );

        handler.removeCallbacks(
                hideControlsRunnable
        );

        handler.postDelayed(
                hideControlsRunnable,
                2000
        );
    }

    private void hideControls() {

        controlsVisible = false;

        // केवल Play/Pause छुपेगा।
        // Timeline और time हमेशा दिखाई देंगे।

        playPauseButton.setVisibility(
                View.GONE
        );

        currentTimeText.setVisibility(
                View.VISIBLE
        );

        totalTimeText.setVisibility(
                View.VISIBLE
        );

        progressBar.setVisibility(
                View.VISIBLE
        );
    }

    // ================================================================
    // TIME
    // ================================================================

    private void showTime(
            int current,
            int total) {

        currentTimeText.setText(
                formatTime(current)
        );

        totalTimeText.setText(
                formatTime(total)
        );
    }

    private String formatTime(
            int milliseconds) {

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

    // ================================================================
    // ZOOM
    // ================================================================

    private float distanceBetweenFingers(
            MotionEvent event) {

        if (event.getPointerCount() < 2) {
            return 0;
        }

        float x =
                event.getX(0) -
                event.getX(1);

        float y =
                event.getY(0) -
                event.getY(1);

        return (float) Math.sqrt(
                x * x + y * y
        );
    }

    private void applyZoom() {

        if (surfaceView == null ||
                videoWidth <= 0 ||
                videoHeight <= 0) {

            return;
        }

        int screenWidth =
                rootLayout.getWidth();

        int screenHeight =
                rootLayout.getHeight();

        if (screenWidth <= 0 ||
                screenHeight <= 0) {

            return;
        }

        float videoRatio =
                (float) videoWidth /
                (float) videoHeight;

        float screenRatio =
                (float) screenWidth /
                (float) screenHeight;

        int baseWidth;
        int baseHeight;

        if (videoRatio > screenRatio) {

            baseWidth =
                    screenWidth;

            baseHeight =
                    (int) (
                            screenWidth /
                            videoRatio
                    );

        } else {

            baseHeight =
                    screenHeight;

            baseWidth =
                    (int) (
                            screenHeight *
                            videoRatio
                    );
        }

        int newWidth =
                (int) (
                        baseWidth *
                        zoomFactor
                );

        int newHeight =
                (int) (
                        baseHeight *
                        zoomFactor
                );

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        newWidth,
                        newHeight
                );

        params.gravity =
                Gravity.CENTER;

        surfaceView.setLayoutParams(
                params
        );
    }

    // ================================================================
    // RELEASE PLAYER
    // ================================================================

    private void releasePlayer() {

        playerPrepared = false;

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

    // ================================================================
    // PAUSE
    // ================================================================

    @Override
    protected void onPause() {

        super.onPause();

        if (mediaPlayer != null &&
                playerPrepared) {

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

    // ================================================================
    // DESTROY
    // ================================================================

    @Override
    protected void onDestroy() {

        handler.removeCallbacks(
                updateProgressRunnable
        );

        handler.removeCallbacks(
                hideControlsRunnable
        );

        releasePlayer();

        super.onDestroy();
    }
}
