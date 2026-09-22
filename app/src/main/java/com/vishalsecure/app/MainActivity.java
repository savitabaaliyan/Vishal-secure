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
