package org.pucusoft.geocelltrack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

public class DetectedActivityReceiver extends BroadcastReceiver {

    private static final String TAG = "ActivityReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ActivityRecognitionResult.hasResult(intent)) {
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            if (result != null) {
                DetectedActivity mostProbableActivity = result.getMostProbableActivity();
                
                // Aquí puedes enviar un broadcast local al servicio para que cambie el intervalo
                // Por ahora, solo lo mostraremos en el log
                Log.d(TAG, "Actividad Detectada: " + getActivityString(mostProbableActivity.getType()) + 
                             " (" + mostProbableActivity.getConfidence() + "%)");
            }
        }
    }

    private String getActivityString(int detectedActivityType) {
        switch (detectedActivityType) {
            case DetectedActivity.IN_VEHICLE: return "IN_VEHICLE";
            case DetectedActivity.ON_BICYCLE: return "ON_BICYCLE";
            case DetectedActivity.RUNNING: return "RUNNING";
            case DetectedActivity.STILL: return "STILL";
            case DetectedActivity.WALKING: return "WALKING";
            default: return "UNKNOWN";
        }
    }
}
