package org.pucusoft.geocelltrack;

import android.util.Log;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class GroqClient {

    private static final String TAG = "GroqClient";
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama3-8b-8192";
    
    // Obtenemos la key de forma segura desde BuildConfig
    // Si BuildConfig no se ha generado aun, esto podria marcar error en el editor hasta compilar
    private static final String API_KEY = BuildConfig.GROQ_API_KEY;

    private final OkHttpClient client;

    public interface GroqCallback {
        void onSuccess(String analysis);
        void onFailure(String error);
    }

    public GroqClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void analyzePayload(String payloadJson, GroqCallback callback) {
        if (API_KEY == null || API_KEY.isEmpty()) {
            callback.onFailure("API Key no configurada en local.properties");
            return;
        }

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", MODEL);
            
            JSONArray messages = new JSONArray();
            
            // System Prompt optimizado para seguridad y brevedad
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "Analiza estos metadatos de rastreo móvil (señal, celdas, batería, GPS). Detecta anomalías técnicas o riesgos de seguridad. Responde muy brevemente en español (máx 20 palabras).");
            messages.put(systemMsg);
            
            // User Message
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", payloadJson);
            messages.put(userMsg);
            
            jsonBody.put("messages", messages);
            jsonBody.put("temperature", 0.5); // Temperatura baja para respuestas más directas
            jsonBody.put("max_tokens", 60);   // Limitar tokens para respuesta rápida

        } catch (Exception e) {
            callback.onFailure("Error construyendo JSON: " + e.getMessage());
            return;
        }

        RequestBody body = RequestBody.create(
                jsonBody.toString(),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error de red Groq", e);
                callback.onFailure("Error de red: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown";
                    Log.e(TAG, "Error API Groq: " + response.code() + " - " + errorBody);
                    callback.onFailure("Error API: " + response.code());
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    
                    String content = jsonResponse
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                            
                    callback.onSuccess(content);
                } catch (Exception e) {
                    Log.e(TAG, "Error parseando respuesta", e);
                    callback.onFailure("Error parseando respuesta IA");
                }
            }
        });
    }
}