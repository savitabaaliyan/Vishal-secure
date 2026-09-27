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

    private boolean textureReady = false;
    private boolean videoPrepared = false;
    private boolean userSeeking = false;

    private boolean wasPlayingBeforeSurfaceDestroy = false;

    private int savedPosition = 0;
    private int videoWidth = 0;
    private int videoHeight = 0;

    private final android.os.Handler handler =
            new android.os.Handler();

    private final Runnable updateProgress =
            new Runnable() {
                @Override
                public void run() {

                    if (mediaPlayer != null
                            && videoPrepared) {

                        try {

                            int duration =
                                    mediaPlayer.getDuration();

                            int position =
                                    mediaPlayer.getCurrentPosition();

                            if (!userSeeking
                                    && duration > 0) {

                                seekBar.setMax(
                                        duration
                                );

                                seekBar.setProgress(
                                        position
                                );
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
         * Screenshot / screen-record protection.
         *
         * TextureView.setSecure() is NOT used because
         * that method is not available on Android TextureView.
         */
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
                    "वीडियो नहीं मिला।"
            );

            return;
        }

        buildPlayerScreen();

        handler.post(
                updateProgress
        );
    }

    private void buildPlayerScreen() {

        /*
         * Main screen:
         *
         * TOP    = video
         * BOTTOM = controls
         *
         * Controls video के ऊपर नहीं आएंगे।
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
         * VIDEO CONTAINER
         *
         * Weight 1 means video gets all remaining
         * screen space after controls.
         */
        videoContainer =
                new FrameLayout(this);

        videoContainer.setBackgroundColor(
                Color.BLACK
        );

        videoContainer.setKeepScreenOn(
                true
        );

        LinearLayout.LayoutParams
                videoContainerParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1.0f
                );

        root.addView(
                videoContainer,
                videoContainerParams
        );

        /*
         * TEXTURE VIEW
         *
         * The TextureView itself always fills the
         * video container. The Matrix below controls
         * the actual video scaling.
         */
        textureView =
                new TextureView(this);

        textureView.setBackgroundColor(
                Color.BLACK
        );

        textureView.setKeepScreenOn(
                true
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

        /*
         * Status text
         */
        statusText =
                new TextView(this);

        statusText.setText(
                "वीडियो तैयार हो रहा है..."
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

        FrameLayout.LayoutParams
                statusParams =
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
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        buttonRow.addView(
                playPauseButton,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        buttonRow.addView(
                forwardButton,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        buttonRow.addView(
                closeButton,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        controls.addView(
                buttonRow,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
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

        /*
         * Controls are added AFTER video container,
         * therefore they do not cover the video.
         */
        root.addView(
                controls,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        setContentView(
                root
        );

        /*
         * BUTTONS
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
                            boolean fromUser
                    ) {

                        if (fromUser
                                && mediaPlayer != null
                                && videoPrepared) {

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
                                && videoPrepared) {

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

        /*
         * Whenever the video container changes size,
         * including rotation, recalculate FIT CENTER.
         */
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

    /*
     * =========================================================
     * TEXTURE SURFACE
     * =========================================================
     */

    @Override
    public void onSurfaceTextureAvailable(
            SurfaceTexture surfaceTexture,
            int width,
            int height
    ) {

        textureReady = true;

        if (videoSurface != null) {

            try {
                videoSurface.release();
            } catch (Exception ignored) {
            }
        }

        videoSurface =
                new Surface(
                        surfaceTexture
                );

        if (mediaPlayer == null) {

            startVideo();

        } else {

            try {

                mediaPlayer.setSurface(
                        videoSurface
                );

                if (savedPosition > 0) {

                    mediaPlayer.seekTo(
                            savedPosition
                    );
                }

                applyVideoTransform();

                if (wasPlayingBeforeSurfaceDestroy
                        && videoPrepared) {

                    mediaPlayer.start();

                    playPauseButton.setText(
                            "PAUSE"
                    );
                }

            } catch (Exception e) {

                showError(
                        "SURFACE REATTACH ERROR\n\n"
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

    @Override
    public void onSurfaceTextureSizeChanged(
            SurfaceTexture surfaceTexture,
            int width,
            int height
    ) {

        /*
         * Rotation can change the TextureView size.
         * Recalculate the video immediately.
         */
        applyVideoTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(
            SurfaceTexture surfaceTexture
    ) {

        textureReady = false;

        if (mediaPlayer != null) {

            try {

                wasPlayingBeforeSurfaceDestroy =
                        mediaPlayer.isPlaying();

                savedPosition =
                        mediaPlayer.getCurrentPosition();

                mediaPlayer.setSurface(
                        null
                );

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
        // Nothing required here.
    }

    /*
     * =========================================================
     * START VIDEO
     * =========================================================
     */

    private void startVideo() {

        if (!textureReady
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

                            videoPrepared = true;

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

                            /*
                             * VERY IMPORTANT:
                             * Calculate the video size only
                             * after Android knows the actual
                             * video width and height.
                             */
                            applyVideoTransform();

                            if (savedPosition > 0) {

                                try {

                                    mp.seekTo(
                                            savedPosition
                                    );

                                } catch (Exception ignored) {
                                }
                            }

                            try {

                                mp.start();

                                playPauseButton.setText(
                                        "PAUSE"
                                );

                            } catch (Exception e) {

                                showError(
                                        "START ERROR\n\n"
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

            mediaPlayer.setOnCompletionListener(
                    new MediaPlayer.OnCompletionListener() {

                        @Override
                        public void onCompletion(
                                MediaPlayer mp
                        ) {

                            playPauseButton.setText(
                                    "PLAY"
                            );

                            seekBar.setProgress(
                                    0
                            );
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

            Uri videoUri =
                    Uri.parse(
                            videoUriString
                    );

            mediaPlayer.setDataSource(
                    this,
                    videoUri
            );

            mediaPlayer.prepareAsync();

        } catch (Exception e) {

            showError(
                    "VIDEO START ERROR\n\n"
                            + e.getClass()
                            .getSimpleName()
                            + "\n"
                            + String.valueOf(
                            e.getMessage()
                    )
            );
        }
    }

    /*
     * =========================================================
     * FIT CENTER VIDEO
     * =========================================================
     *
     * This is the important part.
     *
     * The complete video is fitted INSIDE the available
     * video area.
     *
     * No CENTER-CROP.
     * No stretching.
     * No cutting of top/bottom.
     */

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

        /*
         * FIT CENTER:
         *
         * Choose the smaller scale so the COMPLETE
         * video always remains inside the container.
         */
        float scale;

        if (videoRatio > containerRatio) {

            /*
             * Video is wider.
             * Fit to container width.
             */
            scale =
                    (float) viewWidth
                            / (float) videoWidth;

        } else {

            /*
             * Video is taller.
             * Fit to container height.
             */
            scale =
                    (float) viewHeight
                            / (float) videoHeight;
        }

        float scaledWidth =
                videoWidth * scale;

        float scaledHeight =
                videoHeight * scale;

        /*
         * Center the complete video.
         */
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

    /*
     * =========================================================
     * PLAYBACK CONTROLS
     * =========================================================
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

        } catch (Exception e) {

            showError(
                    "PLAY / PAUSE ERROR\n\n"
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

        } catch (Exception e) {

            showError(
                    "BACKWARD ERROR\n\n"
                            + e.getClass()
                            .getSimpleName()
                            + "\n"
                            + String.valueOf(
                            e.getMessage()
                    )
            );
        }
    }

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

        } catch (Exception e) {

            showError(
                    "FORWARD ERROR\n\n"
                            + e.getClass()
                            .getSimpleName()
                            + "\n"
                            + String.valueOf(
                            e.getMessage()
                    )
            );
        }
    }

    /*
     * =========================================================
     * TIME
     * =========================================================
     */

    private String formatTime(
            int milliseconds
    ) {

        int totalSeconds =
                milliseconds / 1000;

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

    /*
     * =========================================================
     * ERROR
     * =========================================================
     */

    private void showError(
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
                            null
                    )
                    .setCancelable(
                            true
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
                            (dialog, which) ->
                                    finish()
                    )
                    .setCancelable(
                            false
                    )
                    .show();

        } catch (Exception ignored) {

            finish();
        }
    }

    /*
     * =========================================================
     * ACTIVITY LIFECYCLE
     * =========================================================
     */

    @Override
    protected void onPause() {
        super.onPause();

        /*
         * Save current position.
         *
         * We do NOT destroy MediaPlayer here.
         * This helps preserve playback during rotation
         * when configChanges is being used.
         */
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

        handler.removeCallbacks(
                updateProgress
        );

        if (mediaPlayer != null) {

            try {

                mediaPlayer.stop();

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

    /*
     * =========================================================
     * DP
     * =========================================================
     */

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
