package com.vishalsecure.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.ClipData;
import android.net.Uri;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.VideoView;
import android.widget.MediaController;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private LinearLayout mainLayout;
    private LinearLayout videoList;

    private TextView title;
    private TextView subtitle;
    private EditText name;
    private EditText mobile;
    private Button openContent;
    private TextView expiry;

    private FrameLayout videoContainer;
    private VideoView videoView;
    private MediaController controller;

    private final ArrayList<Uri> videoUris = new ArrayList<>();
    private final ArrayList<String> videoNames = new ArrayList<>();

    private static final int VIDEO_PICKER = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        createMainScreen();
    }

    private void createMainScreen() {

        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(35, 40, 35, 35);

        title = new TextView(this);
        title.setText("Vishal Secure");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(21, 101, 192));
        title.setGravity(Gravity.CENTER);
        mainLayout.addView(title);

        subtitle = new TextView(this);
        subtitle.setText("Secure Content Access");
        subtitle.setTextSize(18);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 10, 0, 20);
        mainLayout.addView(subtitle);

        name = new EditText(this);
        name.setHint("Receiver का नाम");
        name.setSingleLine(true);
        mainLayout.addView(name);

        mobile = new EditText(this);
        mobile.setHint("Mobile Number");
        mobile.setInputType(2);
        mobile.setSingleLine(true);
        mainLayout.addView(mobile);

        openContent = new Button(this);
        openContent.setText("OPEN SECURE CONTENT");
        mainLayout.addView(openContent);

        expiry = new TextView(this);
        expiry.setText("Expiry: अभी निर्धारित नहीं");
        expiry.setTextSize(16);
        expiry.setPadding(0, 20, 0, 15);
        mainLayout.addView(expiry);

        TextView listTitle = new TextView(this);
        listTitle.setText("Secure Videos");
        listTitle.setTextSize(20);
        listTitle.setPadding(0, 10, 0, 10);
        mainLayout.addView(listTitle);

        videoList = new LinearLayout(this);
        videoList.setOrientation(LinearLayout.VERTICAL);
        mainLayout.addView(videoList);

        videoContainer = new FrameLayout(this);
        videoContainer.setBackgroundColor(Color.BLACK);
        videoContainer.setVisibility(View.GONE);

        videoView = new VideoView(this);

        FrameLayout.LayoutParams videoParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        videoParams.gravity = Gravity.CENTER;

        videoContainer.addView(videoView, videoParams);

        controller = new MediaController(this);
        videoView.setMediaController(controller);
        controller.setAnchorView(videoView);

        mainLayout.addView(
                videoContainer,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        videoView.setOnPreparedListener(mp -> {
            enterVideoMode();
            videoView.start();
        });

        videoView.setOnCompletionListener(mp -> {
            exitVideoMode();
        });

        openContent.setOnClickListener(v -> openVideoPicker());

        setContentView(mainLayout);
    }

    private void openVideoPicker() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.setType("video/*");

        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        startActivityForResult(intent, VIDEO_PICKER);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != VIDEO_PICKER ||
                resultCode != RESULT_OK ||
                data == null) {
            return;
        }

        if (data.getClipData() != null) {

            ClipData clipData = data.getClipData();

            for (int i = 0; i < clipData.getItemCount(); i++) {

                Uri uri = clipData.getItemAt(i).getUri();

                addVideo(uri);
            }

        } else if (data.getData() != null) {

            addVideo(data.getData());
        }
    }

    private void addVideo(Uri uri) {

        if (uri == null) {
            return;
        }

        if (videoUris.contains(uri)) {
            return;
        }

        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (Exception ignored) {
        }

        videoUris.add(uri);

        String name = uri.getLastPathSegment();

        if (name == null || name.trim().isEmpty()) {
            name = "Video " + videoUris.size();
        }

        videoNames.add(name);

        addVideoButton(
                videoUris.size() - 1,
                name
        );
    }

    private void addVideoButton(
            final int index,
            String name) {

        Button videoButton = new Button(this);

        videoButton.setText(
                "▶  " + (index + 1) + ". " + name
        );

        videoButton.setTextSize(16);

        videoButton.setGravity(Gravity.CENTER_VERTICAL);

        videoList.addView(
                videoButton,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        videoButton.setOnClickListener(v -> {

            if (index >= 0 &&
                    index < videoUris.size()) {

                playVideo(videoUris.get(index));
            }
        });
    }

    private void playVideo(Uri uri) {

        videoView.stopPlayback();

        videoView.setVideoURI(uri);
    }

    private void enterVideoMode() {

        title.setVisibility(View.GONE);
        subtitle.setVisibility(View.GONE);
        name.setVisibility(View.GONE);
        mobile.setVisibility(View.GONE);
        openContent.setVisibility(View.GONE);
        expiry.setVisibility(View.GONE);
        videoList.setVisibility(View.GONE);

        videoContainer.setVisibility(View.VISIBLE);

        mainLayout.setPadding(0, 0, 0, 0);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        videoContainer.requestLayout();
        videoView.requestLayout();
    }

    private void exitVideoMode() {

        title.setVisibility(View.VISIBLE);
        subtitle.setVisibility(View.VISIBLE);
        name.setVisibility(View.VISIBLE);
        mobile.setVisibility(View.VISIBLE);
        openContent.setVisibility(View.VISIBLE);
        expiry.setVisibility(View.VISIBLE);
        videoList.setVisibility(View.VISIBLE);

        videoContainer.setVisibility(View.GONE);

        mainLayout.setPadding(35, 40, 35, 35);

        getWindow().getDecorView().setSystemUiVisibility(0);
    }

    @Override
    public void onConfigurationChanged(
            android.content.res.Configuration newConfig) {

        super.onConfigurationChanged(newConfig);

        if (videoView != null &&
                videoView.isPlaying()) {

            videoContainer.post(() -> {
                enterVideoMode();
            });
        }
    }
}
