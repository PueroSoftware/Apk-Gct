package org.pucusoft.geocelltrack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && 
            (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED) || 
             // SOLUCIÓN: Usar la cadena de texto literal de la acción
             intent.getAction().equals("android.intent.action.QUICKBOOT_POWERON"))) {
            
            Log.d(TAG, "✅ Evento de arranque recibido.");

            // Recuperar el último userId guardado
            SharedPreferences prefs = context.getSharedPreferences("GeoCellTrackPrefs", Context.MODE_PRIVATE);
            String userId = prefs.getString("userId", null);

            if (userId != null && !userId.isEmpty()) {
                Log.d(TAG, "Iniciando TrackerService para userId: " + userId);
                Intent serviceIntent = new Intent(context, TrackerService.class);
                serviceIntent.putExtra("userId", userId);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else {
                Log.w(TAG, "⚠️ No se encontró userId guardado. No se puede reiniciar el servicio.");
            }
        } else {
            Log.w(TAG, "⚠️ Acción desconocida recibida: " + intent.getAction());
        }
    }
}
