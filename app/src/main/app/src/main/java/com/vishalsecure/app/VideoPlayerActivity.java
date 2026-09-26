package com.vishalsecure.app;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.VideoView;

public class VideoPlayerActivity extends Activity {

    private VideoView videoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        videoView = new VideoView(this);

        FrameLayout.LayoutParams videoParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        videoParams.gravity = Gravity.CENTER;
        root.addView(videoView, videoParams);

        Button closeButton = new Button(this);
        closeButton.setText("CLOSE");

        closeButton.setOnClickListener(v -> finish());

        FrameLayout.LayoutParams buttonParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        buttonParams.gravity =
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;

        root.addView(closeButton, buttonParams);

        setContentView(root);

        String videoUri =
                getIntent().getStringExtra("video_uri");

        if (videoUri == null || videoUri.trim().isEmpty()) {
            finish();
            return;
        }

        videoView.setOnPreparedListener(mp -> {
            videoView.requestFocus();
            videoView.start();
        });

        videoView.setVideoURI(Uri.parse(videoUri));
    }

    @Override
    protected void onDestroy() {
        if (videoView != null) {
            try {
                videoView.stopPlayback();
            } catch (Exception ignored) {
            }
        }

        super.onDestroy();
    }
}
