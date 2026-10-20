package com.vishalsecure.app;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public final class SecureDeliveryService {

    private static final String COLLECTION = "videoAssignments";

    private SecureDeliveryService() {
    }

    public static final class Assignment {
        public final String id;
        public final String ownerUid;
        public final String recipientUid;
        public final String recipientEmail;
        public final String recipientMobile;
        public final String videoId;
        public final String videoName;
        public final String storagePath;
        public final long expiresAtMillis;
        public final boolean active;

        private Assignment(DocumentSnapshot doc) {
            id = doc.getId();
            ownerUid = getString(doc, "ownerUid");
            recipientUid = getString(doc, "recipientUid");
            recipientEmail = getString(doc, "recipientEmail");
            recipientMobile = getString(doc, "recipientMobile");
            videoId = getString(doc, "videoId");
            videoName = getString(doc, "videoName");
            storagePath = getString(doc, "storagePath");

            Timestamp expiry = doc.getTimestamp("expiresAt");
            expiresAtMillis = expiry == null
                    ? 0L : expiry.toDate().getTime();

            Boolean enabled = doc.getBoolean("active");
            active = Boolean.TRUE.equals(enabled);
        }

        public boolean isExpired(long nowMillis) {
            return expiresAtMillis > 0L
                    && nowMillis >= expiresAtMillis;
        }

        private static String getString(
                DocumentSnapshot doc, String key) {
            String value = doc.getString(key);
            return value == null ? "" : value;
        }
    }

    public interface Callback {
        void onSuccess(List<Assignment> assignments);

        void onError(String message);
    }

    public static void loadMyAssignments(Callback callback) {
        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            callback.onError("पहले Firebase में लॉगिन करें।");
            return;
        }

        FirebaseFirestore.getInstance()
                .collection(COLLECTION)
                .whereEqualTo("recipientUid", user.getUid())
                .orderBy("expiresAt", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    long now = System.currentTimeMillis();
                    List<Assignment> valid = new ArrayList<>();

                    for (DocumentSnapshot doc
                            : snapshot.getDocuments()) {
                        Assignment item = new Assignment(doc);

                        if (item.active && !item.isExpired(now)) {
                            valid.add(item);
                        }
                    }

                    callback.onSuccess(valid);
                })
                .addOnFailureListener(error -> {
                    String message = error.getLocalizedMessage();

                    callback.onError(
                            "वीडियो लोड नहीं हुए: "
                            + (message == null
                            ? "अज्ञात त्रुटि" : message));
                });
    }
}
