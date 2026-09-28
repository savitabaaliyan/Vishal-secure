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
import android.widget.LinearLayout;
import android.widget.TextView;

public class VideoPlayerActivity extends Activity
        implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;

    private TextView statusText;
    private TextView timeText;

    private String videoUriString;

    private boolean surfaceReady = false;
    private boolean controlsVisible = true;

    private int videoWidth = 0;
    private int videoHeight = 0;

    private float zoomFactor = 1.0f;
    private float playbackSpeed = 1.0f;

    private Handler handler = new Handler();

    private float downX;
    private float downY;
    private float initialDistance;

    private boolean gestureMoved = false;

    private LinearLayout controlsLayout;
    private Button playPauseButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        videoUriString = getIntent().getStringExtra("video_uri");

        buildPlayerScreen();
    }

    private void buildPlayerScreen() {

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

        View touchOverlay = new View(this);
        touchOverlay.setBackgroundColor(Color.TRANSPARENT);

        FrameLayout.LayoutParams touchParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        root.addView(touchOverlay, touchParams);

        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        statusParams.gravity =
                Gravity.TOP | Gravity.CENTER_HORIZONTAL;

        statusParams.topMargin = 25;

        root.addView(statusText, statusParams);

        timeText = new TextView(this);
        timeText.setTextColor(Color.WHITE);
        timeText.setTextSize(13);
        timeText.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams timeParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        timeParams.gravity =
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;

        timeParams.bottomMargin = 75;

        root.addView(timeText, timeParams);

        controlsLayout = new LinearLayout(this);
        controlsLayout.setGravity(Gravity.CENTER);

        playPauseButton = new Button(this);
        playPauseButton.setText("▶");
        playPauseButton.setTextSize(22);

        playPauseButton.setOnClickListener(v -> {

            if (mediaPlayer == null) {
                return;
            }

            if (mediaPlayer.isPlaying()) {

                mediaPlayer.pause();

                playPauseButton.setText("▶");

            } else {

                mediaPlayer.start();

                playPauseButton.setText("❚❚");

                startProgressUpdater();
            }
        });

        controlsLayout.addView(playPauseButton);

        FrameLayout.LayoutParams controlParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        controlParams.gravity =
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;

        controlParams.bottomMargin = 15;

        root.addView(controlsLayout, controlParams);

        touchOverlay.setOnTouchListener(
                new View.OnTouchListener() {

                    long lastTap = 0;

                    @Override
                    public boolean onTouch(
                            View v,
                            MotionEvent event) {

                        if (event.getAction() ==
                                MotionEvent.ACTION_UP) {

                            long now =
                                    System.currentTimeMillis();

                            if (now - lastTap < 350) {

                                changeSpeed();

                                lastTap = 0;

                                return true;
                            }

                            lastTap = now;
                        }

                        return handleGesture(v, event);
                    }
                }
        );

        setContentView(root);

        hideSystemBars();
    }

    private boolean handleGesture(
            View v,
            MotionEvent event) {

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:

                downX = event.getX();
                downY = event.getY();

                gestureMoved = false;

                if (event.getPointerCount() == 2) {
                    initialDistance =
                            getDistance(event);
                }

                return true;

            case MotionEvent.ACTION_POINTER_DOWN:

                if (event.getPointerCount() == 2) {
                    initialDistance =
                            getDistance(event);
                }

                return true;

            case MotionEvent.ACTION_MOVE:

                if (event.getPointerCount() == 2) {

                    float currentDistance =
                            getDistance(event);

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

                if (Math.abs(dx) > 40 &&
                        Math.abs(dx) > Math.abs(dy)) {

                    gestureMoved = true;

                    if (dx > 0) {
                        seekBy(10000);
                    } else {
                        seekBy(-10000);
                    }

                    downX = x;
                }

                return true;

            case MotionEvent.ACTION_UP:

                if (!gestureMoved) {

                    controlsVisible =
                            !controlsVisible;

                    controlsLayout.setVisibility(
                            controlsVisible
                                    ? View.VISIBLE
                                    : View.GONE
                    );

                    statusText.setVisibility(
                            controlsVisible
                                    ? View.VISIBLE
                                    : View.GONE
                    );

                    timeText.setVisibility(
                            controlsVisible
                                    ? View.VISIBLE
                                    : View.GONE
                    );
                }

                return true;
        }

        return true;
    }

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

        return (float)
                Math.sqrt(x * x + y * y);
    }

    private void seekBy(int milliseconds) {

        if (mediaPlayer == null) {
            return;
        }

        try {

            int current =
                    mediaPlayer.getCurrentPosition();

            int duration =
                    mediaPlayer.getDuration();

            int target =
                    current + milliseconds;

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
                    "Speed: " +
                    playbackSpeed +
                    "x"
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

            mediaPlayer.setDisplay(holder);

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

                        mp.start();

                        playPauseButton
                                .setText("❚❚");

                        startProgressUpdater();
                    }
            );

            mediaPlayer.setOnCompletionListener(
                    mp -> {

                        playPauseButton
                                .setText("▶");

                        statusText
                                .setText("Completed");
                    }
            );

            mediaPlayer.setOnErrorListener(
                    (mp, what, extra) -> {

                        statusText.setText(
                                "Video playback error"
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

            mediaPlayer.prepareAsync();

        } catch (Exception e) {

            statusText.setText(
                    "Unable to play video"
            );
        }
    }

    /*
     * =====================================================
     * VIDEO SIZE
     *
     * PORTRAIT  = CENTERED VIDEO
     * LANDSCAPE = FULL SCREEN
     * =====================================================
     */
    private void applyVideoFit() {

        if (surfaceView == null ||
                videoWidth <= 0 ||
                videoHeight <= 0) {
            return;
        }

        int screenWidth =
                surfaceView.getWidth();

        int screenHeight =
                surfaceView.getHeight();

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

        float videoRatio =
                (float) videoWidth /
                (float) videoHeight;

        boolean landscape =
                screenWidth > screenHeight;

        int targetWidth;
        int targetHeight;

        if (landscape) {

            // LANDSCAPE = FULL SCREEN
            targetWidth = screenWidth;
            targetHeight =
                    (int) (targetWidth / videoRatio);

            if (targetHeight < screenHeight) {

                targetHeight = screenHeight;

                targetWidth =
                        (int) (targetHeight *
                                videoRatio);
            }

        } else {

            // PORTRAIT = CENTERED
            targetWidth =
                    (int) (screenWidth * 0.82f);

            targetHeight =
                    (int) (targetWidth /
                            videoRatio);

            int maxHeight =
                    (int) (screenHeight * 0.65f);

            if (targetHeight > maxHeight) {

                targetHeight = maxHeight;

                targetWidth =
                        (int) (targetHeight *
                                videoRatio);
            }
        }

        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams)
                        surfaceView.getLayoutParams();

        params.width = targetWidth;
        params.height = targetHeight;
        params.gravity = Gravity.CENTER;

        surfaceView.setLayoutParams(params);
    }

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

        boolean landscape =
                screenWidth > screenHeight;

        int baseWidth;
        int baseHeight;

        if (landscape) {

            baseWidth = screenWidth;

            baseHeight =
                    (int) (baseWidth /
                            videoRatio);

            if (baseHeight < screenHeight) {

                baseHeight = screenHeight;

                baseWidth =
                        (int) (baseHeight *
                                videoRatio);
            }

        } else {

            baseWidth =
                    (int) (screenWidth * 0.82f);

            baseHeight =
                    (int) (baseWidth /
                            videoRatio);

            int maxHeight =
                    (int) (screenHeight * 0.65f);

            if (baseHeight > maxHeight) {

                baseHeight = maxHeight;

                baseWidth =
                        (int) (baseHeight *
                                videoRatio);
            }
        }

        int targetWidth =
                (int) (baseWidth *
                        zoomFactor);

        int targetHeight =
                (int) (baseHeight *
                        zoomFactor);

        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams)
                        surfaceView.getLayoutParams();

        params.width = targetWidth;
        params.height = targetHeight;
        params.gravity = Gravity.CENTER;

        surfaceView.setLayoutParams(params);
    }

    private void startProgressUpdater() {

        handler.postDelayed(
                new Runnable() {

                    @Override
                    public void run() {

                        if (mediaPlayer != null) {

                            try {

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

    private String formatTime(
            int milliseconds) {

        int totalSeconds =
                milliseconds / 1000;

        int minutes =
                totalSeconds / 60;

        int seconds =
                totalSeconds % 60;

        return String.format(
                "%02d:%02d",
                minutes,
                seconds
        );
    }

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
    protected void onPause() {

        super.onPause();

        if (mediaPlayer != null &&
                mediaPlayer.isPlaying()) {

            mediaPlayer.pause();

            playPauseButton.setText("▶");
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        hideSystemBars();
    }

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

सर, एक महत्वपूर्ण बात: Portrait में मैंने वीडियो को लगभग 82% screen width और अधिकतम 65% screen height रखा है, इसलिए वह बीच में पूरा दिखाई देगा और कटेगा नहीं। Landscape में aspect ratio बनाए रखते हुए screen को पूरा भरने के लिए "CENTER_CROP" जैसा व्यवहार होगा; इसलिए अलग ratio की वीडियो में किनारे थोड़े crop हो सकते हैं।
