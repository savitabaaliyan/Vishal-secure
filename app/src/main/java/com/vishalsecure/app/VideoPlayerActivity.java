package com.vishalsecure.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private boolean encryptedVideo = false;

    private boolean surfaceReady = false;
    private boolean playerPrepared = false;
    private boolean controlsVisible = false;
    private boolean userSeeking = false;

    private int videoWidth = 0;
    private int videoHeight = 0;

    private float zoomFactor = 1.0f;
    private float initialDistance = 0.0f;

    private long lastTapTime = 0L;
    private float lastTapX = 0.0f;
    private float lastTapY = 0.0f;

    private static final long DOUBLE_TAP_TIME = 350;
    private static final int SEEK_MS = 10000;

    private final Handler handler =
            new Handler();

    /*
     * Encryption/decryption background worker.
     */
    private final ExecutorService cryptoExecutor =
            Executors.newSingleThreadExecutor();

    /*
     * Temporary decrypted video.
     */
    private File temporaryDecryptedFile = null;

    private long expiryTime = 0L;

    private boolean expiryHandled = false;

    private boolean decrypting = false;

    private final Runnable updateProgressRunnable =
            new Runnable() {
                @Override
                public void run() {

                    if (mediaPlayer != null &&
                            playerPrepared &&
                            !userSeeking) {

                        try {

                            int position =
                                    mediaPlayer.getCurrentPosition();

                            int duration =
                                    mediaPlayer.getDuration();

                            if (duration > 0) {

                                progressBar.setMax(
                                        duration
                                );

                                progressBar.setProgress(
                                        position
                                );

                                showTime(
                                        position,
                                        duration
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
            };

    /*
     * Expiry को हर 1 सेकंड check करना।
     */
    private final Runnable expiryCheckRunnable =
            new Runnable() {
                @Override
                public void run() {

                    if (expiryHandled) {
                        return;
                    }

                    if (isExpired()) {

                        expirePlayback();

                        return;
                    }

                    handler.postDelayed(
                            this,
                            1000
                    );
                }
            };

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);

        requestWindowFeature(
                Window.FEATURE_NO_TITLE
        );

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        /*
         * Screenshot / screen recording protection.
         */
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        getWindow().setNavigationBarColor(
                Color.BLACK
        );

        getWindow().setStatusBarColor(
                Color.BLACK
        );

        videoUriString =
                getIntent().getStringExtra(
                        "video_uri"
                );

        /*
         * Phase-2A expiry.
         */
        expiryTime =
                getIntent().getLongExtra(
                        "expiry_time",
                        0L
                );

        /*
         * Phase-2B encrypted flag.
         */
        encryptedVideo =
                getIntent().getBooleanExtra(
                        "encrypted_video",
                        false
                );

        buildUI();

        handler.post(
                updateProgressRunnable
        );

        handler.post(
                expiryCheckRunnable
        );

        if (isExpired()) {

            expirePlayback();

            return;
        }
    }

    private boolean isExpired() {

        if (expiryTime <= 0L) {
            return false;
        }

        return System.currentTimeMillis()
                >= expiryTime;
    }

    private void buildUI() {

        rootLayout =
                new FrameLayout(this);

        rootLayout.setBackgroundColor(
                Color.BLACK
        );

        /*
         * Video surface.
         */
        surfaceView =
                new SurfaceView(this);

        surfaceView.setSecure(true);

        surfaceView.setKeepScreenOn(true);

        surfaceHolder =
                surfaceView.getHolder();

        surfaceHolder.addCallback(this);

        rootLayout.addView(
                surfaceView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        /*
         * Transparent touch layer.
         */
        View touchLayer =
                new View(this);

        touchLayer.setBackgroundColor(
                Color.TRANSPARENT
        );

        touchLayer.setOnTouchListener(
                new View.OnTouchListener() {

                    @Override
                    public boolean onTouch(
                            View v,
                            MotionEvent event
                    ) {

                        handleTouch(event);

                        return true;
                    }
                }
        );

        rootLayout.addView(
                touchLayer,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        /*
         * Current time.
         */
        currentTimeText =
                createTimeText();

        FrameLayout.LayoutParams currentParams =
                new FrameLayout.LayoutParams(
                        150,
                        60
                );

        currentParams.gravity =
                Gravity.BOTTOM |
                        Gravity.LEFT;

        currentParams.setMargins(
                20,
                0,
                0,
                25
        );

        rootLayout.addView(
                currentTimeText,
                currentParams
        );

        /*
         * Total time.
         */
        totalTimeText =
                createTimeText();

        FrameLayout.LayoutParams totalParams =
                new FrameLayout.LayoutParams(
                        150,
                        60
                );

        totalParams.gravity =
                Gravity.BOTTOM |
                        Gravity.RIGHT;

        totalParams.setMargins(
                0,
                0,
                20,
                25
        );

        rootLayout.addView(
                totalTimeText,
                totalParams
        );

        /*
         * Timeline.
         */
        progressBar =
                new SeekBar(this);

        progressBar.setMax(
                1000
        );

        progressBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {

                        if (fromUser &&
                                mediaPlayer != null &&
                                playerPrepared) {

                            try {

                                showTime(
                                        progress,
                                        mediaPlayer.getDuration()
                                );

                            } catch (Exception ignored) {
                            }
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(
                            SeekBar seekBar
                    ) {

                        if (isExpired()) {

                            expirePlayback();

                            return;
                        }

                        userSeeking = true;
                    }

                    @Override
                    public void onStopTrackingTouch(
                            SeekBar seekBar
                    ) {

                        if (isExpired()) {

                            expirePlayback();

                            return;
                        }

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
                    }
                }
        );

        FrameLayout.LayoutParams progressParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        70
                );

        progressParams.gravity =
                Gravity.BOTTOM;

        progressParams.setMargins(
                140,
                0,
                140,
                10
        );

        rootLayout.addView(
                progressBar,
                progressParams
        );

        /*
         * Play / Pause button.
         */
        playPauseButton =
                new Button(this);

        playPauseButton.setText(
                "▶"
        );

        playPauseButton.setTextSize(
                22
        );

        playPauseButton.setTextColor(
                Color.WHITE
        );

        GradientDrawable buttonBackground =
                new GradientDrawable();

        buttonBackground.setShape(
                GradientDrawable.OVAL
        );

        buttonBackground.setColor(
                Color.argb(
                        180,
                        0,
                        0,
                        0
                )
        );

        playPauseButton.setBackground(
                buttonBackground
        );

        playPauseButton.setOnClickListener(
                v -> {

                    if (isExpired()) {

                        expirePlayback();

                        return;
                    }

                    togglePlayPause();
                }
        );

        FrameLayout.LayoutParams playParams =
                new FrameLayout.LayoutParams(
                        90,
                        90
                );

        playParams.gravity =
                Gravity.BOTTOM |
                        Gravity.CENTER_HORIZONTAL;

        playParams.setMargins(
                0,
                0,
                0,
                75
        );

        rootLayout.addView(
                playPauseButton,
                playParams
        );

        /*
         * Status text.
         */
        statusText =
                new TextView(this);

        statusText.setTextColor(
                Color.WHITE
        );

        statusText.setTextSize(
                16
        );

        statusText.setGravity(
                Gravity.CENTER
        );

        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        100
                );

        statusParams.gravity =
                Gravity.CENTER;

        rootLayout.addView(
                statusText,
                statusParams
        );

        hideControls();

        setContentView(
                rootLayout
        );
    }

    private TextView createTimeText() {

        TextView text =
                new TextView(this);

        text.setTextColor(
                Color.WHITE
        );

        text.setTextSize(
                14
        );

        text.setGravity(
                Gravity.CENTER
        );

        text.setText(
                "00:00"
        );

        return text;
    }

    private void handleTouch(
            MotionEvent event
    ) {

        if (isExpired()) {

            expirePlayback();

            return;
        }

        switch (
                event.getActionMasked()
        ) {

            case MotionEvent.ACTION_DOWN:

                lastTapX =
                        event.getX();

                lastTapY =
                        event.getY();

                long now =
                        System.currentTimeMillis();

                if (now - lastTapTime
                        <= DOUBLE_TAP_TIME) {

                    if (lastTapX <
                            rootLayout.getWidth()
                                    / 2f) {

                        seekRelative(
                                -SEEK_MS
                        );

                    } else {

                        seekRelative(
                                SEEK_MS
                        );
                    }

                    lastTapTime =
                            0L;

                } else {

                    lastTapTime =
                            now;

                    showControls();
                }

                break;

            case MotionEvent.ACTION_POINTER_DOWN:

                if (event.getPointerCount()
                        >= 2) {

                    initialDistance =
                            distanceBetweenFingers(
                                    event
                            );
                }

                break;

            case MotionEvent.ACTION_MOVE:

                if (event.getPointerCount()
                        >= 2 &&
                        initialDistance > 0) {

                    float currentDistance =
                            distanceBetweenFingers(
                                    event
                            );

                    float scale =
                            currentDistance /
                                    initialDistance;

                    zoomFactor =
                            Math.max(
                                    1.0f,
                                    Math.min(
                                            3.0f,
                                            zoomFactor *
                                                    scale
                                    )
                            );

                    initialDistance =
                            currentDistance;

                    applyZoom();
                }

                break;

            case MotionEvent.ACTION_POINTER_UP:

                initialDistance =
                        0;

                break;
        }
    }

    private float distanceBetweenFingers(
            MotionEvent event
    ) {

        if (event.getPointerCount()
                < 2) {

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

    private void showControls() {

        if (expiryHandled) {
            return;
        }

        controlsVisible =
                true;

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

        playPauseButton.bringToFront();

        currentTimeText.bringToFront();

        totalTimeText.bringToFront();

        progressBar.bringToFront();

        handler.removeCallbacks(
                hideControlsRunnable
        );

        handler.postDelayed(
                hideControlsRunnable,
                2000
        );
    }

    private final Runnable hideControlsRunnable =
            new Runnable() {
                @Override
                public void run() {
                    hideControls();
                }
            };

    private void hideControls() {

        controlsVisible =
                false;

        if (playPauseButton != null) {

            playPauseButton.setVisibility(
                    View.GONE
            );
        }

        if (currentTimeText != null) {

            currentTimeText.setVisibility(
                    View.GONE
            );
        }

        if (totalTimeText != null) {

            totalTimeText.setVisibility(
                    View.GONE
            );
        }

        if (progressBar != null) {

            progressBar.setVisibility(
                    View.GONE
            );
        }
    }

    private void togglePlayPause() {

        if (mediaPlayer == null ||
                !playerPrepared) {

            return;
        }

        if (isExpired()) {

            expirePlayback();

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
                        "Ⅱ"
                );
            }

            showControls();

        } catch (Exception ignored) {
        }
    }

    private void seekRelative(
            int milliseconds
    ) {

        if (isExpired()) {

            expirePlayback();

            return;
        }

        if (mediaPlayer == null ||
                !playerPrepared) {

            return;
        }

        try {

            int current =
                    mediaPlayer.getCurrentPosition();

            int duration =
                    mediaPlayer.getDuration();

            int target =
                    current +
                            milliseconds;

            target =
                    Math.max(
                            0,
                            Math.min(
                                    target,
                                    duration
                            )
                    );

            mediaPlayer.seekTo(
                    target
            );

            showTime(
                    target,
                    duration
            );

            showControls();

        } catch (Exception ignored) {
        }
    }

    @Override
    public void surfaceCreated(
            SurfaceHolder holder
    ) {

        surfaceReady =
                true;

        if (isExpired()) {

            expirePlayback();

            return;
        }

        preparePlayer();
    }

    @Override
    public void surfaceChanged(
            SurfaceHolder holder,
            int format,
            int width,
            int height
    ) {
    }

    @Override
    public void surfaceDestroyed(
            SurfaceHolder holder
    ) {

        surfaceReady =
                false;
    }

    /*
     * Player preparation.
     */
    private void preparePlayer() {

        if (expiryHandled ||
                isExpired()) {

            expirePlayback();

            return;
        }

        if (videoUriString == null ||
                videoUriString.trim().isEmpty()) {

            statusText.setText(
                    "Video not found"
            );

            return;
        }

        /*
         * Encrypted video होने पर पहले
         * temporary decrypted file बनानी होगी।
         */
        if (encryptedVideo) {

            decryptVideoAndPrepare();

        } else {

            prepareMediaPlayer(
                    Uri.parse(
                            videoUriString
                    )
            );
        }
    }

    /*
     * AES-GCM encrypted .vsec file को
     * temporary file में decrypt करना।
     */
    private void decryptVideoAndPrepare() {

        if (decrypting ||
                expiryHandled) {

            return;
        }

        decrypting =
                true;

        statusText.setText(
                "Secure video preparing..."
        );

        cryptoExecutor.execute(
                () -> {

                    File encryptedFile =
                            null;

                    File decryptedFile =
                            null;

                    boolean success =
                            false;

                    try {

                        Uri encryptedUri =
                                Uri.parse(
                                        videoUriString
                                );

                        String path =
                                encryptedUri.getPath();

                        if (path == null ||
                                path.trim().isEmpty()) {

                            throw new Exception(
                                    "Encrypted file path missing"
                            );
                        }

                        encryptedFile =
                                new File(path);

                        if (!encryptedFile.exists()) {

                            throw new Exception(
                                    "Encrypted file not found"
                            );
                        }

                        /*
                         * Temporary file app cache में।
                         */
                        decryptedFile =
                                new File(
                                        getCacheDir(),
                                        "vs_play_" +
                                                System.currentTimeMillis() +
                                                ".mp4"
                                );

                        success =
                                CryptoManager.decryptFile(
                                        encryptedFile,
                                        decryptedFile
                                );

                    } catch (Exception ignored) {

                        success =
                                false;
                    }

                    final File finalDecryptedFile =
                            decryptedFile;

                    final boolean finalSuccess =
                            success;

                    runOnUiThread(
                            () -> {

                                decrypting =
                                        false;

                                if (expiryHandled) {

                                    deleteTemporaryFile(
                                            finalDecryptedFile
                                    );

                                    return;
                                }

                                if (isExpired()) {

                                    deleteTemporaryFile(
                                            finalDecryptedFile
                                    );

                                    expirePlayback();

                                    return;
                                }

                                if (!finalSuccess ||
                                        finalDecryptedFile == null ||
                                        !finalDecryptedFile.exists()) {

                                    statusText.setText(
                                            "Secure video error"
                                    );

                                    deleteTemporaryFile(
                                            finalDecryptedFile
                                    );

                                    return;
                                }

                                temporaryDecryptedFile =
                                        finalDecryptedFile;

                                prepareMediaPlayer(
                                        Uri.fromFile(
                                                finalDecryptedFile
                                        )
                                );
                            }
                    );
                }
        );
    }

    /*
     * MediaPlayer को normal temporary/private
     * file से तैयार करना।
     */
    private void prepareMediaPlayer(
            Uri videoUri
    ) {

        if (expiryHandled ||
                isExpired()) {

            expirePlayback();

            return;
        }

        releasePlayerOnly();

        try {

            mediaPlayer =
                    new MediaPlayer();

            mediaPlayer.setAudioStreamType(
                    AudioManager.STREAM_MUSIC
            );

            mediaPlayer.setDataSource(
                    this,
                    videoUri
            );

            mediaPlayer.setDisplay(
                    surfaceHolder
            );

            mediaPlayer.setOnPreparedListener(
                    mp -> {

                        if (isExpired()) {

                            expirePlayback();

                            return;
                        }

                        playerPrepared =
                                true;

                        videoWidth =
                                mp.getVideoWidth();

                        videoHeight =
                                mp.getVideoHeight();

                        int duration =
                                mp.getDuration();

                        progressBar.setMax(
                                duration
                        );

                        totalTimeText.setText(
                                formatTime(
                                        duration
                                )
                        );

                        statusText.setText(
                                ""
                        );

                        applyZoom();

                        /*
                         * Expiry check playback शुरू
                         * होने से ठीक पहले भी।
                         */
                        if (isExpired()) {

                            expirePlayback();

                            return;
                        }

                        mp.start();

                        playPauseButton.setText(
                                "Ⅱ"
                        );

                        showControls();
                    }
            );

            mediaPlayer.setOnCompletionListener(
                    mp -> {

                        playPauseButton.setText(
                                "▶"
                        );

                        if (!expiryHandled) {
                            showControls();
                        }
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

            mediaPlayer.prepareAsync();

        } catch (Exception e) {

            statusText.setText(
                    "Video playback error"
            );
        }
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

            surfaceView.post(
                    this::applyZoom
            );

            return;
        }

        float videoRatio =
                (float) videoWidth /
                        (float) videoHeight;

        float screenRatio =
                (float) screenWidth /
                        (float) screenHeight;

        int targetWidth;
        int targetHeight;

        if (videoRatio > screenRatio) {

            targetWidth =
                    screenWidth;

            targetHeight =
                    (int)
                            (
                                    screenWidth /
                                            videoRatio
                            );

        } else {

            targetHeight =
                    screenHeight;

            targetWidth =
                    (int)
                            (
                                    screenHeight *
                                            videoRatio
                            );
        }

        targetWidth =
                (int)
                        (
                                targetWidth *
                                        zoomFactor
                        );

        targetHeight =
                (int)
                        (
                                targetHeight *
                                        zoomFactor
                        );

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        targetWidth,
                        targetHeight
                );

        params.gravity =
                Gravity.CENTER;

        surfaceView.setLayoutParams(
                params
        );
    }

    private void showTime(
            int current,
            int total
    ) {

        if (currentTimeText != null) {

            currentTimeText.setText(
                    formatTime(
                            current
                    )
            );
        }

        if (totalTimeText != null) {

            totalTimeText.setText(
                    formatTime(
                            total
                    )
            );
        }
    }

    private String formatTime(
            int milliseconds
    ) {

        int totalSeconds =
                milliseconds /
                        1000;

        int minutes =
                totalSeconds /
                        60;

        int seconds =
                totalSeconds %
                        60;

        return String.format(
                java.util.Locale.getDefault(),
                "%02d:%02d",
                minutes,
                seconds
        );
    }

    /*
     * Expiry होने पर:
     *
     * 1. playback बंद
     * 2. temporary decrypted file delete
     * 3. player release
     * 4. user को message
     * 5. OK पर वापस MainActivity
     */
    private void expirePlayback() {

        if (expiryHandled) {
            return;
        }

        expiryHandled =
                true;

        handler.removeCallbacks(
                expiryCheckRunnable
        );

        handler.removeCallbacks(
                hideControlsRunnable
        );

        if (mediaPlayer != null) {

            try {

                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }

            } catch (Exception ignored) {
            }
        }

        releasePlayerOnly();

        deleteTemporaryFile(
                temporaryDecryptedFile
        );

        temporaryDecryptedFile =
                null;

        new AlertDialog.Builder(this)
                .setTitle(
                        "Vishal Secure"
                )
                .setMessage(
                        "Secure content की expiry हो चुकी है।"
                )
                .setCancelable(false)
                .setPositiveButton(
                        "OK",
                        (dialog, which) ->
                                finish()
                )
                .show();
    }

    /*
     * सिर्फ MediaPlayer release।
     */
    private void releasePlayerOnly() {

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

            mediaPlayer =
                    null;
        }

        playerPrepared =
                false;
    }

    /*
     * Temporary decrypted file delete।
     */
    private void deleteTemporaryFile(
            File file
    ) {

        if (file == null) {
            return;
        }

        try {

            if (file.exists()) {
                file.delete();
            }

        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onPause() {

        super.onPause();

        if (mediaPlayer != null &&
                playerPrepared &&
                !expiryHandled) {

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

    @Override
    protected void onDestroy() {

        handler.removeCallbacks(
                updateProgressRunnable
        );

        handler.removeCallbacks(
                expiryCheckRunnable
        );

        handler.removeCallbacks(
                hideControlsRunnable
        );

        releasePlayerOnly();

        /*
         * Temporary decrypted copy delete।
         */
        deleteTemporaryFile(
                temporaryDecryptedFile
        );

        temporaryDecryptedFile =
                null;

        /*
         * Background encryption/decryption
         * worker बंद।
         */
        cryptoExecutor.shutdownNow();

        super.onDestroy();
    }
}
