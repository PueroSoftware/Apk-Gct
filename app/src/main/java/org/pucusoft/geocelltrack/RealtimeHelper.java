package org.pucusoft.geocelltrack;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Map;

/**
 * Helper sencillo para operaciones con Firebase Realtime Database.
 * Implementado como utilitario con métodos estáticos (fácil de llamar desde Java o Kotlin).
 */
@SuppressWarnings({"unused"})
public final class RealtimeHelper {
    private static final String TAG = "RealtimeHelper";

    private RealtimeHelper() { /* no instancias */ }

    public interface Completion {
        void onSuccess(@Nullable String pushedKey);
        void onFailure(@NonNull Exception e);
    }

    public interface ValueCallback {
        void onData(@NonNull DataSnapshot snapshot);
        void onCancelled(@NonNull DatabaseError error);
    }

    @NonNull
    public static DatabaseReference getRef(@NonNull String path) {
        return FirebaseDatabase.getInstance().getReference(path);
    }

    // Push (push() + setValue)
    public static void pushValue(@NonNull String path, @NonNull Object value, @Nullable Completion cb) {
        try {
            DatabaseReference ref = getRef(path).push();
            final String key = ref.getKey();
            ref.setValue(value)
               .addOnSuccessListener(aVoid -> {
                   if (cb != null) cb.onSuccess(key);
                   Log.d(TAG, "pushValue OK key=" + key);
               })
               .addOnFailureListener(e -> {
                   if (cb != null) cb.onFailure(e);
                   Log.e(TAG, "pushValue FAIL", e);
               });
        } catch (Exception e) {
            if (cb != null) cb.onFailure(e);
            Log.e(TAG, "pushValue exception", e);
        }
    }

    // Set directo (sobrescribe)
    public static void setValue(@NonNull String path, @NonNull Object value, @Nullable Completion cb) {
        try {
            DatabaseReference ref = getRef(path);
            ref.setValue(value)
               .addOnSuccessListener(aVoid -> {
                   if (cb != null) cb.onSuccess(null);
                   Log.d(TAG, "setValue OK path=" + path);
               })
               .addOnFailureListener(e -> {
                   if (cb != null) cb.onFailure(e);
                   Log.e(TAG, "setValue FAIL", e);
               });
        } catch (Exception e) {
            if (cb != null) cb.onFailure(e);
            Log.e(TAG, "setValue exception", e);
        }
    }

    // Update parcial con map
    public static void updateChildren(@NonNull String path, @NonNull Map<String, Object> updates, @Nullable Completion cb) {
        try {
            DatabaseReference ref = getRef(path);
            ref.updateChildren(updates)
               .addOnSuccessListener(aVoid -> {
                   if (cb != null) cb.onSuccess(null);
                   Log.d(TAG, "updateChildren OK path=" + path);
               })
               .addOnFailureListener(e -> {
                   if (cb != null) cb.onFailure(e);
                   Log.e(TAG, "updateChildren FAIL", e);
               });
        } catch (Exception e) {
            if (cb != null) cb.onFailure(e);
            Log.e(TAG, "updateChildren exception", e);
        }
    }

    // Añadir un ChildEventListener (retorna el listener para que puedas quitarlo)
    @NonNull
    public static ChildEventListener addChildListener(@NonNull String path, @NonNull final ValueCallback cb) {
        DatabaseReference ref = getRef(path);
        ChildEventListener listener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) { cb.onData(snapshot); }
            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) { cb.onData(snapshot); }
            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) { cb.onData(snapshot); }
            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) { /* opcional */ }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { cb.onCancelled(error); }
        };
        ref.addChildEventListener(listener);
        return listener;
    }

    public static void removeChildListener(@NonNull String path, @NonNull ChildEventListener listener) {
        try {
            getRef(path).removeEventListener(listener);
        } catch (Exception e) {
            Log.w(TAG, "removeChildListener", e);
        }
    }
}
