package com.vishalsecure.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
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
    private Button backButton;
    private Button forwardButton;
    private Button closeButton;

    private SeekBar seekBar;

    private String videoUriString;

    private boolean surfaceReady = false;
    private boolean userSeeking = false;

    private final Handler handler = new Handler();

    private final Runnable updateProgress =
            new Runnable() {
                @Override
                public void run() {

                    if (mediaPlayer != null &&
                            !userSeeking) {

                        try {

                            if (mediaPlayer.isPlaying()) {

                                int duration =
                                        mediaPlayer.getDuration();

                                int position =
                                        mediaPlayer.getCurrentPosition();

                                if (duration > 0) {

                                    seekBar.setMax(1000);

                                    int progress =
                                            (int)
                                            ((position * 1000L)
                                                    / duration);

                                    seekBar.setProgress(
                                            progress
                                    );

                                    updateTimeText(
                                            position,
                                            duration
                                    );
                                }
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * Secure screen.
         */
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        /*
         * Allow portrait and landscape.
         */
        setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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

    private void buildPlayerScreen() {

        FrameLayout root =
                new FrameLayout(this);

        root.setBackgroundColor(
                Color.BLACK
        );

        /*
         * VIDEO SURFACE
         */
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

        surfaceParams.gravity =
                Gravity.CENTER;

        root.addView(
                surfaceView,
                surfaceParams
        );

        /*
         * LOADING / ERROR TEXT
         */
        statusText =
                new TextView(this);

        statusText.setText(
                "Video loading..."
        );

        statusText.setTextColor(
                Color.WHITE
        );

        statusText.setTextSize(16);

        statusText.setGravity(
                Gravity.CENTER
        );

        FrameLayout.LayoutParams statusParams =
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

        /*
         * CONTROL PANEL
         */
        LinearLayout controls =
                new LinearLayout(this);

        controls.setOrientation(
                LinearLayout.VERTICAL
        );

        controls.setPadding(
                dp(10),
                dp(6),
                dp(10),
                dp(6)
        );

        controls.setBackgroundColor(
                Color.argb(
                        210,
                        0,
                        0,
                        0
                )
        );

        /*
         * SEEK BAR
         */
        seekBar =
                new SeekBar(this);

        seekBar.setMax(1000);

        controls.addView(
                seekBar,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(35)
                )
        );

        /*
         * BUTTON ROW
         */
        LinearLayout buttonRow =
                new LinearLayout(this);

        buttonRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        buttonRow.setGravity(
                Gravity.CENTER
        );

        /*
         * -10 SECONDS
         */
        backButton =
                new Button(this);

        backButton.setText(
                "−10s"
        );

        backButton.setAllCaps(false);

        backButton.setOnClickListener(
                v -> seekBackward()
        );

        buttonRow.addView(
                backButton,
                new LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1
                )
        );

        /*
         * PLAY / PAUSE
         */
        playPauseButton =
                new Button(this);

        playPauseButton.setText(
                "PLAY"
        );

        playPauseButton.setAllCaps(false);

        playPauseButton.setOnClickListener(
                v -> togglePlayPause()
        );

        buttonRow.addView(
                playPauseButton,
                new LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1
                )
        );

        /*
         * +10 SECONDS
         */
        forwardButton =
                new Button(this);

        forwardButton.setText(
                "+10s"
        );

        forwardButton.setAllCaps(false);

        forwardButton.setOnClickListener(
                v -> seekForward()
        );

        buttonRow.addView(
                forwardButton,
                new LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1
                )
        );

        /*
         * CLOSE
         */
        closeButton =
                new Button(this);

        closeButton.setText(
                "CLOSE"
        );

        closeButton.setAllCaps(false);

        closeButton.setOnClickListener(
                v -> finish()
        );

        buttonRow.addView(
                closeButton,
                new LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1
                )
        );

        controls.addView(
                buttonRow,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(55)
                )
        );

        /*
         * TIME
         */
        timeText =
                new TextView(this);

        timeText.setText(
                "00:00 / 00:00"
        );

        timeText.setTextColor(
                Color.WHITE
        );

        timeText.setTextSize(13);

        timeText.setGravity(
                Gravity.CENTER
        );

        controls.addView(
                timeText,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(28)
                )
        );

        /*
         * Controls at bottom.
         */
        FrameLayout.LayoutParams controlParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        controlParams.gravity =
                Gravity.BOTTOM;

        root.addView(
                controls,
                controlParams
        );

        /*
         * SEEK BAR
         */
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar bar,
                            int progress,
                            boolean fromUser) {

                        if (fromUser &&
                                mediaPlayer != null &&
                                userSeeking) {

                            try {

                                int duration =
                                        mediaPlayer.getDuration();

                                int newPosition =
                                        (int)
                                        ((progress / 1000.0)
                                                * duration);

                                updateTimeText(
                                        newPosition,
                                        duration
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

                                int newPosition =
                                        (int)
                                        ((bar.getProgress()
                                                / 1000.0)
                                                * duration);

                                mediaPlayer.seekTo(
                                        newPosition
                                );

                            } catch (Exception ignored) {
                            }
                        }

                        userSeeking = false;
                    }
                }
        );

        setContentView(root);
    }

    @Override
    public void surfaceCreated(
            SurfaceHolder holder) {

        surfaceReady = true;

        surfaceHolder = holder;

        startVideo();
    }

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

                /*
                 * Recalculate video size whenever
                 * phone orientation changes.
                 */
                adjustVideoSize(
                        mediaPlayer.getVideoWidth(),
                        mediaPlayer.getVideoHeight()
                );

            } catch (Exception ignored) {
            }
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

            mediaPlayer.setOnPreparedListener(
                    mp -> {

                        try {

                            mp.setDisplay(
                                    surfaceHolder
                            );

                            adjustVideoSize(
                                    mp.getVideoWidth(),
                                    mp.getVideoHeight()
                            );

                            int duration =
                                    mp.getDuration();

                            updateTimeText(
                                    0,
                                    duration
                            );

                            playPauseButton.setText(
                                    "PAUSE"
                            );

                            statusText.setVisibility(
                                    View.GONE
                            );

                            mp.start();

                        } catch (Exception e) {

                            showError(
                                    "Video चलाने में समस्या हुई।"
                            );
                        }
                    }
            );

            mediaPlayer.setOnCompletionListener(
                    mp -> {

                        try {

                            playPauseButton.setText(
                                    "PLAY"
                            );

                            seekBar.setProgress(
                                    1000
                            );

                            updateTimeText(
                                    mp.getDuration(),
                                    mp.getDuration()
                            );

                        } catch (Exception ignored) {
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

            handler.post(
                    updateProgress
            );

        } catch (Exception e) {

            showError(
                    "Video खोलने में समस्या हुई।"
            );
        }
    }

    /*
     * FULL-SCREEN VIDEO
     *
     * The video keeps its original proportions.
     * Instead of making the whole video smaller,
     * it fills the available screen.
     *
     * If the screen and video have different
     * proportions, the excess part is cropped.
     * This prevents faces from becoming stretched.
     */
    private void adjustVideoSize(
            int videoWidth,
            int videoHeight) {

        if (videoWidth <= 0 ||
                videoHeight <= 0 ||
                surfaceView == null) {

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

        int finalWidth;
        int finalHeight;

        /*
         * CENTER-CROP:
         *
         * Fill the complete screen while
         * preserving the original video ratio.
         */
        if (videoRatio > screenRatio) {

            /*
             * Video is wider than screen.
             * Height fills screen and width becomes
             * larger than screen.
             */
            finalHeight =
                    screenHeight;

            finalWidth =
                    Math.round(
                            screenHeight *
                                    videoRatio
                    );

        } else {

            /*
             * Video is taller than screen.
             * Width fills screen and height becomes
             * larger than screen.
             */
            finalWidth =
                    screenWidth;

            finalHeight =
                    Math.round(
                            screenWidth /
                                    videoRatio
                    );
        }

        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams)
                        surfaceView.getLayoutParams();

        params.width =
                finalWidth;

        params.height =
                finalHeight;

        params.gravity =
                Gravity.CENTER;

        surfaceView.setLayoutParams(
                params
        );
    }

    private void togglePlayPause() {

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
    }

    private void seekBackward() {

        if (mediaPlayer == null) {
            return;
        }

        try {

            int current =
                    mediaPlayer.getCurrentPosition();

            int newPosition =
                    Math.max(
                            0,
                            current - 10000
                    );

            mediaPlayer.seekTo(
                    newPosition
            );

        } catch (Exception ignored) {
        }
    }

    private void seekForward() {

        if (mediaPlayer == null) {
            return;
        }

        try {

            int current =
                    mediaPlayer.getCurrentPosition();

            int duration =
                    mediaPlayer.getDuration();

            int newPosition =
                    Math.min(
                            duration,
                            current + 10000
                    );

            mediaPlayer.seekTo(
                    newPosition
            );

        } catch (Exception ignored) {
        }
    }

    private void updateTimeText(
            int current,
            int duration) {

        if (timeText == null) {
            return;
        }

        timeText.setText(
                formatTime(current)
                        + " / "
                        + formatTime(duration)
        );
    }

    private String formatTime(
            int milliseconds) {

        int totalSeconds =
                Math.max(
                        0,
                        milliseconds / 1000
                );

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

    @Override
    protected void onResume() {

        super.onResume();

        /*
         * Video will not unexpectedly restart
         * after rotation.
         */
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

    private void showError(
            String message) {

        runOnUiThread(
                () -> {

                    if (statusText != null) {

                        statusText.setText(
                                message
                        );

                        statusText.setVisibility(
                                View.VISIBLE
                        );
                    }
                }
        );
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

    @Override
    protected void onDestroy() {

        handler.removeCallbacks(
                updateProgress
        );

        releasePlayer();

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
