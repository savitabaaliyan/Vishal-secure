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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;

import java.io.File;
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

        /*
         * VS-4-EMD:
         * Firebase authentication check.
         *
         * Email/password provider और phone provider
         * दोनों Firebase खाते में मौजूद होने चाहिए।
         *
         * यह जाँच केवल MainActivity के प्रवेश को रोकती है।
         * Server-side authorization और device binding
         * अलग से लागू करना आवश्यक है।
         */
        if (!hasRequiredAuthentication()) {

            Toast.makeText(
                    this,
                    "Please complete email login and mobile verification.",
                    Toast.LENGTH_LONG
            ).show();

            Intent intent =
                    new Intent(
                            MainActivity.this,
                            LoginActivity.class
                    );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_NEW_TASK
            );

            startActivity(intent);
            finish();
            return;
        }

        /*
         * Screenshot और सामान्य screen recording
         * protection.
         */
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

        refreshVideoList();
    }

    /*
     * Check Firebase account providers.
     *
     * "password" means email/password sign-in provider
     * is linked. It does NOT mean the email address
     * has been verified through an email verification link.
     *
     * "phone" means a phone provider is linked.
     */
    private boolean hasRequiredAuthentication() {

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null || !user.isEmailVerified()) {
            return false;
        }

        boolean hasEmailPassword = false;
        boolean hasPhoneProvider = false;

        for (UserInfo info : user.getProviderData()) {

            String providerId =
                    info.getProviderId();

            if ("password".equals(providerId)) {
                hasEmailPassword = true;
            }

            if ("phone".equals(providerId)) {
                hasPhoneProvider = true;
            }
        }

        return hasEmailPassword && hasPhoneProvider;
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

        root.addView(saveReceiverButton);

        Button openSecureButton =
                new Button(this);

        openSecureButton.setText(
                "OPEN SECURE CONTENT"
        );

        openSecureButton.setOnClickListener(
                v -> openSecureContent()
        );

        root.addView(openSecureButton);

        Button expiryButton =
                new Button(this);

        expiryButton.setText(
                "SET EXPIRY"
        );

        expiryButton.setOnClickListener(
                v -> showExpiryPicker()
        );

        root.addView(expiryButton);

        Button ownerSecurityButton = new Button(this);
        ownerSecurityButton.setText("OWNER DEVICE SECURITY");
        ownerSecurityButton.setOnClickListener(v -> showOwnerDeviceEnrollmentPrompt());
        root.addView(ownerSecurityButton);

        Button backupRecoveryButton = new Button(this);
        backupRecoveryButton.setText("BACKUP RECOVERY SECRET");
        backupRecoveryButton.setOnClickListener(v -> showBackupRecoveryMenu());
        root.addView(backupRecoveryButton);

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

        root.addView(expiryText);

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

        root.addView(listTitle);

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


    /** Explicit, user-confirmed first enrollment; never runs automatically. */
    private void showOwnerDeviceEnrollmentPrompt() {
        new AlertDialog.Builder(this)
                .setTitle("Owner Device Security")
                .setMessage("यह सुविधा आपके वर्तमान फोन को आपके Vishal Secure खाते का मुख्य डिवाइस बनाएगी। इसे तभी शुरू करें जब नया owner-device backend Firebase पर deploy हो चुका हो। यह प्रक्रिया पुराने वीडियो या उनकी encryption key को migrate नहीं करती। आगे बढ़ने से पहले सुनिश्चित करें कि आप अपने owner खाते में हाल ही में दोबारा sign in कर चुके हैं।")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CONTINUE", (dialog, which) -> beginOwnerDeviceEnrollment())
                .show();
    }

    private void beginOwnerDeviceEnrollment() {
        Toast.makeText(this, "Owner device enrollment शुरू हो रहा है...", Toast.LENGTH_LONG).show();
        OwnerDeviceSecurity security = new OwnerDeviceSecurity(this);
        security.beginEnrollment().addOnCompleteListener(task -> {
            if (isFinishing() || isDestroyed()) return;
            if (!task.isSuccessful() || task.getResult() == null
                    || !(task.getResult().getData() instanceof java.util.Map)) {
                String message = task.getException() != null
                        ? task.getException().getLocalizedMessage()
                        : "Server से सही challenge नहीं मिला।";
                Toast.makeText(this, "Enrollment शुरू नहीं हुआ: " + message, Toast.LENGTH_LONG).show();
                return;
            }
            try {
                java.util.Map<?, ?> challenge = (java.util.Map<?, ?>) task.getResult().getData();
                String challengeId = String.valueOf(challenge.get("challengeId"));
                String nonce = String.valueOf(challenge.get("nonce"));
                String purpose = String.valueOf(challenge.get("purpose"));
                if (challengeId.isEmpty() || nonce.isEmpty() || !"initial-enrollment".equals(purpose)) {
                    throw new IllegalStateException("Server challenge अधूरा या गलत है।");
                }
                security.completeEnrollment(challengeId, nonce, purpose)
                        .addOnCompleteListener(completion -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (completion.isSuccessful()) {
                                Toast.makeText(this,
                                        "यह फोन owner device के रूप में register हो गया है।",
                                        Toast.LENGTH_LONG).show();
                            } else {
                                String error = completion.getException() != null
                                        ? completion.getException().getLocalizedMessage()
                                        : "Server ने enrollment पूरा नहीं किया।";
                                Toast.makeText(this, "Enrollment पूरा नहीं हुआ: " + error,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
            } catch (Exception e) {
                Toast.makeText(this, "Enrollment error: " + e.getLocalizedMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Backup Recovery Secret is separate from the Owner Device Recovery Code.
     * It is kept locally under Android Keystore protection and never uploaded.
     */
    private void showBackupRecoveryMenu() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.isEmailVerified()) {
            Toast.makeText(this, "पहले अपने verified owner खाते में login करें।", Toast.LENGTH_LONG).show();
            return;
        }
        requireOwnerPasswordReauthentication(user, () -> showBackupRecoveryMenuAfterReauth(user));
    }

    private void requireOwnerPasswordReauthentication(FirebaseUser user, Runnable onSuccess) {
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            Toast.makeText(this, "Owner email उपलब्ध नहीं है। दोबारा login करें।", Toast.LENGTH_LONG).show();
            return;
        }
        EditText passwordInput = new EditText(this);
        passwordInput.setHint("Owner account password");
        passwordInput.setSingleLine(true);
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Owner verification")
                .setMessage("Backup Recovery Secret देखने या import करने से पहले अपने owner खाते का password दोबारा सत्यापित करें।")
                .setView(passwordInput)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("VERIFY", (dialog, which) -> {
                    String password = passwordInput.getText().toString();
                    if (password.isEmpty()) {
                        Toast.makeText(this, "Password दर्ज करें।", Toast.LENGTH_LONG).show();
                        return;
                    }
                    com.google.firebase.auth.AuthCredential credential =
                            com.google.firebase.auth.EmailAuthProvider.getCredential(user.getEmail(), password);
                    passwordInput.setText("");
                    user.reauthenticate(credential).addOnCompleteListener(this, task -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (task.isSuccessful()) {
                            onSuccess.run();
                        } else {
                            Toast.makeText(this, "Owner verification असफल। Password जाँचें और फिर प्रयास करें।",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .show();
    }

    private void showBackupRecoveryMenuAfterReauth(FirebaseUser user) {
        BackupRecoverySecretManager manager;
        try {
            manager = new BackupRecoverySecretManager(this, user.getUid());
        } catch (Exception e) {
            Toast.makeText(this, "Recovery सुविधा अभी उपलब्ध नहीं है।", Toast.LENGTH_LONG).show();
            return;
        }

        if (manager.hasSecret()) {
            new AlertDialog.Builder(this)
                    .setTitle("Backup Recovery Secret")
                    .setMessage("इस खाते का Secret पहले से इस फोन में सुरक्षित है। आप इसे फिर से देख सकते हैं और ऑफलाइन सुरक्षित रख सकते हैं। इसे किसी व्यक्ति को न भेजें।")
                    .setNegativeButton("CLOSE", null)
                    .setPositiveButton("SHOW SECRET", (dialog, which) -> showBackupRecoverySecret(manager, false))
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Backup Recovery Secret")
                    .setMessage("यह 32-byte Secret नए फोन पर आपके encrypted backup को restore करने के लिए होगा। इसे अभी बनाना जरूरी नहीं है। इसे कागज या सुरक्षित password manager में offline रखें। इसे खोने पर इस फोन की local copy नए फोन पर काम नहीं करेगी।")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("CREATE SECRET", (dialog, which) -> showBackupRecoverySecret(manager, true))
                    .setNeutralButton("IMPORT", (dialog, which) ->
                            requireOwnerPasswordReauthentication(user, () -> showBackupRecoveryImport(manager)))
                    .show();
        }
    }

    private void showBackupRecoverySecret(BackupRecoverySecretManager manager, boolean create) {
        try {
            String encoded = create
                    ? manager.createAndExportOnce()
                    : manager.exportExistingSecretForOwner();

            EditText secretView = new EditText(this);
            secretView.setText(encoded);
            secretView.setTextIsSelectable(true);
            secretView.setSingleLine(false);
            secretView.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            secretView.setPadding(24, 16, 24, 16);

            new AlertDialog.Builder(this)
                    .setTitle("आपका Backup Recovery Secret")
                    .setMessage("इसे offline सुरक्षित जगह पर लिखकर रखें। यह केवल आपके owner खाते के लिए है। किसी को न भेजें। स्क्रीन बंद होने पर भी आप इसी फोन से इसे फिर देख सकते हैं।")
                    .setView(secretView)
                    .setCancelable(false)
                    .setPositiveButton("मैंने सुरक्षित रख लिया", (dialog, which) ->
                            Toast.makeText(this, "Secret इस फोन में सुरक्षित है।", Toast.LENGTH_LONG).show())
                    .setNegativeButton("बाद में", (dialog, which) -> { })
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Secret उपलब्ध नहीं हुआ: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showBackupRecoveryImport(BackupRecoverySecretManager manager) {
        EditText input = new EditText(this);
        input.setHint("Paste 32-byte Backup Recovery Secret");
        input.setSingleLine(false);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        new AlertDialog.Builder(this)
                .setTitle("Import Backup Recovery Secret")
                .setMessage("यह विकल्प केवल नए फोन पर उपयोग करें। सुनिश्चित करें कि आपने उसी Firebase owner खाते में login किया है। Import के बाद Secret इस फोन के Keystore से सुरक्षित होगा।")
                .setView(input)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("IMPORT", (dialog, which) -> {
                    FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                    if (current == null || !current.isEmailVerified()
                            || !current.getUid().equals(manager.getOwnerUid())) {
                        Toast.makeText(this, "उसी verified Firebase owner खाते में login करें जिसके लिए Secret बनाया गया था।",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        manager.importSecret(input.getText().toString());
                        Toast.makeText(this, "Backup Recovery Secret इस फोन में सुरक्षित import हो गया।", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Import नहीं हुआ: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
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
     * New video:
     * Source -> temporary private file
     * -> AES-GCM encryption -> .vsec file.
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

        videoNames.add(originalName);

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

            try (
                    InputStream input =
                            getContentResolver()
                                    .openInputStream(sourceUri);

                    FileOutputStream output =
                            new FileOutputStream(temporaryFile)
            ) {

                if (input == null) {
                    return null;
                }

                byte[] buffer =
                        new byte[1024 * 1024];

                int length;

                while (
                        (length = input.read(buffer)) != -1
                ) {

                    output.write(
                            buffer,
                            0,
                            length
                    );
                }

                output.flush();
            }

            boolean encrypted =
                    CryptoManager.encryptFile(
                            temporaryFile,
                            encryptedFile
                    );

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

        input.setSingleLine(true);

        input.setHint("Video name");

        input.setText(
                videoNames.get(index)
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Video Name")
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

    private String getDisplayName(Uri uri) {

        android.database.Cursor cursor = null;

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
                    return cursor.getString(index);
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

            editor.remove("video_uri_" + i);
            editor.remove("video_name_" + i);
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

    private boolean isPrivateVideoPath(String path) {

        if (path == null) {
            return false;
        }

        String privateDirectory =
                new File(
                        getFilesDir(),
                        "secure_videos"
                ).getAbsolutePath();

        return path.startsWith(privateDirectory);
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

            empty.setTextColor(Color.GRAY);

            empty.setPadding(
                    5,
                    20,
                    5,
                    20
            );

            videoListLayout.addView(empty);

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

            name.setTextColor(Color.BLACK);

            row.addView(name);

            LinearLayout buttons =
                    new LinearLayout(this);

            buttons.setOrientation(
                    LinearLayout.HORIZONTAL
            );

            Button play =
                    new Button(this);

            play.setText("PLAY");

            play.setOnClickListener(v -> {

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
                        Uri.fromFile(videoFile).toString()
                );

                intent.putExtra(
                        "expiry_time",
                        expiryTime
                );

                intent.putExtra(
                        "encrypted_video",
                        videoFile.getName().endsWith(".vsec")
                );

                startActivity(intent);
            });

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

            delete.setText("DELETE");

            delete.setOnClickListener(v -> {

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Delete Video")
                        .setMessage(
                                "क्या आप इस वीडियो को delete करना चाहते हैं?"
                        )
                        .setPositiveButton(
                                "DELETE",
                                (dialog, which) -> {

                                    String path =
                                            videoUris.get(index);

                                    if (isPrivateVideoPath(path)) {

                                        File file =
                                                new File(path);

                                        if (file.exists()) {
                                            file.delete();
                                        }
                                    }

                                    videoUris.remove(index);
                                    videoNames.remove(index);

                                    saveVideos();

                                    refreshVideoList();
                                }
                        )
                        .setNegativeButton(
                                "CANCEL",
                                null
                        )
                        .show();
            });

            buttons.addView(
                    delete,
                    new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1
                    )
            );

            row.addView(buttons);

            videoListLayout.addView(row);
        }
    }

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
}
