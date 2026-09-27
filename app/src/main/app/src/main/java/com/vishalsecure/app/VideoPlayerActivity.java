package com.vishalsecure.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.net.Uri;
import android.graphics.Color;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.SeekBar;
import android.media.MediaPlayer;

public class VideoPlayerActivity extends Activity
        implements SurfaceHolder.Callback {

    private FrameLayout rootLayout;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;

    private MediaPlayer mediaPlayer;

    private SeekBar seekBar;
    private TextView timeText;

    private Handler handler = new Handler();

    private boolean videoPrepared = false;
    private boolean shouldResumeAfterSurface = false;
    private boolean userPaused = false;

    private Uri videoUri;

    private final Runnable progressRunnable =
            new Runnable() {
                @Override
                public void run() {

                    if (mediaPlayer != null &&
                            videoPrepared) {

                        try {

                            int position =
                                    mediaPlayer.getCurrentPosition();

                            int duration =
                                    mediaPlayer.getDuration();

                            if (duration > 0) {

                                seekBar.setMax(duration);
                                seekBar.setProgress(position);

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

        buildPlayerScreen();

        String uriString =
                getIntent().getStringExtra(
                        "video_uri"
                );

        if (uriString == null ||
                uriString.isEmpty()) {

            finish();
            return;
        }

        videoUri =
                Uri.parse(uriString);

        surfaceView
                .getHolder()
                .addCallback(this);

        surfaceView.setSecure(true);

        handler.post(progressRunnable);
    }

    private void buildPlayerScreen() {

        rootLayout =
                new FrameLayout(this);

        rootLayout.setBackgroundColor(
                Color.BLACK
        );

        surfaceView =
                new SurfaceView(this);

        surfaceView.setSecure(true);

        FrameLayout.LayoutParams
                surfaceParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        surfaceParams.gravity =
                Gravity.CENTER;

        rootLayout.addView(
                surfaceView,
                surfaceParams
        );

        LinearLayout controls =
                new LinearLayout(this);

        controls.setOrientation(
                LinearLayout.HORIZONTAL
        );

        controls.setGravity(
                Gravity.CENTER
        );

        controls.setPadding(
                12,
                8,
               
