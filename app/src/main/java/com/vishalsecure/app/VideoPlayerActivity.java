package com.vishalsecure.app;

import android.app.Activity;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class VideoPlayerActivity extends Activity
        implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;

    private TextView statusText;
    private TextView timeText;

    private LinearLayout controlsLayout;
    private View touchOverlay;

    private String videoUriString;

    private boolean surfaceReady = false;
    private boolean controlsVisible = true;

    private int videoWidth = 0;
    private int videoHeight = 0;

    private float zoomFactor = 1.0f;

    private float playbackSpeed = 1.0f;

    private final Handler handler = new Handler();

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // PROGRESS
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Runnable progressRunnable =
            new Runnable() {

                @Override
                public void run() {

                    updateProgress();

                    if (mediaPlayer != null) {

                        handler.postDelayed(
                                this,
                                500
                        );
                    }
                }
            };

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // CREATE
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

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

        buildPlayerScreen();
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // BUILD SCREEN
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void buildPlayerScreen() {

        FrameLayout root =
                new FrameLayout(this);

        root.setBackgroundColor(
                Color.BLACK
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // VIDEO SURFACE
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        surfaceView =
                new SurfaceView(this);

        surfaceView.setSecure(true);

        surfaceView.setKeepScreenOn(true);

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

        root.addView(
                surfaceView,
                surfaceParams
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // STATUS
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        statusText =
                new TextView(this);

        statusText.setText(
                "Video loading..."
        );

        statusText.setTextColor(
                Color.WHITE
        );

        statusText.setTextSize(17);

        statusText.setGravity(
                Gravity.CENTER
        );

        FrameLayout.LayoutParams
                statusParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        statusParams.gravity =
                Gravity.CENTER;

        root.addView(
                statusText,
                statusParams
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // TOUCH OVERLAY
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        touchOverlay =
                new View(this);

        touchOverlay.setBackgroundColor(
                Color.TRANSPARENT
        );

        touchOverlay.setClickable(true);

        FrameLayout.LayoutParams
                touchParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        touchParams.gravity =
                Gravity.CENTER;

        root.addView(
                touchOverlay,
                touchParams
        );

        touchOverlay.setOnClickListener(
                v -> toggleControls()
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CONTROLS
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        controlsLayout =
                new LinearLayout(this);

        controlsLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        controlsLayout.setGravity(
                Gravity.CENTER
        );

        controlsLayout.setPadding(
                dp(8),
                dp(6),
                dp(8),
                dp(6)
        );

        controlsLayout.setBackgroundColor(
                Color.argb(
                        190,
                        0,
                        0,
                        0
                )
        );

        FrameLayout.LayoutParams
                controlsParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        controlsParams.gravity =
                Gravity.BOTTOM;

        root.addView(
                controlsLayout,
                controlsParams
        );

        createControlButtons();

        setContentView(root);

        hideSystemBars();
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // CONTROL BUTTONS
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void createControlButtons() {

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

        LinearLayout.LayoutParams
                timeParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(34)
                );

        controlsLayout.addView(
                timeText,
                timeParams
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // FIRST ROW
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        LinearLayout row1 =
                new LinearLayout(this);

        row1.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row1.setGravity(
                Gravity.CENTER
        );

        controlsLayout.addView(
                row1,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                )
        );

        Button backButton =
                makeButton(
                        "⏪ 10s"
                );

        backButton.setOnClickListener(
                v -> seekBy(-10000)
        );

        row1.addView(
                backButton,
                buttonParams()
        );

        Button playButton =
                makeButton(
                        "▶ / ❚❚"
                );

        playButton.setOnClickListener(
                v -> togglePlayPause()
        );

        row1.addView(
                playButton,
                buttonParams()
        );

        Button forwardButton =
                makeButton(
                        "10s ⏩"
                );

        forwardButton.setOnClickListener(
                v -> seekBy(10000)
        );

        row1.addView(
                forwardButton,
                buttonParams()
        );

        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // SECOND ROW
        //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        LinearLayout row2 =
                new LinearLayout(this);

        row2.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row2.setGravity(
                Gravity.CENTER
        );

        controlsLayout.addView(
                row2,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                )
        );

        Button zoomOutButton =
                makeButton(
                        "ZOOM −"
                );

        zoomOutButton.setOnClickListener(
                v -> zoomOut()
        );

        row2.addView(
                zoomOutButton,
                buttonParams()
        );

        Button zoomInButton =
                makeButton(
                        "ZOOM +"
                );

        zoomInButton.setOnClickListener(
                v -> zoomIn()
        );

        row2.addView(
                zoomInButton,
                buttonParams()
        );

        Button speedButton =
                makeButton(
                        "Speed 1x"
                );

        speedButton.setOnClickListener(
                v -> changeSpeed(speedButton)
        );

        row2.addView(
                speedButton,
                buttonParams()
        );
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // BUTTON DESIGN
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private Button makeButton(
            String text) {

        Button button =
                new Button(this);

        button.setText(text);

        button.setTextColor(
                Color.WHITE
        );

        button.setTextSize(13);

        button.setAllCaps(false);

        button.setBackgroundColor(
                Color.DKGRAY
        );

        return button;
    }

    private LinearLayout.LayoutParams
    buttonParams() {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        dp(46),
                        1
                );

        params.setMargins(
                dp(3),
                dp(2),
                dp(3),
                dp(2)
        );

        return params;
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SHOW / HIDE CONTROLS
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void toggleControls() {

        if (controlsLayout == null) {
            return;
        }

        if (controlsVisible) {

            controlsLayout.setVisibility(
                    View.GONE
            );

            if (timeText != null) {

                timeText.setVisibility(
                        View.GONE
                );
            }

            controlsVisible = false;

        } else {

            controlsLayout.setVisibility(
                    View.VISIBLE
            );

            if (timeText != null) {

                timeText.setVisibility(
                        View.VISIBLE
                );
            }

            controlsVisible = true;
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SYSTEM BARS
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void hideSystemBars() {

        getWindow()
                .getDecorView()
                .setSystemUiVisibility(

                        View.SYSTEM_UI_FLAG_FULLSCREEN |

                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |

                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |

                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |

                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |

                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SURFACE CREATED
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    public void surfaceCreated(
            SurfaceHolder holder) {

        surfaceReady = true;

        startVideo();
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SURFACE CHANGED
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

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

        zoomFactor = 1.0f;

        applyVideoFit();
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SURFACE DESTROYED
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    public void surfaceDestroyed(
            SurfaceHolder holder) {

        surfaceReady = false;

        releasePlayer();
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // START VIDEO
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void startVideo() {

        if (!surfaceReady ||
                surfaceHolder == null ||
                videoUriString == null) {

            return;
        }

        releasePlayer();

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

            mediaPlayer.setOnVideoSizeChangedListener(
                    (mp, width, height) -> {

                        videoWidth = width;

                        videoHeight = height;

                        applyVideoFit();
                    }
            );

            mediaPlayer.setOnPreparedListener(
                    mp -> {

                        try {

                            mp.setDisplay(
                                    surfaceHolder
                            );

                            videoWidth =
                                    mp.getVideoWidth();

                            videoHeight =
                                    mp.getVideoHeight();

                            zoomFactor = 1.0f;

                            playbackSpeed = 1.0f;

                            applyVideoFit();

                            int duration =
                                    mp.getDuration();

                            timeText.setText(
                                    "00:00 / "
                                            + formatTime(
                                            duration
                                    )
                            );

                            hideFinishedMessage();

                            mp.start();

                            handler.removeCallbacks(
                                    progressRunnable
                            );

                            handler.post(
                                    progressRunnable
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

                        if (timeText != null) {

                            int duration =
                                    mp.getDuration();

                            timeText.setText(
                                    formatTime(
                                            duration
                                    )
                                            + " / "
                                            + formatTime(
                                            duration
                                    )
                            );
                        }

                        if (statusText != null) {

                            statusText.setText(
                                    "Video समाप्त"
                            );

                            statusText.setVisibility(
                                    View.VISIBLE
                            );
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

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // PLAY / PAUSE
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void togglePlayPause() {

        if (mediaPlayer == null) {
            return;
        }

        try {

            int duration =
                    mediaPlayer.getDuration();

            int position =
                    mediaPlayer.getCurrentPosition();

            if (duration > 0 &&
                    position >= duration - 500) {

                mediaPlayer.seekTo(0);

                hideFinishedMessage();

                mediaPlayer.start();

                return;
            }

            if (mediaPlayer.isPlaying()) {

                mediaPlayer.pause();

            } else {

                hideFinishedMessage();

                mediaPlayer.start();
            }

        } catch (Exception ignored) {
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 10 SECOND SEEK
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void seekBy(
            int milliseconds) {

        if (mediaPlayer == null) {
            return;
        }

        try {

            int duration =
                    mediaPlayer.getDuration();

            int current =
                    mediaPlayer.getCurrentPosition();

            int target =
                    current + milliseconds;

            if (target < 0) {
                target = 0;
            }

            if (target > duration) {
                target = duration;
            }

            mediaPlayer.seekTo(
                    target
            );

            hideFinishedMessage();

            if (timeText != null) {

                timeText.setText(
                        formatTime(target)
                                + " / "
                                + formatTime(duration)
                );
            }

        } catch (Exception ignored) {
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ZOOM IN
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void zoomIn() {

        zoomFactor += 0.25f;

        if (zoomFactor > 3.0f) {

            zoomFactor = 3.0f;
        }

        applyZoom();
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ZOOM OUT
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void zoomOut() {

        zoomFactor -= 0.25f;

        if (zoomFactor < 1.0f) {

            zoomFactor = 1.0f;
        }

        applyZoom();
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SPEED
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void changeSpeed(
            Button speedButton) {

        if (mediaPlayer == null) {
            return;
        }

        try {

            if (playbackSpeed == 0.5f) {

                playbackSpeed = 1.0f;

            } else if (playbackSpeed == 1.0f) {

                playbackSpeed = 1.5f;

            } else if (playbackSpeed == 1.5f) {

                playbackSpeed = 2.0f;

            } else {

                playbackSpeed = 0.5f;
            }

            if (android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.M) {

                mediaPlayer.setPlaybackParams(
                        mediaPlayer
                                .getPlaybackParams()
                                .setSpeed(
                                        playbackSpeed
                                )
                );
            }

            speedButton.setText(
                    "Speed "
                            + speedText(
                            playbackSpeed
                    )
            );

        } catch (Exception ignored) {
        }
    }

    private String speedText(
            float speed) {

        if (speed == 0.5f) {
            return "0.5x";
        }

        if (speed == 1.5f) {
            return "1.5x";
        }

        if (speed == 2.0f) {
            return "2x";
        }

        return "1x";
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // VIDEO FIT
    // PORTRAIT = CENTERED
    // LANDSCAPE = FULL SCREEN
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void applyVideoFit() {

        if (surfaceView == null ||
                videoWidth <= 0 ||
                videoHeight <= 0) {

            return;
        }

        try {

            int screenWidth =
                    getResources()
                            .getDisplayMetrics()
                            .widthPixels;

            int screenHeight =
                    getResources()
                            .getDisplayMetrics()
                            .heightPixels;

            if (screenWidth <= 0 ||
                    screenHeight <= 0) {

                return;
            }

            float videoRatio =
                    (float) videoWidth
                            / videoHeight;

            boolean landscape =
                    screenWidth > screenHeight;

            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams)
                            surfaceView
                                    .getLayoutParams();

            //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // LANDSCAPE
            // FULL SCREEN
            //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            if (landscape) {

                float screenRatio =
                        (float) screenWidth
                                / screenHeight;

                if (videoRatio > screenRatio) {

                    params.width =
                            screenWidth;

                    params.height =
                            (int) (
                                    screenWidth
                                            / videoRatio
                            );

                } else {

                    params.height =
                            screenHeight;

                    params.width =
                            (int) (
                                    screenHeight
                                            * videoRatio
                            );
                }

            }

            //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // PORTRAIT
            // CENTERED / NOT FULL SCREEN
            //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            else {

                /*
                 * Portrait में video को
                 * screen की लगभग 85% width दी जाएगी।
                 *
                 * इससे video बीच में रहेगी और
                 * पूरा portrait screen नहीं घेरेगी।
                 */

                int targetWidth =
                        (int) (
                                screenWidth * 0.85f
                        );

                int targetHeight =
                        (int) (
                                targetWidth
                                        / videoRatio
                        );

                /*
                 * बहुत लंबी video होने पर
                 * उसे भी सीमित रखें।
                 */

                int maxHeight =
                        (int) (
                                screenHeight * 0.65f
                        );

                if (targetHeight > maxHeight) {

                    targetHeight =
                            maxHeight;

                    targetWidth =
                            (int) (
                                    targetHeight
                                            * videoRatio
                            );
                }

                params.width =
                        targetWidth;

                params.height =
                        targetHeight;
            }

            params.gravity =
                    Gravity.CENTER;

            surfaceView.setLayoutParams(
                    params
            );

        } catch (Exception ignored) {
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ZOOM
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void applyZoom() {

        if (surfaceView == null ||
                videoWidth <= 0 ||
                videoHeight <= 0) {

            return;
        }

        try {

            int screenWidth =
                    getResources()
                            .getDisplayMetrics()
                            .widthPixels;

            int screenHeight =
                    getResources()
                            .getDisplayMetrics()
                            .heightPixels;

            if (screenWidth <= 0 ||
                    screenHeight <= 0) {

                return;
            }

            float videoRatio =
                    (float) videoWidth
                            / videoHeight;

            boolean landscape =
                    screenWidth > screenHeight;

            float fitWidth;
            float fitHeight;

            //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // LANDSCAPE BASE SIZE
            //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            if (landscape) {

                float screenRatio =
                        (float) screenWidth
                                / screenHeight;

                if (videoRatio > screenRatio) {

                    fitWidth =
                            screenWidth;

                    fitHeight =
                            screenWidth
                                    / videoRatio;

                } else {

                    fitHeight =
                            screenHeight;

                    fitWidth =
                            screenHeight
                                    * videoRatio;
                }

            }

            //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // PORTRAIT BASE SIZE
            //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            else {

                fitWidth =
                        screenWidth * 0.85f;

                fitHeight =
                        fitWidth / videoRatio;

                float maxHeight =
                        screenHeight * 0.65f;

                if (fitHeight > maxHeight) {

                    fitHeight =
                            maxHeight;

                    fitWidth =
                            fitHeight * videoRatio;
                }
            }

            int newWidth =
                    (int) (
                            fitWidth
                                    * zoomFactor
                    );

            int newHeight =
                    (int) (
                            fitHeight
                                    * zoomFactor
                    );

            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams)
                            surfaceView
                                    .getLayoutParams();

            params.width =
                    newWidth;

            params.height =
                    newHeight;

            params.gravity =
                    Gravity.CENTER;

            surfaceView.setLayoutParams(
                    params
            );

        } catch (Exception ignored) {
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // UPDATE TIME
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void updateProgress() {

        if (mediaPlayer == null ||
                timeText == null) {

            return;
        }

        try {

            int duration =
                    mediaPlayer.getDuration();

            int position =
                    mediaPlayer.getCurrentPosition();

            timeText.setText(
                    formatTime(position)
                            + " / "
                            + formatTime(duration)
            );

        } catch (Exception ignored) {
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // HIDE FINISHED
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void hideFinishedMessage() {

        if (statusText != null) {

            statusText.setVisibility(
                    View.GONE
            );
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // TIME FORMAT
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private String formatTime(
            int milliseconds) {

        int totalSeconds =
                milliseconds / 1000;

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

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // PAUSE
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

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

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // RESUME
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    protected void onResume() {

        super.onResume();

        hideSystemBars();

        if (surfaceReady &&
                mediaPlayer != null) {

            try {

                int duration =
                        mediaPlayer.getDuration();

                int position =
                        mediaPlayer.getCurrentPosition();

                if (duration > 0 &&
                        position < duration - 500 &&
                        !mediaPlayer.isPlaying()) {

                    mediaPlayer.start();
                }

            } catch (Exception ignored) {
            }
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // RELEASE
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void releasePlayer() {

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
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ERROR
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void showError(
            String message) {

        runOnUiThread(() -> {

            if (statusText != null) {

                statusText.setText(
                        message
                );

                statusText.setVisibility(
                        View.VISIBLE
                );
            }
        });
    }

    private void showErrorAndClose(
            String message) {

        new android.app.AlertDialog.Builder(this)
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

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // DESTROY
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    protected void onDestroy() {

        releasePlayer();

        super.onDestroy();
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // DP
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

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
