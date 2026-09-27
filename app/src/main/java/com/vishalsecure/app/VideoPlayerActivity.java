package com.vishalsecure.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
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

import java.util.Locale;

public class VideoPlayerActivity extends Activity
        implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;

    private TextView statusText;
    private TextView timeText;

    private Button backButton;
    private Button playPauseButton;
    private Button forwardButton;
    private Button closeButton;

    private SeekBar seekBar;

    private String videoUriString;

    private boolean surfaceReady = false;
    private boolean prepared = false;

    private int savedPosition = 0;
    private boolean wasPlaying = false;

    private int videoWidth = 0;
    private int videoHeight = 0;

    private final android.os.Handler handler =
            new android.os.Handler();

    private final Runnable progressRunnable =
            new Runnable() {

                @Override
                public void run() {

                    if (mediaPlayer != null &&
                            prepared) {

                        try {

                            int position =
                                    mediaPlayer.getCurrentPosition();

                            int duration =
                                    mediaPlayer.getDuration();

                            if (duration > 0) {

                                seekBar.setMax(duration);
                                seekBar.setProgress(position);

                                timeText.setText(
                                        formatTime(position)
                                                + " / "
                                                + formatTime(duration)
                                );
                            }

                            if (mediaPlayer.isPlaying()) {

                                playPauseButton.setText(
                                        "PAUSE"
                                );

                            } else {

                                playPauseButton.setText(
                                        "PLAY"
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

        if (savedInstanceState != null) {

            savedPosition =
                    savedInstanceState.getInt(
                            "position",
                            0
                    );

            wasPlaying =
                    savedInstanceState.getBoolean(
                            "playing",
                            false
                    );
        }

        buildPlayerScreen();

        handler.post(
                progressRunnable
        );
    }

    private void buildPlayerScreen() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setBackgroundColor(
                Color.BLACK
        );

        /*
         * VIDEO AREA
         */
        FrameLayout videoContainer =
                new FrameLayout(this);

        videoContainer.setBackgroundColor(
                Color.BLACK
        );

        videoContainer.setKeepScreenOn(
                true
        );

        root.addView(
                videoContainer,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1.0f
                )
        );

        surfaceView =
                new SurfaceView(this);

        surfaceView.setBackgroundColor(
                Color.BLACK
        );

        surfaceView.setKeepScreenOn(
                true
        );

        surfaceView.setSecure(true);

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

        videoContainer.addView(
                surfaceView,
                surfaceParams
        );

        /*
         * STATUS
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

        videoContainer.addView(
                statusText,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                )
        );

        /*
         * CONTROLS
         */
        LinearLayout controls =
                new LinearLayout(this);

        controls.setOrientation(
                LinearLayout.VERTICAL
        );

        controls.setGravity(
                Gravity.CENTER
        );

        controls.setPadding(
                dp(6),
                dp(4),
                dp(6),
                dp(5)
        );

        controls.setBackgroundColor(
                Color.BLACK
        );

        /*
         * SEEK BAR
         */
        seekBar =
                new SeekBar(this);

        controls.addView(
                seekBar,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(32)
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

        timeText.setTextSize(14);

        timeText.setGravity(
                Gravity.CENTER
        );

        controls.addView(
                timeText,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(26)
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

        backButton =
                new Button(this);

        backButton.setText(
                "−10s"
        );

        playPauseButton =
                new Button(this);

        playPauseButton.setText(
                "PLAY"
        );

        forwardButton =
                new Button(this);

        forwardButton.setText(
                "+10s"
        );

        closeButton =
                new Button(this);

        closeButton.setText(
                "CLOSE"
        );

        buttonRow.addView(
                backButton,
                equalButtonParams()
        );

        buttonRow.addView(
                playPauseButton,
                equalButtonParams()
        );

        buttonRow.addView(
                forwardButton,
                equalButtonParams()
        );

        buttonRow.addView(
                closeButton,
                equalButtonParams()
        );

        controls.addView(
                buttonRow,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        root.addView(
                controls,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        setContentView(root);

        /*
         * BUTTON ACTIONS
         */

        backButton.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        seekBackward();
                    }
                }
        );

        playPauseButton.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        togglePlayPause();
                    }
                }
        );

        forwardButton.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        seekForward();
                    }
                }
        );

        closeButton.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                }
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
                                prepared) {

                            try {

                                timeText.setText(
                                        formatTime(progress)
                                                + " / "
                                                + formatTime(
                                                mediaPlayer
                                                        .getDuration()
                                        )
                                );

                            } catch (Exception ignored) {
                            }
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(
                            SeekBar bar) {
                    }

                    @Override
                    public void onStopTrackingTouch(
                            SeekBar bar) {

                        if (mediaPlayer != null &&
                                prepared) {

                            try {

                                mediaPlayer.seekTo(
                                        bar.getProgress()
                                );

                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
        );
    }

    /*
     * SURFACE CREATED
     */
    @Override
    public void surfaceCreated(
            SurfaceHolder holder) {

        surfaceReady = true;

        surfaceHolder = holder;

        startVideo();
    }

    /*
     * SURFACE CHANGED
     */
    @Override
    public void surfaceChanged(
            SurfaceHolder holder,
            int format,
            int width,
            int height) {

        surfaceHolder = holder;

        surfaceReady = true;

        if (mediaPlayer != null) {

            try {

                mediaPlayer.setDisplay(
                        surfaceHolder
                );

            } catch (Exception ignored) {
            }
        }

        if (prepared) {
            fitVideo();
        }
    }

    /*
     * SURFACE DESTROYED
     */
    @Override
    public void surfaceDestroyed(
            SurfaceHolder holder) {

        surfaceReady = false;

        if (mediaPlayer != null) {

            try {

                savedPosition =
                        mediaPlayer.getCurrentPosition();

                wasPlaying =
                        mediaPlayer.isPlaying();

            } catch (Exception ignored) {
            }

            try {
                mediaPlayer.setDisplay(null);
            } catch (Exception ignored) {
            }
        }
    }

    /*
     * START VIDEO
     */
    private void startVideo() {

        if (!surfaceReady ||
                surfaceHolder == null ||
                videoUriString == null) {

            return;
        }

        releasePlayer();

        prepared = false;

        try {

            Uri videoUri =
                    Uri.parse(videoUriString);

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
                    new MediaPlayer.OnPreparedListener() {

                        @Override
                        public void onPrepared(
                                MediaPlayer mp) {

                            try {

                                prepared = true;

                                videoWidth =
                                        mp.getVideoWidth();

                                videoHeight =
                                        mp.getVideoHeight();

                                /*
                                 * सबसे पहले display
                                 */
                                mp.setDisplay(
                                        surfaceHolder
                                );

                                /*
                                 * पूरा video fit करें।
                                 */
                                fitVideo();

                                /*
                                 * पुरानी position वापस।
                                 */
                                int duration =
                                        mp.getDuration();

                                if (savedPosition > 0 &&
                                        savedPosition < duration) {

                                    mp.seekTo(
                                            savedPosition
                                    );
                                }

                                /*
                                 * Video पहली बार खोलते समय
                                 * भी play होगी।
                                 */
                                mp.start();

                                playPauseButton.setText(
                                        "PAUSE"
                                );

                                if (statusText != null) {

                                    statusText.setVisibility(
                                            View.GONE
                                    );
                                }

                            } catch (Exception e) {

                                showError(
                                        "Video चलाने में समस्या हुई।"
                                );
                            }
                        }
                    }
            );

            mediaPlayer.setOnCompletionListener(
                    new MediaPlayer.OnCompletionListener() {

                        @Override
                        public void onCompletion(
                                MediaPlayer mp) {

                            playPauseButton.setText(
                                    "PLAY"
                            );

                            if (statusText != null) {

                                statusText.setText(
                                        "Video समाप्त"
                                );

                                statusText.setVisibility(
                                        View.VISIBLE
                                );
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

                            showError(
                                    "Video चलाने में समस्या हुई।"
                            );

                            return true;
                        }
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

    /*
     * =========================================================
     * IMPORTANT:
     *
     * यहाँ CENTER-CROP नहीं है।
     *
     * पूरा video proportionally दिखाई देगा।
     * कोई हिस्सा कटेगा नहीं।
     * =========================================================
     */
    private void fitVideo() {

        if (!surfaceReady ||
                !prepared ||
                videoWidth <= 0 ||
                videoHeight <= 0) {

            return;
        }

        surfaceView.post(
                new Runnable() {

                    @Override
                    public void run() {

                        int availableWidth =
                                surfaceView.getWidth();

                        int availableHeight =
                                surfaceView.getHeight();

                        if (availableWidth <= 0 ||
                                availableHeight <= 0) {

                            return;
                        }

                        float videoRatio =
                                (float) videoWidth
                                        / (float) videoHeight;

                        float screenRatio =
                                (float) availableWidth
                                        / (float) availableHeight;

                        int finalWidth;
                        int finalHeight;

                        if (videoRatio >
                                screenRatio) {

                            /*
                             * Wide video
                             */
                            finalWidth =
                                    availableWidth;

                            finalHeight =
                                    Math.round(
                                            finalWidth
                                                    / videoRatio
                                    );

                        } else {

                            /*
                             * Tall video
                             */
                            finalHeight =
                                    availableHeight;

                            finalWidth =
                                    Math.round(
                                            finalHeight
                                                    * videoRatio
                                    );
                        }

                        FrameLayout.LayoutParams params =
                                new FrameLayout.LayoutParams(
                                        finalWidth,
                                        finalHeight,
                                        Gravity.CENTER
                                );

                        surfaceView.setLayoutParams(
                                params
                        );
                    }
                }
        );
    }

    /*
     * PLAY / PAUSE
     */
    private void togglePlayPause() {

        if (mediaPlayer == null ||
                !prepared) {

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
     * BACKWARD
     */
    private void seekBackward() {

        if (mediaPlayer == null ||
                !prepared) {

            return;
        }

        try {

            int position =
                    mediaPlayer.getCurrentPosition()
                            - 10000;

            if (position < 0) {
                position = 0;
            }

            mediaPlayer.seekTo(
                    position
            );

        } catch (Exception ignored) {
        }
    }

    /*
     * FORWARD
     */
    private void seekForward() {

        if (mediaPlayer == null ||
                !prepared) {

            return;
        }

        try {

            int position =
                    mediaPlayer.getCurrentPosition()
                            + 10000;

            int duration =
                    mediaPlayer.getDuration();

            if (position > duration) {
                position = duration;
            }

            mediaPlayer.seekTo(
                    position
            );

        } catch (Exception ignored) {
        }
    }

    /*
     * SAVE POSITION
     */
    @Override
    protected void onSaveInstanceState(
            Bundle outState) {

        if (mediaPlayer != null) {

            try {

                savedPosition =
                        mediaPlayer.getCurrentPosition();

                wasPlaying =
                        mediaPlayer.isPlaying();

            } catch (Exception ignored) {
            }
        }

        outState.putInt(
                "position",
                savedPosition
        );

        outState.putBoolean(
                "playing",
                wasPlaying
        );

        super.onSaveInstanceState(
                outState
        );
    }

    /*
     * ROTATION
     */
    @Override
    public void onConfigurationChanged(
            android.content.res.Configuration newConfig) {

        super.onConfigurationChanged(
                newConfig
        );

        surfaceView.postDelayed(
                new Runnable() {

                    @Override
                    public void run() {

                        if (prepared) {
                            fitVideo();
                        }
                    }
                },
                250
        );
    }

    /*
     * PAUSE
     */
    @Override
    protected void onPause() {

        super.onPause();

        if (mediaPlayer != null) {

            try {

                savedPosition =
                        mediaPlayer.getCurrentPosition();

                wasPlaying =
                        mediaPlayer.isPlaying();

                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }

            } catch (Exception ignored) {
            }
        }
    }

    /*
     * RESUME
     */
    @Override
    protected void onResume() {

        super.onResume();

        if (mediaPlayer != null &&
                prepared &&
                wasPlaying) {

            try {

                mediaPlayer.start();

                playPauseButton.setText(
                        "PAUSE"
                );

            } catch (Exception ignored) {
            }
        }
    }

    /*
     * RELEASE
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

        prepared = false;
    }

    /*
     * ERROR
     */
    private void showError(
            String message) {

        runOnUiThread(
                new Runnable() {

                    @Override
                    public void run() {

                        if (statusText != null) {

                            statusText.setText(
                                    message
                            );

                            statusText.setVisibility(
                                    View.VISIBLE
                            );
                        }
                    }
                }
        );
    }

    /*
     * ERROR + CLOSE
     */
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
                        (dialog, which) -> finish()
                )
                .setCancelable(false)
                .show();
    }

    /*
     * TIME FORMAT
     */
    private String formatTime(
            int milliseconds) {

        int totalSeconds =
                milliseconds / 1000;

        int minutes =
                totalSeconds / 60;

        int seconds =
                totalSeconds % 60;

        return String.format(
                Locale.getDefault(),
                "%02d:%02d",
                minutes,
                seconds
        );
    }

    /*
     * BUTTON WIDTH
     */
    private LinearLayout.LayoutParams
    equalButtonParams() {

        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
    }

    /*
     * DP
     */
    private int dp(int value) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return (int)
                (value * density + 0.5f);
    }

    /*
     * DESTROY
     */
    @Override
    protected void onDestroy() {

        handler.removeCallbacks(
                progressRunnable
        );

        releasePlayer();

        super.onDestroy();
    }
}
