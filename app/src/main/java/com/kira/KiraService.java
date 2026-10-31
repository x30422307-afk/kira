package com.kira;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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

public class KiraService extends Service {
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
            float neckW = dp(26) + dp(10) * a;
            float neckL = camX - neckW / 2f;
            rf.set(neckL, 0, neckL + neckW, neckBottom);
            c.drawRoundRect(rf, r, r, paint);
            if (b > 0f) {
                float boxL = Math.max(dp(4), camX - dp(18));
                float boxR = (boxL + dp(36)) + ((screenW - dp(8)) - (boxL + dp(36))) * b;
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
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
    public void onCreate() {
        super.onCreate();
        startAsForeground();
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

    void toggle() {
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
                h.postDelayed(KiraService.this::close, HOLD_MS);
            }
        });
        open.start();
    }

    void close() {
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
    public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        try { wm.removeView(shape); } catch (Exception e) { }
        try { wm.removeView(trigger); } catch (Exception e) { }
        super.onDestroy();
    }
}
