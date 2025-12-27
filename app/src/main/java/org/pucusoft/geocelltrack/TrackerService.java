package org.pucusoft.geocelltrack;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TrackerService extends Service {

    private static final String TAG = "TrackerService";
    public static final String CHANNEL_ID = "TrackerServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    
    // Intervalos dinámicos
    private static final long INTERVALO_MOVIMIENTO = 70 * 1000; // 70 segundos
    private static final long INTERVALO_QUIETO = 5 * 60 * 1000; // 5 minutos
    private long currentInterval = INTERVALO_MOVIMIENTO;

    private final IBinder binder = new TrackerBinder();
    private final Handler dataCollectionHandler = new Handler(Looper.getMainLooper());
    private Runnable dataCollectionRunnable;
    
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private Runnable clockRunnable;
    private long startTimeMillis = 0;

    private String userId;
    private PowerManager.WakeLock wakeLock;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityRecognitionClient activityRecognitionClient;
    private PendingIntent activityRecognitionPendingIntent; // SOLUCIÓN: Variable miembro para el PendingIntent

    private final MutableLiveData<String> lastPayload = new MutableLiveData<>();
    private final MutableLiveData<String> elapsedTime = new MutableLiveData<>();
    private final MutableLiveData<Integer> elapsedSeconds = new MutableLiveData<>();

    public class TrackerBinder extends Binder {
        public TrackerService getService() { return TrackerService.this; }
    }

    public LiveData<String> getLastPayload() { return lastPayload; }
    public LiveData<String> getElapsedTime() { return elapsedTime; }
    public LiveData<Integer> getElapsedSeconds() { return elapsedSeconds; }

    public void stopTracking() { stopSelf(); }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        activityRecognitionClient = ActivityRecognition.getClient(this);
        activityRecognitionPendingIntent = getPendingIntent(); // SOLUCIÓN: Crear la instancia UNA SOLA VEZ

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GeoCellTrack::TrackerWakelock");
            wakeLock.acquire(10 * 60 * 1000L);
        }
        startActivityUpdates();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("Rastreando..."));

        userId = intent.getStringExtra("userId");
        if (userId == null || userId.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startDataCollection();
        startClock();
        return START_STICKY;
    }

    private void startClock() {
        startTimeMillis = System.currentTimeMillis();
        clockRunnable = new Runnable() {
            @Override
            public void run() {
                long millis = System.currentTimeMillis() - startTimeMillis;
                int seconds = (int) (millis / 1000);
                int minutes = seconds / 60;
                int secs = seconds % 60;
                elapsedTime.postValue(String.format(Locale.getDefault(), "%02d:%02d", minutes, secs));
                elapsedSeconds.postValue(seconds);
                clockHandler.postDelayed(this, 1000);
            }
        };
        clockHandler.post(clockRunnable);
    }

    private void startDataCollection() {
        dataCollectionRunnable = () -> {
            detectActivityAndCollect();
            dataCollectionHandler.postDelayed(dataCollectionRunnable, currentInterval);
        };
        dataCollectionHandler.post(dataCollectionRunnable);
    }
    
    // --- Lógica de Actividad y Caja Negra ---

    @SuppressLint("MissingPermission")
    private void startActivityUpdates() {
        if (!hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) {
            Log.w(TAG, "No se tiene permiso de ACTIVITY_RECOGNITION");
            return;
        }
        try {
            Task<Void> task = activityRecognitionClient.requestActivityUpdates(
                    10000, // Cada 10 segundos
                    activityRecognitionPendingIntent); // SOLUCIÓN: Usar la instancia guardada

            task.addOnSuccessListener(result -> Log.d(TAG, "Detección de actividad iniciada."));
            task.addOnFailureListener(e -> Log.e(TAG, "Fallo al iniciar detección de actividad.", e));
        } catch (SecurityException e) {
            Log.e(TAG, "Error de seguridad al iniciar detección de actividad", e);
        }
    }
    
    private PendingIntent getPendingIntent() {
        Intent intent = new Intent(this, DetectedActivityReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(this, 0, intent, flags);
    }

    private void detectActivityAndCollect() {
         // Aquí la lógica de detección de actividad actualizaría la variable `currentInterval`.
         // Por simplicidad, asumimos que ya se actualizó por el BroadcastReceiver (que no hemos creado).
         // La recolección de datos sigue su curso.
        collectAndSendData();
    }
    
    @SuppressLint("MissingPermission")
    private void collectAndSendData() {
        // 1. Intentar enviar datos offline primero
        sendOfflineData();

        // 2. Recolectar datos actuales
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        Map<String, Object> dataPoint = new HashMap<>();
        dataPoint.put("timestamp", System.currentTimeMillis());
        dataPoint.put("hardware", getHardwareData(tm));
        dataPoint.put("sim", getSimData(tm));
        
        fusedLocationClient.getLastLocation().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                Location location = task.getResult();
                dataPoint.put("location", Map.of("lat", location.getLatitude(), "lon", location.getLongitude()));
            } else {
                Log.w(TAG, "No se pudo obtener la ubicación.");
                dataPoint.put("location", null);
            }
            // Se llama a sendFinalData aquí para asegurar que se envíe con o sin ubicación
            sendFinalData(dataPoint, false);
        });
    }

    private void sendFinalData(Map<String, Object> dataPoint, boolean isOffline) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("geocelltrack").child("tracker").child(userId);
        ref.push().setValue(dataPoint)
                .addOnSuccessListener(aVoid -> {
                    if (!isOffline) lastPayload.postValue(new Gson().toJson(dataPoint));
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Fallo envío a Firebase. Guardando en caja negra.");
                    saveDataOffline(dataPoint);
                });
    }

    private void saveDataOffline(Map<String, Object> dataPoint) {
        SharedPreferences prefs = getSharedPreferences("GeoCellTrackPrefs", Context.MODE_PRIVATE);
        String offlineDataJson = prefs.getString("offline_data", "[]");
        Type type = new TypeToken<ArrayList<Map<String, Object>>>(){}.getType();
        List<Map<String, Object>> offlineData = new Gson().fromJson(offlineDataJson, type);
        offlineData.add(dataPoint);
        prefs.edit().putString("offline_data", new Gson().toJson(offlineData)).apply();
    }

    private void sendOfflineData() {
        SharedPreferences prefs = getSharedPreferences("GeoCellTrackPrefs", Context.MODE_PRIVATE);
        String offlineDataJson = prefs.getString("offline_data", "[]");
        if (offlineDataJson.equals("[]")) return;

        Type type = new TypeToken<ArrayList<Map<String, Object>>>(){}.getType();
        List<Map<String, Object>> offlineData = new Gson().fromJson(offlineDataJson, type);
        
        List<Map<String, Object>> remainingData = new ArrayList<>(offlineData);
        for (Map<String, Object> dataPoint : offlineData) {
            sendFinalData(dataPoint, true);
            remainingData.remove(dataPoint);
        }

        prefs.edit().putString("offline_data", new Gson().toJson(remainingData)).apply();
    }

    // --- Métodos de recolección (getters) ---

    @SuppressLint({"MissingPermission", "HardwareIds"})
    @SuppressWarnings("deprecation")
    private Map<String, Object> getHardwareData(TelephonyManager tm) {
        Map<String, Object> node = new HashMap<>();
        if (hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            try {
                node.put("imei", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? tm.getImei() : tm.getDeviceId());
            } catch (Exception e) { /* ignore */ }
        }
        return node;
    }
    
    // ... otros getters como getSimData, etc.
    private Map<String, Object> getSimData(TelephonyManager tm) { return new HashMap<>(); }

    private Notification createNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GeoCellTrack")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Tracking Service", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (dataCollectionHandler != null) dataCollectionHandler.removeCallbacksAndMessages(null);
        if (clockHandler != null) clockHandler.removeCallbacksAndMessages(null);
        if (activityRecognitionClient != null && activityRecognitionPendingIntent != null) {
            try {
                activityRecognitionClient.removeActivityUpdates(activityRecognitionPendingIntent);
            } catch (SecurityException e) {
                Log.e(TAG, "Error de seguridad al remover la detección de actividad", e);
            }
        }
    }
}
