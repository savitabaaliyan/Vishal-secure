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
    private boolean controlsVisible = false;
    private boolean userSeeking = false;

    private int videoWidth = 0;
    private int videoHeight = 0;

    private float zoomFactor = 1.0f;
    private float initialDistance = 0.0f;

    private float downX;
    private float downY;

    private long lastTapTime = 0;

    private final Handler handler = new Handler();

    private final Runnable hideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            hideControls();
        }
    };

    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {

            if (mediaPlayer != null && mediaPlayer.isPrepared()) {

                try {
                    int position = mediaPlayer.getCurrentPosition();
                    int duration = mediaPlayer.getDuration();

                    if (!userSeeking) {
                        progressBar.setProgress(position);
                    }

                    showTime(position, duration);

                } catch (Exception ignored) {
                }
            }

            handler.postDelayed(this, 500);
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

        hideSystemUI();

        videoUriString = getIntent().getStringExtra("video_uri");

        if (videoUriString == null) {
            finish();
            return;
        }

        createPlayerUI();
    }

    private void hideSystemUI() {

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void createPlayerUI() {

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        setContentView(rootLayout);

        // ------------------------------------------------
        // VIDEO SURFACE
        // ------------------------------------------------

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

        rootLayout.addView(surfaceView, videoParams);

        // ------------------------------------------------
        // TOUCH AREA
        // ------------------------------------------------

        TextView touchView = new TextView(this);

        touchView.setBackgroundColor(Color.TRANSPARENT);
        touchView.setClickable(true);
        touchView.setFocusable(false);

        FrameLayout.LayoutParams touchParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        rootLayout.addView(touchView, touchParams);

        setupTouchControls(touchView);

        // ------------------------------------------------
        // CURRENT TIME
        // ------------------------------------------------

        currentTimeText = new TextView(this);

        currentTimeText.setTextColor(Color.WHITE);
        currentTimeText.setTextSize(13);
        currentTimeText.setGravity(Gravity.CENTER);
        currentTimeText.setText("00:00");

        FrameLayout.LayoutParams currentParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        currentParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
        currentParams.leftMargin = 25;
        currentParams.bottomMargin = 72;

        rootLayout.addView(currentTimeText, currentParams);

        // ------------------------------------------------
        // TOTAL TIME
        // ------------------------------------------------

        totalTimeText = new TextView(this);

        totalTimeText.setTextColor(Color.WHITE);
        totalTimeText.setTextSize(13);
        totalTimeText.setGravity(Gravity.CENTER);
        totalTimeText.setText("00:00");

        FrameLayout.LayoutParams totalParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        totalParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        totalParams.rightMargin = 25;
        totalParams.bottomMargin = 72;

        rootLayout.addView(totalTimeText, totalParams);

        // ------------------------------------------------
        // TIMELINE
        // ------------------------------------------------

        progressBar = new SeekBar(this);

        progressBar.setMax(1000);
        progressBar.setProgress(0);

        FrameLayout.LayoutParams seekParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        seekParams.gravity = Gravity.BOTTOM;
        seekParams.leftMargin = 20;
        seekParams.rightMargin = 20;
        seekParams.bottomMargin = 38;

        rootLayout.addView(progressBar, seekParams);

        progressBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser) {

                        if (fromUser &&
                                mediaPlayer != null &&
                                mediaPlayer.isPrepared()) {

                            try {
                                int duration = mediaPlayer.getDuration();

                                int newPosition =
                                        (int) ((progress / 1000.0f) * duration);

                                currentTimeText.setText(
                                        formatTime(newPosition)
                                );

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

                        if (mediaPlayer != null &&
                                mediaPlayer.isPrepared()) {

                            try {

                                int duration =
                                        mediaPlayer.getDuration();

                                int newPosition =
                                        (int) ((seekBar.getProgress()
                                                / 1000.0f) * duration);

                                mediaPlayer.seekTo(newPosition);

                            } catch (Exception ignored) {
                            }
                        }

                        userSeeking = false;
                        showControls();
                    }
                }
        );

        // ------------------------------------------------
        // PLAY / PAUSE ONLY
        // ------------------------------------------------

        playPauseButton = new Button(this);

        playPauseButton.setText("▶");
        playPauseButton.setTextColor(Color.WHITE);
        playPauseButton.setTextSize(22);

        playPauseButton.setBackgroundColor(
                Color.argb(190, 0, 0, 0)
        );

        playPauseButton.setPadding(0, 0, 0, 0);

        FrameLayout.LayoutParams playParams =
                new FrameLayout.LayoutParams(
                        75,
                        75
                );

        playParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        playParams.bottomMargin = 0;

        rootLayout.addView(playPauseButton, playParams);

        playPauseButton.setVisibility(View.GONE);

        playPauseButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        if (mediaPlayer == null ||
                                !mediaPlayer.isPrepared()) {
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

        // ------------------------------------------------
        // STATUS
        // ------------------------------------------------

        statusText = new TextView(this);

        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        statusText.setVisibility(View.GONE);

        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        statusParams.gravity = Gravity.CENTER;

        rootLayout.addView(statusText, statusParams);

        handler.post(updateProgressRunnable);
    }

    // ====================================================
    // TOUCH
    // ====================================================

    private void setupTouchControls(TextView touchView) {

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

                                initialDistance = 0;

                                showControls();

                                return true;

                            case MotionEvent.ACTION_POINTER_DOWN:

                                if (event.getPointerCount() >= 2) {

                                    initialDistance =
                                            getDistance(event);
                                }

                                return true;

                            case MotionEvent.ACTION_MOVE:

                                if (event.getPointerCount() >= 2) {

                                    float newDistance =
                                            getDistance(event);

                                    if (initialDistance > 0) {

                                        float scale =
                                                newDistance /
                                                        initialDistance;

                                        zoomFactor =
                                                zoomFactor * scale;

                                        if (zoomFactor < 1.0f) {
                                            zoomFactor = 1.0f;
                                        }

                                        if (zoomFactor > 3.0f) {
                                            zoomFactor = 3.0f;
                                        }

                                        applyZoom();

                                        initialDistance =
                                                newDistance;
                                    }

                                    return true;
                                }

                                return true;

                            case MotionEvent.ACTION_UP:

                                showControls();

                                return true;
                        }

                        return true;
                    }
                }
        );
    }

    // ====================================================
    // ZOOM
    // ====================================================

    private float getDistance(MotionEvent event) {

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

        if (videoWidth <= 0 ||
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

        int width;
        int height;

        if (videoRatio > screenRatio) {

            width = screenWidth;

            height =
                    (int) (screenWidth /
                            videoRatio);

        } else {

            height = screenHeight;

            width =
                    (int) (screenHeight *
                            videoRatio);
        }

        width =
                (int) (width * zoomFactor);

        height =
                (int) (height * zoomFactor);

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        width,
                        height
                );

        params.gravity = Gravity.CENTER;

        surfaceView.setLayoutParams(params);
    }

    // ====================================================
    // SURFACE
    // ====================================================

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
    }

    // ====================================================
    // PLAYER
    // ====================================================

    private void preparePlayer() {

        if (!surfaceReady) {
            return;
        }

        try {

            releasePlayer();

            mediaPlayer = new MediaPlayer();

            mediaPlayer.setAudioStreamType(
                    AudioManager.STREAM_MUSIC
            );

            mediaPlayer.setDataSource(
                    this,
                    Uri.parse(videoUriString)
            );

            mediaPlayer.setDisplay(surfaceHolder);

            mediaPlayer.setOnPreparedListener(
                    new MediaPlayer.OnPreparedListener() {

                        @Override
                        public void onPrepared(
                                MediaPlayer mp) {

                            try {

                                videoWidth =
                                        mp.getVideoWidth();

                                videoHeight =
                                        mp.getVideoHeight();

                                progressBar.setMax(
                                        mp.getDuration()
                                );

                                showTime(
                                        0,
                                        mp.getDuration()
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

            statusText.setText(
                    "Video could not be played"
            );

            statusText.setVisibility(
                    View.VISIBLE
            );
        }
    }

    // ====================================================
    // CONTROLS
    // ====================================================

    private void showControls() {

        if (playPauseButton == null) {
            return;
        }

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

        if (playPauseButton != null) {

            playPauseButton.setVisibility(
                    View.GONE
            );
        }

        // टाइमलाइन और समय हमेशा वैसे ही रहेंगे
        // जैसा अभी है।

        if (currentTimeText != null) {
            currentTimeText.setVisibility(
                    View.VISIBLE
            );
        }

        if (totalTimeText != null) {
            totalTimeText.setVisibility(
                    View.VISIBLE
            );
        }

        if (progressBar != null) {
            progressBar.setVisibility(
                    View.VISIBLE
            );
        }
    }

    // ====================================================
    // TIME
    // ====================================================

    private void showTime(
            int current,
            int duration) {

        if (currentTimeText != null) {

            currentTimeText.setText(
                    formatTime(current)
            );
        }

        if (totalTimeText != null) {

            totalTimeText.setText(
                    formatTime(duration)
            );
        }
    }

    private String formatTime(int milliseconds) {

        int totalSeconds =
                milliseconds / 1000;

        int seconds =
                totalSeconds % 60;

        int minutes =
                (totalSeconds / 60) % 60;

        int hours =
                totalSeconds / 3600;

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

    // ====================================================
    // RELEASE
    // ====================================================

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

    // ====================================================
    // LIFECYCLE
    // ====================================================

    @Override
    protected void onPause() {

        super.onPause();

        if (mediaPlayer != null) {

            try {

                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();

                    if (playPauseButton != null) {
                        playPauseButton.setText("▶");
                    }
                }

            } catch (Exception ignored) {
            }
        }
    }

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
