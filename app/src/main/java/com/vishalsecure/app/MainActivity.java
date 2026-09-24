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

    private static final String PREFS_NAME = "VishalSecureVideos";
    private static final String KEY_VIDEO_URIS = "video_uris";
    private static final String KEY_VIDEO_NAMES = "video_names";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Screenshot / screen recording protection
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        createMainScreen();

        loadSavedVideos();
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

        // Video container
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

        // Media controls
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

        // Video prepared
        videoView.setOnPreparedListener(mp -> {
            enterVideoMode();
            videoView.start();
        });

        // Video completed
        videoView.setOnCompletionListener(mp -> {
            exitVideoMode();
        });
        // Video playback error
videoView.setOnErrorListener((mp, what, extra) -> {

    exitVideoMode();

    new android.app.AlertDialog.Builder(this)
            .setTitle("Vishal Secure")
            .setMessage("Video play error: " + what + " / " + extra)
            .setPositiveButton("OK", null)
            .show();

    return true;
});

        // Open video picker
        openContent.setOnClickListener(v -> openVideoPicker());

        setContentView(mainLayout);
    }

    private void openVideoPicker() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.setType("video/*");

        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Multiple videos select करने की अनुमति
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        // Persistent read permission
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

        // Multiple videos
        if (data.getClipData() != null) {

            ClipData clipData = data.getClipData();

            for (int i = 0; i < clipData.getItemCount(); i++) {

                Uri uri = clipData.getItemAt(i).getUri();

                addVideo(uri);
            }

        }

        // Single video
        else if (data.getData() != null) {

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

        // Permanent permission लेने की कोशिश
        try {

            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

        } catch (Exception ignored) {
        }

        videoUris.add(uri);

        String videoName = uri.getLastPathSegment();

        if (videoName == null ||
                videoName.trim().isEmpty()) {

            videoName = "Video " + videoUris.size();
        }

        videoNames.add(videoName);

        saveVideos();

        refreshVideoList();
    }

    private void loadSavedVideos() {

        android.content.SharedPreferences prefs =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        String savedUris =
                prefs.getString(KEY_VIDEO_URIS, "");

        String savedNames =
                prefs.getString(KEY_VIDEO_NAMES, "");

        if (savedUris == null ||
                savedUris.trim().isEmpty()) {

            return;
        }

        String[] uriArray =
                savedUris.split("\\|", -1);

        String[] nameArray =
                savedNames.split("\\|", -1);

        for (int i = 0; i < uriArray.length; i++) {

            if (uriArray[i] == null ||
                    uriArray[i].trim().isEmpty()) {

                continue;
            }

            try {

                /*
                 * IMPORTANT:
                 * saveVideos() में URI encode हुई है।
                 * इसलिए load करते समय decode करना जरूरी है।
                 */
                Uri uri =
                        Uri.parse(
                                Uri.decode(uriArray[i])
                        );

                /*
                 * Saved URI की read permission दोबारा लेने की कोशिश।
                 */
                try {

                    getContentResolver()
                            .takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );

                } catch (Exception ignored) {
                }

                videoUris.add(uri);

                String videoName;

                if (i < nameArray.length &&
                        nameArray[i] != null &&
                        !nameArray[i].trim().isEmpty()) {

                    /*
                     * Video name भी encode हुआ है,
                     * इसलिए decode करना जरूरी है।
                     */
                    videoName =
                            Uri.decode(nameArray[i]);

                } else {

                    videoName =
                            "Video " + videoUris.size();
                }

                videoNames.add(videoName);

            } catch (Exception ignored) {
            }
        }

        refreshVideoList();
    }

    private void saveVideos() {

        StringBuilder uriBuilder =
                new StringBuilder();

        StringBuilder nameBuilder =
                new StringBuilder();

        for (int i = 0;
                i < videoUris.size();
                i++) {

            if (i > 0) {

                uriBuilder.append("|");
                nameBuilder.append("|");
            }

            /*
             * URI को encode करके save कर रहे हैं।
             */
            uriBuilder.append(
                    Uri.encode(
                            videoUris.get(i).toString()
                    )
            );

            /*
             * Video name को भी encode कर रहे हैं।
             */
            nameBuilder.append(
                    Uri.encode(
                            videoNames.get(i)
                    )
            );
        }

        getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
        )
                .edit()
                .putString(
                        KEY_VIDEO_URIS,
                        uriBuilder.toString()
                )
                .putString(
                        KEY_VIDEO_NAMES,
                        nameBuilder.toString()
                )
                .apply();
    }

    private void refreshVideoList() {

        videoList.removeAllViews();

        for (int i = 0;
                i < videoUris.size();
                i++) {

            addVideoButton(
                    i,
                    videoNames.get(i)
            );
        }
    }

    private void addVideoButton(
            final int index,
            String name) {

        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );

        // Play button
        Button videoButton =
                new Button(this);

        videoButton.setText(
                "▶  " +
                        (index + 1) +
                        ". " +
                        name
        );

        videoButton.setTextSize(16);

        videoButton.setGravity(
                Gravity.CENTER_VERTICAL
        );

        row.addView(
                videoButton,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        // Delete button
        Button deleteButton =
                new Button(this);

        deleteButton.setText("DELETE");
        deleteButton.setTextSize(13);

        row.addView(
                deleteButton,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        videoList.addView(
                row,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        // Play
        videoButton.setOnClickListener(v -> {

            if (index >= 0 &&
                    index < videoUris.size()) {

                playVideo(
                        videoUris.get(index)
                );
            }
        });

        // Delete
        deleteButton.setOnClickListener(v -> {

            deleteVideo(index);
        });
    }

    private void deleteVideo(int index) {

        if (index < 0 ||
                index >= videoUris.size()) {

            return;
        }

        Uri uri =
                videoUris.get(index);

        // Persistent permission release
        try {

            getContentResolver()
                    .releasePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );

        } catch (Exception ignored) {
        }

        videoUris.remove(index);

        videoNames.remove(index);

        saveVideos();

        refreshVideoList();
    }

    private void playVideo(Uri uri) {

        if (uri == null) {
            return;
        }

        try {

            videoView.stopPlayback();

            /*
             * यही actual saved URI है।
             * loadSavedVideos() में इसे decode करके
             * सही Uri बनाया गया है।
             */
            videoView.setVideoURI(uri);

        } catch (Exception e) {

            exitVideoMode();
        }
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

        mainLayout.setPadding(
                0,
                0,
                0,
                0
        );

        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
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

        mainLayout.setPadding(
                35,
                40,
                35,
                35
        );

        getWindow()
                .getDecorView()
                .setSystemUiVisibility(0);
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
