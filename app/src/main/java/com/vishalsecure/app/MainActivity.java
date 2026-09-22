package com.vishalsecure.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.VideoView;
import android.widget.MediaController;

public class MainActivity extends Activity {

    private static final int VIDEO_PICKER = 1001;

    private LinearLayout layout;
    private TextView title;
    private TextView subtitle;
    private EditText name;
    private EditText mobile;
    private Button openContent;
    private TextView expiry;
    private VideoView videoView;
    private MediaController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Screenshot और screen recording को रोकना
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(35, 40, 35, 35);
        layout.setGravity(Gravity.TOP);

        title = new TextView(this);
        title.setText("Vishal Secure");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(21, 101, 192));
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        subtitle = new TextView(this);
        subtitle.setText("Secure Content Access");
        subtitle.setTextSize(18);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 10, 0, 20);
        layout.addView(subtitle);

        name = new EditText(this);
        name.setHint("Receiver का नाम");
        name.setSingleLine(true);
        layout.addView(name);

        mobile = new EditText(this);
        mobile.setHint("Mobile Number");
        mobile.setInputType(2);
        mobile.setSingleLine(true);
        layout.addView(mobile);

        openContent = new Button(this);
        openContent.setText("OPEN SECURE CONTENT");
        layout.addView(openContent);

        expiry = new TextView(this);
        expiry.setText("Expiry: अभी निर्धारित नहीं");
        expiry.setTextSize(16);
        expiry.setPadding(0, 20, 0, 15);
        layout.addView(expiry);

        videoView = new VideoView(this);

        LinearLayout.LayoutParams videoParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        600
                );

        layout.addView(videoView, videoParams);

        controller = new MediaController(this);
        videoView.setMediaController(controller);
        controller.setAnchorView(videoView);

        videoView.setOnPreparedListener(mp -> {
            enterVideoMode();
            videoView.start();
        });

        videoView.setOnCompletionListener(mp -> {
            exitVideoMode();
        });

        openContent.setOnClickListener(v -> {

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

            intent.setType("video/*");

            intent.addCategory(Intent.CATEGORY_OPENABLE);

            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

            startActivityForResult(intent, VIDEO_PICKER);
        });

        setContentView(layout);
    }

    private void enterVideoMode() {

        title.setVisibility(View.GONE);
        subtitle.setVisibility(View.GONE);
        name.setVisibility(View.GONE);
        mobile.setVisibility(View.GONE);
        openContent.setVisibility(View.GONE);
        expiry.setVisibility(View.GONE);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                );

        videoView.setLayoutParams(params);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void exitVideoMode() {

        title.setVisibility(View.VISIBLE);
        subtitle.setVisibility(View.VISIBLE);
        name.setVisibility(View.VISIBLE);
        mobile.setVisibility(View.VISIBLE);
        openContent.setVisibility(View.VISIBLE);
        expiry.setVisibility(View.VISIBLE);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        600
                );

        videoView.setLayoutParams(params);

        getWindow().getDecorView().setSystemUiVisibility(0);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == VIDEO_PICKER &&
                resultCode == RESULT_OK &&
                data != null) {

            Uri videoUri = data.getData();

            if (videoUri != null) {

                try {
                    getContentResolver().takePersistableUriPermission(
                            videoUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (Exception ignored) {
                }

                videoView.setVideoURI(videoUri);
            }
        }
    }

    @Override
    public void onConfigurationChanged(
            android.content.res.Configuration newConfig) {

        super.onConfigurationChanged(newConfig);

        if (videoView != null && videoView.isPlaying()) {
            enterVideoMode();
        }
    }
}
