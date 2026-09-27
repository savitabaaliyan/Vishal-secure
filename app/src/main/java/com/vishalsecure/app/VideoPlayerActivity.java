package com.vishalsecure.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

public class VideoPlayerActivity extends Activity {

    private VideoView videoView;

    private TextView statusText;
    private TextView timeText;

    private Button backButton;
    private Button playPauseButton;
    private Button forwardButton;
    private Button closeButton;

    private SeekBar seekBar;

    private String videoUriString;

    private boolean prepared = false;
    private boolean userSeeking = false;
    private boolean destroyed = false;

    private int savedPosition = 0;

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

                        if (videoView != null
                                && prepared) {

                            int duration =
                                    videoView.getDuration();

                            int position =
                                    videoView.getCurrentPosition();

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

                            if (videoView.isPlaying()) {

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
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );

        /*
         * IMPORTANT:
         *
         * इस TEST version में FLAG_SECURE
         * जानबूझकर नहीं लगाया गया है।
         *
         * इसका उद्देश्य केवल यह पता लगाना है
         * कि black video का कारण security layer
         * है या नहीं।
         */

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
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1.0f
                )
        );

        videoView =
                new VideoView(this);

        videoView.setBackgroundColor(
                Color.BLACK
        );

        videoView.setKeepScreenOn(
                true
        );

        /*
         * VideoView पूरा available area लेगी।
         * Android खुद video aspect ratio संभालेगा।
         */

        FrameLayout.LayoutParams
                videoParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );

        videoParams.gravity =
                Gravity.CENTER;

        videoContainer.addView(
                videoView,
                videoParams
        );

        /*
         * STATUS
         */

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
                buttonParams()
        );

        buttonRow.addView(
                playPauseButton,
                buttonParams()
        );

        buttonRow.addView(
                forwardButton,
                buttonParams()
        );

        buttonRow.addView(
                closeButton,
                buttonParams()
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

        setContentView(
                root
        );

        /*
         * VIDEO SOURCE
         */

        try {

            Uri uri =
                    Uri.parse(
                            videoUriString
                    );

            videoView.setVideoURI(
                    uri
            );

        } catch (Exception e) {

            showError(
                    "VIDEO URI ERROR\n\n"
                            + e.getClass()
                            .getSimpleName()
                            + "\n"
                            + String.valueOf(
                            e.getMessage()
                    )
            );

            return;
        }

        /*
         * PREPARED
         */

        videoView.setOnPreparedListener(
                mp -> {

                    try {

                        prepared = true;

                        statusText.setVisibility(
                                View.GONE
                        );

                        int duration =
                                videoView.getDuration();

                        seekBar.setMax(
                                duration
                        );

                        if (savedPosition > 0) {

                            videoView.seekTo(
                                    savedPosition
                            );
                        }

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
        );

        /*
         * ERROR
         */

        videoView.setOnErrorListener(
                (mp, what, extra) -> {

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
        );

        /*
         * COMPLETION
         */

        videoView.setOnCompletionListener(
                mp -> {

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
        );

        /*
         * PLAY / PAUSE
         */

        playPauseButton.setOnClickListener(
                v -> togglePlayPause()
        );

        /*
         * BACKWARD
         */

        backButton.setOnClickListener(
                v -> seekBackward()
        );

        /*
         * FORWARD
         */

        forwardButton.setOnClickListener(
                v -> seekForward()
        );

        /*
         * CLOSE
         */

        closeButton.setOnClickListener(
                v -> finish()
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
                                && videoView != null
                                && prepared) {

                            try {

                                timeText.setText(
                                        formatTime(progress)
                                                + " / "
                                                + formatTime(
                                                videoView
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

                        if (videoView != null
                                && prepared) {

                            try {

                                videoView.seekTo(
                                        bar.getProgress()
                                );

                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
        );
    }

    private void togglePlayPause() {

        if (videoView == null
                || !prepared) {

            showError(
                    "वीडियो अभी तैयार नहीं है।"
            );

            return;
        }

        try {

            if (videoView.isPlaying()) {

                videoView.pause();

                playPauseButton.setText(
                        "PLAY"
                );

            } else {

                videoView.start();

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

        if (videoView == null
                || !prepared) {

            return;
        }

        try {

            int current =
                    videoView.getCurrentPosition();

            videoView.seekTo(
                    Math.max(
                            0,
                            current - 10000
                    )
            );

        } catch (Exception ignored) {
        }
    }

    private void seekForward() {

       
