package org.pucusoft.geocelltrack;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TrackerService extends Service {

    private static final String TAG = "TrackerService";
    public static final String CHANNEL_ID = "TrackerServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    // Requisito: Intervalo de 70 segundos
    private static final long COLLECTION_INTERVAL = 70000;

    private final IBinder binder = new TrackerBinder();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    
    // Handler para el cronómetro de la UI
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private Runnable clockRunnable;
    private long startTimeMillis = 0;

    private String userId;
    private PowerManager.WakeLock wakeLock;
    private FusedLocationProviderClient fusedLocationClient;

    // LiveData para la UI
    private final MutableLiveData<String> lastPayload = new MutableLiveData<>();
    // Nuevos LiveData restaurados para RunningActivity
    private final MutableLiveData<String> elapsedTime = new MutableLiveData<>();
    private final MutableLiveData<Integer> elapsedSeconds = new MutableLiveData<>();

    public class TrackerBinder extends Binder {
        public TrackerService getService() {
            return TrackerService.this;
        }
    }

    public LiveData<String> getLastPayload() {
        return lastPayload;
    }

    // Métodos restaurados para compatibilidad
    public LiveData<String> getElapsedTime() {
        return elapsedTime;
    }

    public LiveData<Integer> getElapsedSeconds() {
        return elapsedSeconds;
    }

    public void stopTracking() {
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Servicio creado.");
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GeoCellTrack::TrackerWakelock");
            wakeLock.acquire(10 * 60 * 1000L /*10 minutes timeout*/);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GeoCellTrack Activo")
                .setContentText("Rastreando ubicación y señal...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(NOTIFICATION_ID, notification);

        if (intent != null) {
            userId = intent.getStringExtra("userId");
        }

        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "UserId nulo. Deteniendo servicio.");
            stopSelf();
            return START_NOT_STICKY;
        }

        Log.d(TAG, "Servicio iniciado para userId: " + userId);
        
        // Iniciar recolección de datos
        startDataCollection();
        
        // Iniciar cronómetro para la UI
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
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                collectAndSendData();
                timerHandler.postDelayed(this, COLLECTION_INTERVAL);
            }
        };
        timerHandler.post(timerRunnable);
    }

    @SuppressLint("MissingPermission")
    private void collectAndSendData() {
        Log.d(TAG, "Recolectando datos...");
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        Map<String, Object> dataPoint = new HashMap<>();

        dataPoint.put("sim", getSimData(tm));
        dataPoint.put("hardware", getHardwareData(tm));
        dataPoint.put("red", getNetworkData(tm));
        dataPoint.put("estado", getDeviceState());
        dataPoint.put("timestamp", System.currentTimeMillis());

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                dataPoint.put("lat", location.getLatitude());
                dataPoint.put("lon", location.getLongitude());
                dataPoint.put("acc", location.getAccuracy());
            } else {
                dataPoint.put("lat", 0);
                dataPoint.put("lon", 0);
            }
            sendFinalData(dataPoint);
        }).addOnFailureListener(e -> {
            Log.w(TAG, "Error obteniendo ubicación", e);
            dataPoint.put("lat", 0);
            dataPoint.put("lon", 0);
            sendFinalData(dataPoint);
        });
    }

    private void sendFinalData(Map<String, Object> dataPoint) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        lastPayload.postValue(gson.toJson(dataPoint));

        if (userId != null) {
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("geocelltrack").child("tracker").child(userId);
            ref.push().setValue(dataPoint)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Datos enviados a Firebase."))
                    .addOnFailureListener(e -> Log.e(TAG, "Error enviando a Firebase", e));
        }
    }

    @SuppressLint({"MissingPermission", "HardwareIds"})
    @SuppressWarnings("deprecation")
    private Map<String, Object> getHardwareData(TelephonyManager tm) {
        Map<String, Object> hardwareNode = new HashMap<>();
        
        try {
            if (hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    hardwareNode.put("imei", tm.getImei());
                } else {
                    hardwareNode.put("imei", tm.getDeviceId());
                }
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Permiso denegado para IMEI: " + e.getMessage());
            hardwareNode.put("imei", "PERMISSION_DENIED");
        } catch (Exception e) {
             hardwareNode.put("imei", "UNAVAILABLE");
        }

        hardwareNode.put("android_id", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
        
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        if (bm != null) {
            hardwareNode.put("bateria", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            hardwareNode.put("en_carga", bm.isCharging());
        }
        return hardwareNode;
    }

    @SuppressLint({"MissingPermission", "HardwareIds"})
    @SuppressWarnings("deprecation")
    private Map<String, Object> getSimData(TelephonyManager tm) {
        Map<String, Object> simNode = new HashMap<>();
        try {
            if (hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                simNode.put("imsi", tm.getSubscriberId());
                simNode.put("iccid", tm.getSimSerialNumber());
                simNode.put("msisdn", tm.getLine1Number());
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Permiso denegado para SIM: " + e.getMessage());
        }
        
        simNode.put("operador", tm.getNetworkOperatorName());
        simNode.put("pais_iso", tm.getNetworkCountryIso());
        return simNode;
    }

    @SuppressLint("MissingPermission")
    private Map<String, Object> getNetworkData(TelephonyManager tm) {
        Map<String, Object> redNode = new HashMap<>();
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return redNode;
        }

        List<CellInfo> cellInfoList = tm.getAllCellInfo();
        if (cellInfoList != null) {
            for (CellInfo cellInfo : cellInfoList) {
                if (cellInfo.isRegistered()) {
                    if (cellInfo instanceof CellInfoLte) {
                        CellInfoLte lte = (CellInfoLte) cellInfo;
                        redNode.put("tipo", "LTE");
                        redNode.put("ci", lte.getCellIdentity().getCi());
                        redNode.put("pci", lte.getCellIdentity().getPci());
                        redNode.put("tac", lte.getCellIdentity().getTac());
                        redNode.put("dbm", lte.getCellSignalStrength().getDbm());
                    } else if (cellInfo instanceof CellInfoGsm) {
                        CellInfoGsm gsm = (CellInfoGsm) cellInfo;
                        redNode.put("tipo", "GSM");
                        redNode.put("cid", gsm.getCellIdentity().getCid());
                        redNode.put("lac", gsm.getCellIdentity().getLac());
                        redNode.put("dbm", gsm.getCellSignalStrength().getDbm());
                    }
                    break; 
                }
            }
        }
        return redNode;
    }

    private Map<String, Object> getDeviceState() {
        Map<String, Object> estadoNode = new HashMap<>();
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            estadoNode.put("pantalla_encendida", powerManager.isInteractive());
            estadoNode.put("ahorro_bateria", powerManager.isPowerSaveMode());
        }
        return estadoNode;
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Servicio destruido.");
        
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        
        if (clockHandler != null && clockRunnable != null) {
            clockHandler.removeCallbacks(clockRunnable);
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "GeoCellTrack Servicio",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
