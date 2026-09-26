package com.vishalsecure.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int PICK_VIDEO_REQUEST = 1001;

    private LinearLayout rootLayout;
    private LinearLayout videoListLayout;
    private LinearLayout videoContainer;

    private EditText receiverName;
    private EditText receiverMobile;

    private Button openContentButton;
    private Button setExpiryButton;

    private TextView expiryText;
    private TextView titleText;
    private TextView subtitleText;

    private VideoView videoView;
    private MediaController mediaController;

    private SharedPreferences preferences;

    private static final String PREFS_NAME =
            "VishalSecurePrefs";

    private static final String KEY_EXPIRY_TIME =
            "expiry_time";

    private static final String KEY_VIDEO_COUNT =
            "video_count";

    private long expiryTime = 0L;

    private boolean videoMode = false;

    private final ArrayList<String> videoUris =
            new ArrayList<>();

    private final ArrayList<String> videoNames =
            new ArrayList<>();

    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        rootLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        rootLayout.setPadding(
                30,
                30,
                30,
                30
        );

        rootLayout.setBackgroundColor(
                Color.WHITE
        );

        // --------------------------------------------------------
        // TITLE
        // --------------------------------------------------------

        titleText = new TextView(this);

        titleText.setText(
                "VISHAL SECURE"
        );

        titleText.setTextSize(26);
        titleText.setTextColor(Color.BLACK);
        titleText.setGravity(
                Gravity.CENTER
        );

        titleText.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        rootLayout.addView(
                titleText,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        // --------------------------------------------------------
        // SUBTITLE
        // --------------------------------------------------------

        subtitleText = new TextView(this);

        subtitleText.setText(
                "Secure Content"
        );

        subtitleText.setTextSize(16);
        subtitleText.setTextColor(
                Color.DKGRAY
        );

        subtitleText.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.setMargins(
                0,
                5,
                0,
                25
        );

        rootLayout.addView(
                subtitleText,
                subtitleParams
        );

        // --------------------------------------------------------
        // RECEIVER NAME
        // --------------------------------------------------------

        receiverName =
                new EditText(this);

        receiverName.setHint(
                "Receiver Name"
        );

        receiverName.setTextSize(16);

        rootLayout.addView(
                receiverName,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        // --------------------------------------------------------
        // RECEIVER MOBILE
        // --------------------------------------------------------

        receiverMobile =
                new EditText(this);

        receiverMobile.setHint(
                "Receiver Mobile"
        );

        receiverMobile.setTextSize(16);

        receiverMobile.setInputType(
                android.text.InputType.TYPE_CLASS_PHONE
        );

        LinearLayout.LayoutParams mobileParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        mobileParams.setMargins(
                0,
                10,
                0,
                10
        );

        rootLayout.addView(
                receiverMobile,
                mobileParams
        );

        // --------------------------------------------------------
        // OPEN SECURE CONTENT
        // --------------------------------------------------------

        openContentButton =
                new Button(this);

        openContentButton.setText(
                "OPEN SECURE CONTENT"
        );

        openContentButton.setOnClickListener(v -> {

            if (isExpired()) {

                Toast.makeText(
                        MainActivity.this,
                        "Content Expired",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            openVideoPicker();
        });

        rootLayout.addView(
                openContentButton,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        // --------------------------------------------------------
        // SET EXPIRY
        // --------------------------------------------------------

        setExpiryButton =
                new Button(this);

        setExpiryButton.setText(
                "SET EXPIRY"
        );

        setExpiryButton.setTextSize(15);

        setExpiryButton.setOnClickListener(v -> {
            showExpiryDatePicker();
        });

        LinearLayout.LayoutParams expiryButtonParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        expiryButtonParams.setMargins(
                0,
                5,
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

        expiryText =
                new TextView(this);

        expiryText.setTextSize(15);
        expiryText.setTextColor(
                Color.DKGRAY
        );

        expiryText.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams expiryTextParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        expiryTextParams.setMargins(
                0,
                5,
                0,
                15
        );

        rootLayout.addView(
                expiryText,
                expiryTextParams
        );

        updateExpiryText();

        // --------------------------------------------------------
        // VIDEO LIST TITLE
        // --------------------------------------------------------

        TextView listTitle =
                new TextView(this);

        listTitle.setText(
                "SECURE VIDEOS"
        );

        listTitle.setTextSize(18);
        listTitle.setTextColor(
                Color.BLACK
        );

        listTitle.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        rootLayout.addView(
                listTitle,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        // --------------------------------------------------------
        // VIDEO LIST
        // --------------------------------------------------------

        videoListLayout =
                new LinearLayout(this);

        videoListLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams listParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                );

        listParams.setMargins(
                0,
                10,
                0,
                0
        );

        rootLayout.addView(
                videoListLayout,
                listParams
        );

        // --------------------------------------------------------
        // VIDEO CONTAINER
        // --------------------------------------------------------

        videoContainer =
                new LinearLayout(this);

        videoContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        videoContainer.setGravity(
                Gravity.CENTER
        );

        videoContainer.setBackgroundColor(
                Color.BLACK
        );

        videoContainer.setVisibility(
                View.GONE
        );

        videoView =
                new VideoView(this);

        videoView.setBackgroundColor(
                Color.BLACK
        );

        mediaController =
                new MediaController(this);

        videoView.setMediaController(
                mediaController
        );

        videoContainer.addView(
                videoView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                )
        );

        LinearLayout.LayoutParams videoContainerParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0
                );

        rootLayout.addView(
                videoContainer,
                videoContainerParams
        );

        // --------------------------------------------------------
        // VIDEO PREPARED
        // --------------------------------------------------------

        videoView.setOnPreparedListener(mp -> {

            try {

                videoView.requestFocus();

                mp.setScreenOnWhilePlaying(true);

                videoView.start();

            } catch (Exception ignored) {
            }
        });

        // --------------------------------------------------------
        // VIDEO ERROR
        // --------------------------------------------------------

        videoView.setOnErrorListener(
                (mp, what, extra) -> {

                    Toast.makeText(
                            MainActivity.this,
                            "Video play नहीं हो सकी",
                            Toast.LENGTH_LONG
                    ).show();

                    exitVideoMode();

                    return true;
                }
        );

        setContentView(rootLayout);

        refreshVideoList();
    }

    // ============================================================
    // EXPIRY DATE PICKER
    // ============================================================

    private void showExpiryDatePicker() {

        final java.util.Calendar calendar =
                java.util.Calendar.getInstance();

        DatePickerDialog datePickerDialog =
                new DatePickerDialog(
                        this,
                        (view, year, month, dayOfMonth) -> {

                            showExpiryTimePicker(
                                    year,
                                    month,
                                    dayOfMonth
                            );

                        },
                        calendar.get(
                                java.util.Calendar.YEAR
                        ),
                        calendar.get(
                                java.util.Calendar.MONTH
                        ),
                        calendar.get(
                                java.util.Calendar.DAY_OF_MONTH
                        )
                );

        datePickerDialog.setTitle(
                "Expiry Date"
        );

        datePickerDialog.show();
    }

    // ============================================================
    // EXPIRY TIME PICKER
    // ============================================================

    private void showExpiryTimePicker(
            int year,
            int month,
            int day
    ) {

        final java.util.Calendar calendar =
                java.util.Calendar.getInstance();

        TimePickerDialog timePickerDialog =
                new TimePickerDialog(
                        this,
                        (view, hourOfDay, minute) -> {

                            java.util.Calendar expiryCalendar =
                                    java.util.Calendar.getInstance();

                            expiryCalendar.set(
                                    java.util.Calendar.YEAR,
                                    year
                            );

                            expiryCalendar.set(
                                    java.util.Calendar.MONTH,
                                    month
                            );

                            expiryCalendar.set(
                                    java.util.Calendar.DAY_OF_MONTH,
                                    day
                            );

                            expiryCalendar.set(
                                    java.util.Calendar.HOUR_OF_DAY,
                                    hourOfDay
                            );

                            expiryCalendar.set(
                                    java.util.Calendar.MINUTE,
                                    minute
                            );

                            expiryCalendar.set(
                                    java.util.Calendar.SECOND,
                                    0
                            );

                            expiryCalendar.set(
                                    java.util.Calendar.MILLISECOND,
                                    0
                            );

                            expiryTime =
                                    expiryCalendar
                                            .getTimeInMillis();

                            saveExpiry();

                            updateExpiryText();

                            Toast.makeText(
                                    MainActivity.this,
                                    "Expiry Set Successfully",
                                    Toast.LENGTH_SHORT
                            ).show();

                        },
                        calendar.get(
                                java.util.Calendar.HOUR_OF_DAY
                        ),
                        calendar.get(
                                java.util.Calendar.MINUTE
                        ),
                        false
                );

        timePickerDialog.setTitle(
                "Expiry Time"
        );

        timePickerDialog.show();
    }

    // ============================================================
    // SAVE EXPIRY
    // ============================================================

    private void saveExpiry() {

        preferences.edit()
                .putLong(
                        KEY_EXPIRY_TIME,
                        expiryTime
                )
                .apply();
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
    // UPDATE EXPIRY TEXT
    // ============================================================

    private void updateExpiryText() {

        if (expiryText == null) {
            return;
        }

        if (expiryTime <= 0L) {

            expiryText.setText(
                    "Expiry: अभी निर्धारित नहीं"
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

        String date =
                format.format(
                        new Date(expiryTime)
                );

        if (isExpired()) {

            expiryText.setText(
                    "Expiry: EXPIRED (" +
                            date +
                            ")"
            );

            expiryText.setTextColor(
                    Color.RED
            );

        } else {

            expiryText.setText(
                    "Expiry: " +
                            date
            );

            expiryText.setTextColor(
                    Color.DKGRAY
            );
        }
    }

    // ============================================================
    // VIDEO PICKER
    // ============================================================

    private void openVideoPicker() {

        Intent intent =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                );

        intent.setType(
                "video/*"
        );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
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

        if (requestCode != PICK_VIDEO_REQUEST ||
                resultCode != RESULT_OK ||
                data == null) {

            return;
        }

        try {

            ClipData clipData =
                    data.getClipData();

            if (clipData != null) {

                for (
                        int i = 0;
                        i < clipData.getItemCount();
                        i++
                ) {

                    Uri uri =
                            clipData
                                    .getItemAt(i)
                                    .getUri();

                    addVideo(uri);
                }

            } else {

                Uri uri =
                        data.getData();

                if (uri != null) {
                    addVideo(uri);
                }
            }

            saveVideos();

            refreshVideoList();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Video select नहीं हो सकी",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // ============================================================
    // ADD VIDEO
    // ============================================================

    private void addVideo(Uri uri) {

        if (uri == null) {
            return;
        }

        try {

            getContentResolver()
                    .takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );

        } catch (Exception ignored) {
        }

        String uriString =
                uri.toString();

        if (videoUris.contains(uriString)) {
            return;
        }

        videoUris.add(
                uriString
        );

        String name =
                uri.getLastPathSegment();

        if (name == null ||
                name.trim().isEmpty()) {

            name = "Secure Video";
        }

        videoNames.add(
                name
        );
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

        for (
                int i = 0;
                i < videoUris.size();
                i++
        ) {

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

        for (
                int i = 0;
                i < count;
                i++
        ) {

            String uri =
                    preferences.getString(
                            "video_uri_" + i,
                            null
                    );

            String name =
                    preferences.getString(
                            "video_name_" + i,
                            "Secure Video"
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

            TextView empty =
                    new TextView(this);

            empty.setText(
                    "अभी कोई secure video नहीं है"
            );

            empty.setTextSize(15);

            empty.setTextColor(
                    Color.GRAY
            );

            empty.setPadding(
                    10,
                    20,
                    
