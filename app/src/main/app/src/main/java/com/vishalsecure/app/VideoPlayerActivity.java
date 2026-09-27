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
    private boolean videoPrepared = false;
    private boolean shouldResumeAfterSurface = false;

    private final Handler handler = new Handler();

    private final Runnable updateProgress =
            new Runnable() {
                @Override
                public void run() {

                    if (mediaPlayer != null
                            && videoPrepared
                            && !userSeeking) {

                        try {

                            int duration =
                                    mediaPlayer.getDuration();

                            int position =
                                    mediaPlayer.getCurrentPosition();

                            if (duration > 0) {

                                int progress =
                                        (int) (
                                                position * 1000L
                                                        / duration
                                        );

                                seekBar.setProgress(
                                        progress
                                );

                                updateTimeText(
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

    @Override
    protected void onCreate(
            Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        /*
         * SECURE SCREEN
         */
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        /*
         * Allow portrait / landscape rotation.
         */
        setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        );

        videoUriString =
                getIntent().getStringExtra(
                        "video_uri"
                );

        if (videoUriString == null
                || videoUriString.trim().isEmpty()) {

            showErrorAndClose(
                    "Video file नहीं मिली।"
            );

            return;
        }

        buildPlayerScreen();
    }

    private void buildPlayerScreen() {

        /*
         * ROOT
         *
         * IMPORTANT:
         * Video और controls अलग-अलग areas में हैं।
         * Controls अब video के ऊपर नहीं आएँगे।
         */
        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setBackgroundColor(
                Color.BLACK
        );

        /*
         * ========================================================
         * VIDEO AREA
         * ========================================================
         */

        FrameLayout videoContainer =
                new FrameLayout(this);

        videoContainer.setBackgroundColor(
                Color.BLACK
        );

        LinearLayout.LayoutParams videoContainerParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0
                );

        videoContainerParams.weight = 1f;

        /*
         * VIDEO SURFACE
         */
        surfaceView =
                new SurfaceView(this);

        /*
         * SECURITY
         */
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

        videoContainer.addView(
                surfaceView,
                surfaceParams
        );

        /*
         * STATUS TEXT
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

        videoContainer.addView(
                statusText,
                statusParams
        );

        root.addView(
                videoContainer,
                videoContainerParams
        );

        /*
         * ========================================================
         * CONTROL PANEL
         * ========================================================
         */

        LinearLayout controls =
                new LinearLayout(this);

        controls.setOrientation(
                LinearLayout.VERTICAL
        );

        controls.setPadding(
                dp(8),
                dp(4),
                dp(8),
                dp(4)
        );

        controls.setBackgroundColor(
                Color.BLACK
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
                        dp(32)
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
         * BACK
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
                        dp(48),
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
                        dp(48),
                        1
                )
        );

        /*
         * FORWARD
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
                        dp(48),
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
                        dp(48),
                        1
                )
        );

        controls.addView(
                buttonRow,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
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
                        dp(24)
                )
        );

        root.addView(
                controls,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        /*
         * SEEK BAR LISTENER
         */
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar bar,
                            int progress,
                            boolean fromUser) {

                        if (fromUser
                                && mediaPlayer != null
                                && videoPrepared
                                && userSeeking) {

                            try {

                                int duration =
                                        mediaPlayer.getDuration();

                                int newPosition =
                                        (int) (
                                                progress
                                                        / 1000.0
                                                        * duration
                                        );

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

                        if (mediaPlayer != null
                                && videoPrepared) {

                            try {

                                int duration =
                                        mediaPlayer.getDuration();

                                int newPosition =
                                        (int) (
                                                bar.getProgress()
                                                        / 1000.0
                                                        * duration
                                        );

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

    /*
     * ============================================================
     * SURFACE CREATED
     * ============================================================
     */

    @Override
    public void surfaceCreated(
            SurfaceHolder holder) {

        surfaceReady = true;

        surfaceHolder = holder;

        if (mediaPlayer != null
                && videoPrepared) {

            try {

                mediaPlayer.setDisplay(
                        surfaceHolder
                );

                /*
                 * IMPORTANT:
                 * SCALE_TO_FIT prevents cropping.
                 */
                mediaPlayer.setVideoScalingMode(
                        MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                );

                if (shouldResumeAfterSurface) {

                    mediaPlayer.start();

                    playPauseButton.setText(
                            "PAUSE"
                    );

                    shouldResumeAfterSurface =
                            false;
                }

            } catch (Exception ignored) {
            }

            return;
        }

        startVideo();
    }

    /*
     * ============================================================
     * SURFACE CHANGED
     * ============================================================
     */

    @Override
    public void surfaceChanged(
            SurfaceHolder holder,
            int format,
            int width,
            int height) {

        surfaceHolder = holder;

        if (mediaPlayer != null
                && videoPrepared) {

            try {

                mediaPlayer.setDisplay(
                        surfaceHolder
                );

                /*
                 * Re-apply FIT on every rotation / resize.
                 */
                mediaPlayer.setVideoScalingMode(
                        MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                );

            } catch (Exception ignored) {
            }
        }
    }

    /*
     * ============================================================
     * SURFACE DESTROYED
     * ============================================================
     */

    @Override
    public void surfaceDestroyed(
            SurfaceHolder holder) {

        surfaceReady = false;

        /*
         * MediaPlayer को release नहीं करना है।
         *
         * इससे rotation के समय current position बनी रहती है।
         */
        if (mediaPlayer != null
                && videoPrepared) {

            try {

                shouldResumeAfterSurface =
                        mediaPlayer.isPlaying();

                if (mediaPlayer.isPlaying()) {

                    mediaPlayer.pause();
                }

            } catch (Exception ignored) {
            }
        }
    }

    /*
     * ============================================================
     * START VIDEO
     * ============================================================
     */

    private void startVideo() {

        if (!surfaceReady
                || surfaceHolder == null
                || videoUriString == null) {

            return;
        }

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

            /*
             * IMPORTANT:
             * Full video inside surface.
             * No cropping.
             */
            mediaPlayer.setVideoScalingMode(
                    MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
            );

            mediaPlayer.setOnPreparedListener(
                    mp -> {

                        try {

                            videoPrepared = true;

                            mp.setDisplay(
                                    surfaceHolder
                            );

                            /*
                             * FINAL VIDEO SCALING MODE
                             */
                            mp.setVideoScalingMode(
                                    MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
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
     * ============================================================
     * PLAY / PAUSE
     * ============================================================
     */

    private void togglePlayPause() {

        if (mediaPlayer == null
                || !videoPrepared) {

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

    /*
     * ============================================================
     * SEEK BACKWARD
     * ============================================================
     */

    private void seekBackward() {

        if (mediaPlayer == null
                || !videoPrepared) {

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

    /*
     * ============================================================
     * SEEK FORWARD
     * ============================================================
     */

    private void seekForward() {

        if (mediaPlayer == null
                || !videoPrepared) {

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

    /*
     * ============================================================
     * TIME
     * ============================================================
     */

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

    /*
     * ============================================================
     * PAUSE
     * ============================================================
     */

    @Override
    protected void onPause() {

        super.onPause();

        /*
         * Rotation के समय position सुरक्षित रहे।
         */
        if (mediaPlayer != null
                && videoPrepared) {

            try {

                shouldResumeAfterSurface =
                        mediaPlayer.isPlaying();

            } catch (Exception ignored) {
            }
        }
    }

    /*
     * ============================================================
     * RESUME
     * ============================================================
     */

    @Override
    protected void onResume() {

        super.onResume();

        /*
         * Surface callbacks rotation को handle करते हैं।
         */
    }

    /*
     * ============================================================
     * DESTROY
     * ============================================================
     */

    @Override
    protected void onDestroy() {

        handler.removeCallbacks(
                updateProgress
        );

        releasePlayer();

        super.onDestroy();
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

        videoPrepared = false;
    }

    /*
     * ============================================================
     * ERROR
     * ============================================================
     */

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

    /*
     * ============================================================
     * DP
     * ============================================================
     */

    private int dp(int value) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return (int)
                (value * density + 0.5f);
    }
}
