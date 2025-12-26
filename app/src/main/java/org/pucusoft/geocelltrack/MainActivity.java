package org.pucusoft.geocelltrack;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GeoCellTrack";
    private FirebaseAuth auth;
    private DatabaseReference usersRef;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        usersRef = database.getReference("users");
        firestore = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() != null) {
            navigateToPowerActivity(auth.getCurrentUser().getUid());
            return;
        }

        setupUI();
    }

    private void setupUI() {
        MaterialButton loginButton = findViewById(R.id.begin_btn);
        MaterialButton registerButton = findViewById(R.id.register_email_btn);

        loginButton.setText("Iniciar Sesión");
        loginButton.setOnClickListener(v -> showLoginDialog());
        loginButton.setVisibility(View.VISIBLE);

        registerButton.setText("Crear Cuenta");
        registerButton.setOnClickListener(v -> showRegisterDialog());
        registerButton.setVisibility(View.VISIBLE);
    }

    private void showRegisterDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_register, null);
        
        final EditText etName = view.findViewById(R.id.dialogEditTextName);
        final EditText etEmail = view.findViewById(R.id.dialogEditTextEmail);
        final EditText etPass = view.findViewById(R.id.dialogEditTextPassword);
        // Asumiendo que has añadido estos IDs en tu dialog_register.xml
        final EditText etCountry = view.findViewById(R.id.dialogEditTextCountry);
        final EditText etAreaCode = view.findViewById(R.id.dialogEditTextAreaCode);
        final EditText etPhone = view.findViewById(R.id.dialogEditTextPhone);

        new AlertDialog.Builder(this)
                .setTitle("Registro de Agente")
                .setView(view)
                .setPositiveButton("Registrar", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String email = etEmail.getText().toString().trim();
                    String pass = etPass.getText().toString().trim();
                    String country = etCountry.getText().toString().trim();
                    String areaCode = etAreaCode.getText().toString().trim();
                    String phone = etPhone.getText().toString().trim();

                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || pass.length() < 6 ||
                        TextUtils.isEmpty(country) || TextUtils.isEmpty(areaCode) || TextUtils.isEmpty(phone)) {
                        Toast.makeText(this, "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String fullPhoneNumber = "+" + areaCode + phone; // Construir número completo

                    performRegistration(name, email, pass, country, fullPhoneNumber);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void performRegistration(String name, String email, String password, String country, String phoneNumber) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        saveUserProfile(user.getUid(), email, name, country, phoneNumber);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error en registro: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void showLoginDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_login, null);

        final EditText etEmail = view.findViewById(R.id.dialogLoginEmail);
        final EditText etPass = view.findViewById(R.id.dialogLoginPassword);
        
        etEmail.setHint("Email");
        etPass.setHint("Contraseña");

        new AlertDialog.Builder(this)
                .setTitle("Iniciar Sesión")
                .setView(view)
                .setPositiveButton("Entrar", (dialog, which) -> {
                    String email = etEmail.getText().toString().trim();
                    String pass = etPass.getText().toString().trim();
                    if (TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
                        Toast.makeText(this, "Email y contraseña requeridos", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    performLogin(email, pass);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void performLogin(String email, String password) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    Log.d(TAG, "Login exitoso");
                    navigateToPowerActivity(authResult.getUser().getUid());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error de Login: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void saveUserProfile(String uid, String email, String name, String country, String phoneNumber) {
        Map<String, Object> profileData = new HashMap<>();
        profileData.put("userId", uid);
        profileData.put("email", email);
        profileData.put("name", name);
        profileData.put("country", country);
        profileData.put("phone", phoneNumber);
        profileData.put("createdAt", new Date().toString());
        profileData.put("last_login", new Date().toString());
        profileData.put("status", "active");

        // Guardar en Firestore (como fuente principal de perfiles)
        firestore.collection("profiles").document(uid).set(profileData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Perfil guardado en Firestore.");
                    // Guardar copia o referencia en Realtime Database (opcional)
                    usersRef.child(uid).setValue(profileData);
                    navigateToPowerActivity(uid);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error guardando perfil", e);
                    Toast.makeText(this, "Error al crear perfil", Toast.LENGTH_SHORT).show();
                });
    }

    private void navigateToPowerActivity(String userId) {
        Intent intent = new Intent(MainActivity.this, PowerActivity.class);
        intent.putExtra("userId", userId);
        startActivity(intent);
        finish();
    }
}
