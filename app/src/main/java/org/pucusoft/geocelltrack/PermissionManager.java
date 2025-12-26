package org.pucusoft.geocelltrack;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionManager {

    public static final int LOCATION_AND_PHONE_PERMISSION_REQUEST_CODE = 101;

    // Actualizamos la lista de permisos para incluir la lectura del número de teléfono
    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            "android.permission.READ_PHONE_NUMBERS"
    };

    public static boolean hasRequiredPermissions(Context context) {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static void requestRequiredPermissions(Activity activity) {
        ActivityCompat.requestPermissions(
                activity,
                REQUIRED_PERMISSIONS,
                LOCATION_AND_PHONE_PERMISSION_REQUEST_CODE
        );
    }
}
