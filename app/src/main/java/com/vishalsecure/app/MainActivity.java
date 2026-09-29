package com.vishalsecure.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final int PICK_VIDEO_REQUEST = 1001;

    private static final String PREFS_NAME =
            "VishalSecurePrefs";

    private static final String KEY_EXPIRY_TIME =
            "expiry_time";

    private static final String KEY_VIDEO_COUNT =
            "video_count";

    private static final String KEY_RECEIVER_NAME =
            "receiver_name";

    private static final String KEY_RECEIVER_MOBILE =
            "receiver_mobile";

    private SharedPreferences prefs;

    private EditText receiverNameEdit;
    private EditText receiverMobileEdit;
    private TextView expiryText;
    private LinearLayout videoListLayout;

    private final ArrayList<String> videoUris =
            new ArrayList<>();

    private final ArrayList<String> videoNames =
            new ArrayList<>();

    private long expiryTime = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        prefs = getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
        );

        expiryTime =
                prefs.getLong(
                        KEY_EXPIRY_TIME,
                        0L
                );

        buildUI();

        loadReceiverDetails();
        loadExpiry();
        loadVideos();

        /*
         * पुराने videos को अभी भी वैसे ही रहने देंगे।
         * उन्हें इस चरण में encrypt नहीं करेंगे।
         */
        refreshVideoList();
    }

    private void buildUI() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                30,
                30,
                30,
                30
        );

        root.setBackgroundColor(
                Color.WHITE
        );

        TextView title =
                new TextView(this);

        title.setText(
                "VISHAL SECURE"
        );

        title.setTextSize(26);

        title.setTextColor(
                Color.BLACK
        );

        title.setGravity(
                Gravity.CENTER
        );

        title.setPadding(
                0,
                10,
                0,
                5
        );

        root.addView(title);

        TextView subtitle =
                new TextView(this);

        subtitle.setText(
                "Secure Content"
        );

        subtitle.setTextSize(16);

        subtitle.setTextColor(
                Color.DKGRAY
        );

        subtitle.setGravity(
                Gravity.CENTER
        );

        subtitle.setPadding(
                0,
                0,
                0,
                25
        );

        root.addView(subtitle);

        receiverNameEdit =
                new EditText(this);

        receiverNameEdit.setHint(
                "Receiver Name"
        );

        receiverNameEdit.setSingleLine(
                true
        );

        root.addView(
                receiverNameEdit,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        receiverMobileEdit =
                new EditText(this);

        receiverMobileEdit.setHint(
                "Receiver Mobile"
        );

        receiverMobileEdit.setInputType(
                android.text.InputType.TYPE_CLASS_PHONE
        );

        receiverMobileEdit.setSingleLine(
                true
        );

        root.addView(
                receiverMobileEdit,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        Button saveReceiverButton =
                new Button(this);

        saveReceiverButton.setText(
                "SAVE RECEIVER"
        );

        saveReceiverButton.setOnClickListener(
                v -> saveReceiverDetails()
        );

        root.addView(
                saveReceiverButton
        );

        Button openSecureButton =
                new Button(this);

        openSecureButton.setText(
                "OPEN SECURE CONTENT"
        );

        openSecureButton.setOnClickListener(
                v -> openSecureContent()
        );

        root.addView(
                openSecureButton
        );

        Button expiryButton =
                new Button(this);

        expiryButton.setText(
                "SET EXPIRY"
        );

        expiryButton.setOnClickListener(
                v -> showExpiryPicker()
        );

        root.addView(
                expiryButton
        );

        expiryText =
                new TextView(this);

        expiryText.setTextSize(16);

        expiryText.setTextColor(
                Color.RED
        );

        expiryText.setPadding(
                5,
                10,
                5,
                20
        );

        root.addView(
                expiryText
        );

        TextView listTitle =
                new TextView(this);

        listTitle.setText(
                "Secure Videos"
        );

        listTitle.setTextSize(20);

        listTitle.setTextColor(
                Color.BLACK
        );

        listTitle.setPadding(
                0,
                10,
                0,
                10
        );

        root.addView(
                listTitle
        );

        videoListLayout =
                new LinearLayout(this);

        videoListLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        root.addView(
                videoListLayout,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        setContentView(root);
    }

    private void saveReceiverDetails() {

        String name =
                receiverNameEdit
                        .getText()
                        .toString()
                        .trim();

        String mobile =
                receiverMobileEdit
                        .getText()
                        .toString()
                        .trim();

        prefs.edit()
                .putString(
                        KEY_RECEIVER_NAME,
                        name
                )
                .putString(
                        KEY_RECEIVER_MOBILE,
                        mobile
                )
                .apply();

        Toast.makeText(
                this,
                "Receiver details saved",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void loadReceiverDetails() {

        receiverNameEdit.setText(
                prefs.getString(
                        KEY_RECEIVER_NAME,
                        ""
                )
        );

        receiverMobileEdit.setText(
                prefs.getString(
                        KEY_RECEIVER_MOBILE,
                        ""
                )
        );
    }

    private void showExpiryPicker() {

        Calendar now =
                Calendar.getInstance();

        DatePickerDialog datePickerDialog =
                new DatePickerDialog(
                        this,
                        (view,
                         year,
                         month,
                         dayOfMonth) -> {

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

                            TimePickerDialog timePickerDialog =
                                    new TimePickerDialog(
                                            this,
                                            (timeView,
                                             hourOfDay,
                                             minute) -> {

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

                                                prefs.edit()
                                                        .putLong(
                                                                KEY_EXPIRY_TIME,
                                                                expiryTime
                                                        )
                                                        .apply();

                                                updateExpiryText();

                                                Toast.makeText(
                                                        this,
                                                        "Expiry saved",
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

                            timePickerDialog.show();
                        },
                        now.get(Calendar.YEAR),
                        now.get(Calendar.MONTH),
                        now.get(Calendar.DAY_OF_MONTH)
                );

        datePickerDialog.show();
    }

    private void loadExpiry() {

        expiryTime =
                prefs.getLong(
                        KEY_EXPIRY_TIME,
                        0L
                );

        updateExpiryText();
    }

    private void updateExpiryText() {

        if (expiryText == null) {
            return;
        }

        if (expiryTime <= 0L) {

            expiryText.setText(
                    "Expiry: Not Set"
            );

            return;
        }

        SimpleDateFormat sdf =
                new SimpleDateFormat(
                        "dd-MM-yyyy hh:mm a",
                        Locale.getDefault()
                );

        expiryText.setText(
                "Expiry: " +
                        sdf.format(
                                new Date(expiryTime)
                        )
        );
    }

    private boolean isExpired() {

        if (expiryTime <= 0L) {
            return false;
        }

        return System.currentTimeMillis()
                >= expiryTime;
    }

    private void openSecureContent() {

        if (isExpired()) {

            showExpiredMessage();

            return;
        }

        Intent intent =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType(
                "video/*"
        );

        intent.putExtra(
                Intent.EXTRA_ALLOW_MULTIPLE,
                true
        );

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        startActivityForResult(
                intent,
                PICK_VIDEO_REQUEST
        );
    }

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

            if (data.getClipData() != null) {

                ClipData clipData =
                        data.getClipData();

                for (int i = 0;
                     i < clipData.getItemCount();
                     i++) {

                    Uri uri =
                            clipData
                                    .getItemAt(i)
                                    .getUri();

                    addVideo(uri);
                }

            } else if (data.getData() != null) {

                addVideo(
                        data.getData()
                );
            }

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Video add error",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    /*
     * नई video:
     *
     * Gallery
     *    ↓
     * Temporary private file
     *    ↓
     * AES-GCM encryption
     *    ↓
     * .vsec encrypted file
     *
     * Original temporary file delete.
     */
    private void addVideo(Uri uri) {

        if (uri == null) {
            return;
        }

        File encryptedFile =
                encryptSelectedVideo(uri);

        if (encryptedFile == null) {

            Toast.makeText(
                    this,
                    "Video encryption failed",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        /*
         * Database/preferences में अब
         * encrypted file का path save होगा।
         */
        videoUris.add(
                encryptedFile.getAbsolutePath()
        );

        String originalName =
                getDisplayName(uri);

        if (originalName == null ||
                originalName.trim().isEmpty()) {

            originalName =
                    "Secure Video " +
                            videoUris.size();
        }

        videoNames.add(
                originalName
        );

        saveVideos();

        askCustomVideoName(
                videoUris.size() - 1
        );
    }

    private File encryptSelectedVideo(
            Uri sourceUri
    ) {

        File secureDir =
                new File(
                        getFilesDir(),
                        "secure_videos"
                );

        if (!secureDir.exists()) {

            if (!secureDir.mkdirs()) {
                return null;
            }
        }

        String temporaryName =
                "temp_" +
                        UUID.randomUUID() +
                        ".tmp";

        File temporaryFile =
                new File(
                        secureDir,
                        temporaryName
                );

        File encryptedFile =
                new File(
                        secureDir,
                        UUID.randomUUID() +
                                ".vsec"
                );

        try {

            /*
             * Step 1:
             * Source video को temporary private
             * file में copy करना।
             */
            try (
                    InputStream input =
                            getContentResolver()
                                    .openInputStream(
                                            sourceUri
                                    );

                    FileOutputStream output =
                            new FileOutputStream(
                                    temporaryFile
                            )
            ) {

                if (input == null) {
                    return null;
                }

                byte[] buffer =
                        new byte[1024 * 1024];

                int length;

                while (
                        (length =
                                input.read(buffer))
                                != -1
                ) {

                    output.write(
                            buffer,
                            0,
                            length
                    );
                }

                output.flush();
            }

            /*
             * Step 2:
             * AES-GCM encryption।
             */
            boolean encrypted =
                    CryptoManager.encryptFile(
                            temporaryFile,
                            encryptedFile
                    );

            /*
             * Step 3:
             * Original temporary unencrypted
             * file तुरंत delete।
             */
            if (temporaryFile.exists()) {
                temporaryFile.delete();
            }

            if (!encrypted) {

                if (encryptedFile.exists()) {
                    encryptedFile.delete();
                }

                return null;
            }

            return encryptedFile;

        } catch (Exception e) {

            if (temporaryFile.exists()) {
                temporaryFile.delete();
            }

            if (encryptedFile.exists()) {
                encryptedFile.delete();
            }

            return null;
        }
    }

    private void askCustomVideoName(
            final int index
    ) {

        if (index < 0 ||
                index >= videoNames.size()) {

            return;
        }

        final EditText input =
                new EditText(this);

        input.setSingleLine(
                true
        );

        input.setHint(
                "Video name"
        );

        input.setText(
                videoNames.get(index)
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Video Name"
                        )
                        .setView(input)
                        .setPositiveButton(
                                "SAVE",
                                (d, which) -> {

                                    String name =
                                            input.getText()
                                                    .toString()
                                                    .trim();

                                    if (!name.isEmpty()) {

                                        videoNames.set(
                                                index,
                                                name
                                        );

                                        saveVideos();

                                        refreshVideoList();
                                    }
                                }
                        )
                        .setNegativeButton(
                                "CANCEL",
                                null
                        )
                        .create();

        dialog.show();
    }

    private String getDisplayName(
            Uri uri
    ) {

        android.database.Cursor cursor =
                null;

        try {

            cursor =
                    getContentResolver().query(
                            uri,
                            null,
                            null,
                            null,
                            null
                    );

            if (cursor != null &&
                    cursor.moveToFirst()) {

                int index =
                        cursor.getColumnIndex(
                                android.provider.OpenableColumns.DISPLAY_NAME
                        );

                if (index >= 0) {

                    return cursor.getString(
                            index
                    );
                }
            }

        } catch (Exception ignored) {

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }

        return null;
    }

    private void loadVideos() {

        videoUris.clear();
        videoNames.clear();

        int count =
                prefs.getInt(
                        KEY_VIDEO_COUNT,
                        0
                );

        for (int i = 0;
             i < count;
             i++) {

            String uri =
                    prefs.getString(
                            "video_uri_" + i,
                            null
                    );

            String name =
                    prefs.getString(
                            "video_name_" + i,
                            null
                    );

            if (uri != null &&
                    !uri.trim().isEmpty()) {

                videoUris.add(uri);

                if (name == null ||
                        name.trim().isEmpty()) {

                    name =
                            "Secure Video " +
                                    videoUris.size();
                }

                videoNames.add(name);
            }
        }
    }

    private void saveVideos() {

        SharedPreferences.Editor editor =
                prefs.edit();

        int oldCount =
                prefs.getInt(
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

    private boolean isPrivateVideoPath(
            String path
    ) {

        if (path == null) {
            return false;
        }

        String privateDirectory =
                new File(
                        getFilesDir(),
                        "secure_videos"
                ).getAbsolutePath();

        return path.startsWith(
                privateDirectory
        );
    }

    private void refreshVideoList() {

        videoListLayout.removeAllViews();

        if (videoUris.isEmpty()) {

            TextView empty =
                    new TextView(this);

            empty.setText(
                    "No secure videos added"
            );

            empty.setTextSize(16);

            empty.setTextColor(
                    Color.GRAY
            );

            empty.setPadding(
                    5,
                    20,
                    5,
                    20
            );

            videoListLayout.addView(
                    empty
            );

            return;
        }

        for (int i = 0;
             i < videoUris.size();
             i++) {

            final int index = i;

            LinearLayout row =
                    new LinearLayout(this);

            row.setOrientation(
                    LinearLayout.VERTICAL
            );

            row.setPadding(
                    5,
                    10,
                    5,
                    10
            );

            TextView name =
                    new TextView(this);

            name.setText(
                    videoNames.get(index)
            );

            name.setTextSize(17);

            name.setTextColor(
                    Color.BLACK
            );

            row.addView(name);

            LinearLayout buttons =
                    new LinearLayout(this);

            buttons.setOrientation(
                    LinearLayout.HORIZONTAL
            );

            Button play =
                    new Button(this);

            play.setText(
                    "PLAY"
            );

            play.setOnClickListener(
                    v -> {

                        if (isExpired()) {

                            showExpiredMessage();

                            return;
                        }

                        String path =
                                videoUris.get(index);

                        if (path == null ||
                                path.trim().isEmpty()) {

                            Toast.makeText(
                                    this,
                                    "Video path missing",
                                    Toast.LENGTH_LONG
                            ).show();

                            return;
                        }

                        File videoFile =
                                new File(path);

                        if (!videoFile.exists()) {

                            Toast.makeText(
                                    this,
                                    "Video file not found",
                                    Toast.LENGTH_LONG
                            ).show();

                            return;
                        }

                        Intent intent =
                                new Intent(
                                        MainActivity.this,
                                        VideoPlayerActivity.class
                                );

                        intent.putExtra(
                                "video_uri",
                                Uri.fromFile(
                                        videoFile
                                ).toString()
                        );

                        /*
                         * Phase-2A expiry
                         */
                        intent.putExtra(
                                "expiry_time",
                                expiryTime
                        );

                        /*
                         * Phase-2B:
                         * Player को बताना है कि यह
                         * encrypted .vsec file है।
                         */
                        intent.putExtra(
                                "encrypted_video",
                                videoFile.getName()
                                        .endsWith(".vsec")
                        );

                        startActivity(intent);
                    }
            );

            buttons.addView(
                    play,
                    new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1
                    )
            );

            Button delete =
                    new Button(this);

            delete.setText(
                    "DELETE"
            );

            delete.setOnClickListener(
                    v -> {

                        new AlertDialog.Builder(
                                MainActivity.this
                        )
                                .setTitle(
                                        "Delete Video"
                                )
                                .setMessage(
                                        "क्या आप इस वीडियो को delete करना चाहते हैं?"
                                )
                                .setPositiveButton(
                                        "DELETE",
                                        (dialog, which) -> {

                                            String path =
                                                    videoUris.get(index);

                                            if (isPrivateVideoPath(
                                                    path
                                            )) {

                                                File file =
                                                        new File(
                                                                path
                                                        );

                                                if (file.exists()) {
                                                    file.delete();
                                                }
                                            }

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
                                .setNegativeButton(
                                        "CANCEL",
                                        null
                                )
                                .show();
                    }
            );

            buttons.addView(
                    delete,
                    new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1
                    )
            );

            row.addView(
                    buttons
            );

            videoListLayout.addView(
                    row
            );
        }
    }

    private void showExpiredMessage() {

        new AlertDialog.Builder(this)
                .setTitle(
                        "Vishal Secure"
                )
                .setMessage(
                        "Secure content की expiry हो चुकी है।"
                )
                .setPositiveButton(
                        "OK",
                        null
                )
                .show();
    }
}
