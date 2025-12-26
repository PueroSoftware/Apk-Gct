package org.pucusoft.geocelltrack;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PowerActivity extends AppCompatActivity {

    private static final String TAG = "PowerActivity";
    private String userId;
    private ImageView powerButton;
    private Animation scaleDown, scaleUp;
    
    // Requisito: ExecutorService (SingleThread)
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    // Requisito: Handler con Looper principal
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Requisito: ActivityResultLauncher para permisos modernos
    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::handlePermissionsResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_power);

        // Obtener userId con prioridad: Firebase -> Intent -> Fallback
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
        } else {
            // Requisito: Clave "userId" corregida
            userId = getIntent().getStringExtra("userId");
            if (userId == null) {
                userId = "unknown_user_" + System.currentTimeMillis();
            }
        }
        Log.d(TAG, "PowerActivity iniciado con userId: " + userId);

        setupUI();
    }

    private void setupUI() {
        powerButton = findViewById(R.id.power_button);
        ImageButton exitButton = findViewById(R.id.exit_button);

        // Cargar animaciones en segundo plano usando ExecutorService
        executorService.execute(() -> {
            // Carga de recursos pesados
            final Animation animDown = AnimationUtils.loadAnimation(this, R.anim.scale_down);
            final Animation animUp = AnimationUtils.loadAnimation(this, R.anim.scale_up);
            
            // Actualizar UI en hilo principal
            mainHandler.post(() -> {
                scaleDown = animDown;
                scaleUp = animUp;
                setupAnimationListeners();
            });
        });

        powerButton.setOnClickListener(v -> checkPermissionsAndStart());
        exitButton.setOnClickListener(v -> logout());
    }

    private void checkPermissionsAndStart() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_PHONE_STATE
        };

        boolean allGranted = true;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            startPowerAnimation();
        } else {
            // Solicitar permisos si faltan
            requestPermissionsLauncher.launch(permissions);
        }
    }

    private void handlePermissionsResult(Map<String, Boolean> result) {
        boolean locationGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
        boolean phoneGranted = Boolean.TRUE.equals(result.get(Manifest.permission.READ_PHONE_STATE));

        if (locationGranted && phoneGranted) {
            startPowerAnimation();
        } else {
            Toast.makeText(this, "Se requieren permisos de Ubicación y Teléfono para funcionar.", Toast.LENGTH_LONG).show();
        }
    }

    private void setupAnimationListeners() {
        if (scaleUp == null || scaleDown == null) return;

        scaleDown.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationRepeat(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                powerButton.startAnimation(scaleUp);
            }
        });

        scaleUp.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationRepeat(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                startTrackerService();
            }
        });
    }

    private void startPowerAnimation() {
        if (scaleDown != null) {
            powerButton.startAnimation(scaleDown);
        } else {
            startTrackerService();
        }
    }

    private void startTrackerService() {
        Log.d(TAG, "Iniciando servicio y navegando...");
        Intent serviceIntent = new Intent(this, TrackerService.class);
        // Requisito: Clave "userId" exacta
        serviceIntent.putExtra("userId", userId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Intent intent = new Intent(this, RunningActivity.class);
        // Pasamos el userId también a la siguiente actividad por si acaso
        intent.putExtra("userId", userId);
        startActivity(intent);
        finish();
    }

    private void logout() {
        stopService(new Intent(this, TrackerService.class));
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Limpiar el ExecutorService
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
