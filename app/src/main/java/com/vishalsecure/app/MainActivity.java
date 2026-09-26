package com.vishalsecure.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import android.graphics.Color;

import android.net.Uri;

import android.os.Bundle;

import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup;

import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int PICK_VIDEO_REQUEST = 1001;

    private static final String PREFS_NAME = "VishalSecurePrefs";
    private static final String KEY_EXPIRY_TIME = "expiry_time";
    private static final String KEY_VIDEO_COUNT = "video_count";

    private LinearLayout rootLayout;
    private LinearLayout contentLayout;
    private LinearLayout videoListLayout;
    private FrameLayout videoContainer;

    private EditText receiverName;
    private EditText receiverMobile;

    private Button openContentButton;
    private Button setExpiryButton;

    private TextView expiryText;
    private TextView titleText;
    private TextView subtitleText;

    private VideoView videoView;
    private Button closeVideoButton;

    private SharedPreferences preferences;

    private long expiryTime = 0L;

    private final ArrayList<String> videoUris = new ArrayList<>();
    private final ArrayList<String> videoNames = new ArrayList<>();

    private boolean videoMode = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Screenshot / screen recording protection
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        preferences = getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );

        loadExpiry();
        loadVideos();

        buildMainScreen();
    }


    // ============================================================
    // MAIN SCREEN
    // ============================================================

    private void buildMainScreen() {

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.WHITE);

        rootLayout.setPadding(
                dp(16),
                dp(18),
                dp(16),
                dp(16)
        );

        // --------------------------------------------------------
        // TITLE
        // --------------------------------------------------------

        titleText = new TextView(this);
        titleText.setText("VISHAL SECURE");
        titleText.setTextSize(25);
        titleText.setTextColor(Color.BLACK);
        titleText.setGravity(Gravity.CENTER);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);

        rootLayout.addView(
                titleText,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );


        // --------------------------------------------------------
        // SUBTITLE
        // --------------------------------------------------------

        subtitleText = new TextView(this);
        subtitleText.setText("Secure Content");
        subtitleText.setTextSize(15);
        subtitleText.setTextColor(Color.DKGRAY);
        subtitleText.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.setMargins(
                0,
                dp(4),
                0,
                dp(18)
        );

        rootLayout.addView(
                subtitleText,
                subtitleParams
        );


        // --------------------------------------------------------
        // RECEIVER NAME
        // --------------------------------------------------------

        receiverName = new EditText(this);
        receiverName.setHint("Receiver Name");
        receiverName.setSingleLine(true);
        receiverName.setTextSize(16);

        rootLayout.addView(
                receiverName,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );


        // --------------------------------------------------------
        // RECEIVER MOBILE
        // --------------------------------------------------------

        receiverMobile = new EditText(this);
        receiverMobile.setHint("Receiver Mobile");
        receiverMobile.setSingleLine(true);
        receiverMobile.setInputType(
                android.text.InputType.TYPE_CLASS_PHONE
        );
        receiverMobile.setTextSize(16);

        LinearLayout.LayoutParams mobileParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        mobileParams.setMargins(
                0,
                dp(8),
                0,
                dp(14)
        );

        rootLayout.addView(
                receiverMobile,
                mobileParams
        );


        // --------------------------------------------------------
        // OPEN SECURE CONTENT BUTTON
        // --------------------------------------------------------

        openContentButton = new Button(this);
        openContentButton.setText("OPEN SECURE CONTENT");
        openContentButton.setAllCaps(false);

        openContentButton.setOnClickListener(
                v -> openSecureContent()
        );

        rootLayout.addView(
                openContentButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );


        // --------------------------------------------------------
        // SET EXPIRY BUTTON
        // --------------------------------------------------------

        setExpiryButton = new Button(this);
        setExpiryButton.setText("SET EXPIRY");
        setExpiryButton.setAllCaps(false);

        setExpiryButton.setOnClickListener(
                v -> chooseExpiryDate()
        );

        LinearLayout.LayoutParams expiryButtonParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        expiryButtonParams.setMargins(
                0,
                dp(8),
                0,
                0
        );

        rootLayout.addView(
                setExpiryButton,
                expiryButtonParams
        );


        // --------------------------------------------------------
        // EXPIRY TEXT
        // --------------------------------------------------------

        expiryText = new TextView(this);
        expiryText.setTextSize(15);
        expiryText.setTextColor(Color.DKGRAY);
        expiryText.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams expiryTextParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        expiryTextParams.setMargins(
                0,
                dp(6),
                0,
                dp(10)
        );

        rootLayout.addView(
                expiryText,
                expiryTextParams
        );

        updateExpiryText();


        // --------------------------------------------------------
        // VIDEO LIST TITLE
        // --------------------------------------------------------

        TextView listTitle = new TextView(this);
        listTitle.setText("Secure Videos");
        listTitle.setTextSize(18);
        listTitle.setTextColor(Color.BLACK);
        listTitle.setTypeface(null, android.graphics.Typeface.BOLD);

        LinearLayout.LayoutParams listTitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        listTitleParams.setMargins(
                0,
                dp(4),
                0,
                dp(6)
        );

        rootLayout.addView(
                listTitle,
                listTitleParams
        );


        // --------------------------------------------------------
        // VIDEO LIST
        // --------------------------------------------------------

        videoListLayout = new LinearLayout(this);
        videoListLayout.setOrientation(LinearLayout.VERTICAL);

        rootLayout.addView(
                videoListLayout,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );


        // --------------------------------------------------------
        // VIDEO CONTAINER
        // --------------------------------------------------------

        videoContainer = new FrameLayout(this);
        videoContainer.setBackgroundColor(Color.BLACK);
        videoContainer.setVisibility(View.GONE);

        // VideoView
        videoView = new VideoView(this);
        videoView.setBackgroundColor(Color.BLACK);
        videoView.setFocusable(true);
        videoView.setFocusableInTouchMode(true);

        FrameLayout.LayoutParams videoParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );

        videoContainer.addView(
                videoView,
                videoParams
        );


        // --------------------------------------------------------
        // CLOSE VIDEO BUTTON
        // --------------------------------------------------------

        closeVideoButton = new Button(this);
        closeVideoButton.setText("CLOSE");
        closeVideoButton.setTextColor(Color.WHITE);
        closeVideoButton.setBackgroundColor(Color.DKGRAY);
        closeVideoButton.setAllCaps(false);

        closeVideoButton.setOnClickListener(
                v -> exitVideoMode()
        );

        FrameLayout.LayoutParams closeParams =
                new FrameLayout.LayoutParams(
                        dp(100),
                        dp(55)
                );

        closeParams.gravity =
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;

        closeParams.setMargins(
                dp(10),
                dp(10),
                dp(10),
                dp(15)
        );

        videoContainer.addView(
                closeVideoButton,
                closeParams
        );


        // --------------------------------------------------------
        // VIDEO PREPARED
        // --------------------------------------------------------

        videoView.setOnPreparedListener(
                mp -> {

                    // Video is ready
                    videoView.requestFocus();

                    // Start only after MediaPlayer is prepared
                    videoView.start();
                }
        );


        // --------------------------------------------------------
        // VIDEO COMPLETION
        // --------------------------------------------------------

        videoView.setOnCompletionListener(
                mp -> {
                    // Video finished.
                    // Keep the video screen open.
                }
        );


        // --------------------------------------------------------
        // VIDEO ERROR
        // --------------------------------------------------------

        videoView.setOnErrorListener(
                (mp, what, extra) -> {

                    showVideoError(
                            "Video play नहीं हो सकी.\n\n"
                                    + "Error: "
                                    + what
                                    + "\nExtra: "
                                    + extra
                    );

                    return true;
                }
        );


        // --------------------------------------------------------
        // ADD VIDEO CONTAINER TO ROOT
        // --------------------------------------------------------

        rootLayout.addView(
                videoContainer,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );


        setContentView(rootLayout);

        refreshVideoList();
    }


    // ============================================================
    // OPEN SECURE CONTENT
    // ============================================================

    private void openSecureContent() {

        if (isExpired()) {

            showExpiredMessage();

            return;
        }

        Intent intent = new Intent(
                Intent.ACTION_OPEN_DOCUMENT
        );

        intent.setType("video/*");

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        intent.putExtra(
                Intent.EXTRA_ALLOW_MULTIPLE,
                true
        );

        startActivityForResult(
                intent,
                PICK_VIDEO_REQUEST
        );
    }


    // ============================================================
    // EXPIRY DATE
    // ============================================================

    private void chooseExpiryDate() {

        Calendar calendar = Calendar.getInstance();

        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,
                        (view, year, month, dayOfMonth) -> {

                            Calendar selected =
                                    Calendar.getInstance();

                            selected.set(
                                    Calendar.YEAR,
                                    year
                            );

                            selected.set(
                                    Calendar.MONTH,
                                    month
                            );

                            selected.set(
                                    Calendar.DAY_OF_MONTH,
                                    dayOfMonth
                            );

                            chooseExpiryTime(selected);

                        },

                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                );

        dialog.show();
    }


    private void chooseExpiryTime(
            Calendar selected
    ) {

        Calendar now =
                Calendar.getInstance();

        TimePickerDialog dialog =
                new TimePickerDialog(
                        this,
                        (view, hourOfDay, minute) -> {

                            selected.set(
                                    Calendar.HOUR_OF_DAY,
                                    hourOfDay
                            );

                            selected.set(
                                    Calendar.MINUTE,
                                    minute
                            );

                            selected.set(
                                    Calendar.SECOND,
                                    0
                            );

                            selected.set(
                                    Calendar.MILLISECOND,
                                    0
                            );

                            expiryTime =
                                    selected.getTimeInMillis();

                            preferences.edit()
                                    .putLong(
                                            KEY_EXPIRY_TIME,
                                            expiryTime
                                    )
                                    .apply();

                            updateExpiryText();

                            if (isExpired()) {
                                expiryText.setTextColor(Color.RED);
                            }

                        },

                        now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE),
                        false
                );

        dialog.show();
    }


    // ============================================================
    // LOAD EXPIRY
    // ============================================================

    private void loadExpiry() {

        expiryTime =
                preferences.getLong(
                        KEY_EXPIRY_TIME,
                        0L
                );
    }


    // ============================================================
    // UPDATE EXPIRY TEXT
    // ============================================================

    private void updateExpiryText() {

        if (expiryText == null) {
            return;
        }

        if (expiryTime <= 0L) {

            expiryText.setText(
                    "Expiry: Not Set"
            );

            expiryText.setTextColor(
                    Color.DKGRAY
            );

            return;
        }

        SimpleDateFormat format =
                new SimpleDateFormat(
                        "dd-MM-yyyy hh:mm a",
                        Locale.getDefault()
                );

        String text =
                "Expiry: "
                        + format.format(
                        new Date(expiryTime)
                );

        expiryText.setText(text);

        if (isExpired()) {

            expiryText.setTextColor(
                    Color.RED
            );

        } else {

            expiryText.setTextColor(
                    Color.DKGRAY
            );
        }
    }


    // ============================================================
    // CHECK EXPIRY
    // ============================================================

    private boolean isExpired() {

        if (expiryTime <= 0L) {
            return false;
        }

        return System.currentTimeMillis()
                >= expiryTime;
    }


    // ============================================================
    // EXPIRED MESSAGE
    // ============================================================

    private void showExpiredMessage() {

        new AlertDialog.Builder(this)
                .setTitle("Vishal Secure")
                .setMessage(
                        "Secure content की expiry हो चुकी है।"
                )
                .setPositiveButton(
                        "OK",
                        null
                )
                .show();
    }


    // ============================================================
    // ACTIVITY RESULT
    // ============================================================

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != PICK_VIDEO_REQUEST) {
            return;
        }

        if (resultCode != RESULT_OK) {
            return;
        }

        if (data == null) {
            return;
        }


        // Multiple videos
        ClipData clipData =
                data.getClipData();

        if (clipData != null) {

            for (int i = 0;
                 i < clipData.getItemCount();
                 i++) {

                Uri uri =
                        clipData
                                .getItemAt(i)
                                .getUri();

                addVideo(uri);
            }

        } else {

            // Single video
            Uri uri =
                    data.getData();

            if (uri != null) {
                addVideo(uri);
            }
        }

        refreshVideoList();
    }


    // ============================================================
    // ADD VIDEO
    // ============================================================

    private void addVideo(Uri uri) {

        if (uri == null) {
            return;
        }

        try {

            final int takeFlags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

            try {

                getContentResolver()
                        .takePersistableUriPermission(
                                uri,
                                takeFlags
                        );

            } catch (Exception ignored) {

                try {

                    getContentResolver()
                            .takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );

                } catch (Exception ignoredAgain) {
                    // Some providers do not support persistable permission.
                }
            }

        } catch (Exception ignored) {
        }


        String uriString =
                uri.toString();

        if (videoUris.contains(uriString)) {
            return;
        }

        String name =
                getVideoName(uri);

        videoUris.add(uriString);
        videoNames.add(name);

        saveVideos();
    }


    // ============================================================
    // GET VIDEO NAME
    // ============================================================

    private String getVideoName(Uri uri) {

        String name = null;

        try {

            android.database.Cursor cursor =
                    getContentResolver().query(
                            uri,
                            new String[]{
                                    android.provider.OpenableColumns.DISPLAY_NAME
                            },
                            null,
                            null,
                            null
                    );

            if (cursor != null) {

                int nameIndex =
                        cursor.getColumnIndex(
                                android.provider.OpenableColumns.DISPLAY_NAME
                        );

                if (nameIndex >= 0
                        && cursor.moveToFirst()) {

                    name =
                            cursor.getString(nameIndex);
                }

                cursor.close();
            }

        } catch (Exception ignored) {
        }

        if (name == null || name.trim().isEmpty()) {

            name = "Secure Video "
                    + (videoUris.size() + 1);
        }

        return name;
    }


    // ============================================================
    // SAVE VIDEOS
    // ============================================================

    private void saveVideos() {

        SharedPreferences.Editor editor =
                preferences.edit();

        editor.putInt(
                KEY_VIDEO_COUNT,
                videoUris.size()
        );

        for (int i = 0;
             i < videoUris.size();
             i++) {

            editor.putString(
                    "video_uri_" + i,
                    videoUris.get(i)
            );

            editor.putString(
                    "video_name_" + i,
                    videoNames.get(i)
            );
        }

        editor.apply();
    }


    // ============================================================
    // LOAD VIDEOS
    // ============================================================

    private void loadVideos() {

        videoUris.clear();
        videoNames.clear();

        int count =
                preferences.getInt(
                        KEY_VIDEO_COUNT,
                        0
                );

        for (int i = 0;
             i < count;
             i++) {

            String uri =
                    preferences.getString(
                            "video_uri_" + i,
                            null
                    );

            String name =
                    preferences.getString(
                            "video_name_" + i,
                            "Secure Video " + (i + 1)
                    );

            if (uri != null) {

                videoUris.add(uri);
                videoNames.add(name);
            }
        }
    }


    // ============================================================
    // REFRESH VIDEO LIST
    // ============================================================

    private void refreshVideoList() {

        if (videoListLayout == null) {
            return;
        }

        videoListLayout.removeAllViews();

        if (videoUris.isEmpty()) {

            TextView emptyText =
                    new TextView(this);

            emptyText.setText(
                    "No secure videos added."
            );

            emptyText.setTextSize(15);
            emptyText.setTextColor(Color.GRAY);
            emptyText.setPadding(
                    0,
                    dp(10),
                    0,
                    dp(10)
            );

            videoListLayout.addView(
                    emptyText
            );

            return;
        }


        for (int i = 0;
             i < videoUris.size();
             i++) {

            final int position = i;

            LinearLayout row =
                    new LinearLayout(this);

            row.setOrientation(
                    LinearLayout.HORIZONTAL
            );

            row.setGravity(
                    Gravity.CENTER_VERTICAL
            );

            row.setPadding(
                    0,
                    dp(4),
                    0,
                    dp(4)
            );


            TextView name =
                    new TextView(this);

            name.setText(
                    videoNames.get(position)
            );

            name.setTextSize(15);
            name.setTextColor(Color.BLACK);

            name.setGravity(
                    Gravity.CENTER_VERTICAL
            );

            row.addView(
                    name,
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1
                    )
            );


            Button play =
                    new Button(this);

            play.setText("PLAY");
            play.setAllCaps(false);

            play.setOnClickListener(
                    v -> {

                        if (isExpired()) {

                            showExpiredMessage();

                            return;
                        }

                        try {

                            Uri uri =
                                    Uri.parse(
                                            videoUris.get(position)
                                    );

                            playVideo(uri);

                        } catch (Exception e) {

                            showVideoError(
                                    "Video URI सही नहीं है."
                            );
                        }
                    }
            );


            row.addView(
                    play,
                    new LinearLayout.LayoutParams(
                            dp(80),
                            dp(50)
                    )
            );


            Button delete =
                    new Button(this);

            delete.setText("DELETE");
            delete.setAllCaps(false);

            delete.setOnClickListener(
                    v -> confirmDelete(position)
            );

            row.addView(
                    delete,
                    new LinearLayout.LayoutParams(
                            dp(90),
                            dp(50)
                    )
            );


            videoListLayout.addView(
                    row
            );
        }
    }


    // ============================================================
    // PLAY VIDEO
    // ============================================================

    private void playVideo(Uri uri) {

        if (uri == null) {
            return;
        }

        if (isExpired()) {

            showExpiredMessage();

            return;
        }

        try {

            // Stop previous video
            videoView.stopPlayback();

            videoMode = true;

            showOnlyVideo();

            // Set video
            videoView.setVideoURI(uri);

            videoView.requestFocus();

            /*
             * IMPORTANT:
             * start() is NOT called here.
             *
             * VideoView will call start()
             * from OnPreparedListener.
             */

        } catch (Exception e) {

            exitVideoMode();

            showVideoError(
                    "Video open नहीं हो सकी.\n\n"
                            + e.getMessage()
            );
        }
    }


    // ============================================================
    // SHOW ONLY VIDEO
    // ============================================================

    private void showOnlyVideo() {

        contentLayoutVisibilityGone();

        videoListLayout.setVisibility(
                View.GONE
        );

        videoContainer.setVisibility(
                View.VISIBLE
        );

        // Make video area large
        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams)
                        videoContainer.getLayoutParams();

        params.width =
                ViewGroup.LayoutParams.MATCH_PARENT;

        params.height = 0;

        params.weight = 1;

        videoContainer.setLayoutParams(params);

        videoView.setVisibility(
                View.VISIBLE
        );

        videoView.requestFocus();
    }


    // ============================================================
    // HIDE MAIN CONTENT
    // ============================================================

    private void contentLayoutVisibilityGone() {

        /*
         * The current screen is built directly inside rootLayout.
         * Hide all main controls except the video container.
         */

        for (int i = 0;
             i < rootLayout.getChildCount();
             i++) {

            View child =
                    rootLayout.getChildAt(i);

            if (child != videoContainer) {

                child.setVisibility(
                        View.GONE
                );
            }
        }
    }


    // ============================================================
    // EXIT VIDEO MODE
    // ============================================================

    private void exitVideoMode() {

        videoMode = false;

        try {
            videoView.stopPlayback();
        } catch (Exception ignored) {
        }

        videoContainer.setVisibility(
                View.GONE
        );

        /*
         * Restore all main screen views
         */
        for (int i = 0;
             i < rootLayout.getChildCount();
             i++) {

            View child =
                    rootLayout.getChildAt(i);

            if (child != videoContainer) {

                child.setVisibility(
                        View.VISIBLE
                );
            }
        }

        refreshVideoList();
    }


    // ============================================================
    // DELETE CONFIRMATION
    // ============================================================

    private void confirmDelete(int position) {

        if (position < 0
                || position >= videoUris.size()) {
            return;
        }

        String name =
                videoNames.get(position);

        new AlertDialog.Builder(this)
                .setTitle("Delete Video")
                .setMessage(
                        "क्या आप इस वीडियो को delete करना चाहते हैं?\n\n"
                                + name
                )
                .setNegativeButton(
                        "CANCEL",
                        null
                )
                .setPositiveButton(
                        "DELETE",
                        (dialog, which) -> {

                            deleteVideo(position);
                        }
                )
                .show();
    }


    // ============================================================
    // DELETE VIDEO
    // ============================================================

    private void deleteVideo(int position) {

        if (position < 0
                || position >= videoUris.size()) {
            return;
        }

        videoUris.remove(position);
        videoNames.remove(position);

        /*
         * Clear old saved video entries first
         */
        SharedPreferences.Editor editor =
                preferences.edit();

        int oldCount =
                preferences.getInt(
                        KEY_VIDEO_COUNT,
                        0
                );

        for (int i = 0;
             i < oldCount;
             i++) {

            editor.remove(
                    "video_uri_" + i
            );

            editor.remove(
                    "video_name_" + i
            );
        }

        editor.apply();

        saveVideos();

        refreshVideoList();
    }


    // ============================================================
    // VIDEO ERROR
    // ============================================================

    private void showVideoError(
            String message
    ) {

        try {
            videoView.stopPlayback();
        } catch (Exception ignored) {
        }

        new AlertDialog.Builder(this)
                .setTitle("Vishal Secure")
                .setMessage(message)
                .setPositiveButton(
                        "OK",
                        null
                )
                .show();
    }


    // ============================================================
    // BACK BUTTON
    // ============================================================

    @Override
    public void onBackPressed() {

        if (videoMode) {

            exitVideoMode();

            return;
        }

        super.onBackPressed();
    }


    // ============================================================
    // STOP VIDEO WHEN APP GOES BACKGROUND
    // ============================================================

    @Override
    protected void onPause() {

        super.onPause();

        if (videoMode) {

            try {
                videoView.pause();
            } catch (Exception ignored) {
            }
        }
    }


    @Override
    protected void onDestroy() {

        try {

            if (videoView != null) {
                videoView.stopPlayback();
            }

        } catch (Exception ignored) {
        }

        super.onDestroy();
    }


    // ============================================================
    // DP HELPER
    // ============================================================

    private int dp(int value) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return (int)
                (value * density + 0.5f);
    }
}
