package com.vishalsecure.app;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.TextView;

public class VideoPlayerActivity extends Activity
        implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;

    private TextView statusText;
    private Button closeButton;

    private String videoUriString;
    private boolean surfaceReady = false;


    // =========================================================
    // ON CREATE
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Screenshot / screen recording protection
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        videoUriString = getIntent().getStringExtra("video_uri");

        if (videoUriString == null ||
                videoUriString.trim().isEmpty()) {

            showErrorAndClose(
                    "Video file नहीं मिली।"
            );
            return;
        }

        buildPlayerScreen();
    }


    // =========================================================
    // BUILD PLAYER SCREEN
    // =========================================================

    private void buildPlayerScreen() {

        FrameLayout root =
                new FrameLayout(this);

        root.setBackgroundColor(
                android.graphics.Color.BLACK
        );


        // -----------------------------------------------------
        // SECURE SURFACE VIEW
        // -----------------------------------------------------

        surfaceView =
                new SurfaceView(this);

        surfaceView.setSecure(true);
        surfaceView.setKeepScreenOn(true);

        surfaceHolder =
                surfaceView.getHolder();

        surfaceHolder.addCallback(this);


        FrameLayout.LayoutParams surfaceParams =
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


        // -----------------------------------------------------
        // STATUS TEXT
        // -----------------------------------------------------

        statusText =
                new TextView(this);

        statusText.setText(
                "Video loading..."
        );

        statusText.setTextColor(
                android.graphics.Color.WHITE
        );

        statusText.setTextSize(16);

        statusText.setGravity(
                Gravity.CENTER
        );

        statusText.setBackgroundColor(
                android.graphics.Color.TRANSPARENT
        );


        FrameLayout.LayoutParams statusParams =
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


        // -----------------------------------------------------
        // CLOSE BUTTON
        // -----------------------------------------------------

        closeButton =
                new Button(this);

        closeButton.setText(
                "CLOSE"
        );

        closeButton.setAllCaps(false);

        closeButton.setTextColor(
                android.graphics.Color.WHITE
        );

        closeButton.setBackgroundColor(
                android.graphics.Color.DKGRAY
        );

        closeButton.setOnClickListener(
                v -> finish()
        );


        FrameLayout.LayoutParams closeParams =
                new FrameLayout.LayoutParams(
                        dp(110),
                        dp(55)
                );

        closeParams.gravity =
                Gravity.BOTTOM |
                Gravity.CENTER_HORIZONTAL;

        closeParams.setMargins(
                dp(10),
                dp(10),
                dp(10),
                dp(20)
        );

        root.addView(
                closeButton,
                closeParams
        );


        setContentView(root);
    }


    // =========================================================
    // SURFACE CREATED
    // =========================================================

    @Override
    public void surfaceCreated(
            SurfaceHolder holder) {

        surfaceReady = true;

        startVideo();
    }


    // =========================================================
    // SURFACE CHANGED
    // =========================================================

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
    }


    // =========================================================
    // SURFACE DESTROYED
    // =========================================================

    @Override
    public void surfaceDestroyed(
            SurfaceHolder holder) {

        surfaceReady = false;

        releasePlayer();
    }


    // =========================================================
    // START VIDEO
    // =========================================================

    private void startVideo() {

        if (!surfaceReady ||
                surfaceHolder == null ||
                videoUriString == null) {
            return;
        }

        releasePlayer();

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
                    mp -> {

                        try {

                            mp.setDisplay(
                                    surfaceHolder
                            );

                            mp.start();

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
            );


            mediaPlayer.setOnCompletionListener(
                    mp -> {

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


    // =========================================================
    // PAUSE
    // =========================================================

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


    // =========================================================
    // RESUME
    // =========================================================

    @Override
    protected void onResume() {

        super.onResume();

        if (surfaceReady &&
                mediaPlayer != null) {

            try {

                if (!mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                }

            } catch (Exception ignored) {
            }
        }
    }


    // =========================================================
    // RELEASE PLAYER
    // =========================================================

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
    }


    // =========================================================
    // ERROR
    // =========================================================

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


    // =========================================================
    // ERROR AND CLOSE
    // =========================================================

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


    // =========================================================
    // ON DESTROY
    // =========================================================

    @Override
    protected void onDestroy() {

        releasePlayer();

        super.onDestroy();
    }


    // =========================================================
    // DP
    // =========================================================

    private int dp(int value) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return (int)
                (value * density + 0.5f);
    }
}
