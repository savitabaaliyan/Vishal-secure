package com.vishalsecure.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;

public class VideoPlayerActivity extends Activity
        implements SurfaceHolder.Callback {

    private AspectRatioSurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;

    private SeekBar seekBar;
    private TextView timeText;

    private boolean isPrepared = false;
    private boolean shouldResumeAfterSurface = false;

    private int videoWidth = 0;
    private int videoHeight = 0;

    private String videoUriString;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // SECURITY
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        );

        videoUriString = getIntent().getStringExtra("video_uri");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        // VIDEO AREA
        surfaceView = new AspectRatioSurfaceView(this);
        surfaceView.setBackgroundColor(Color.BLACK);
        surfaceView.setSecure(true);
        surfaceView.setKeepScreenOn(true);

        LinearLayout.LayoutParams videoParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0
                );

        videoParams.weight = 1;

        root.addView(surfaceView, videoParams);

        // CONTROLS
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setBackgroundColor(Color.BLACK);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(android.view.Gravity.CENTER);

        Button back10 = new Button(this);
        back10.setText("−10s");

        Button playPause = new Button(this);
        playPause.setText("PLAY");

        Button forward10 = new Button(this);
        forward10.setText("+10s");

        Button close = new Button(this);
        close.setText("CLOSE");

        buttons.addView(back10);
        buttons.addView(playPause);
        buttons.addView(forward10);
        buttons.addView(close);

        controls.addView(
                buttons,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        seekBar = new SeekBar(this);

        controls.addView(
                seekBar,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        timeText = new TextView(this);
        timeText.setTextColor(Color.WHITE);
        timeText.setText("00:00 / 00:00");
        timeText.setGravity(android.view.Gravity.CENTER);

        controls.addView(
                timeText,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
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

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        // PLAY / PAUSE
        playPause.setOnClickListener(v -> {

            if (mediaPlayer == null || !isPrepared) {
                return;
            }

            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                playPause.setText("PLAY");
            } else {
                mediaPlayer.start();
                playPause.setText("PAUSE");
            }
        });

        // BACK 10 SECONDS
        back10.setOnClickListener(v -> {

            if (mediaPlayer == null || !isPrepared) {
                return;
            }

            int position = mediaPlayer.getCurrentPosition();

            position = Math.max(
                    0,
                    position - 10000
            );

            mediaPlayer.seekTo(position);
        });

        // FORWARD 10 SECONDS
        forward10.setOnClickListener(v -> {

            if (mediaPlayer == null || !isPrepared) {
                return;
            }

            int position = mediaPlayer.getCurrentPosition();
            int duration = mediaPlayer.getDuration();

            position = Math.min(
                    duration,
                    position + 10000
            );

            mediaPlayer.seekTo(position);
        });

        // CLOSE
        close.setOnClickListener(v -> finish());

        // SEEK BAR
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser) {

                        if (fromUser
                                && mediaPlayer != null
                                && isPrepared) {

                            mediaPlayer.seekTo(progress);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(
                            SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(
                            SeekBar seekBar) {
                    }
                }
        );

        startPlayer();
    }

    private void startPlayer() {

        if (videoUriString == null) {
            return;
        }

        if (mediaPlayer != null) {
            return;
        }

        try {

            mediaPlayer = new MediaPlayer();

            mediaPlayer.setDataSource(
                    this,
                    Uri.parse(videoUriString)
            );

            mediaPlayer.setOnPreparedListener(mp -> {

                isPrepared = true;

                videoWidth = mp.getVideoWidth();
                videoHeight = mp.getVideoHeight();

                // IMPORTANT:
                // Preserve original video ratio.
                surfaceView.setVideoSize(
                        videoWidth,
                        videoHeight
                );

                mp.setDisplay(surfaceHolder);

                seekBar.setMax(
                        mp.getDuration()
                );

                mp.start();
            });

            mediaPlayer.setOnCompletionListener(mp -> {

                seekBar.setProgress(0);
            });

            mediaPlayer.prepareAsync();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    @Override
    public void surfaceCreated(
            SurfaceHolder holder) {

        surfaceHolder = holder;

        if (mediaPlayer != null) {

            mediaPlayer.setDisplay(holder);

            if (shouldResumeAfterSurface
                    && isPrepared) {

                mediaPlayer.start();

                shouldResumeAfterSurface = false;
            }
        }
    }

    @Override
    public void surfaceChanged(
            SurfaceHolder holder,
            int format,
            int width,
            int height) {

        surfaceHolder = holder;

        if (mediaPlayer != null) {
            mediaPlayer.setDisplay(holder);
        }

        // Recalculate the video area after rotation.
        if (videoWidth > 0 && videoHeight > 0) {

            surfaceView.setVideoSize(
                    videoWidth,
                    videoHeight
            );
        }
    }

    @Override
    public void surfaceDestroyed(
            SurfaceHolder holder) {

        if (mediaPlayer != null
                && mediaPlayer.isPlaying()) {

            shouldResumeAfterSurface = true;

            mediaPlayer.pause();
        }

        // IMPORTANT:
        // Do NOT release MediaPlayer here.
        // This preserves playback position during rotation.
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mediaPlayer != null
                && mediaPlayer.isPlaying()) {

            shouldResumeAfterSurface = true;
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (surfaceHolder != null
                && mediaPlayer != null
                && isPrepared
                && shouldResumeAfterSurface) {

            mediaPlayer.setDisplay(surfaceHolder);
            mediaPlayer.start();

            shouldResumeAfterSurface = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mediaPlayer != null) {

            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    // ============================================================
    // CUSTOM VIDEO SURFACE
    // ============================================================

    public static class AspectRatioSurfaceView
            extends SurfaceView {

        private int videoWidth = 0;
        private int videoHeight = 0;

        public AspectRatioSurfaceView(Context context) {
            super(context);
        }

        public AspectRatioSurfaceView(
                Context context,
                AttributeSet attrs) {

            super(context, attrs);
        }

        public AspectRatioSurfaceView(
                Context context,
                AttributeSet attrs,
                int defStyleAttr) {

            super(
                    context,
                    attrs,
                    defStyleAttr
            );
        }

        public void setVideoSize(
                int width,
                int height) {

            if (width <= 0 || height <= 0) {
                return;
            }

            videoWidth = width;
            videoHeight = height;

            requestLayout();
        }

        @Override
        protected void onMeasure(
                int widthMeasureSpec,
                int heightMeasureSpec) {

            int parentWidth =
                    MeasureSpec.getSize(
                            widthMeasureSpec
                    );

            int parentHeight =
                    MeasureSpec.getSize(
                            heightMeasureSpec
                    );

            if (videoWidth <= 0
                    || videoHeight <= 0
                    || parentWidth <= 0
                    || parentHeight <= 0) {

                super.onMeasure(
                        widthMeasureSpec,
                        heightMeasureSpec
                );

                return;
            }

            float videoRatio =
                    (float) videoWidth
                            / (float) videoHeight;

            float parentRatio =
                    (float) parentWidth
                            / (float) parentHeight;

            int finalWidth;
            int finalHeight;

            // FIT CENTER
            // Entire video remains visible.
            // Nothing is cropped.

            if (videoRatio > parentRatio) {

                finalWidth = parentWidth;

                finalHeight =
                        (int) (
                                parentWidth
                                        / videoRatio
                        );

            } else {

                finalHeight = parentHeight;

                finalWidth =
                        (int) (
                                parentHeight
                                        * videoRatio
                        );
            }

            setMeasuredDimension(
                    finalWidth,
                    finalHeight
            );
        }
    }
}
