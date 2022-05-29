package dev.eseudom.softpowerbutton.service;

import static dev.eseudom.softpowerbutton.util.C.ACTION_ACCESSIBILITY_DISABLE_SERVICE;
import static dev.eseudom.softpowerbutton.util.C.ACTION_ACCESSIBILITY_GLOBAL_ACTIONS_DIALOG;
import static dev.eseudom.softpowerbutton.util.C.ACTION_ACCESSIBILITY_LOCK_SCREEN;
import static dev.eseudom.softpowerbutton.util.C.ACTION_ACCESSIBILITY_SCREENSHOT;
import static dev.eseudom.softpowerbutton.util.C.ACTION_ACCESSIBILITY_SERVICE_PING;
import static dev.eseudom.softpowerbutton.util.C.INTENT_EXTRA_ACTION;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import dev.eseudom.softpowerbutton.util.U;

public class SPBAccessibilityService extends AccessibilityService {

    private static final String TAG = SPBAccessibilityService.class.getSimpleName();
    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;

    public BroadcastReceiver SPBAccessibilityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Broadcast received");
            String action = intent.getAction();
            switch(action) {
                default:{
                    if(!performGlobalAction(intent.getIntExtra(INTENT_EXTRA_ACTION, -1)))
                        Toast.makeText(context, "Not supported", Toast.LENGTH_SHORT).show();
                    break;
                }
                case ACTION_ACCESSIBILITY_DISABLE_SERVICE:{
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        disableSelf();
                    } else stopSelf();
                    break;
                }
                case ACTION_ACCESSIBILITY_SERVICE_PING: {
                    //respond to ping
                    U.Companion.sendAccessibilityServicePong(context);
                    break;
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        //probably not necessary
        //initPowerManager();
        registerSPBAccessibilityReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //initPowerManager();
        registerSPBAccessibilityReceiver();

        return START_STICKY;
    }

    private void initPowerManager(){
        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), TAG);
    }

    private void registerSPBAccessibilityReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_ACCESSIBILITY_GLOBAL_ACTIONS_DIALOG);
        filter.addAction(ACTION_ACCESSIBILITY_LOCK_SCREEN);
        filter.addAction(ACTION_ACCESSIBILITY_SCREENSHOT);
        filter.addAction(ACTION_ACCESSIBILITY_DISABLE_SERVICE);

        registerReceiver(SPBAccessibilityReceiver, filter);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(SPBAccessibilityReceiver);
    }

    @Override
    public void onTaskRemoved(Intent intent) {
        super.onTaskRemoved(intent);
        U.Companion.addServiceRestarter(this);
    }

    @Override
    public void onInterrupt() {}

    /*@Override
    public boolean onKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        if (action == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                Log.d(TAG, "Volume key pressed");
                mWakeLock.acquire(60 * 1000L); //1 minute
                //mWakeLock.release();
            }
            return false;
        } else {
            return super.onKeyEvent(event);
        }
    }*/
}
