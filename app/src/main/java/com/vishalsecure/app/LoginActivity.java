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
        root.addView(email);

        password = new EditText(this);
        password.setHint("Password");
        password.setSingleLine(true);
        password.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        root.addView(password);

        Button login = new Button(this);
        login.setText("EMAIL LOGIN");
        login.setOnClickListener(v -> emailLogin());
        root.addView(login);

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

    private void emailLogin() {

        String e = email.getText().toString().trim();
        String p = password.getText().toString().trim();

        if (e.isEmpty() || p.isEmpty()) {
            Toast.makeText(this,
                    "Enter email and password",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        auth.signInWithEmailAndPassword(e, p)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                "Email login successful. Now send OTP.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this,
                                "Login failed: " +
                                task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void sendOtp() {

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this,
                    "First complete Email Login",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String number = mobile.getText().toString().trim();

        if (number.isEmpty()) {
            Toast.makeText(this,
                    "Enter mobile number",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(number)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(
                                new PhoneAuthProvider
                                        .OnVerificationStateChangedCallbacks() {

                            @Override
                            public void onVerificationCompleted(
                                    PhoneAuthCredential credential) {
                                linkPhone(credential);
                            }

                            @Override
                            public void onVerificationFailed(
                                    com.google.firebase.FirebaseException e) {
                                Toast.makeText(
                                        LoginActivity.this,
                                        "OTP failed: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onCodeSent(
                                    String id,
                                    PhoneAuthProvider.ForceResendingToken token) {
                                verificationId = id;
                                Toast.makeText(
                                        LoginActivity.this,
                                        "OTP sent",
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                        .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void verifyOtp() {

        if (verificationId == null) {
            Toast.makeText(this,
                    "First send OTP",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String code = otp.getText().toString().trim();

        if (code.length() != 6) {
            Toast.makeText(this,
                    "Enter 6 digit OTP",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        PhoneAuthCredential credential =
                PhoneAuthProvider.getCredential(
                        verificationId, code);

        linkPhone(credential);
    }

    private void linkPhone(PhoneAuthCredential credential) {

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            Toast.makeText(this,
                    "Please login with email first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        user.linkWithCredential(credential)
                .addOnCompleteListener(this, task -> {

                    if (task.isSuccessful()) {

                        Toast.makeText(this,
                                "Email + Mobile verified",
                                Toast.LENGTH_LONG).show();

                        startActivity(new Intent(
                                this, MainActivity.class));

                        finish();

                    } else {

                        Toast.makeText(this,
                                "Mobile verification failed: " +
                                task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}