package com.kira;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.*;
import java.net.*;
import org.json.*;

public class KiraAccess extends AccessibilityService {
    static final float CAM_X_FRAC = 0.07f;
    static final int CAM_Y_DP = 14;
    static final int HOLD_MS = 2000;

    WindowManager wm;
    ShapeView shape;
    View trigger;
    WindowManager.LayoutParams trigLp;
    Handler h = new Handler(Looper.getMainLooper());
    boolean busy = false;
    float camX, camY;
    int screenW;
    static KiraAccess inst;
    static float dx = 8f, dy = 3f, dw = 0f;

    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density); }

    class ShapeView extends View {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF rf = new RectF();
        float p = 0f;
        int statusH;

        ShapeView(Context c) {
            super(c);
            paint.setColor(Color.BLACK);
        }

        void setProgress(float v) { p = v; invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            float r = dp(18);
            float a = Math.min(1f, Math.max(0f, p));
            float b = Math.min(1f, Math.max(0f, p - 1f));
            float bottomFull = dp(210);
            float neckBottom = (camY + dp(12)) + (bottomFull - (camY + dp(12))) * a;
            float neckW = dp(24 + dw) + dp(8) * a;
            float neckL = camX + dp(dx) - neckW / 2f;
            rf.set(neckL, Math.max(0f, camY + dp(dy) - dp(12)), neckL + neckW, neckBottom);
            c.drawRoundRect(rf, r, r, paint);
            if (b > 0f) {
                float boxL = camX + dp(dx) - dp(16 + dw / 2f);
                float boxR = (boxL + dp(32 + dw)) + ((screenW - dp(8)) - (boxL + dp(32 + dw))) * b;
                float boxT = statusH + dp(2);
                if (neckBottom > boxT) {
                    rf.set(boxL, boxT, boxR, neckBottom);
                    c.drawRoundRect(rf, r, r, paint);
                }
            }
        }
    }

    WindowManager.LayoutParams baseParams(int w, int hgt) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, hgt,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return lp;
    }

    void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(
            new NotificationChannel("kira", "Kira", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "kira")
            .setContentTitle("Kira activa")
            .setContentText("Toca la cámara")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    void placeTrigger() {
        trigLp.x = Math.max(0, (int) camX - dp(24));
        trigLp.y = Math.max(0, (int) camY - dp(24));
    }

    void detectCutout() {
        if (Build.VERSION.SDK_INT < 28) return;
        WindowInsets wi = shape.getRootWindowInsets();
        if (wi == null) return;
        DisplayCutout dc = wi.getDisplayCutout();
        if (dc == null) return;
        for (Rect r : dc.getBoundingRects()) {
            if (r.top <= dp(6)) {
                camX = r.exactCenterX();
                camY = r.exactCenterY();
                return;
            }
        }
    }

    @Override
    public void onServiceConnected() {
        inst = this;
        SharedPreferences sp = getSharedPreferences("kira", 0);
        dx = sp.getFloat("dx", 8f);
        dy = sp.getFloat("dy", 3f);
        dw = sp.getFloat("dw", 0f);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        screenW = getResources().getDisplayMetrics().widthPixels;
        camX = screenW * CAM_X_FRAC;
        camY = dp(CAM_Y_DP);

        shape = new ShapeView(this);
        int idSb = getResources().getIdentifier("status_bar_height", "dimen", "android");
        shape.statusH = idSb > 0 ? getResources().getDimensionPixelSize(idSb) : dp(24);
        shape.setVisibility(View.INVISIBLE);
        WindowManager.LayoutParams sl =
            baseParams(WindowManager.LayoutParams.MATCH_PARENT, dp(220));
        sl.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        wm.addView(shape, sl);

        trigger = new View(this);
        trigLp = baseParams(dp(48), dp(48));
        placeTrigger();
        trigger.setOnClickListener(v -> toggle());
        wm.addView(trigger, trigLp);

        shape.post(() -> {
            detectCutout();
            placeTrigger();
            wm.updateViewLayout(trigger, trigLp);
        });
    }

    boolean abierto = false;
    boolean panelOn = false;
    LinearLayout panel;
    ScrollView sv;
    TextView chat;
    EditText campo;

    void mostrarPanel() {
        if (panelOn) return;
        if (panel == null) {
            panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            chat = new TextView(this);
            chat.setTextColor(Color.WHITE);
            chat.setTextSize(14);
            sv = new ScrollView(this);
            sv.addView(chat);
            panel.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout fila = new LinearLayout(this);
            campo = new EditText(this);
            campo.setHint("Escribe a Kira...");
            campo.setHintTextColor(0x88FFFFFF);
            campo.setTextColor(Color.WHITE);
            campo.setSingleLine(true);
            fila.addView(campo, new LinearLayout.LayoutParams(0, -2, 1f));
            Button env = new Button(this);
            env.setText("Enviar");
            env.setOnClickListener(v -> enviar());
            fila.addView(env, new LinearLayout.LayoutParams(-2, -2));
            panel.addView(fila, new LinearLayout.LayoutParams(-1, -2));
        }
        float bl = camX + dp(dx) - dp(16 + dw / 2f);
        float bt = shape.statusH + dp(2);
        int w = (int) (screenW - dp(8) - bl) - dp(16);
        int hh = (int) (dp(210) - bt) - dp(16);
        WindowManager.LayoutParams lp = baseParams(w, hh);
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        lp.x = (int) bl + dp(8);
        lp.y = (int) bt + dp(8);
        wm.addView(panel, lp);
        panelOn = true;
    }

    void ocultarPanel() {
        if (!panelOn) return;
        try { wm.removeView(panel); } catch (Exception e) { }
        panelOn = false;
    }

    JSONArray hist = new JSONArray();
    boolean esperando = false;

    String preguntar(String clave) throws Exception {
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content",
            "Eres Kira, una asistente femenina, amable y directa. Responde siempre en español, corto y claro."));
        int ini = Math.max(0, hist.length() - 12);
        for (int i = ini; i < hist.length(); i++) msgs.put(hist.get(i));
        JSONObject body = new JSONObject();
        body.put("model", "openai/gpt-oss-120b");
        body.put("messages", msgs);
        HttpURLConnection c = (HttpURLConnection) new URL(
            "https://api.groq.com/openai/v1/chat/completions").openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Authorization", "Bearer " + clave);
        c.setRequestProperty("Content-Type", "application/json");
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setDoOutput(true);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        JSONObject j = new JSONObject(bo.toString("UTF-8"));
        if (code >= 400) {
            return "Error " + code + ": " + j.getJSONObject("error").optString("message");
        }
        return j.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").trim();
    }

    void enviar() {
        String t = campo.getText().toString().trim();
        if (t.isEmpty() || esperando) return;
        campo.setText("");
        if (t.startsWith("/clave ")) {
            getSharedPreferences("kira", 0).edit()
                .putString("clave", t.substring(7).trim()).apply();
            chat.append("Kira: Clave guardada.\n\n");
            return;
        }
        final String clave = getSharedPreferences("kira", 0).getString("clave", "");
        if (clave.isEmpty()) {
            chat.append("Kira: Primero escribe /clave y luego tu clave de Groq.\n\n");
            return;
        }
        chat.append("Tu: " + t + "\n");
        try { hist.put(new JSONObject().put("role", "user").put("content", t)); }
        catch (Exception e) { }
        esperando = true;
        campo.setHint("Kira esta pensando...");
        new Thread(() -> {
            String r;
            try { r = preguntar(clave); } catch (Exception e) { r = "Error: " + e.getMessage(); }
            final String fin = r;
            h.post(() -> {
                chat.append("Kira: " + fin + "\n\n");
                try { hist.put(new JSONObject().put("role", "assistant").put("content", fin)); }
                catch (Exception e) { }
                esperando = false;
                campo.setHint("Escribe a Kira...");
                sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
            });
        }).start();
    }

    void enviarViejo() {
        String t = campo.getText().toString().trim();
        if (t.isEmpty()) return;
        chat.append("Tu: " + t + "\nKira: (aun no estoy conectada a la IA)\n\n");
        campo.setText("");
        sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
    }

    void preview(boolean on) {
        busy = on;
        h.removeCallbacksAndMessages(null);
        shape.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        shape.setProgress(on ? 2f : 0f);
    }

    void toggle() {
        if (abierto) { close(); return; }
        if (busy) return;
        busy = true;
        shape.setVisibility(View.VISIBLE);
        ValueAnimator open = ValueAnimator.ofFloat(0f, 2f);
        open.setDuration(900);
        open.setInterpolator(new AccelerateDecelerateInterpolator());
        open.addUpdateListener(an -> shape.setProgress((float) an.getAnimatedValue()));
        open.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator an) {
                abierto = true;
                busy = false;
                mostrarPanel();
            }
        });
        open.start();
    }

    void close() {
        busy = true;
        ocultarPanel();
        abierto = false;
        ValueAnimator cl = ValueAnimator.ofFloat(2f, 0f);
        cl.setDuration(300);
        cl.setInterpolator(new AccelerateInterpolator());
        cl.addUpdateListener(an -> shape.setProgress((float) an.getAnimatedValue()));
        cl.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator an) {
                shape.setVisibility(View.INVISIBLE);
                busy = false;
            }
        });
        cl.start();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) { }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        try { wm.removeView(shape); } catch (Exception e) { }
        try { wm.removeView(trigger); } catch (Exception e) { }
        super.onDestroy();
    }
}
