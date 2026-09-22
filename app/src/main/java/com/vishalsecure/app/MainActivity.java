package com.vishalsecure.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 50, 40, 40);
        layout.setGravity(Gravity.TOP);

        TextView title = new TextView(this);
        title.setText("Vishal Secure");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(21, 101, 192));
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Secure Content Access");
        subtitle.setTextSize(18);
        subtitle.setGravity(Gravity.CENTER);
        layout.addView(subtitle);

        EditText name = new EditText(this);
        name.setHint("Receiver का नाम");
        name.setSingleLine(true);
        layout.addView(name);

        EditText mobile = new EditText(this);
        mobile.setHint("Mobile Number");
        mobile.setInputType(2);
        mobile.setSingleLine(true);
        layout.addView(mobile);

        Button openContent = new Button(this);
        openContent.setText("Open Secure Content");
        layout.addView(openContent);

        TextView expiry = new TextView(this);
        expiry.setText("Expiry: अभी निर्धारित नहीं");
        expiry.setTextSize(16);
        expiry.setPadding(0, 30, 0, 0);
        layout.addView(expiry);

        setContentView(layout);
    }
}
