package com.vishalsecure.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
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
    private SeekBar seekBar;
    private Button closeButton;

    private String videoUriString;

    private boolean surfaceReady = false;
    private boolean prepared = false;
    private boolean userSeeking = false;

    private int videoWidth = 0;
    private int videoHeight = 0;

    private final Handler handler = new Handler();

    private final Runnable updateProgress = new Runnable() {
        @Override
        public void run() {

            if (mediaPlayer != null && prepared) {

                try {

                    int position = mediaPlayer.getCurrentPosition();
                    int duration = mediaPlayer.getDuration();

                    if (!userSeeking && seekBar != null) {

                        seekBar.setMax(Math.max(duration, 1));
                        seekBar.setProgress(
                                Math.min(position, Math.max(duration, 1))
                        );
                    }

                    updateTimeText(position, duration);

                    updatePlayPauseButton();

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

        if (videoUriString == null ||
                videoUriString.trim().isEmpty()) {

            showErrorAndClose("Video file नहीं मिली।");
            return;
        }

        buildPlayerScreen();
    }

    private void buildPlayerScreen() {

        FrameLayout root = new FrameLayout(this);

        root.setBackgroundColor(Color.BLACK);

        /*
         * VIDEO AREA
         */
        surfaceView = new SurfaceView(this);

        surfaceView.setSecure(true);
        surfaceView.setKeepScreenOn(true);

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        FrameLayout.LayoutParams surfaceParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        surfaceParams.gravity = Gravity.CENTER;

        root.addView(surfaceView, surfaceParams);

        /*
         * LOADING / ERROR TEXT
         */
        statusText = new TextView(this);

        statusText.setText("Video loading...");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        statusParams.gravity = Gravity.CENTER;

        root.addView(statusText, statusParams);

        /*
         * BOTTOM CONTROL PANEL
         */
        LinearLayout controlPanel =
                new LinearLayout(this);

        controlPanel.setOrientation(
                LinearLayout.VERTICAL
        );

        controlPanel.setPadding(
                dp(12),
                dp(8),
                dp(12),
                dp(12)
        );

        controlPanel.setBackgroundColor(
                Color.argb(210, 0, 0, 0)
        );

        /*
         * TIME TEXT
         */
        timeText = new TextView(this);

        timeText.setText("00:00 / 00:00");
        timeText.setTextColor(Color.WHITE);
        timeText.setTextSize(14);
        timeText.setGravity(Gravity.CENTER);

        controlPanel.addView(
                timeText,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(28)
                )
        );

        /*
         * SEEK BAR
         */
        seekBar = new SeekBar(this);

        seekBar.setMax(1);
        seekBar.setProgress(0);

        controlPanel.addView(
                seekBar,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(45)
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

        buttonRow.setGravity(Gravity.CENTER);

        /*
         * PLAY / PAUSE
         */
        playPauseButton =
                new Button(this);

        playPauseButton.setText("PLAY");
        playPauseButton.setAllCaps(false);

        playPauseButton.setOnClickListener(v -> {

            if (mediaPlayer == null ||
                    !prepared) {
                return;
            }

            try {

                if (mediaPlayer.isPlaying()) {

                    mediaPlayer.pause();

                } else {

                    mediaPlayer.start();
                }

                updatePlayPauseButton();

            } catch (Exception ignored) {
            }
        });

        buttonRow.addView(
                playPauseButton,
                new LinearLayout.LayoutParams(
                        dp(120),
                        dp(55)
                )
        );

        /*
         * CLOSE
         */
        closeButton =
                new Button(this);

        closeButton.setText("CLOSE");
        closeButton.setAllCaps(false);

        closeButton.setOnClickListener(v -> finish());

        LinearLayout.LayoutParams closeButtonParams =
                new LinearLayout.LayoutParams(
                        dp(120),
                        dp(55)
                );

        closeButtonParams.setMargins(
                dp(20),
                0,
                0,
                0
        );

        buttonRow.addView(
                closeButton,
                closeButtonParams
        );

        controlPanel.addView(
                buttonRow,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(60)
                )
        );

        /*
         * SEEK BAR LISTENER
         */
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser) {

                        if (fromUser &&
                                mediaPlayer != null &&
                                prepared) {

                            try {

                                updateTimeText(
                                        progress,
                                        mediaPlayer.getDuration()
                                );

                            } catch (Exception ignored) {
                            }
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(
                            SeekBar seekBar) {

                        userSeeking = true;
                    }

                    @Override
                    public void onStopTrackingTouch(
                            SeekBar seekBar) {

                        if (mediaPlayer != null &&
                                prepared) {

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

        /*
         * PUT CONTROL PANEL AT BOTTOM
         */
        FrameLayout.LayoutParams controlParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        controlParams.gravity =
                Gravity.BOTTOM;

        root.addView(
                controlPanel,
                controlParams
        );

        setContentView(root);
    }

    @Override
    public void surfaceCreated(
            SurfaceHolder holder) {

        surfaceReady = true;

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

            } catch (Exception ignored) {
            }
        }

        /*
         * केवल वीडियो का proportion सही रखने के लिए
         * SurfaceView को adjust करेंगे।
         */
        adjustVideoSize();
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

            mediaPlayer.setOnVideoSizeChangedListener(
                    (mp, width, height) -> {

                        videoWidth = width;
                        videoHeight = height;

                        adjustVideoSize();
                    }
            );

            mediaPlayer.setOnPreparedListener(
                    mp -> {

                        try {

                            prepared = true;

                            int duration =
                                    mp.getDuration();

                            if (seekBar != null) {

                                seekBar.setMax(
                                        Math.max(duration, 1)
                                );

                                seekBar.setProgress(0);
                            }

                            updateTimeText(
                                    0,
                                    duration
                            );

                            mp.setDisplay(
                                    surfaceHolder
                            );

                            /*
                             * Video अपने आप शुरू होगी।
                             */
                            mp.start();

                            if (statusText != null) {

                                statusText.setVisibility(
                                        View.GONE
                                );
                            }

                            updatePlayPauseButton();

                            adjustVideoSize();

                            handler.removeCallbacks(
                                    updateProgress
                            );

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

                        if (seekBar != null) {

                            try {

                                seekBar.setProgress(
                                        mp.getDuration()
                                );

                            } catch (Exception ignored) {
                            }
                        }

                        updateTimeText(
                                getDurationSafe(),
                                getDurationSafe()
                        );

                        updatePlayPauseButton();
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

    /*
     * VIDEO को stretch होने से रोकना।
     * वीडियो का original width/height ratio रखा जाता है।
     */
    private void adjustVideoSize() {

        if (surfaceView == null ||
                videoWidth <= 0 ||
                videoHeight <= 0) {

            return;
        }

        surfaceView.post(() -> {

            try {

                int parentWidth =
                        surfaceView.getRootView()
                                .getWidth();

                int parentHeight =
                        surfaceView.getRootView()
                                .getHeight();

                if (parentWidth <= 0 ||
                        parentHeight <= 0) {
                    return;
                }

                float videoRatio =
                        (float) videoWidth /
                        (float) videoHeight;

                float screenRatio =
                        (float) parentWidth /
                        (float) parentHeight;

                int finalWidth;
                int finalHeight;

                if (videoRatio > screenRatio) {

                    /*
                     * Video चौड़ी है।
                     */
                    finalWidth =
                            parentWidth;

                    finalHeight =
                            (int) (
                                    parentWidth /
                                    videoRatio
                            );

                } else {

                    /*
                     * Video लंबी है।
                     */
                    finalHeight =
                            parentHeight;

                    finalWidth =
                            (int) (
                                    parentHeight *
                                    videoRatio
                            );
                }

                FrameLayout.LayoutParams params =
                        (FrameLayout.LayoutParams)
                                surfaceView.getLayoutParams();

                params.width = finalWidth;
                params.height = finalHeight;
                params.gravity = Gravity.CENTER;

                surfaceView.setLayoutParams(
                        params
                );

            } catch (Exception ignored) {
            }
        });
    }

    private void updatePlayPauseButton() {

        if (playPauseButton == null) {
            return;
        }

        if (mediaPlayer != null &&
                prepared) {

            try {

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
    }

    private void updateTimeText(
            int current,
            int total) {

        if (timeText == null) {
            return;
        }

        timeText.setText(
                formatTime(current)
                        + " / "
                        + formatTime(total)
        );
    }

    private String formatTime(int milliseconds) {

        if (milliseconds < 0) {
            milliseconds = 0;
        }

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

    private int getDurationSafe() {

        if (mediaPlayer == null) {
            return 0;
        }

        try {

            return mediaPlayer.getDuration();

        } catch (Exception e) {

            return 0;
        }
    }

    private void releasePlayer() {

        handler.removeCallbacks(
                updateProgress
        );

        prepared = false;

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

        runOnUiThread(() -> {

            if (statusText != null) {

                statusText.setText(message);

                statusText.setVisibility(
                        View.VISIBLE
                );
            }
        });
    }

    private void showErrorAndClose(
            String message) {

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

                    mediaPlayer.pause();
                }

            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        if (surfaceReady &&
                mediaPlayer != null &&
                prepared) {

            try {

                if (!mediaPlayer.isPlaying()) {

                    mediaPlayer.start();
                }

                updatePlayPauseButton();

            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onDestroy() {

        releasePlayer();

        super.onDestroy();
    }

    private int dp(int value) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return (int) (
                value * density + 0.5f
        );
    }
}
