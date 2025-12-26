package org.pucusoft.geocelltrack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && 
            (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED) || 
             intent.getAction().equals("android.intent.action.QUICKBOOT_POWERON"))) {

            Log.d(TAG, "✅ Evento de arranque recibido. Iniciando servicio...");

            Intent serviceIntent = new Intent(context, TrackerService.class);
            
            // Para Android 8 (API 26) y superior, DEBES usar startForegroundService
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } else {
            Log.w(TAG, "⚠️ Acción desconocida recibida: " + intent.getAction());
        }
    }
}
