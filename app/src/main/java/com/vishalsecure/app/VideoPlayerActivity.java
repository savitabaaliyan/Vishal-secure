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
    private boolean shouldResumeAfterSurface = false;

    private int savedPosition = 0;
    private int videoWidth = 0;
    private int videoHeight = 0;

    private final Handler handler = new Handler();

    private final Runnable updateProgress = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && videoPrepared) {
                try {
                    int duration = mediaPlayer.getDuration();
                    int position = mediaPlayer.getCurrentPosition();

                    if (!userSeeking && duration > 0) {
                        seekBar.setMax(duration);
                        seekBar.setProgress(position);
                    }

                    timeText.setText(
                            formatTime(position)
                                    + " / "
                                    + formatTime(duration)
                    );
                } catch (Exception ignored) {
                }
            }

            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        videoUriString = getIntent().getStringExtra("video_uri");

        if (videoUriString == null || videoUriString.trim().isEmpty()) {
            showErrorAndClose("वीडियो नहीं मिला।");
            return;
        }

        buildPlayerScreen();

        handler.post(updateProgress);
    }

    private void buildPlayerScreen() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        videoContainer = new FrameLayout(this);
        videoContainer.setBackgroundColor(Color.BLACK);
        videoContainer.setKeepScreenOn(true);

        LinearLayout.LayoutParams videoContainerParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1.0f
                );

        root.addView(videoContainer, videoContainerParams);

        textureView = new TextureView(this);
        textureView.setBackgroundColor(Color.BLACK);
        textureView.setSurfaceTextureListener(this);
        textureView.setKeepScreenOn(true);

        FrameLayout.LayoutParams textureParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );

        textureParams.gravity = Gravity.CENTER;

        videoContainer.addView(textureView, textureParams);

        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(15);
        statusText.setGravity(Gravity.CENTER);
        statusText.setText("वीडियो तैयार हो रहा है...");
        statusText.setBackgroundColor(Color.TRANSPARENT);

        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        statusParams.gravity = Gravity.CENTER;

        videoContainer.addView(statusText, statusParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(
                dp(8),
                dp(6),
                dp(8),
                dp(8)
        );
        controls.setBackgroundColor(Color.BLACK);

        seekBar = new SeekBar(this);

        controls.addView(
                seekBar,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(35)
                )
        );

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);

        backButton = new Button(this);
        backButton.setText("−10s");

        playPauseButton = new Button(this);
        playPauseButton.setText("PLAY");

        forwardButton = new Button(this);
        forwardButton.setText("+10s");

        closeButton = new Button(this);
        closeButton.setText("CLOSE");

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

        timeText = new TextView(this);
        timeText.setTextColor(Color.WHITE);
        timeText.setTextSize(14);
        timeText.setGravity(Gravity.CENTER);
        timeText.setText("00:00 / 00:00");

        controls.addView(
                timeText,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(30)
                )
        );

        root.addView(
                controls,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

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
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {
                        if (fromUser && mediaPlayer != null && videoPrepared) {
                            timeText.setText(
                                    formatTime(progress)
                                            + " / "
                                            + formatTime(mediaPlayer.getDuration())
                            );
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        userSeeking = true;
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        userSeeking = false;

                        if (mediaPlayer != null && videoPrepared) {
                            try {
                                mediaPlayer.seekTo(seekBar.getProgress());
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

        setContentView(root);
    }

    @Override
    public void onSurfaceTextureAvailable(
            SurfaceTexture surface,
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

        videoSurface = new Surface(surface);

        if (mediaPlayer == null) {
            startVideo();
        } else {
            try {
                boolean wasPlaying = shouldResumeAfterSurface;

                mediaPlayer.setSurface(videoSurface);

                if (savedPosition > 0) {
                    mediaPlayer.seekTo(savedPosition);
                }

                applyVideoTransform();

                if (wasPlaying && videoPrepared) {
                    mediaPlayer.start();
                    playPauseButton.setText("PAUSE");
                }

            } catch (Exception e) {
                showErrorAndClose("वीडियो दोबारा शुरू नहीं हो सका।");
            }
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(
            SurfaceTexture surface,
            int width,
            int height
    ) {
        applyVideoTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(
            SurfaceTexture surface
    ) {
        textureReady = false;

        if (mediaPlayer != null) {
            try {
                shouldResumeAfterSurface = mediaPlayer.isPlaying();
                savedPosition = mediaPlayer.getCurrentPosition();

                mediaPlayer.setSurface(null);
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
            SurfaceTexture surface
    ) {
    }

    private void startVideo() {

        if (!textureReady || videoSurface == null) {
            return;
        }

        try {

            mediaPlayer = new MediaPlayer();

            mediaPlayer.setAudioStreamType(
                    AudioManager.STREAM_MUSIC
            );

            mediaPlayer.setScreenOnWhilePlaying(true);

            mediaPlayer.setSurface(videoSurface);

            mediaPlayer.setOnPreparedListener(
                    new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {

                            videoPrepared = true;

                            videoWidth = mp.getVideoWidth();
                            videoHeight = mp.getVideoHeight();

                            seekBar.setMax(mp.getDuration());

                            statusText.setVisibility(View.GONE);

                            applyVideoTransform();

                            if (savedPosition > 0) {
                                try {
                                    mp.seekTo(savedPosition);
                                } catch (Exception ignored) {
                                }
                            }

                            mp.start();

                            playPauseButton.setText("PAUSE");
                        }
                    }
            );

            mediaPlayer.setOnCompletionListener(
                    new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            playPauseButton.setText("PLAY");
                            seekBar.setProgress(0);
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
                            showErrorAndClose(
                                    "वीडियो चलाने में समस्या हुई।"
                            );
                            return true;
                        }
                    }
            );

            Uri videoUri = Uri.parse(videoUriString);

            mediaPlayer.setDataSource(
                    this,
                    videoUri
            );

            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            showErrorAndClose(
                    "वीडियो खोलने में समस्या हुई।"
            );
        }
    }

    private void applyVideoTransform() {

        if (textureView == null
                || videoContainer == null
                || videoWidth <= 0
                || videoHeight <= 0) {
            return;
        }

        int viewWidth = videoContainer.getWidth();
        int viewHeight = videoContainer.getHeight();

        if (viewWidth <= 0 || viewHeight <= 0) {
            return;
        }

        float videoRatio =
                (float) videoWidth / (float) videoHeight;

        float viewRatio =
                (float) viewWidth / (float) viewHeight;

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
                (viewWidth - scaledWidth) / 2.0f;

        float dy =
                (viewHeight - scaledHeight) / 2.0f;

        Matrix matrix = new Matrix();

        matrix.setScale(
                scale,
                scale
        );

        matrix.postTranslate(
                dx,
                dy
        );

        textureView.setTransform(matrix);
    }

    private void togglePlayPause() {

        if (mediaPlayer == null || !videoPrepared) {
            return;
        }

        try {

            if (mediaPlayer.isPlaying()) {

                mediaPlayer.pause();

                playPauseButton.setText("PLAY");

            } else {

                mediaPlayer.start();

                playPauseButton.setText("PAUSE");
            }

        } catch (Exception ignored) {
        }
    }

    private void seekBackward() {

        if (mediaPlayer == null || !videoPrepared) {
            return;
        }

        try {

            int current =
                    mediaPlayer.getCurrentPosition();

            int newPosition =
                    Math.max(0, current - 10000);

            mediaPlayer.seekTo(newPosition);

        } catch (Exception ignored) {
        }
    }

    private void seekForward() {

        if (mediaPlayer == null || !videoPrepared) {
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

            mediaPlayer.seekTo(newPosition);

        } catch (Exception ignored) {
        }
    }

    private String formatTime(int milliseconds) {

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

    private void showErrorAndClose(
            String message
    ) {

        new AlertDialog.Builder(this)
                .setTitle("Vishal Secure")
                .setMessage(message)
                .setPositiveButton(
                        "OK",
                        (dialog, which) -> finish()
                )
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mediaPlayer != null) {

            try {

                if (mediaPlayer.isPlaying()) {
                    shouldResumeAfterSurface = true;
                }

                savedPosition =
                        mediaPlayer.getCurrentPosition();

                mediaPlayer.pause();

            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(updateProgress);

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

    private int dp(int value) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return (int)
                (value * density + 0.5f);
    }
}
