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

    private Button backButton;
    private Button playPauseButton;
    private Button forwardButton;
    private Button closeButton;

    private SeekBar seekBar;

    private String videoUriString;

    private boolean surfaceReady = false;
    private boolean prepared = false;
    private boolean userSeeking = false;
    private boolean destroyed = false;

    private int savedPosition = 0;

    private int videoWidth = 0;
    private int videoHeight = 0;

    private final android.os.Handler handler =
            new android.os.Handler();

    private final Runnable progressRunnable =
            new Runnable() {
                @Override
                public void run() {

                    if (destroyed) {
                        return;
                    }

                    try {

                        if (mediaPlayer != null
                                && prepared) {

                            int duration =
                                    mediaPlayer.getDuration();

                            int position =
                                    mediaPlayer.getCurrentPosition();

                            if (!userSeeking
                                    && duration > 0) {

                                seekBar.setMax(duration);
                                seekBar.setProgress(position);
                            }

                            timeText.setText(
                                    formatTime(position)
                                            + " / "
                                            + formatTime(duration)
                            );

                            if (mediaPlayer.isPlaying()) {

                                playPauseButton.setText(
                                        "PAUSE"
                                );

                            } else {

                                playPauseButton.setText(
                                        "PLAY"
                                );
                            }
                        }

                    } catch (Exception ignored) {
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

        if (videoUriString == null
                || videoUriString.trim().isEmpty()) {

            showErrorAndClose(
                    "वीडियो नहीं मिली।"
            );

            return;
        }

        buildScreen();

        handler.post(
                progressRunnable
        );
    }

    private void buildScreen() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setBackgroundColor(
                Color.BLACK
        );

        videoContainer =
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
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1.0f
                )
        );

        textureView =
                new TextureView(this);

        textureView.setBackgroundColor(
                Color.BLACK
        );

        textureView.setSurfaceTextureListener(
                this
        );

        FrameLayout.LayoutParams
                textureParams =
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
                "वीडियो तैयार हो रही है..."
        );

        statusText.setTextColor(
                Color.WHITE
        );

        statusText.setTextSize(
                15
        );

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

        seekBar =
                new SeekBar(this);

        controls.addView(
                seekBar,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(32)
                )
        );

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
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        timeText =
                new TextView(this);

        timeText.setText(
                "00:00 / 00:00"
        );

        timeText.setTextColor(
                Color.WHITE
        );

        timeText.setTextSize(
                14
        );

        timeText.setGravity(
                Gravity.CENTER
        );

        controls.addView(
                timeText,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(26)
                )
        );

        root.addView(
                controls,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        setContentView(root);

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

        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar bar,
                            int progress,
                            boolean fromUser
                    ) {

                        if (fromUser
                                && mediaPlayer != null
                                && prepared) {

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
                            SeekBar bar
                    ) {

                        userSeeking = true;
                    }

                    @Override
                    public void onStopTrackingTouch(
                            SeekBar bar
                    ) {

                        userSeeking = false;

                        if (mediaPlayer != null
                                && prepared) {

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

        videoContainer.addOnLayoutChangeListener(
                new View.OnLayoutChangeListener() {

                    @Override
                    public void onLayoutChange(
                            View v,
                            int left,
                            int top,
                            int right,
                            int bottom,
                            int oldLeft,
                            int oldTop,
                            int oldRight,
                            int oldBottom
                    ) {

                        applyVideoTransform();
                    }
                }
        );
    }

    private LinearLayout.LayoutParams
    equalButtonParams() {

        return new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        );
    }

    @Override
    public void onSurfaceTextureAvailable(
            SurfaceTexture surfaceTexture,
            int width,
            int height
    ) {

        if (destroyed) {
            return;
        }

        try {

            surfaceReady = true;

            if (videoSurface != null) {

                try {
                    videoSurface.release();
                } catch (Exception ignored) {
                }

                videoSurface = null;
            }

            videoSurface =
                    new Surface(
                            surfaceTexture
                    );

            if (mediaPlayer == null) {

                prepareVideo();

            } else {

                mediaPlayer.setSurface(
                        videoSurface
                );

                if (savedPosition > 0
                        && prepared) {

                    mediaPlayer.seekTo(
                            savedPosition
                    );
                }

                applyVideoTransform();
            }

        } catch (Exception e) {

            showError(
                    "VIDEO SURFACE ERROR\n\n"
                            + e.getClass()
                            .getSimpleName()
                            + "\n"
                            + String.valueOf(
                            e.getMessage()
                    )
            );
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(
            SurfaceTexture surfaceTexture,
            int width,
            int height
    ) {

        applyVideoTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(
            SurfaceTexture surfaceTexture
    ) {

        surfaceReady = false;

        if (mediaPlayer != null) {

            try {

                savedPosition =
                        mediaPlayer.getCurrentPosition();

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
            SurfaceTexture surfaceTexture
    ) {
    }

    private void prepareVideo() {

        if (destroyed
                || !surfaceReady
                || videoSurface == null) {

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
                    new MediaPlayer.OnPreparedListener() {

                        @Override
                        public void onPrepared(
                                MediaPlayer mp
                        ) {

                            if (destroyed) {
                                return;
                            }

                            try {

                                prepared = true;

                                videoWidth =
                                        mp.getVideoWidth();

                                videoHeight =
                                        mp.getVideoHeight();

                                seekBar.setMax(
                                        mp.getDuration()
                                );

                                statusText.setVisibility(
                                        View.GONE
                                );

                                applyVideoTransform();

                                if (savedPosition > 0) {

                                    mp.seekTo(
                                            savedPosition
                                    );
                                }

                                /*
                                 * यहां वीडियो अपने आप START नहीं होगी।
                                 * पहले surface तैयार होगा।
                                 * PLAY दबाने पर वीडियो चलेगी।
                                 */

                                playPauseButton.setText(
                                        "PLAY"
                                );

                            } catch (Exception e) {

                                showError(
                                        "VIDEO READY ERROR\n\n"
                                                + e.getClass()
                                                .getSimpleName()
                                                + "\n"
                                                + String.valueOf(
                                                e.getMessage()
                                        )
                                );
                            }
                        }
                    }
            );

            mediaPlayer.setOnVideoSizeChangedListener(
                    new MediaPlayer.OnVideoSizeChangedListener() {

                        @Override
                        public void onVideoSizeChanged(
                                MediaPlayer mp,
                                int width,
                                int height
                        ) {

                            videoWidth = width;
                            videoHeight = height;

                            applyVideoTransform();
                        }
                    }
            );

            mediaPlayer.setOnCompletionListener(
                    new MediaPlayer.OnCompletionListener() {

                        @Override
                        public void onCompletion(
                                MediaPlayer mp
                        ) {

                            try {

                                playPauseButton.setText(
                                        "PLAY"
                                );

                                seekBar.setProgress(
                                        0
                                );

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
                                int extra
                        ) {

                            prepared = false;

                            showError(
                                    "VIDEO PLAYBACK ERROR\n\n"
                                            + "what = "
                                            + what
                                            + "\n"
                                            + "extra = "
                                            + extra
                            );

                            return true;
                        }
                    }
            );

            Uri uri =
                    Uri.parse(
                            videoUriString
                    );

            mediaPlayer.setDataSource(
                    this,
                    uri
            );

            mediaPlayer.prepareAsync();

        } catch (Exception e) {

            showError(
                    "VIDEO PREPARE ERROR\n\n"
                            + e.getClass()
                            .getSimpleName()
                            + "\n"
                            + String.valueOf(
                            e.getMessage()
                    )
            );
        }
    }

    private void togglePlayPause() {

        if (mediaPlayer == null
                || !prepared
                || !surfaceReady) {

            showError(
                    "वीडियो अभी तैयार नहीं है।"
            );

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

        } catch (Exception e) {

            showError(
                    "PLAY ERROR\n\n"
                            + e.getClass()
                            .getSimpleName()
                            + "\n"
                            + String.valueOf(
                            e.getMessage()
                    )
            );
        }
    }

    private void seekBackward() {

        if (mediaPlayer == null
                || !prepared) {

            return;
        }

        try {

            int current =
                    mediaPlayer.getCurrentPosition();

            mediaPlayer.seekTo(
                    Math.max(
                            0,
                            current - 10000
                    )
            );

        } catch (Exception ignored) {
        }
    }

    private void seekForward() {

        if (mediaPlayer == null
                || !prepared) {

            return;
        }

        try {

            int current =
                    mediaPlayer.getCurrentPosition();

            int duration =
                    mediaPlayer.getDuration();

            mediaPlayer.seekTo(
                    Math.min(
                            duration,
                            current + 10000
                    )
            );

        } catch (Exception ignored) {
        }
    }

    private void applyVideoTransform() {

        if (textureView == null
                || videoContainer == null
                || videoWidth <= 0
                || videoHeight <= 0) {

            return;
        }

        int viewWidth =
                videoContainer.getWidth();

        int viewHeight =
                videoContainer.getHeight();

        if (viewWidth <= 0
                || viewHeight <= 0) {

            return;
        }

        float videoRatio =
                (float) videoWidth
                        / (float) videoHeight;

        float containerRatio =
                (float) viewWidth
                        / (float) viewHeight;

        float scale;

        /*
         * FIT CENTER:
         * पूरा वीडियो दिखाई देगा।
         * कोई हिस्सा crop नहीं होगा।
         */

        if (videoRatio > containerRatio) {

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
                        / 2.0f;

        float dy =
                (viewHeight - scaledHeight)
                        / 2.0f;

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

    private String formatTime(
            int milliseconds
    ) {

        int totalSeconds =
                Math.max(
                        0,
                        milliseconds / 1000
                );

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

    private void showError(
            String message
    ) {

        if (destroyed) {
            return;
        }

        try {

            new AlertDialog.Builder(this)
                    .setTitle(
                            "Vishal Secure"
                    )
                    .setMessage(
                            message
                    )
                    .setPositiveButton(
                            "OK",
                            null
                    )
                    .show();

        } catch (Exception ignored) {
        }
    }

    private void showErrorAndClose(
            String message
    ) {

        try {

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
                    .show();

        } catch (Exception ignored) {

            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mediaPlayer != null) {

            try {

                savedPosition =
                        mediaPlayer.getCurrentPosition();

            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onDestroy() {

        destroyed = true;

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

        if (videoSurface != null) {

            try {
                videoSurface.release();
            } catch (Exception ignored) {
            }

            videoSurface = null;
        }

        super.onDestroy();
    }

    private int dp(
            int value
    ) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return (int)
                (value * density + 0.5f);
    }
}
