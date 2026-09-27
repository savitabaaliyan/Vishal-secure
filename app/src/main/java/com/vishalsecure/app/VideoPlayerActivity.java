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
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public class VideoPlayerActivity extends Activity
        implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;

    private TextView statusText;
    private TextView timeText;

    private View touchOverlay;

    private String videoUriString;

    private boolean surfaceReady = false;

    private int videoWidth = 0;
    private int videoHeight = 0;

    // Zoom
    private float zoomFactor = 1.0f;

    private float baseWidth = 0;
    private float baseHeight = 0;

    private ScaleGestureDetector scaleDetector;

    // Double touch
    private long lastTapTime = 0;
    private float lastTapX = 0;
    private static final long DOUBLE_TAP_TIME = 300;

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

        // Secure screen
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
        // VIDEO SURFACE
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
        // TIME
        // नीचे छोटा time indicator
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

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
                Color.argb(
                        150,
                        0,
                        0,
                        0
                )
        );

        FrameLayout.LayoutParams
                timeParams =
                new FrameLayout.LayoutParams(
                        dp(120),
                        dp(35)
                );

        timeParams.gravity =
                Gravity.BOTTOM |
                        Gravity.CENTER_HORIZONTAL;

        timeParams.bottomMargin =
                dp(8);

        root.addView(
                timeText,
                timeParams
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

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // SCALE / PINCH ZOOM
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        scaleDetector =
                new ScaleGestureDetector(
                        this,
                        new ScaleGestureDetector
                                .SimpleOnScaleGestureListener() {

                            @Override
                            public boolean onScale(
                                    ScaleGestureDetector detector) {

                                float scale =
                                        detector.getScaleFactor();

                                zoomFactor =
                                        zoomFactor * scale;

                                // Minimum 1x
                                if (zoomFactor < 1.0f) {
                                    zoomFactor = 1.0f;
                                }

                                // Maximum 3x
                                if (zoomFactor > 3.0f) {
                                    zoomFactor = 3.0f;
                                }

                                applyZoom();

                                return true;
                            }
                        }
                );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // TOUCH CONTROL
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        touchOverlay.setOnTouchListener(
                (v, event) -> {

                    scaleDetector.onTouchEvent(
                            event
                    );

                    if (event.getAction() ==
                            MotionEvent.ACTION_UP) {

                        // Pinch के बाद tap action न करें
                        if (scaleDetector.isInProgress()) {
                            return true;
                        }

                        long now =
                                System.currentTimeMillis();

                        float x =
                                event.getX();

                        float width =
                                touchOverlay.getWidth();

                        boolean doubleTap =
                                (now - lastTapTime)
                                        <= DOUBLE_TAP_TIME;

                        if (doubleTap) {

                            if (x < width / 2f) {

                                // LEFT
                                seekBy(-10000);

                            } else {

                                // RIGHT
                                seekBy(10000);
                            }

                            lastTapTime = 0;

                        } else {

                            lastTapTime = now;
                            lastTapX = x;

                            /*
                             * थोड़ा delay:
                             * अगर दूसरा tap आता है तो
                             * उसे 10 sec seek माना जाएगा।
                             */
                            handler.postDelayed(
                                    () -> {

                                        if (lastTapTime == now) {

                                            togglePlayPause();

                                            lastTapTime = 0;
                                        }

                                    },
                                    DOUBLE_TAP_TIME
                            );
                        }

                        return true;
                    }

                    return true;
                }
        );

        setContentView(root);

        // Screen को immersive रखने की कोशिश
        hideSystemBars();
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // HIDE SYSTEM BARS
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void hideSystemBars() {

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
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

        /*
         * Rotation के बाद zoom reset करके
         * video को पूरा fit करें।
         */
        zoomFactor = 1.0f;

        applyVideoFit();
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

                        applyVideoFit();
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

                            zoomFactor = 1.0f;

                            applyVideoFit();

                            int duration =
                                    mp.getDuration();

                            timeText.setText(
                                    "00:00 / "
                                            + formatTime(
                                            duration
                                    )
                            );

                            hideFinishedMessage();

                            mp.start();

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

            /*
             * Video समाप्त हो चुकी है तो
             * शुरुआत से फिर चलायें।
             */
            if (duration > 0 &&
                    position >= duration - 500) {

                mediaPlayer.seekTo(0);

                hideFinishedMessage();

                mediaPlayer.start();

                return;
            }

            if (mediaPlayer.isPlaying()) {

                mediaPlayer.pause();

            } else {

                hideFinishedMessage();

                mediaPlayer.start();
            }

        } catch (Exception ignored) {
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 10 SEC SEEK
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void seekBy(
            int milliseconds) {

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

            if (timeText != null) {

                timeText.setText(
                        formatTime(target)
                                + " / "
                                + formatTime(duration)
                );
            }

        } catch (Exception ignored) {
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // NORMAL VIDEO FIT
    // पूरा video दिखाना
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void applyVideoFit() {

        if (surfaceView == null ||
                videoWidth <= 0 ||
                videoHeight <= 0) {

            return;
        }

        try {

            int screenWidth =
                    getResources()
                            .getDisplayMetrics()
                            .widthPixels;

            int screenHeight =
                    getResources()
                            .getDisplayMetrics()
                            .heightPixels;

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
             * FIT INSIDE:
             *
             * पूरा video दिखाई देगा।
             * ऊपर/नीचे या दोनों तरफ black area
             * हो सकता है, लेकिन video crop नहीं होगी।
             */

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

            baseWidth =
                    params.width;

            baseHeight =
                    params.height;

        } catch (Exception ignored) {
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ZOOM
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void applyZoom() {

        if (surfaceView == null ||
                videoWidth <= 0 ||
                videoHeight <= 0) {

            return;
        }

        try {

            int screenWidth =
                    getResources()
                            .getDisplayMetrics()
                            .widthPixels;

            int screenHeight =
                    getResources()
                            .getDisplayMetrics()
                            .heightPixels;

            float videoRatio =
                    (float) videoWidth
                            / videoHeight;

            float screenRatio =
                    (float) screenWidth
                            / screenHeight;

            float fitWidth;
            float fitHeight;

            if (videoRatio > screenRatio) {

                fitWidth =
                        screenWidth;

                fitHeight =
                        screenWidth
                                / videoRatio;

            } else {

                fitHeight =
                        screenHeight;

                fitWidth =
                        screenHeight
                                * videoRatio;
            }

            int newWidth =
                    (int) (
                            fitWidth
                                    * zoomFactor
                    );

            int newHeight =
                    (int) (
                            fitHeight
                                    * zoomFactor
                    );

            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams)
                            surfaceView
                                    .getLayoutParams();

            params.width =
                    newWidth;

            params.height =
                    newHeight;

            params.gravity =
                    Gravity.CENTER;

            surfaceView.setLayoutParams(
                    params
            );

        } catch (Exception ignored) {
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // UPDATE TIME
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void updateProgress() {

        if (mediaPlayer == null ||
                timeText == null) {

            return;
        }

        try {

            int duration =
                    mediaPlayer.getDuration();

            int position =
                    mediaPlayer.getCurrentPosition();

            timeText.setText(
                    formatTime(position)
                            + " / "
                            + formatTime(duration)
            );

        } catch (Exception ignored) {
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // FINISHED MESSAGE HIDE
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

        hideSystemBars();

        if (surfaceReady &&
                mediaPlayer != null) {

            try {

                int duration =
                        mediaPlayer.getDuration();

                int position =
                        mediaPlayer.getCurrentPosition();

                if (duration > 0 &&
                        position < duration - 500 &&
                        !mediaPlayer.isPlaying()) {

                    mediaPlayer.start();
                }

            } catch (Exception ignored) {
            }
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // RELEASE
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
                .setMessage(
                        message
                )
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
