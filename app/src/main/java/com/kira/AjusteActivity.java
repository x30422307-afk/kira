package com.kira;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AjusteActivity extends Activity {
    TextView info;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    void guardar() {
        getSharedPreferences("kira", 0).edit()
            .putFloat("dx", KiraAccess.dx)
            .putFloat("dy", KiraAccess.dy)
            .putFloat("dw", KiraAccess.dw)
            .apply();
        info.setText("Derecha: " + KiraAccess.dx + "\nAbajo: " + KiraAccess.dy
            + "\nAncho: " + KiraAccess.dw);
        if (KiraAccess.inst != null) KiraAccess.inst.shape.invalidate();
    }

    void mover(float ax, float ay, float aw) {
        KiraAccess.dx += ax;
        KiraAccess.dy += ay;
        KiraAccess.dw += aw;
        guardar();
    }

    Button boton(String t, Runnable r) {
        Button b = new Button(this);
        b.setText(t);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    LinearLayout fila(Button... bs) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        for (Button b : bs) l.addView(b);
        return l;
    }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        SharedPreferences sp = getSharedPreferences("kira", 0);
        KiraAccess.dx = sp.getFloat("dx", 8f);
        KiraAccess.dy = sp.getFloat("dy", 3f);
        KiraAccess.dw = sp.getFloat("dw", 0f);

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.BOTTOM);
        l.setBackgroundColor(0xFF0B0E14);
        l.setPadding(dp(16), dp(16), dp(16), dp(24));

        info = new TextView(this);
        info.setTextColor(Color.WHITE);
        info.setTextSize(16);
        l.addView(info);
        l.addView(fila(boton("Izquierda", () -> mover(-1, 0, 0)),
                       boton("Derecha", () -> mover(1, 0, 0))));
        l.addView(fila(boton("Arriba", () -> mover(0, -1, 0)),
                       boton("Abajo", () -> mover(0, 1, 0))));
        l.addView(fila(boton("Mas angosto", () -> mover(0, 0, -1)),
                       boton("Mas ancho", () -> mover(0, 0, 1))));
        l.addView(fila(boton("Listo", () -> finish())));
        setContentView(l);
        guardar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (KiraAccess.inst != null) KiraAccess.inst.preview(true);
        else info.append("\n\nPrimero activa Kira en Accesibilidad.");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (KiraAccess.inst != null) KiraAccess.inst.preview(false);
    }
}
