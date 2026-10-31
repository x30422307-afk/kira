package com.kira;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    Button boton(String texto) {
        Button b = new Button(this);
        b.setText(texto);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(16);
        b.setLayoutParams(p);
        return b;
    }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(0xFF0B0E14);
        l.setPadding(dp(24), dp(64), dp(24), dp(24));
        TextView t = new TextView(this);
        t.setText("Kira");
        t.setTextColor(Color.WHITE);
        t.setTextSize(32);
        l.addView(t);
        Button b1 = boton("1. Dar permiso (mostrar sobre otras apps)");
        Button b2 = boton("2. Iniciar Kira");
        Button b3 = boton("Detener Kira");
        l.addView(b1);
        l.addView(b2);
        l.addView(b3);
        setContentView(l);

        b1.setOnClickListener(v -> {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Ya tienes el permiso", Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        });

        b2.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Primero toca el botón 1", Toast.LENGTH_SHORT).show();
                return;
            }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
            startForegroundService(new Intent(this, KiraService.class));
            Toast.makeText(this, "Kira activa. Toca la cámara.", Toast.LENGTH_SHORT).show();
        });

        b3.setOnClickListener(v -> stopService(new Intent(this, KiraService.class)));
    }
}
