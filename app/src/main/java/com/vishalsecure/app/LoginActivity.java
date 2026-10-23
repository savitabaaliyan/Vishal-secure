package com.vishalsecure.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class LoginActivity extends Activity {
    private FirebaseAuth auth;
    private EditText email, password, mobile, otp;
    private String verificationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 30, 30, 30);

        TextView title = new TextView(this);
        title.setText("VISHAL SECURE");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 20, 0, 30);
        root.addView(title);

        email = new EditText(this);
        email.setHint("Email");
        email.setSingleLine(true);
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        root.addView(email);

        password = new EditText(this);
        password.setHint("Password (at least 6 characters)");
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(password);

        Button register = new Button(this);
        register.setText("CREATE NEW ACCOUNT");
        register.setOnClickListener(v -> createAccount());
        root.addView(register);

        Button login = new Button(this);
        login.setText("EMAIL LOGIN");
        login.setOnClickListener(v -> emailLogin());
        root.addView(login);

        Button checkEmail = new Button(this);
        checkEmail.setText("I VERIFIED EMAIL / RESEND EMAIL");
        checkEmail.setOnClickListener(v -> checkOrResendEmail());
        root.addView(checkEmail);

        mobile = new EditText(this);
        mobile.setHint("Mobile (+91XXXXXXXXXX)");
        mobile.setSingleLine(true);
        mobile.setInputType(InputType.TYPE_CLASS_PHONE);
        root.addView(mobile);

        Button send = new Button(this);
        send.setText("SEND OTP");
        send.setOnClickListener(v -> sendOtp());
        root.addView(send);

        otp = new EditText(this);
        otp.setHint("Enter 6 digit OTP");
        otp.setSingleLine(true);
        otp.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(otp);

        Button verify = new Button(this);
        verify.setText("VERIFY OTP");
        verify.setOnClickListener(v -> verifyOtp());
        root.addView(verify);

        setContentView(root);
    }

    private String emailValue() {
        return email.getText().toString().trim();
    }

    private String passwordValue() {
        return password.getText().toString();
    }

    private void createAccount() {
        String e = emailValue();
        String p = passwordValue();
        if (e.isEmpty() || p.length() < 6) {
            toast("Enter a valid email and a password of at least 6 characters");
            return;
        }

        auth.createUserWithEmailAndPassword(e, p)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        toast("Account creation failed: " + errorMessage(task.getException()));
                        return;
                    }
                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        toast("Account created, but sign-in state is unavailable. Please log in.");
                        return;
                    }
                    user.sendEmailVerification().addOnCompleteListener(this, mailTask -> {
                        if (mailTask.isSuccessful()) {
                            toast("Verification email sent. Open your email link, then return here and tap I VERIFIED EMAIL / RESEND EMAIL.");
                        } else {
                            toast("Account created, but email could not be sent: " + errorMessage(mailTask.getException()));
                        }
                    });
                });
    }

    private void emailLogin() {
        String e = emailValue();
        String p = passwordValue();
        if (e.isEmpty() || p.isEmpty()) {
            toast("Enter email and password");
            return;
        }

        auth.signInWithEmailAndPassword(e, p)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        toast("Login failed: " + errorMessage(task.getException()));
                        return;
                    }
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null && user.isEmailVerified()) {
                        toast("Email verified. Now verify your mobile with OTP.");
                    } else {
                        toast("Please verify your email first. Open the email link, then tap I VERIFIED EMAIL / RESEND EMAIL.");
                    }
                });
    }

    private void checkOrResendEmail() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            toast("Create an account or log in first");
            return;
        }

        user.reload().addOnCompleteListener(this, reloadTask -> {
            FirebaseUser refreshed = auth.getCurrentUser();
            if (!reloadTask.isSuccessful() || refreshed == null) {
                toast("Could not refresh account status. Please try again.");
                return;
            }
            if (refreshed.isEmailVerified()) {
                toast("Email verified. You can now send mobile OTP.");
            } else {
                refreshed.sendEmailVerification().addOnCompleteListener(this, sendTask -> {
                    if (sendTask.isSuccessful()) {
                        toast("Email is not verified yet. A verification email has been sent again.");
                    } else {
                        toast("Could not send verification email: " + errorMessage(sendTask.getException()));
                    }
                });
            }
        });
    }

    private void sendOtp() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            toast("Create an account or log in first");
            return;
        }
        if (!user.isEmailVerified()) {
            toast("Verify your email before requesting mobile OTP");
            return;
        }

        String number = mobile.getText().toString().trim();
        if (number.isEmpty() || !number.startsWith("+") || number.length() < 8) {
            toast("Enter mobile with country code, for example +91XXXXXXXXXX");
            return;
        }

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(number)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        linkPhone(credential);
                    }

                    @Override
                    public void onVerificationFailed(FirebaseException e) {
                        toast("OTP failed: " + errorMessage(e));
                    }

                    @Override
                    public void onCodeSent(String id, PhoneAuthProvider.ForceResendingToken token) {
                        verificationId = id;
                        toast("OTP sent. Enter the code received on your mobile.");
                    }
                })
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void verifyOtp() {
        if (verificationId == null) {
            toast("Tap SEND OTP first");
            return;
        }
        String code = otp.getText().toString().trim();
        if (code.length() != 6) {
            toast("Enter the 6 digit OTP");
            return;
        }
        linkPhone(PhoneAuthProvider.getCredential(verificationId, code));
    }

    private void linkPhone(PhoneAuthCredential credential) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            toast("Please log in with email first");
            return;
        }
        if (!user.isEmailVerified()) {
            toast("Verify your email first");
            return;
        }

        user.linkWithCredential(credential).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                toast("Mobile verification/link failed: " + errorMessage(task.getException()) +
                        ". If this number belongs to another account, do not create or merge accounts automatically.");
                return;
            }
            FirebaseUser linked = auth.getCurrentUser();
            if (linked == null || !linked.isEmailVerified()) {
                toast("Verification state could not be confirmed. Please log in again.");
                return;
            }
            toast("Email and mobile linked successfully.");
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private String errorMessage(Exception e) {
        return e == null || e.getMessage() == null ? "Unknown error" : e.getMessage();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}