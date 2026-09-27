package com.vishalsecure.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class VideoPlayerActivity extends Activity
        implements TextureView.SurfaceTextureListener {

    private TextureView textureView;
    private FrameLayout videoContainer;
    private Surface videoSurface;
    private MediaPlayer mediaPlayer;

    private TextView statusText;
    private TextView timeText;
    private Button playPauseButton;
    private Button backButton;
    private Button forwardButton;
    private Button closeButton;
    private SeekBar seekBar;

    private String videoUriString;

    private boolean textureReady = false;
    private boolean videoPrepared = false;
    private boolean userSeeking = false;
    private boolean shouldResumeAfterSurface = false;

    private int savedPosition = 0;
    private int videoWidth = 0;
    private int videoHeight = 0;

    private final android.os.Handler handler =
            new android.os.Handler();

    private final Runnable updateProgress =
            new Runnable() {
                @Override
                public void run() {

                    if (mediaPlayer != null &&
                            videoPrepared &&
                            !userSeeking) {

                        try {

                            int duration =
                                    mediaPlayer.getDuration();

                            int position =
                                    mediaPlayer.getCurrentPosition();

                            if (duration > 0) {

                                seekBar.setMax(1000);

                                seekBar.setProgress(
                                        (int) (
                                                (position * 1000L)
                                                        / duration
                                        )
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
    protected void onCreate(Bundle savedInstanceState) {
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

        buildPlayerScreen();
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
        videoContainer =
                new FrameLayout(this);

        videoContainer.setBackgroundColor(
                Color.BLACK
        );

        textureView =
                new TextureView(this);

        textureView.setSurfaceTextureListener(
                this
        );

        textureView.setSecure(true);

        textureView.setKeepScreenOn(true);

        FrameLayout.LayoutParams textureParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                );

        videoContainer.addView(
                textureView,
                textureParams
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
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                )
        );

        /*
         * VIDEO AREA TAKES REMAINING SPACE
         */
        root.addView(
                videoContainer,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
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
                        ViewGroup.LayoutParams.MATCH_PARENT,
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
         * BACK 10
         */
        backButton =
                makeButton("−10s");

        backButton.setOnClickListener(
                v -> seekBackward()
        );

        buttonRow.addView(
                backButton,
                buttonParams()
        );

        /*
         * PLAY / PAUSE
         */
        playPauseButton =
                makeButton("PLAY");

        playPauseButton.setOnClickListener(
                v -> togglePlayPause()
        );

        buttonRow.addView(
                playPauseButton,
                buttonParams()
        );

        /*
         * FORWARD 10
         */
        forwardButton =
                makeButton("+10s");

        forwardButton.setOnClickListener(
                v -> seekForward()
        );

        buttonRow.addView(
                forwardButton,
                buttonParams()
        );

        /*
         * CLOSE
         */
        closeButton =
                makeButton("CLOSE");

        closeButton.setOnClickListener(
                v -> finish()
        );

        buttonRow.addView(
                closeButton,
                buttonParams()
        );

        controls.addView(
                buttonRow,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(50)
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
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(24)
                )
        );

        /*
         * CONTROLS ARE BELOW VIDEO,
         * NOT OVER THE VIDEO.
         */
        root.addView(
                controls,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        /*
         * SEEK LISTENER
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

                                int position =
                                        (int) (
                                                (progress / 1000.0)
                                                        * duration
                                        );

                                updateTimeText(
                                        position,
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

                                int position =
                                        (int) (
                                                (bar.getProgress()
                                                        / 1000.0)
                                                        * duration
                                        );

                                mediaPlayer.seekTo(
                                        position
                                );

                                savedPosition =
                                        position;

                            } catch (Exception ignored) {
                            }
                        }

                        userSeeking = false;
                    }
                }
        );

        /*
         * RECALCULATE WHEN VIDEO AREA SIZE CHANGES
         */
        videoContainer.addOnLayoutChangeListener(
                (v,
                 left,
                 top,
                 right,
                 bottom,
                 oldLeft,
                 oldTop,
                 oldRight,
                 oldBottom) ->
                        applyVideoTransform()
        );

        setContentView(root);
    }

    private Button makeButton(
            String text) {

        Button button =
                new Button(this);

        button.setText(text);

        button.setAllCaps(false);

        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {

        return new LinearLayout.LayoutParams(
                0,
                dp(50),
                1f
        );
    }

    @Override
    public void onSurfaceTextureAvailable(
            SurfaceTexture surfaceTexture,
            int width,
            int height) {

        textureReady = true;

        videoSurface =
                new Surface(surfaceTexture);

        if (mediaPlayer != null &&
                videoPrepared) {

            try {

                mediaPlayer.setSurface(
                        videoSurface
                );

                applyVideoTransform();

                if (savedPosition > 0) {

                    mediaPlayer.seekTo(
                            savedPosition
                    );
                }

                if (shouldResumeAfterSurface) {

                    mediaPlayer.start();

                    playPauseButton.setText(
                            "PAUSE"
                    );
                }

            } catch (Exception ignored) {
            }

            shouldResumeAfterSurface = false;

        } else {

            startVideo();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(
            SurfaceTexture surfaceTexture,
            int width,
            int height) {

        applyVideoTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(
            SurfaceTexture surfaceTexture) {

        textureReady = false;

        if (mediaPlayer != null &&
                videoPrepared) {

            try {

                savedPosition =
                        mediaPlayer.getCurrentPosition();

                shouldResumeAfterSurface =
                        mediaPlayer.isPlaying();

                mediaPlayer.pause();

            } catch (Exception ignored) {
            }
        }

        if (videoSurface != null) {

            try {
                videoSurface.release();
            } catch (Exception ignored) {
            }

            videoSurface = null;
        }

        /*
         * KEEP MediaPlayer ALIVE
         */
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(
            SurfaceTexture surfaceTexture) {
    }

    private void startVideo() {

        if (!textureReady ||
                videoSurface == null ||
                mediaPlayer != null) {

            return;
        }

        try {

            mediaPlayer =
                    new MediaPlayer();

            mediaPlayer.setAudioStreamType(
                    AudioManager.STREAM_MUSIC
            );

            mediaPlayer.setScreenOnWhilePlaying(
                    true
            );

            mediaPlayer.setSurface(
                    videoSurface
            );

            mediaPlayer.setOnPreparedListener(
                    mp -> {

                        videoPrepared = true;

                        videoWidth =
                                mp.getVideoWidth();

                        videoHeight =
                                mp.getVideoHeight();

                        try {

                            applyVideoTransform();

                            int duration =
                                    mp.getDuration();

                            int position =
                                    Math.min(
                                            savedPosition,
                                            duration
                                    );

                            if (position > 0) {

                                mp.seekTo(
                                        position
                                );
                            }

                            updateTimeText(
                                    position,
                                    duration
                            );

                            seekBar.setProgress(
                                    duration > 0
                                            ? (int) (
                                                    (position * 1000L)
                                                            / duration
                                            )
                                            : 0
                            );

                            statusText.setVisibility(
                                    View.GONE
                            );

                            mp.start();

                            playPauseButton.setText(
                                    "PAUSE"
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

                        try {

                            savedPosition =
                                    mp.getDuration();

                            seekBar.setProgress(
                                    1000
                            );

                            updateTimeText(
                                    mp.getDuration(),
                                    mp.getDuration()
                            );

                            playPauseButton.setText(
                                    "PLAY"
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
                    Uri.parse(
                            videoUriString
                    )
            );

            mediaPlayer.prepareAsync();

            handler.removeCallbacks(
                    updateProgress
            );

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
     * FIT CENTER
     *
     * पूरा video उपलब्ध video area के अंदर रहेगा।
     * कोई crop नहीं।
     * कोई stretching नहीं।
     *
     * Controls अलग नीचे हैं।
     */
    private void applyVideoTransform() {

        if (textureView == null ||
                videoContainer == null ||
                videoWidth <= 0 ||
                videoHeight <= 0) {

            return;
        }

        int viewWidth =
                videoContainer.getWidth();

        int viewHeight =
                videoContainer.getHeight();

        if (viewWidth <= 0 ||
                viewHeight <= 0) {

            return;
        }

        float videoRatio =
                (float) videoWidth /
                (float) videoHeight;

        float viewRatio =
                (float) viewWidth /
                (float) viewHeight;

        float scale;

        if (videoRatio > viewRatio) {

            scale =
                    (float) viewWidth /
                    (float) videoWidth;

        } else {

            scale =
                    (float) viewHeight /
                    (float) videoHeight;
        }

        float scaledWidth =
                videoWidth * scale;

        float scaledHeight =
                videoHeight * scale;

        float dx =
                (viewWidth - scaledWidth)
                        / 2f;

        float dy =
                (viewHeight - scaledHeight)
                        / 2f;

        Matrix matrix =
                new Matrix();

        matrix.setScale(
                scale,
                scale
        );

        matrix.postTranslate(
                dx,
                dy
        );

        textureView.setTransform(
                matrix
        );
    }

    private void togglePlayPause() {

        if (mediaPlayer == null ||
                !videoPrepared) {

            return;
        }

        try {

            if (mediaPlayer.isPlaying()) {

                mediaPlayer.pause();

                savedPosition =
                        mediaPlayer.getCurrentPosition();

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

        if (mediaPlayer == null ||
                !videoPrepared) {

            return;
        }

        try {

            int position =
                    Math.max(
                            0,
                            mediaPlayer.getCurrentPosition()
                                    - 10000
                    );

            mediaPlayer.seekTo(
                    position
            );

            savedPosition =
                    position;

        } catch (Exception ignored) {
        }
    }

    private void seekForward() {

        if (mediaPlayer == null ||
                !videoPrepared) {

            return;
        }

        try {

            int duration =
                    mediaPlayer.getDuration();

            int position =
                    Math.min(
                            duration,
                            mediaPlayer.getCurrentPosition()
                                    + 10000
                    );

            mediaPlayer.seekTo(
                    position
            );

            savedPosition =
                    position;

        } catch (Exception ignored) {
        }
    }

    private void updateTimeText(
            int current,
            int duration) {

        if (timeText != null) {

            timeText.setText(
                    formatTime(current)
                            + " / "
                            + formatTime(duration)
            );
        }
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
    protected void onPause() {

        super.onPause();

        if (mediaPlayer != null &&
                videoPrepared) {

            try {

                if (mediaPlayer.isPlaying()) {

                    savedPosition =
                            mediaPlayer.getCurrentPosition();

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
    protected void onDestroy() {

        handler.removeCallbacks(
                updateProgress
        );

        if (videoSurface != null) {

            try {
                videoSurface.release();
            } catch (Exception ignored) {
            }

            videoSurface = null;
        }

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
