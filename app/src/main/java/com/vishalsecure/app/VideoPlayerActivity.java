package com.vishalsecure.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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

    private final Handler handler =
            new Handler();

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

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

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

        textureView =
                new TextureView(this);

        textureView.setSecure(true);

        textureView.setKeepScreenOn(true);

        textureView.setSurfaceTextureListener(
                this
        );

        FrameLayout.LayoutParams textureParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );

        textureParams.gravity =
                Gravity.CENTER;

        videoContainer.addView(
                textureView,
                textureParams
        );

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
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        statusParams.gravity =
                Gravity.CENTER;

        videoContainer.addView(
                statusText,
                statusParams
        );

        LinearLayout.LayoutParams videoParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0
                );

        videoParams.weight = 1f;

        root.addView(
                videoContainer,
                videoParams
        );

        /*
         * CONTROL AREA
         */
        LinearLayout controls =
                new LinearLayout(this);

        controls.setOrientation(
                LinearLayout.VERTICAL
        );

        controls.setBackgroundColor(
                Color.BLACK
        );

        controls.setPadding(
                dp(8),
                dp(4),
                dp(8),
                dp(4)
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
                        1f
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
                        1f
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
                        1f
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
                        1f
                )
        );

        controls.addView(
                buttonRow,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
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
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(24)
                )
        );

        root.addView(
                controls,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
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

                                int position =
                                        (int) (
                                                progress
                                                        / 1000.0
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

                        if (mediaPlayer != null
                                && videoPrepared) {

                            try {

                                int duration =
                                        mediaPlayer.getDuration();

                                int position =
                                        (int) (
                                                bar.getProgress()
                                                        / 1000.0
                                                        * duration
                                        );

                                mediaPlayer.seekTo(
                                        position
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
    public void onSurfaceTextureAvailable(
            SurfaceTexture surfaceTexture,
            int width,
            int height) {

        textureReady = true;

        createVideoSurface(
                surfaceTexture
        );

        if (mediaPlayer != null
                && videoPrepared) {

            attachExistingPlayer();

            if (shouldResumeAfterSurface) {

                try {

                    mediaPlayer.start();

                    playPauseButton.setText(
                            "PAUSE"
                    );

                    shouldResumeAfterSurface =
                            false;

                } catch (Exception ignored) {
                }
            }

            applyVideoTransform(
                    width,
                    height
            );

            return;
        }

        startVideo();
    }

    @Override
    public void onSurfaceTextureSizeChanged(
            SurfaceTexture surfaceTexture,
            int width,
            int height) {

        if (videoPrepared) {

            applyVideoTransform(
                    width,
                    height
            );
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(
            SurfaceTexture surfaceTexture) {

        textureReady = false;

        if (mediaPlayer != null
                && videoPrepared) {

            try {

                savedPosition =
                        mediaPlayer.getCurrentPosition();

                shouldResumeAfterSurface =
                        mediaPlayer.isPlaying();

                if (mediaPlayer.isPlaying()) {

                    mediaPlayer.pause();
                }

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

        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(
            SurfaceTexture surfaceTexture) {
    }

    private void createVideoSurface(
            SurfaceTexture surfaceTexture) {

        try {

            if (videoSurface != null) {

                videoSurface.release();

                videoSurface = null;
            }

            videoSurface =
                    new Surface(
                            surfaceTexture
                    );

        } catch (Exception ignored) {
        }
    }

    private void startVideo() {

        if (!textureReady
                || videoSurface == null
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

            mediaPlayer.setSurface(
                    videoSurface
            );

            mediaPlayer.setOnPreparedListener(
                    mp -> {

                        try {

                            videoPrepared = true;

                            videoWidth =
                                    mp.getVideoWidth();

                            videoHeight =
                                    mp.getVideoHeight();

                            applyVideoTransform();

                            int duration =
                                    mp.getDuration();

                            updateTimeText(
                                    0,
                                    duration
                            );

                            statusText.setVisibility(
                                    View.GONE
                            );

                            playPauseButton.setText(
                                    "PAUSE"
                            );

                            mp.start();

                            handler.post(
                                    updateProgress
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

        } catch (Exception e) {

            showError(
                    "Video खोलने में समस्या हुई।"
            );
        }
    }

    private void attachExistingPlayer() {

        if (mediaPlayer == null
                || videoSurface == null
                || !videoPrepared) {

            return;
        }

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

        } catch (Exception ignored) {
        }
    }

    private void applyVideoTransform() {

        if (textureView == null) {
            return;
        }

        applyVideoTransform(
                textureView.getWidth(),
                textureView.getHeight()
        );
    }

    private void applyVideoTransform(
            int viewWidth,
            int viewHeight) {

        if (textureView == null
                || videoWidth <= 0
                || videoHeight <= 0
                || viewWidth <= 0
                || viewHeight <= 0) {

            return;
        }

        float videoRatio =
                (float) videoWidth
                        / (float) videoHeight;

        float viewRatio =
                (float) viewWidth
                        / (float) viewHeight;

        float scale;

        if (videoRatio > viewRatio) {

            scale =
                    (float) viewWidth
                            / (float) videoWidth;

        } else {

            scale =
                    (float) viewHeight
                            / (float) videoHeight;
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

    private void seekBackward() {

        if (mediaPlayer == null
                || !videoPrepared) {

            return;
        }

        try {

            int position =
                    mediaPlayer.getCurrentPosition();

            mediaPlayer.seekTo(
                    Math.max(
                            0,
                            position - 10000
                    )
            );

        } catch (Exception ignored) {
        }
    }

    private void seekForward() {

        if (mediaPlayer == null
                || !videoPrepared) {

            return;
        }

        try {

            int position =
                    mediaPlayer.getCurrentPosition();

            int duration =
                    mediaPlayer.getDuration();

            mediaPlayer.seekTo(
                    Math.min(
                            duration,
                            position + 10000
                    )
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

        if (mediaPlayer != null
                && videoPrepared) {

            try {

                savedPosition =
                        mediaPlayer.getCurrentPosition();

                shouldResumeAfterSurface =
                        mediaPlayer.isPlaying();

            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onResume() {

        super.onResume();
    }

    @Override
    protected void onDestroy() {

        handler.removeCallbacks(
                updateProgress
        );

        releasePlayer();

        super.onDestroy();
    }

    private void releasePlayer() {

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

        videoPrepared = false;
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

    private int dp(int value) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return (int)
                (value * density + 0.5f);
    }
}
