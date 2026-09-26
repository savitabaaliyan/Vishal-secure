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
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int PICK_VIDEO_REQUEST = 1001;

    private static final String PREFS_NAME =
            "VishalSecurePrefs";

    private static final String KEY_EXPIRY_TIME =
            "expiry_time";

    private static final String KEY_VIDEO_COUNT =
            "video_count";

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
    private MediaController mediaController;

    private SharedPreferences preferences;

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
    // DP
    // ============================================================

    private int dp(int value) {

        return (int) (
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
                        + 0.5f
        );
    }


    // ============================================================
    // TEXT VIEW HELPER
    // ============================================================

    private TextView makeText(
            String text,
            float size,
            int color
    ) {

        TextView textView =
                new TextView(this);

        textView.setText(text);
        textView.setTextSize(size);
        textView.setTextColor(color);

        return textView;
    }


    // ============================================================
    // MAIN SCREEN
    // ============================================================

    private void buildMainScreen() {

        rootLayout =
                new LinearLayout(this);

        rootLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        rootLayout.setPadding(
                dp(20),
                dp(20),
                dp(20),
                dp(20)
        );

        rootLayout.setBackgroundColor(
                Color.WHITE
        );


        // --------------------------------------------------------
        // CONTENT LAYOUT
        // --------------------------------------------------------

        contentLayout =
                new LinearLayout(this);

        contentLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams contentParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                );

        rootLayout.addView(
                contentLayout,
                contentParams
        );


        // --------------------------------------------------------
        // TITLE
        // --------------------------------------------------------

        titleText =
                makeText(
                        "VISHAL SECURE",
                        26,
                        Color.BLACK
                );

        titleText.setGravity(
                Gravity.CENTER
        );

        titleText.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        contentLayout.addView(
                titleText,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );


        // --------------------------------------------------------
        // SUBTITLE
        // --------------------------------------------------------

        subtitleText =
                makeText(
                        "Secure Content",
                        16,
                        Color.DKGRAY
                );

        subtitleText.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        subtitleParams.setMargins(
                0,
                dp(5),
                0,
                dp(20)
        );

        contentLayout.addView(
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

        receiverName.setTextSize(
                16
        );

        contentLayout.addView(
                receiverName,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
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

        receiverMobile.setTextSize(
                16
        );

        receiverMobile.setInputType(
                android.text.InputType.TYPE_CLASS_PHONE
        );

        LinearLayout.LayoutParams mobileParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        mobileParams.setMargins(
                0,
                dp(8),
                0,
                dp(8)
        );

        contentLayout.addView(
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

        openContentButton.setOnClickListener(
                v -> {

                    if (isExpired()) {

                        Toast.makeText(
                                MainActivity.this,
                                "Content Expired",
                                Toast.LENGTH_LONG
                        ).show();

                        updateExpiryText();

                        return;
                    }

                    openVideoPicker();
                }
        );

        contentLayout.addView(
                openContentButton,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
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

        setExpiryButton.setOnClickListener(
                v -> showExpiryDatePicker()
        );

        LinearLayout.LayoutParams expiryButtonParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        expiryButtonParams.setMargins(
                0,
                dp(5),
                0,
                0
        );

        contentLayout.addView(
                setExpiryButton,
                expiryButtonParams
        );


        // --------------------------------------------------------
        // EXPIRY TEXT
        // --------------------------------------------------------

        expiryText =
                makeText(
                        "",
                        15,
                        Color.DKGRAY
                );

        expiryText.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams expiryTextParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        expiryTextParams.setMargins(
                0,
                dp(5),
                0,
                dp(10)
        );

        contentLayout.addView(
                expiryText,
                expiryTextParams
        );

        updateExpiryText();


        // --------------------------------------------------------
        // SECURE VIDEOS TITLE
        // --------------------------------------------------------

        TextView listTitle =
                makeText(
                        "SECURE VIDEOS",
                        18,
                        Color.BLACK
                );

        listTitle.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        contentLayout.addView(
                listTitle,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
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
                        -1,
                        0,
                        1
                );

        listParams.setMargins(
                0,
                dp(8),
                0,
                0
        );

        contentLayout.addView(
                videoListLayout,
                listParams
        );


        // --------------------------------------------------------
        // VIDEO CONTAINER
        // --------------------------------------------------------

        videoContainer =
                new FrameLayout(this);

        videoContainer.setBackgroundColor(
                Color.BLACK
        );

        videoContainer.setVisibility(
                View.GONE
        );


        // --------------------------------------------------------
        // VIDEO VIEW
        // --------------------------------------------------------

        videoView =
                new VideoView(this);

        videoView.setBackgroundColor(
                Color.BLACK
        );


        FrameLayout.LayoutParams videoParams =
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                );

        videoContainer.addView(
                videoView,
                videoParams
        );


        // Initially height = 0.
        // PLAY के समय इसे weight = 1 करके full screen किया जाएगा.

        LinearLayout.LayoutParams videoContainerParams =
                new LinearLayout.LayoutParams(
                        -1,
                        0
                );

        rootLayout.addView(
                videoContainer,
                videoContainerParams
        );


        // --------------------------------------------------------
        // MEDIA CONTROLLER
        // --------------------------------------------------------

        mediaController =
                new MediaController(this);

        mediaController.setAnchorView(
                videoView
        );

        videoView.setMediaController(
                mediaController
        );


        // --------------------------------------------------------
        // VIDEO PREPARED
        // --------------------------------------------------------

        videoView.setOnPreparedListener(
                mp -> {

                    try {

                        mp.setScreenOnWhilePlaying(
                                true
                        );

                        videoView.requestFocus();

                        videoView.start();

                    } catch (Exception e) {

                        showVideoError(
                                e.getMessage()
                        );
                    }
                }
        );


        // --------------------------------------------------------
        // VIDEO ERROR
        // --------------------------------------------------------

        videoView.setOnErrorListener(
                (mp, what, extra) -> {

                    showVideoError(
                            "Video format या file access में समस्या है."
                    );

                    return true;
                }
        );


        // --------------------------------------------------------
        // SET CONTENT VIEW
        // --------------------------------------------------------

        setContentView(
                rootLayout
        );

        refreshVideoList();
    }


    // ============================================================
    // EXPIRY DATE PICKER
    // ============================================================

    private void showExpiryDatePicker() {

        Calendar now =
                Calendar.getInstance();

        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,

                        (view, year, month, day) -> {

                            showExpiryTimePicker(
                                    year,
                                    month,
                                    day
                            );
                        },

                        now.get(
                                Calendar.YEAR
                        ),

                        now.get(
                                Calendar.MONTH
                        ),

                        now.get(
                                Calendar.DAY_OF_MONTH
                        )
                );

        dialog.setTitle(
                "Expiry Date"
        );

        dialog.show();
    }


    // ============================================================
    // EXPIRY TIME PICKER
    // ============================================================

    private void showExpiryTimePicker(
            int year,
            int month,
            int day
    ) {

        Calendar now =
                Calendar.getInstance();

        TimePickerDialog dialog =
                new TimePickerDialog(
                        this,

                        (view, hour, minute) -> {

                            Calendar expiry =
                                    Calendar.getInstance();

                            expiry.set(
                                    Calendar.YEAR,
                                    year
                            );

                            expiry.set(
                                    Calendar.MONTH,
                                    month
                            );

                            expiry.set(
                                    Calendar.DAY_OF_MONTH,
                                    day
                            );

                            expiry.set(
                                    Calendar.HOUR_OF_DAY,
                                    hour
                            );

                            expiry.set(
                                    Calendar.MINUTE,
                                    minute
                            );

                            expiry.set(
                                    Calendar.SECOND,
                                    0
                            );

                            expiry.set(
                                    Calendar.MILLISECOND,
                                    0
                            );

                            expiryTime =
                                    expiry.getTimeInMillis();

                            saveExpiry();

                            updateExpiryText();

                            Toast.makeText(
                                    MainActivity.this,
                                    "Expiry Set Successfully",
                                    Toast.LENGTH_SHORT
                            ).show();
                        },

                        now.get(
                                Calendar.HOUR_OF_DAY
                        ),

                        now.get(
                                Calendar.MINUTE
                        ),

                        false
                );

        dialog.setTitle(
                "Expiry Time"
        );

        dialog.show();
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

        return expiryTime > 0L &&
                System.currentTimeMillis()
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
                        new Date(
                                expiryTime
                        )
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

        if (
                requestCode != PICK_VIDEO_REQUEST ||
                        resultCode != RESULT_OK ||
                        data == null
        ) {

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

                    addVideo(
                            clipData
                                    .getItemAt(i)
                                    .getUri()
                    );
                }

            } else if (
                    data.getData() != null
            ) {

                addVideo(
                        data.getData()
                );
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

    private void addVideo(
            Uri uri
    ) {

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

        if (
                videoUris.contains(
                        uriString
                )
        ) {

            return;
        }

        videoUris.add(
                uriString
        );

        String name =
                uri.getLastPathSegment();

        if (
                name == null ||
                        name.trim().isEmpty()
        ) {

            name =
                    "Secure Video";
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

                videoUris.add(
                        uri
                );

                videoNames.add(
                        name
                );
            }
        }
    }


    // ============================================================
    // REFRESH VIDEO LIST
    // ============================================================

    private void refreshVideoList() {

        if (
                videoListLayout == null
        ) {

            return;
        }

        videoListLayout.removeAllViews();

        if (videoUris.isEmpty()) {

            TextView empty =
                    makeText(
                            "अभी कोई secure video नहीं है",
                            15,
                            Color.GRAY
                    );

            empty.setPadding(
                    dp(10),
                    dp(20),
                    dp(10),
                    dp(20)
            );

            videoListLayout.addView(
                    empty
            );

            return;
        }


        // --------------------------------------------------------
        // EVERY VIDEO
        // --------------------------------------------------------

        for (
                int i = 0;
                i < videoUris.size();
                i++
        ) {

            final int index =
                    i;

            LinearLayout row =
                    new LinearLayout(this);

            row.setOrientation(
                    LinearLayout.HORIZONTAL
            );

            row.setGravity(
                    Gravity.CENTER_VERTICAL
            );

            row.setPadding(
                    dp(5),
                    dp(4),
                    dp(5),
                    dp(4)
            );


            // ----------------------------------------------------
            // NAME
            // ----------------------------------------------------

            TextView name =
                    makeText(
                            videoNames.get(i),
                            15,
                            Color.BLACK
                    );

            LinearLayout.LayoutParams nameParams =
                    new LinearLayout.LayoutParams(
                            0,
                            -2,
                            1
                    );

            row.addView(
                    name,
                    nameParams
            );


            // ----------------------------------------------------
            // PLAY
            // ----------------------------------------------------

            Button play =
                    new Button(this);

            play.setText(
                    "PLAY"
            );

            play.setOnClickListener(
                    v -> {

                        if (isExpired()) {

                            Toast.makeText(
                                    MainActivity.this,
                                    "Content Expired",
                                    Toast.LENGTH_LONG
                            ).show();

                            updateExpiryText();

                            return;
                        }

                        if (
                                index >= 0 &&
                                        index < videoUris.size()
                        ) {

                            playVideo(
                                    Uri.parse(
                                            videoUris.get(index)
                                    )
                            );
                        }
                    }
            );

            row.addView(
                    play,
                    new LinearLayout.LayoutParams(
                            -2,
                            -2
                    )
            );


            // ----------------------------------------------------
            // DELETE
            // ----------------------------------------------------

            Button delete =
                    new Button(this);

            delete.setText(
                    "DELETE"
            );

            delete.setOnClickListener(
                    v ->
                            confirmDeleteVideo(
                                    index
                            )
            );

            row.addView(
                    delete,
                    new LinearLayout.LayoutParams(
                            -2,
                            -2
                    )
            );


            videoListLayout.addView(
                    row,
                    new LinearLayout.LayoutParams(
                            -1,
                            -2
                    )
            );
        }
    }


    // ============================================================
    // DELETE CONFIRMATION
    // ============================================================

    private void confirmDeleteVideo(
            int index
    ) {

        if (
                index < 0 ||
                        index >= videoUris.size()
        ) {

            return;
        }

        new AlertDialog.Builder(this)

                .setTitle(
                        "Delete Video"
                )

                .setMessage(
                        "क्या यह secure video हटानी है?"
                )

                .setNegativeButton(
                        "CANCEL",
                        null
                )

                .setPositiveButton(
                        "DELETE",
                        (dialog, which) -> {

                            videoUris.remove(
                                    index
                            );

                            videoNames.remove(
                                    index
                            );

                            saveVideos();

                            refreshVideoList();
                        }
                )

                .show();
    }


    // ============================================================
    // PLAY VIDEO
    // ============================================================

    private void playVideo(
            Uri uri
    ) {

        if (uri == null) {
            return;
        }

        if (isExpired()) {

            Toast.makeText(
                    this,
                    "Content Expired",
                    Toast.LENGTH_LONG
            ).show();

            updateExpiryText();

            return;
        }

        try {

            // पुराने playback को पहले रोकें
            stopVideoPlayback();

            videoMode = true;

            showOnlyVideo();

            // URI सेट करें
            videoView.setVideoURI(
                    uri
            );

            videoView.requestFocus();

        } catch (Exception e) {

            exitVideoMode();

            showVideoError(
                    e.getMessage()
            );
        }
    }


    // ============================================================
    // SHOW VIDEO ONLY
    // ============================================================

    private void showOnlyVideo() {

        // Main controls छिपाएँ
        if (contentLayout != null) {

            contentLayout.setVisibility(
                    View.GONE
            );
        }


        // Video container दिखाएँ
        videoContainer.setVisibility(
                View.VISIBLE
        );


        // सबसे जरूरी FIX:
        // Video container को पूरी available height दें.

        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams)
                        videoContainer
                                .getLayoutParams();

        params.width =
                LinearLayout.LayoutParams.MATCH_PARENT;

        params.height = 0;

        params.weight = 1f;

        videoContainer.setLayoutParams(
                params
        );


        // पूरा background black
        rootLayout.setBackgroundColor(
                Color.BLACK
        );
    }


    // ============================================================
    // EXIT VIDEO MODE
    // ============================================================

    private void exitVideoMode() {

        stopVideoPlayback();

        videoMode = false;


        if (videoContainer != null) {

            videoContainer.setVisibility(
                    View.GONE
            );

            LinearLayout.LayoutParams params =
                    (LinearLayout.LayoutParams)
                            videoContainer
                                    .getLayoutParams();

            params.height = 0;
            params.weight = 0;

            videoContainer.setLayoutParams(
                    params
            );
        }


        if (contentLayout != null) {

            contentLayout.setVisibility(
                    View.VISIBLE
            );
        }


        rootLayout.setBackgroundColor(
                Color.WHITE
        );

        refreshVideoList();

        updateExpiryText();
    }


    // ============================================================
    // STOP VIDEO
    // ============================================================

    private void stopVideoPlayback() {

        try {

            if (videoView != null) {

                videoView.stopPlayback();
            }

        } catch (Exception ignored) {
        }
    }


    // ============================================================
    // VIDEO ERROR
    // ============================================================

    private void showVideoError(
            String message
    ) {

        exitVideoMode();

        String finalMessage;

        if (
                message == null ||
                        message.trim().isEmpty()
        ) {

            finalMessage =
                    "Video play नहीं हो सकी.";

        } else {

            finalMessage =
                    "Video play नहीं हो सकी.\n\n" +
                            message;
        }

        new AlertDialog.Builder(this)

                .setTitle(
                        "Vishal Secure"
                )

                .setMessage(
                        finalMessage
                )

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
    // APP GOES TO BACKGROUND
    // ============================================================

    @Override
    protected void onStop() {

        stopVideoPlayback();

        super.onStop();
    }


    // ============================================================
    // ACTIVITY DESTROY
    // ============================================================

    @Override
    protected void onDestroy() {

        stopVideoPlayback();

        super.onDestroy();
    }
}
