package org.pucusoft.geocelltrack;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RunningActivity extends AppCompatActivity {

    private static final String TAG = "GeoCellTrack_Running";

    private TrackerService trackerService;
    private boolean isServiceBound = false;

    private ProgressBar circularProgressBar;
    private TextView timeTextView;
    private TextView backendPayloadTextView;
    private String userId;
    
    // Cliente Groq
    private GroqClient groqClient;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "✅ === SERVICIO DE RASTREO CONECTADO ===");

            TrackerService.TrackerBinder binder = (TrackerService.TrackerBinder) service;
            trackerService = binder.getService();
            isServiceBound = true;

            // Mostrar información de conexión en UI
            mostrarInformacionSistema();

            // Suscribirse a los datos del servicio
            subscribeToServiceData();

            Log.d(TAG, "📡 Servicio listo para recolección de datos");
            Log.d(TAG, "⏱️ Intervalo de envío: 30 segundos");
            Log.d(TAG, "👤 Agente ID: " + (userId != null ? userId : "No identificado"));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "⚠️ Servicio de rastreo DESCONECTADO inesperadamente");
            isServiceBound = false;
            backendPayloadTextView.append("\n\n⚠️ [SISTEMA] Servicio desconectado");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running_app);

        Log.d(TAG, "=== RunningActivity INICIALIZADA ===");
        Log.d(TAG, "Layout: activity_running_app.xml");
        
        // Inicializar cliente IA
        groqClient = new GroqClient();

        // Inicializar vistas
        circularProgressBar = findViewById(R.id.circularProgressBar);
        timeTextView = findViewById(R.id.timeTextView);
        backendPayloadTextView = findViewById(R.id.backendPayloadTextView);
        backendPayloadTextView.setMovementMethod(new ScrollingMovementMethod());

        // Configurar UI inicial
        configurarUIInicial();

        // Obtener USER_ID del Intent
        obtenerYValidarUserId();

        // Configurar botón de STOP
        configurarBotonStop();

        Log.d(TAG, "✅ RunningActivity configurada correctamente");
    }

    private void configurarUIInicial() {
        String textoInicial =
                "=== GEOCELLTRACK - SISTEMA DE LOCALIZACIÓN ===\n\n" +
                        "🎯 PROPÓSITO: Rastreo de agentes en campo\n" +
                        "📱 MÓDULO: Aplicación móvil para agentes\n\n" +
                        "🔍 MÉTODOS DE LOCALIZACIÓN ACTIVOS:\n" +
                        "  1. 📍 GPS Consentido (Tiempo Real)\n" +
                        "  2. 📡 Triangulación BTS (Múltiples celdas)\n" +
                        "  3. 🏢 Ubicación por Celda (Aproximada)\n\n" +
                        "📊 DATOS RECOLECTADOS:\n" +
                        "  • Coordenadas GPS (si disponibles)\n" +
                        "  • Datos de celdas (MCC/MNC/LAC/Cell-ID)\n" +
                        "  • Potencia de señal (dbm)\n" +
                        "  • Estado del dispositivo\n" +
                        "  • Metadatos de red\n\n" +
                        "⏱️ ENVÍO AL BACKEND: Cada 30 segundos\n" +
                        "🔥 BACKEND: Firebase Realtime Database\n\n" +
                        "========================================\n" +
                        "ESPERANDO CONEXIÓN CON SERVICIO...\n" +
                        "========================================";

        backendPayloadTextView.setText(textoInicial);
        timeTextView.setText("00:00");
        circularProgressBar.setProgress(0);
    }

    private void obtenerYValidarUserId() {
        Intent intent = getIntent();

        if (intent == null) {
            Log.e(TAG, "❌ ERROR: Intent nulo");
            mostrarError("No se recibió intent de inicio");
            return;
        }

        userId = intent.getStringExtra("userId");

        Log.d(TAG, "=== VERIFICACIÓN DE PARÁMETROS ===");
        Log.d(TAG, "Action: " + intent.getAction());
        Log.d(TAG, "Extras count: " + (intent.getExtras() != null ? intent.getExtras().size() : 0));

        // Listar todos los extras para debug
        if (intent.getExtras() != null) {
            for (String key : intent.getExtras().keySet()) {
                Object value = intent.getExtras().get(key);
                Log.d(TAG, "  🔹 " + key + " = " + value +
                        " (tipo: " + (value != null ? value.getClass().getSimpleName() : "null") + ")");
            }
        }

        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "⚠️ ADVERTENCIA: userId nulo o vacío");
            backendPayloadTextView.append("\n\n⚠️ [ADVERTENCIA] No se recibió ID de agente");
            backendPayloadTextView.append("\n⚠️ Se usará identificador del dispositivo");

            Toast.makeText(this,
                    "Iniciando con identificador del dispositivo",
                    Toast.LENGTH_LONG).show();
        } else {
            Log.d(TAG, "✅ userId válido recibido: " + userId);
            backendPayloadTextView.append("\n\n✅ [SISTEMA] Agente identificado: " + userId);

            Toast.makeText(this,
                    "Sistema activado para agente: " + userId,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void mostrarInformacionSistema() {
        String infoSistema =
                "\n\n========================================\n" +
                        "✅ SISTEMA DE RASTREO ACTIVADO\n" +
                        "========================================\n" +
                        "👤 Agente: " + (userId != null ? userId : "ID Dispositivo") + "\n" +
                        "📱 Dispositivo: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\n" +
                        "🤖 Android: " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")\n" +
                        "⏰ Hora inicio: " + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n" +
                        "========================================";

        backendPayloadTextView.append(infoSistema);
    }

    private void configurarBotonStop() {
        ImageButton stopButton = findViewById(R.id.stopButton);
        stopButton.setOnClickListener(v -> {
            Log.d(TAG, "🛑 === BOTÓN STOP PRESIONADO ===");

            // Mostrar confirmación en UI
            backendPayloadTextView.append("\n\n🛑 =================================");
            backendPayloadTextView.append("\n🛑 DETENIENDO SISTEMA DE RASTREO");
            backendPayloadTextView.append("\n🛑 =================================");
            backendPayloadTextView.append("\n⏰ Hora final: " +
                    new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));

            // Detener servicio si está conectado
            if (isServiceBound && trackerService != null) {
                Log.d(TAG, "Deteniendo TrackerService...");
                trackerService.stopTracking();
            } else {
                Log.w(TAG, "Servicio no disponible para detener");
            }

            // Preparar transición a PowerActivity
            Intent powerIntent = new Intent(RunningActivity.this, PowerActivity.class);

            // Pasar USER_ID si existe
            if (userId != null && !userId.isEmpty()) {
                powerIntent.putExtra("userId", userId);
                Log.d(TAG, "userId transferido a PowerActivity: " + userId);
            }

            // Iniciar PowerActivity
            Log.d(TAG, "Iniciando PowerActivity...");
            startActivity(powerIntent);

            // Finalizar esta actividad
            Log.d(TAG, "Finalizando RunningActivity...");
            finish();
        });

        Log.d(TAG, "✅ Botón STOP configurado");
    }

    private void subscribeToServiceData() {
        if (!isServiceBound) {
            Log.w(TAG, "⚠️ No se puede suscribir - servicio no conectado");
            backendPayloadTextView.append("\n\n⚠️ [ERROR] No se pudo conectar al servicio");
            return;
        }

        Log.d(TAG, "🔗 Suscribiéndose a datos del servicio...");

        // 1. Observar tiempo transcurrido
        trackerService.getElapsedTime().observe(this, tiempo -> {
            if (tiempo != null) {
                timeTextView.setText(tiempo);
                // Log cada minuto
                if (tiempo.endsWith(":00") || tiempo.equals("01:00")) {
                    Log.d(TAG, "⏱️ Tiempo transcurrido: " + tiempo);
                }
            }
        });

        // 2. Observar segundos para ProgressBar
        trackerService.getElapsedSeconds().observe(this, segundos -> {
            if (segundos != null) {
                circularProgressBar.setProgress(segundos);

                // Log cada 15 segundos
                if (segundos % 15 == 0) {
                    Log.d(TAG, "📊 Progreso: " + segundos + "/60 segundos");
                }
            }
        });

        // 3. Observar payload del backend (LO MÁS IMPORTANTE)
        trackerService.getLastPayload().observe(this, payload -> {
            if (payload != null && !payload.isEmpty()) {
                procesarNuevoPayload(payload);
            } else {
                Log.w(TAG, "Payload recibido nulo o vacío");
            }
        });

        Log.d(TAG, "✅ Suscripciones activas establecidas");
    }

    private void procesarNuevoPayload(String payload) {
        try {
            // 1. Procesamiento existente (parseo local)
            org.json.JSONObject jsonPayload = new org.json.JSONObject(payload);

            String metodoLocalizacion = "Desconocido";
            if (jsonPayload.has("metadata") && jsonPayload.getJSONObject("metadata").has("metodo_posible")) {
                metodoLocalizacion = jsonPayload.getJSONObject("metadata").getString("metodo_posible");
            }

            int totalCeldas = jsonPayload.has("celda_actual") ? 1 : 0;
            String coordenadas = "No disponibles";
            if (jsonPayload.has("gps")) {
                org.json.JSONObject gps = jsonPayload.getJSONObject("gps");
                if (gps.has("lat")) {
                    coordenadas = String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f",
                            gps.getDouble("lat"), gps.getDouble("lon"));
                }
            }

            // Crear resumen base
            String resumenBase = String.format(Locale.getDefault(),
                    "\n\n📦 [ENVÍO %s]\n📍 %s | 🌐 Celdas: %d | 🎯 %s",
                    new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()),
                    metodoLocalizacion, totalCeldas, coordenadas
            );

            // Actualizar UI inmediatamente con datos crudos
            actualizarUITexto(resumenBase, payload);

            // 2. INTEGRACIÓN IA GROQ (Llama 3)
            // Solo analizar si tenemos datos relevantes para no saturar
            if (groqClient != null) {
                backendPayloadTextView.append("\n⏳ Consultando IA...");
                
                groqClient.analyzePayload(payload, new GroqClient.GroqCallback() {
                    @Override
                    public void onSuccess(String analysis) {
                        runOnUiThread(() -> {
                            String aiResult = "\n🤖 [ANÁLISIS IA]: " + analysis;
                            actualizarUITexto(aiResult, payload);
                            Log.d(TAG, "✅ Análisis IA recibido: " + analysis);
                        });
                    }

                    @Override
                    public void onFailure(String error) {
                        runOnUiThread(() -> {
                            Log.e(TAG, "❌ Fallo IA: " + error);
                            // Opcional: mostrar error en UI o silenciarlo
                        });
                    }
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error procesando payload", e);
            actualizarUITexto("\n⚠️ Error leyendo datos", payload);
        }
    }

    private void actualizarUITexto(String resumen, String payloadCompleto) {
        String textoActual = backendPayloadTextView.getText().toString();

        // Limitar el historial a 10 entradas aprox
        String[] lineas = textoActual.split("\n");
        if (lineas.length > 50) {
            StringBuilder nuevoTexto = new StringBuilder();
            // Mantener encabezado y últimas entradas
            for (int i = 0; i < 15; i++) {
                if (i < lineas.length) {
                    nuevoTexto.append(lineas[i]).append("\n");
                }
            }
            nuevoTexto.append("... (historial recortado) ...\n");
            // Agregar las últimas 5 entradas
            for (int i = Math.max(0, lineas.length - 10); i < lineas.length; i++) {
                nuevoTexto.append(lineas[i]).append("\n");
            }
            nuevoTexto.append(resumen);
            backendPayloadTextView.setText(nuevoTexto.toString());
        } else {
            backendPayloadTextView.append(resumen);
        }

        // Auto-scroll al final
        backendPayloadTextView.post(() -> {
            int scrollAmount = backendPayloadTextView.getLayout() != null ?
                    backendPayloadTextView.getLayout().getLineTop(backendPayloadTextView.getLineCount()) : 0;
            if (scrollAmount > 0) {
                backendPayloadTextView.scrollTo(0, Math.max(0, scrollAmount - backendPayloadTextView.getHeight()));
            }
        });
    }

    private void mostrarError(String mensaje) {
        backendPayloadTextView.append("\n\n❌ [ERROR] " + mensaje);
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart() - Iniciando conexión con servicio");

        // Crear Intent para el servicio
        Intent serviceIntent = new Intent(this, TrackerService.class);

        // Pasar USER_ID al servicio si existe
        if (userId != null && !userId.isEmpty()) {
            serviceIntent.putExtra("userId", userId);
            Log.d(TAG, "userId enviado al servicio: " + userId);
        } else {
            Log.w(TAG, "userId no disponible para enviar al servicio");
        }

        // Intentar conectar al servicio
        try {
            boolean bound = bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            if (bound) {
                Log.d(TAG, "✅ bindService() iniciado - Esperando conexión...");
            } else {
                Log.e(TAG, "❌ bindService() retornó FALSE");
                backendPayloadTextView.append("\n\n❌ [ERROR] No se pudo iniciar el servicio");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ Error de seguridad al conectar servicio", e);
            mostrarError("Error de permisos en el servicio");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop() - Liberando conexión con servicio");

        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
            Log.d(TAG, "✅ Servicio desconectado");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "=== RunningActivity DESTRUIDA ===");

        if (isServiceBound) {
            Log.w(TAG, "⚠️ Servicio aún conectado al destruir actividad");
            unbindService(serviceConnection);
        }

        Log.d(TAG, "========================================");
        Log.d(TAG, "SESIÓN DE RASTREO FINALIZADA");
        Log.d(TAG, "Agente: " + (userId != null ? userId : "No identificado"));
        Log.d(TAG, "Duración: " + timeTextView.getText().toString());
        Log.d(TAG, "========================================");
    }
}
