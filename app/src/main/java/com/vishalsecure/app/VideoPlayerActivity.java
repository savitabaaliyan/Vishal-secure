package com.vishalsecure.app;

import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.SeekBar;
import android.media.MediaPlayer;
import android.content.res.Configuration;

import java.util.Locale;

public class VideoPlayerActivity extends Activity {

    private FrameLayout root;
    private SurfaceView surfaceView;
    private MediaPlayer mediaPlayer;

    private LinearLayout controlsLayout;
    private Button backButton;
    private Button rewindButton;
    private Button playPauseButton;
    private Button forwardButton;
    private SeekBar seekBar;
    private TextView timeText;

    private Uri videoUri;

    private int savedPosition = 0;
    private boolean wasPlaying = false;
    private boolean surfaceReady = false;
    private boolean prepared = false;

    private int videoWidth = 0;
    private int videoHeight = 0;

    private final android.os.Handler handler =
            new android.os.Handler();

    private final Runnable updateProgress = new Runnable() {
        @Override
        public void run() {

            if (mediaPlayer != null && prepared) {

                try {
                    int position = mediaPlayer.getCurrentPosition();
                    int duration = mediaPlayer.getDuration();

                    if (duration > 0) {
                        seekBar.setMax(duration);
                        seekBar.setProgress(position);
                        timeText.setText(
                                formatTime(position)
                                        + " / "
                                        + formatTime(duration)
                        );
                    }

                } catch (Exception ignored) {
                }
            }

            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().setStatusBarColor(Color.BLACK);

        videoUri = getIntent().getParcelableExtra("video_uri");

        if (videoUri == null) {
            finish();
            return;
        }

        createPlayerLayout();

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

        createMediaPlayer();

        handler.post(updateProgress);
    }

    private void createPlayerLayout() {

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout mainLayout =
                new LinearLayout(this);

        mainLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        mainLayout.setBackgroundColor(Color.BLACK);

        root.addView(
                mainLayout,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        FrameLayout videoContainer =
                new FrameLayout(this);

        videoContainer.setBackgroundColor(Color.BLACK);

        mainLayout.addView(
                videoContainer,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1.0f
                )
        );

        surfaceView = new SurfaceView(this);

        surfaceView.setBackgroundColor(Color.BLACK);

        videoContainer.addView(
                surfaceView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                )
        );

        SurfaceHolder holder =
                surfaceView.getHolder();

        holder.addCallback(
                new SurfaceHolder.Callback() {

                    @Override
                    public void surfaceCreated(
                            SurfaceHolder holder
                    ) {

                        surfaceReady = true;

                        if (mediaPlayer != null) {

                            try {
                                mediaPlayer.setDisplay(holder);

                                if (prepared) {
                                    adjustVideoSize();
                                }

                            } catch (Exception ignored) {
                            }
                        }
                    }

                    @Override
                    public void surfaceChanged(
                            SurfaceHolder holder,
                            int format,
                            int width,
                            int height
                    ) {

                        surfaceReady = true;

                        if (mediaPlayer != null) {

                            try {
                                mediaPlayer.setDisplay(holder);

                                if (prepared) {
                                    adjustVideoSize();
                                }

                            } catch (Exception ignored) {
                            }
                        }
                    }

                    @Override
                    public void surfaceDestroyed(
                            SurfaceHolder holder
                    ) {

                        surfaceReady = false;

                        if (mediaPlayer != null) {

                            try {
                                savedPosition =
                                        mediaPlayer.getCurrentPosition();

                                wasPlaying =
                                        mediaPlayer.isPlaying();

                                mediaPlayer.setDisplay(null);

                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
        );

        createControls(mainLayout);

        setContentView(root);
    }

    private void createControls(
            LinearLayout mainLayout
    ) {

        controlsLayout =
                new LinearLayout(this);

        controlsLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        controlsLayout.setGravity(
                Gravity.CENTER
        );

        controlsLayout.setPadding(
                12,
                8,
                12,
                8
        );

        controlsLayout.setBackgroundColor(
                Color.BLACK
        );

        mainLayout.addView(
                controlsLayout,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        seekBar = new SeekBar(this);

        controlsLayout.addView(
                seekBar,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        timeText =
                new TextView(this);

        timeText.setTextColor(Color.WHITE);
        timeText.setTextSize(14);
        timeText.setGravity(Gravity.CENTER);

        timeText.setText("00:00 / 00:00");

        controlsLayout.addView(
                timeText,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        LinearLayout buttons =
                new LinearLayout(this);

        buttons.setOrientation(
                LinearLayout.HORIZONTAL
        );

        buttons.setGravity(
                Gravity.CENTER
        );

        controlsLayout.addView(
                buttons,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        rewindButton =
                new Button(this);

        rewindButton.setText("−10s");

        buttons.addView(
                rewindButton,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        playPauseButton =
                new Button(this);

        playPauseButton.setText("PLAY");

        buttons.addView(
                playPauseButton,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        forwardButton =
                new Button(this);

        forwardButton.setText("+10s");

        buttons.addView(
                forwardButton,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        backButton =
                new Button(this);

        backButton.setText("CLOSE");

        buttons.addView(
                backButton,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        playPauseButton.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {

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
                }
        );

        rewindButton.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        seekBackward();
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

        backButton.setOnClickListener(
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

                        if (fromUser &&
                                mediaPlayer != null &&
                                prepared) {

                            try {
                                mediaPlayer.seekTo(progress);
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(
                            SeekBar seekBar
                    ) {
                    }

                    @Override
                    public void onStopTrackingTouch(
                            SeekBar seekBar
                    ) {
                    }
                }
        );
    }

    private void createMediaPlayer() {

        mediaPlayer =
                new MediaPlayer();

        try {

            mediaPlayer.setDataSource(
                    this,
                    videoUri
            );

            mediaPlayer.setOnPreparedListener(
                    new MediaPlayer.OnPreparedListener() {

                        @Override
                        public void onPrepared(
                                MediaPlayer mp
                        ) {

                            prepared = true;

                            videoWidth =
                                    mp.getVideoWidth();

                            videoHeight =
                                    mp.getVideoHeight();

                            if (surfaceReady) {
                                mp.setDisplay(
                                        surfaceView.getHolder()
                                );

                                adjustVideoSize();
                            }

                            int duration =
                                    mp.getDuration();

                            seekBar.setMax(duration);

                            if (savedPosition > 0 &&
                                    savedPosition < duration) {

                                mp.seekTo(savedPosition);
                            }

                            if (wasPlaying) {

                                mp.start();

                                playPauseButton.setText(
                                        "PAUSE"
                                );

                            } else {

                                playPauseButton.setText(
                                        "PLAY"
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
                                    mp.getDuration()
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

                            playPauseButton.setText(
                                    "PLAY"
                            );

                            return true;
                        }
                    }
            );

            mediaPlayer.prepareAsync();

        } catch (Exception e) {

            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }
    }

    /*
     * IMPORTANT:
     *
     * यहाँ CENTER-CROP नहीं है।
     *
     * वीडियो को उपलब्ध जगह के अंदर पूरा रखा जाता है।
     * इसलिए ऊपर/नीचे या बायाँ/दायाँ हिस्सा कटेगा नहीं।
     */
    private void adjustVideoSize() {

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

                        int containerWidth =
                                surfaceView.getWidth();

                        int containerHeight =
                                surfaceView.getHeight();

                        if (containerWidth <= 0 ||
                                containerHeight <= 0) {
                            return;
                        }

                        float videoRatio =
                                (float) videoWidth
                                        / (float) videoHeight;

                        float containerRatio =
                                (float) containerWidth
                                        / (float) containerHeight;

                        int newWidth;
                        int newHeight;

                        if (videoRatio >
                                containerRatio) {

                            /*
                             * वीडियो ज्यादा चौड़ी है।
                             * Width पूरी, height proportionally कम।
                             */
                            newWidth =
                                    containerWidth;

                            newHeight =
                                    Math.round(
                                            newWidth
                                                    / videoRatio
                                    );

                        } else {

                            /*
                             * वीडियो ज्यादा ऊँची है।
                             * Height पूरी, width proportionally कम।
                             */
                            newHeight =
                                    containerHeight;

                            newWidth =
                                    Math.round(
                                            newHeight
                                                    * videoRatio
                                    );
                        }

                        FrameLayout.LayoutParams params =
                                new FrameLayout.LayoutParams(
                                        newWidth,
                                        newHeight,
                                        Gravity.CENTER
                                );

                        surfaceView.setLayoutParams(
                                params
                        );
                    }
                }
        );
    }

    private void seekBackward() {

        if (mediaPlayer == null ||
                !prepared) {
            return;
        }

        try {

            int newPosition =
                    mediaPlayer.getCurrentPosition()
                            - 10000;

            if (newPosition < 0) {
                newPosition = 0;
            }

            mediaPlayer.seekTo(newPosition);

        } catch (Exception ignored) {
        }
    }

    private void seekForward() {

        if (mediaPlayer == null ||
                !prepared) {
            return;
        }

        try {

            int newPosition =
                    mediaPlayer.getCurrentPosition()
                            + 10000;

            int duration =
                    mediaPlayer.getDuration();

            if (newPosition > duration) {
                newPosition = duration;
            }

            mediaPlayer.seekTo(newPosition);

        } catch (Exception ignored) {
        }
    }

    private String formatTime(
            int milliseconds
    ) {

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

    @Override
    protected void onSaveInstanceState(
            Bundle outState
    ) {

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

    @Override
    public void onConfigurationChanged(
            Configuration newConfig
    ) {

        super.onConfigurationChanged(
                newConfig
        );

        /*
         * Rotation के बाद नई screen size मिलने पर
         * वीडियो को दोबारा proportionally fit करें।
         */
        surfaceView.postDelayed(
                new Runnable() {

                    @Override
                    public void run() {
                        adjustVideoSize();
                    }
                },
                200
        );
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

        super.onDestroy();
    }
}
