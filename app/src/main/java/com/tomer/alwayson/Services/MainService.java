package com.tomer.alwayson.Services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.annotation.Nullable;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import com.tomer.alwayson.Activities.DummyBrightnessActivity;
import com.tomer.alwayson.Activities.DummyCapacitiveButtonsActivity;
import com.tomer.alwayson.Activities.DummyHomeButtonActivity;
import com.tomer.alwayson.Activities.MainActivity;
import com.tomer.alwayson.Constants;
import com.tomer.alwayson.HomeWatcher;
import com.tomer.alwayson.Prefs;
import com.tomer.alwayson.R;

import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by tomer on 6/9/16.
 */

public class MainService extends Service {

    FrameLayout frameLayout;
    TextView textView;
    LinearLayout.LayoutParams lp2;
    View mainView;
    LinearLayout iconWrapper;

    @Override
    public void onCreate() {
        super.onCreate();
        startService(new Intent(this,NotificationListener.class));
        Prefs prefs = new Prefs(getApplicationContext());
        prefs.apply();

        setBrightness(prefs.brightness/255, 0);

        WindowManager.LayoutParams lp;

        if (Build.VERSION.SDK_INT < 19) {
            lp = new WindowManager.LayoutParams(-1, -1, 2010, 65794, -2);
        } else {
            lp = new WindowManager.LayoutParams(-1, -1, 2003, 65794, -2);
        }

        lp.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        LayoutInflater layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        frameLayout = new FrameLayout(getApplicationContext());
        mainView = layoutInflater.inflate(R.layout.clock_widget, frameLayout);
        textView = (TextView) mainView.findViewById(R.id.time_tv);
        iconWrapper = (LinearLayout) mainView.findViewById(R.id.icons_wrapper);

        textView.setTextSize(72);
        lp2 = new LinearLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);

        if (!prefs.moveWidget) {lp2.gravity = Gravity.CENTER;}
        else {
            refreshLong();
            lp2.gravity = Gravity.CENTER_HORIZONTAL;
        }

        mainView.setLayoutParams(lp2);

        frameLayout.setBackgroundColor(R.color.amoledBlack);

        if (prefs.touchToStop) {

            final GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    stopSelf();
                    return true;
                }
            });

            frameLayout.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (gestureDetector.onTouchEvent(event)){
                        stopSelf();
                        return true;
                    }

                    return false;
                }
            });

        }
        if (prefs.swipeToStop) {
            frameLayout.setOnTouchListener(new OnSwipeTouchListener(getApplicationContext()) {
                public void onSwipeTop() {
                    stopSelf();
                }
            });
        }

        try {
            ((WindowManager) getSystemService("window")).addView(frameLayout, lp);
        } catch (Exception e) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }

        refresh();

        new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        WakeLock1 = ((PowerManager) getApplicationContext().getSystemService(POWER_SERVICE)).newWakeLock(268435482, "WAKEUP");
                        WakeLock1.acquire();
                    }
                },
                500);

        frameLayout.setForegroundGravity(Gravity.CENTER);

        disableButtonBacklight();
    }

    float originalBrightness = 0.7f;
    int autoBrightnessStatus;

    private void disableButtonBacklight(){
        try {
            Settings.System.putInt(getContentResolver(), "button_key_light", 0);
        }
        catch (Exception ignored){}
    }

    private void enableButtonBacklight(){
        try {
            Settings.System.putInt(getContentResolver(), "button_key_light", -1);
        }
        catch (Exception ignored){}
    }

    void setBrightness(double brightnessVal, int autoBrightnessStatusVar) {

        autoBrightnessStatus = Settings.System.getInt(getApplicationContext().getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);

        try {
            originalBrightness = Settings.System.getInt(
                    getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        Log.d("ORINGIN BRIGHTNESS = ", String.valueOf(originalBrightness));

        int brightnessInt = (int) (brightnessVal * 255);

        if (brightnessInt < 1) {
            brightnessInt = 1;
        }
        try {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE, autoBrightnessStatusVar);
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, brightnessInt);

            Intent intent = new Intent(getBaseContext(), DummyBrightnessActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("brightness value", brightnessVal);
            // getApplication().startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(MainService.this, "Please allow settings modification permission for this app!", Toast.LENGTH_SHORT).show();
        }
    }

    ArrayList<ImageView> icons = new ArrayList<>();

    void refresh() {
        iconWrapper.removeAllViews();
        for (Drawable drawable : Constants.notificationsDrawables) {
            drawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
            ImageView icon = new ImageView(getApplicationContext());
            icon.setImageDrawable(drawable);
            FrameLayout.LayoutParams iconLayoutParams = new FrameLayout.LayoutParams(64, 64, Gravity.CENTER);
            icon.setPadding(12,0,12,0);
            icon.setLayoutParams(iconLayoutParams);

            icons.add(icon);
            iconWrapper.addView(icon);
        }

        String currentDateandTime = android.text.format.DateFormat.getTimeFormat(getApplicationContext()).format(new Date());
        textView.setText(currentDateandTime);

        new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        refresh();
                    }
                },
                5000);

    }

    void refreshLong() {
        Display display = ((WindowManager) getSystemService("window")).getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int height = size.y;

        mainView.setY((float) (height / randInt(1.4, 1.4)));

        new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        refreshLong();
                    }
                },
                30000);
    }

    PowerManager.WakeLock WakeLock1;

    @Override
    public void onDestroy() {
        super.onDestroy();
        enableButtonBacklight();
        Constants.isShown = false;
        try {
            ((WindowManager) getSystemService("window")).removeView(frameLayout);
            WakeLock1.release();
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "An error has occurred", Toast.LENGTH_SHORT).show();
        }
        setBrightness(originalBrightness / 255, autoBrightnessStatus);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static double randInt(double min, double max) {
        double random = new Random().nextInt((int) ((max - min) + 1)) + min;
        Log.d("Random is ", String.valueOf(random));
        return random;
    }


    public class OnSwipeTouchListener implements View.OnTouchListener {

        private final GestureDetector gestureDetector;

        public OnSwipeTouchListener(Context ctx) {
            gestureDetector = new GestureDetector(ctx, new GestureListener());
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return gestureDetector.onTouchEvent(event);
        }

        private final class GestureListener extends GestureDetector.SimpleOnGestureListener {

            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                boolean result = false;
                try {
                    float diffY = e2.getY() - e1.getY();
                    float diffX = e2.getX() - e1.getX();
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX > 0) {
                                onSwipeRight();
                            } else {
                                onSwipeLeft();
                            }
                        }
                        result = true;
                    } else if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) {
                            onSwipeBottom();
                        } else {
                            onSwipeTop();
                        }
                    }
                    result = true;

                } catch (Exception exception) {
                    exception.printStackTrace();
                }
                return result;
            }
        }

        public void onSwipeRight() {
        }

        public void onSwipeLeft() {
        }

        public void onSwipeTop() {
        }

        public void onSwipeBottom() {
        }
    }

}
