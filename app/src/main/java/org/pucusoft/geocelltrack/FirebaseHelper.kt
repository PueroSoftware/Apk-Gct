package org.pucusoft.geocelltrack

import android.app.Activity
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

object FirebaseHelper {
    private const val TAG = "FirebaseHelper"

    /**
     * Asegura que haya un usuario autenticado (se intenta signInAnonymously si no hay).
     * onComplete recibe el usuario autenticado o null si falla.
     */
    fun ensureSignedIn(activity: Activity, onComplete: (FirebaseUser?) -> Unit) {
        val auth: FirebaseAuth = Firebase.auth
        val current = auth.currentUser
        if (current != null) {
            onComplete(current)
            return
        }

        // Intentar signInAnonymously (útil para pruebas y para evitar PERMISSION_DENIED si reglas requieren auth)
        auth.signInAnonymously()
            .addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInAnonymously OK")
                    onComplete(auth.currentUser)
                } else {
                    Log.e(TAG, "signInAnonymously FAILED", task.exception)
                    onComplete(null)
                }
            }
    }
}

