package dev.eseudom.softpowerbutton.service;

import static android.app.Notification.EXTRA_NOTIFICATION_ID;
import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_SCREEN_ON;
import static dev.eseudom.softpowerbutton.util.C.ACTION_CLOSE_FLOATING_WIDGET;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.VolumeProviderCompat;

import dev.eseudom.softpowerbutton.R;
import dev.eseudom.softpowerbutton.SoftPowerButtonWindow;
import dev.eseudom.softpowerbutton.util.C;
import dev.eseudom.softpowerbutton.util.U;

public class FloatingWindowService extends Service {

    private static final String TAG = FloatingWindowService.class.getSimpleName();
    private static FloatingWindowService instance;
    private SoftPowerButtonWindow mSoftPowerButtonWindow;
    private BroadcastReceiver mLocalReceiver, mScreenStateReceiver;
    private MediaSessionCompat mMediaSession;
    private VolumeProviderCompat mVolumeProvider;
    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private AudioManager mAudioManager;
    private static DevicePolicyManager mDPM;
    private ComponentName mSPBDeviceAdmin;

    private boolean mIsVolumeReleased = true;
    private boolean internalDestroy = false;

    @Override
    public void onCreate() {
        instance = this;
        startForeground(C.FLOATING_BUTTON_NOTIFICATION_ID, getSoftPowerButtonNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(C.FLOATING_BUTTON_NOTIFICATION_ID, getSoftPowerButtonNotification());

        mSoftPowerButtonWindow = new SoftPowerButtonWindow(this);
        //mFloatingWindow.setCapturePermIntent(intent.getParcelableExtra(C.PERMISSION_DATA),
        //intent.getIntExtra(C.RESULT_CODE, Activity.RESULT_CANCELED));
        mSoftPowerButtonWindow.show();

        mDPM = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        mSPBDeviceAdmin = new ComponentName(this, FloatingWindowService.SPBDeviceAdminReceiver.class);

        //for accessibility service power menu
        U.Companion.setComponentEnabled(this, SPBAccessibilityService.class, true);
        U.Companion.enableAccessibilityService(this, U.Companion.isAccessibilityServiceRunning(this));

        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), TAG);
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        startMediaSessionVolumeKeyListener();

        registerLocalReceiver();

        registerScreenStateReceiver();

        return START_STICKY;
    }

    private void startMediaSessionVolumeKeyListener() {
        mMediaSession = new MediaSessionCompat(this, TAG);
        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mMediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 0) //you simulate a player which plays something.
                .build());

        int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int streamMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        //this will only work on Lollipop and up, see https://code.google.com/p/android/issues/detail?id=224134
        mVolumeProvider = new VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_RELATIVE,
                /*max volume*/streamMaxVolume, /*initial volume level*/currentVolume) {

            @Override
            public void onAdjustVolume(int direction) {
                        /* -1 -- volume down
                            1 -- volume up
                            0 -- volume button released */
                mWakeLock.acquire(1000L); /*1sec*/
                //mWakeLock.release();
                switch (direction) {
                    case -1: {
                        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
                        //if (getCurrentVolume() != 0)
                        //setCurrentVolume(getCurrentVolume() - 25);
                        Log.e(TAG, "volume down");
                        mIsVolumeReleased = false;
                        break;
                    }
                    case 1: {
                        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
                        //if (getCurrentVolume() != 100)
                        //setCurrentVolume(getCurrentVolume() + 25);
                        Log.e(TAG, "volume up");
                        mIsVolumeReleased = false;
                        break;
                    }
                    case 0: {
                        Log.e(TAG, "volume released");
                        mIsVolumeReleased = true;
                        break;
                    }
                }

                setCurrentVolume(mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
            }
        };

        mMediaSession.setPlaybackToRemote(mVolumeProvider);
        mMediaSession.setActive(true);
    }

    private void registerLocalReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_CLOSE_FLOATING_WIDGET);

        mLocalReceiver = getBroadcastReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalReceiver, filter);
    }

    private BroadcastReceiver getBroadcastReceiver() {
        return new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                switch (action) {
                    case ACTION_CLOSE_FLOATING_WIDGET: {
                        internalDestroy = true;
                        int notificationId = C.FLOATING_BUTTON_NOTIFICATION_ID;

                        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
                        notificationManager.cancel(null, notificationId);

                        stopForeground(true);
                        stopService();
                        break;
                    }
                }
            }
        };
    }

    private void registerScreenStateReceiver() {
        IntentFilter ssFilter = new IntentFilter();
        ssFilter.addAction(ACTION_SCREEN_OFF);
        ssFilter.addAction(ACTION_SCREEN_ON);

        mScreenStateReceiver = getSSBroadcastReceiver();
        registerReceiver(mScreenStateReceiver, ssFilter);
    }

    private BroadcastReceiver getSSBroadcastReceiver() {
        return new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                switch (action) {
                    case ACTION_SCREEN_OFF: {
                        //Log.e(TAG, "Screen off");
                        //acquire media commands again
                        if (mMediaSession != null) {
                            if (!mMediaSession.isActive()) {
                                mMediaSession.setActive(true);//startMediaSessionVolumeKeyListener();
                                mMediaSession.setPlaybackToRemote(mVolumeProvider);
                            }
                        } else startMediaSessionVolumeKeyListener();
                        break;
                    }

                    case ACTION_SCREEN_ON: {
                        //Log.e(TAG, "Screen on");
                        //Release media commands for system and other apps
                        mMediaSession.addOnActiveChangeListener(new MediaSessionCompat.OnActiveChangeListener() {
                            @Override
                            public void onActiveChanged() {
                                //Log.e(TAG, "Active changed.");
                            }
                        });
                        mMediaSession.setActive(false);
                        //mMediaSession.release();
                        //mMediaSession = null;
                        break;
                    }
                }
            }
        };
    }

    public static FloatingWindowService getInstance() {
        return instance;
    }

    public Notification getSoftPowerButtonNotification() {
        RemoteViews notificationLayout = new RemoteViews(getPackageName(), R.layout.fragment_notification_view_small);
        RemoteViews notificationLayoutExpanded = new RemoteViews(getPackageName(), R.layout.fragment_notification_view_large);
        notificationLayoutExpanded.setOnClickPendingIntent(R.id.notificationAction1, getSleepScreenActionPendingIntent());
        notificationLayoutExpanded.setOnClickPendingIntent(R.id.notificationAction2, getPowerMenuActionPendingIntent());
        notificationLayoutExpanded.setOnClickPendingIntent(R.id.notificationAction3, getStopAllActionPendingIntent());
        notificationLayoutExpanded.setOnClickPendingIntent(R.id.notificationAction4, getCloseActionPendingIntent());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, C.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                /*.setContentTitle(getString(R.string.spb_notification_title))
                .setContentText(getString(R.string.spb_notification_desc))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(getString(R.string.spb_notification_desc)))*/
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(notificationLayout)
                .setCustomBigContentView(notificationLayoutExpanded)
                .setContent(notificationLayoutExpanded)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                /*.addAction(R.drawable.ic_baseline_bedtime_24, getString(R.string.sleep_screen),
                        getSleepScreenActionPendingIntent())
                .addAction(R.drawable.ic_action_power, getString(R.string.power_menu),
                        getPowerMenuActionPendingIntent())
                .addAction(R.drawable.ic_baseline_stop_circle_24, getString(R.string.stop_all),
                        getStopAllActionPendingIntent())
                .addAction(R.drawable.ic_baseline_close_24, getString(R.string.close), getCloseActionPendingIntent())*/;

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = createSoftPowerButtonNotificationChannel();
            builder.setChannelId(channel.getId());
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = builder.build();
        notificationManager.notify(C.FLOATING_BUTTON_NOTIFICATION_ID, notification);

        return notification;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private NotificationChannel createSoftPowerButtonNotificationChannel() {
        CharSequence name = getString(R.string.spb_notification_channel_name);
        String description = getString(R.string.spb_notification_channel_description);
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(C.NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        //NotificationManager notificationManager = getSystemService(NotificationManager.class);
        //notificationManager.createNotificationChannel(channel);
        return channel;
    }

    private PendingIntent getCloseActionPendingIntent() {
        Intent closeIntent = new Intent(this, NotificationActionReceiver.class);
        closeIntent.setAction(C.ACTION_NOTIFICATION_CLOSE_FLOATING_WIDGET);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            closeIntent.putExtra(EXTRA_NOTIFICATION_ID, C.FLOATING_BUTTON_NOTIFICATION_ID);
        }
        closeIntent.setClass(this, NotificationActionReceiver.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.getBroadcast(this, 12, closeIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else
            return PendingIntent.getBroadcast(this, 12, closeIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getSleepScreenActionPendingIntent() {
        Intent sleepIntent = new Intent(this, NotificationActionReceiver.class);
        sleepIntent.setAction(C.ACTION_NOTIFICATION_SLEEP_DEVICE_SCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sleepIntent.putExtra(EXTRA_NOTIFICATION_ID, C.FLOATING_BUTTON_NOTIFICATION_ID);
        }
        sleepIntent.setClass(this, NotificationActionReceiver.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.getBroadcast(this, 12, sleepIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else
            return PendingIntent.getBroadcast(this, 12, sleepIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getPowerMenuActionPendingIntent() {
        Intent powerMenuIntent = new Intent(this, NotificationActionReceiver.class);
        powerMenuIntent.setAction(C.ACTION_NOTIFICATION_SHOW_GLOBAL_ACTIONS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            powerMenuIntent.putExtra(EXTRA_NOTIFICATION_ID, C.FLOATING_BUTTON_NOTIFICATION_ID);
        }
        powerMenuIntent.setClass(this, NotificationActionReceiver.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.getBroadcast(this, 12, powerMenuIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else
            return PendingIntent.getBroadcast(this, 12, powerMenuIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getStopAllActionPendingIntent() {
        Intent stopIntent = new Intent(this, NotificationActionReceiver.class);
        stopIntent.setAction(C.ACTION_NOTIFICATION_STOP_ALL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopIntent.putExtra(EXTRA_NOTIFICATION_ID, C.FLOATING_BUTTON_NOTIFICATION_ID);
        }
        stopIntent.setClass(this, NotificationActionReceiver.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.getBroadcast(this, 12, stopIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else
            return PendingIntent.getBroadcast(this, 12, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public boolean isVolumeButtonReleased() {
        return mIsVolumeReleased;
    }

    public void stopService() {
        Log.d(TAG, "Stop service called");
        mSoftPowerButtonWindow.close();
        mSoftPowerButtonWindow = null;
        if (mMediaSession != null)
            mMediaSession.release();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "onDestroy");
        if (!internalDestroy)
            U.Companion.addServiceRestarter(this);

        if (mSoftPowerButtonWindow != null)
            mSoftPowerButtonWindow.close();
        if (mMediaSession != null)
            mMediaSession.release();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalReceiver);
        unregisterReceiver(mScreenStateReceiver);
        stopForeground(true);
    }

    @Override
    public void onTaskRemoved(Intent intent) {
        Log.e(TAG, "onTaskRemoved");
        U.Companion.addServiceRestarter(this);
        super.onTaskRemoved(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static class NotificationActionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Stop action receiver started");
            String action = intent.getAction();
            Log.d(TAG, "Action: " + action);

            switch (action) {
                case C.ACTION_NOTIFICATION_CLOSE_FLOATING_WIDGET: {
                    stopWidgetService(intent, context);
                    break;
                }
                case C.ACTION_NOTIFICATION_SLEEP_DEVICE_SCREEN: {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && U.Companion.isAccessibilityServiceRunning(context)) {
                        if (U.Companion.isAccessibilityServiceRunning(context))
                            U.Companion.sendAccessibilityLockScreenBroadcast(context);
                        else
                            Toast.makeText(context, R.string.enable_accessibility_service_screen_lock, Toast.LENGTH_LONG).show();
                    } else mDPM.lockNow();
                    break;
                }
                case C.ACTION_NOTIFICATION_SHOW_GLOBAL_ACTIONS: {
                    if (U.Companion.isAccessibilityServiceRunning(context))
                        U.Companion.sendAccessibilityPowerDialogBroadcast(context);
                    break;
                }
                case C.ACTION_NOTIFICATION_STOP_ALL: {
                    U.Companion.sendStopAccessibilityServiceBroadcast(context);
                    stopWidgetService(intent, context);
                    U.Companion.disableDeviceAdmin(context);
                    break;
                }
            }
        }

        private void stopWidgetService(Intent intent, Context context) {
            int notificationId = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                notificationId = intent.getExtras().getInt(EXTRA_NOTIFICATION_ID);
            else notificationId = C.FLOATING_BUTTON_NOTIFICATION_ID;

            Log.d(TAG, "Extra Noti Id: " + notificationId);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.cancel(null, notificationId);

            FloatingWindowService floatingService = FloatingWindowService.getInstance();
            floatingService.internalDestroy = true;
            floatingService.stopForeground(true);

            floatingService.mSoftPowerButtonWindow.close();
            floatingService.stopService();

            Log.d(TAG, "Stop action receiver ended");
        }
    }

    public static class SPBDeviceAdminReceiver extends DeviceAdminReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == ACTION_DEVICE_ADMIN_DISABLE_REQUESTED) {
                abortBroadcast();
            }
            super.onReceive(context, intent);
        }

        @Override
        public void onLockTaskModeEntering(@NonNull Context context, @NonNull Intent intent, @NonNull String pkg) {
            super.onLockTaskModeEntering(context, intent, pkg);
        }

        @Override
        public void onLockTaskModeExiting(@NonNull Context context, @NonNull Intent intent) {
            super.onLockTaskModeExiting(context, intent);
        }
    }
}
