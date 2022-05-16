package dev.eseudom.softpowerbutton;

import static android.content.Context.DEVICE_POLICY_SERVICE;
import static android.content.Context.WINDOW_SERVICE;
import static dev.eseudom.softpowerbutton.util.C.ACCESSIBILITY_SETTINGS_GUIDE;
import static dev.eseudom.softpowerbutton.util.C.ACTION_SHOW_FLOATING_GUIDE_DIALOG;
import static dev.eseudom.softpowerbutton.util.C.GENERAL_INSTRUCTIONS;
import static dev.eseudom.softpowerbutton.util.C.INTENT_EXTRA_CONFIRM_ENABLE_SERVICE;
import static dev.eseudom.softpowerbutton.util.C.INTENT_EXTRA_DIALOG_TYPE;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import dev.eseudom.softpowerbutton.listener.DoubleClickListener;
import dev.eseudom.softpowerbutton.service.FloatingWindowService;
import dev.eseudom.softpowerbutton.service.SPBAccessibilityService;
import dev.eseudom.softpowerbutton.util.U;

public class SoftPowerButtonWindow {

    private final static String TAG = SoftPowerButtonWindow.class.getSimpleName();
    private final Context mContext;
    private final View mFloatingButtonView, mFloatingDialogView, mCloseView;
    private final WindowManager.LayoutParams mButtonLayoutParams, mCloseLayoutParams, mDialogLayoutParams;
    private final WindowManager mWindowManager;
    private final DevicePolicyManager mDPM;
    private final ComponentName mSPBDeviceAdmin;
    private final AccessibilityManager mASM;
    private final ActivityManager mAM;
    private final CardView floatingCardView;
    private final ImageView mCloseIcon, mPowerIcon, mHideIcon;
    private final TextView mDialogMessageView, mDialogPosView, mDialogNegView;


    boolean inClosePos = false, inHidePos = false;
    float displayDensity;

    private final BroadcastReceiver mReceiver;

    final Handler minimizeBtnTimeHandler = new Handler();
    Runnable minimizeBtnTimeRunnable;

    public SoftPowerButtonWindow(Context context) {
        mContext = context;

        LayoutInflater layoutInflater = LayoutInflater.from(context);//(LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mFloatingButtonView = layoutInflater.inflate(R.layout.fragment_floating_power_button, null);
        mFloatingDialogView = layoutInflater.inflate(R.layout.fragment_floating_alert_dialog, null);
        mCloseView = layoutInflater.inflate(R.layout.fragment_floating_button_close, null);

        //Main widget layout params
        mButtonLayoutParams = new WindowManager.LayoutParams(
                // Shrink window to wrap content not fill the screen
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                // Overlay/Draw on top of other application windows
                overlayType(),
                // Don't let it grab the input focus
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                // Make the underlying application window visible
                // through any transparent parts
                PixelFormat.TRANSLUCENT);

        // position of window within screen
        mButtonLayoutParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;

        // x and y position values
        //mButtonLayoutParams.x = 0;
        //mButtonLayoutParams.y = 100;

        //set margins
        //mParams.horizontalMargin = 20.0f;
        //mParams.verticalMargin = 20.0f;

        //Dialog widget layout params
        mDialogLayoutParams = new WindowManager.LayoutParams(
                // Shrink window to wrap content not fill the screen
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                // Overlay/Draw on top of other application windows
                overlayType(),
                // Don't let it grab the input focus
                fullScreenFlags(),
                // Make the underlying application window visible
                // through any transparent parts
                PixelFormat.TRANSLUCENT);

        // position of window within screen
        mDialogLayoutParams.gravity = Gravity.CENTER;

        //Widget close params
        mCloseLayoutParams = new WindowManager.LayoutParams(
                // Shrink window to 100px
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                // Overlay/Draw on top of other application windows
                overlayType(),
                // Don't let it grab the input focus
                fullScreenFlags(),
                // Make the underlying application window visible
                // through any transparent parts
                PixelFormat.TRANSLUCENT);

        // position of window within screen
        mCloseLayoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        mCloseLayoutParams.y = 100;

        mWindowManager = (WindowManager) context.getSystemService(WINDOW_SERVICE);

        mDPM = (DevicePolicyManager) context.getSystemService(DEVICE_POLICY_SERVICE);
        mSPBDeviceAdmin = new ComponentName(context, FloatingWindowService.SPBDeviceAdminReceiver.class);

        mAM = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        mASM = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        //for accessibility service power menu
        U.Companion.setComponentEnabled(context, SPBAccessibilityService.class, true);
        U.Companion.enableAccessibilityService(context, U.Companion.isAccessibilityServiceRunning(context));

        floatingCardView = mFloatingButtonView.findViewById(R.id.floatingCardView);
        mPowerIcon = mFloatingButtonView.findViewById(R.id.power);

        displayDensity = context.getResources()
                .getDisplayMetrics()
                .density;

        //mCloseView = new ImageView(context);
        //mCloseView.setImageResource(R.drawable.ic_action_close);
        mCloseView.setVisibility(View.INVISIBLE);
        mCloseIcon = mCloseView.findViewById(R.id.close);
        mHideIcon = mCloseView.findViewById(R.id.hide);

        //floatingCardView.setOnClickListener(view -> mDPM.lockNow());

        floatingCardView.setOnLongClickListener(view -> {
            //mDPM.reboot(mSPBDeviceAdmin); //Doesn't work needs app to be device owner which is not replicable for
            // other users of this app accept the developer (adb stuff)
            if (U.Companion.isAccessibilityServiceRunning(context)) {
                U.Companion.sendAccessibilityPowerDialogBroadcast(context);
                return true;
            } else Toast.makeText(context, R.string.enable_accessibility_service_power_menu, Toast.LENGTH_LONG).show();//showEnableAccessibilityServiceDialog(context);
            return false;
        });

        floatingCardView.setOnClickListener(new DoubleClickListener() {
            @Override
            public void onDoubleClick(@Nullable View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (U.Companion.isAccessibilityServiceRunning(context)) {
                        hideFloatingButtonTimed(2000);
                        U.Companion.sendAccessibilityScreenshotBroadcast(context);
                    } else
                        Toast.makeText(context, R.string.enable_accessibility_service_screen_lock, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onSingleClick() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (U.Companion.isAccessibilityServiceRunning(context))
                        U.Companion.sendAccessibilityLockScreenBroadcast(context);
                    else
                        Toast.makeText(context, R.string.enable_accessibility_service_screen_lock, Toast.LENGTH_LONG).show();
                } else mDPM.lockNow();
            }
        });

        floatingCardView.setOnTouchListener(getFloatingViewTouchListener(context));

        ImageView mDialogCloseView = mFloatingDialogView.findViewById(R.id.dialogClose);
        mDialogCloseView.setOnClickListener(view -> closeDialog());
        mDialogMessageView = mFloatingDialogView.findViewById(R.id.dialogMessage);
        mDialogPosView = mFloatingDialogView.findViewById(R.id.positiveBtn);
        mDialogPosView.setOnClickListener(view -> closeDialog());
        mDialogNegView = mFloatingDialogView.findViewById(R.id.negativeBtn);
        mDialogNegView.setOnClickListener(view -> closeDialog());

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SHOW_FLOATING_GUIDE_DIALOG);

        mReceiver = getBroadcastReceiver();
        LocalBroadcastManager.getInstance(context).registerReceiver(mReceiver, filter);
    }

    private void hideFloatingButtonTimed(long delay) {
        floatingCardView.setVisibility(View.INVISIBLE);
        new Handler().postDelayed(() -> floatingCardView.setVisibility(View.VISIBLE), delay);
    }

    private void autoMinimizeButton(long delay, int mainWidth, int mainHeight,
                                    View mainView, View icon) {
        minimizeBtnTimeRunnable = () -> {
            resizeView(mainWidth, mainHeight, mainView);
            //20% reductions from original size of 30dp and margin of 10dp
            resizeAndResetMarginView(dpToPx(24), dpToPx(24), dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8), icon);
            mainView.setAlpha(0.5f); //make it faint a bit
        };
        new Handler().postDelayed(minimizeBtnTimeRunnable, delay);
    }

    private void restoreButton(int mainWidth, int mainHeight, View mainView, View icon) {
        minimizeBtnTimeHandler.removeCallbacks(minimizeBtnTimeRunnable);
        resizeView(mainWidth, mainHeight, mainView);
        //restore original size of 30dp and margin of 10dp
        resizeAndResetMarginView(dpToPx(30), dpToPx(30), dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10), icon);
        mainView.setAlpha(1.0f);
    }

    private void resizeView(int width, int height, View view) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        layoutParams.width = width;
        layoutParams.height = height;
        view.setLayoutParams(layoutParams);
    }

    private void resizeAndResetMarginView(int width, int height, int topMargin, int rightMargin, int bottomMargin,
                                          int leftMargin, View view) {
        view.getLayoutParams().width = width;
        view.getLayoutParams().height = height;
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            p.setMargins(leftMargin, topMargin, rightMargin, bottomMargin);
            view.requestLayout();
        }
    }

    private int dpToPx(int dp, Context context) {
        float density = context.getResources()
                .getDisplayMetrics()
                .density;
        return dpToPx(dp, density);
    }

    private int dpToPx(int dp) {
        return dpToPx(dp, displayDensity);
    }

    private int dpToPx(int dp, float density) {
        return Math.round((float) dp * density);
    }

    private int overlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        else return WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
    }

    private int fullScreenFlags() {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    }

    private int fullScreenFlags1() {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
    }

    private void showEnableAccessibilityServiceDialog(Context context) {
        Intent newIntent = new Intent(context, MainActivity.class);
        newIntent.putExtra(INTENT_EXTRA_CONFIRM_ENABLE_SERVICE, true);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(newIntent);
    }

    private BroadcastReceiver getBroadcastReceiver() {
        return new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    final int extra = intent.getIntExtra(INTENT_EXTRA_DIALOG_TYPE, ACCESSIBILITY_SETTINGS_GUIDE);
                    switch (action) {
                        case ACTION_SHOW_FLOATING_GUIDE_DIALOG: {
                            if (extra == ACCESSIBILITY_SETTINGS_GUIDE) {
                                mDialogMessageView.setText(R.string.accessibility_guide);
                                mDialogPosView.setText(R.string.ok);
                                mDialogNegView.setVisibility(View.GONE);
                                showDialog();
                            }
                            else if (extra == GENERAL_INSTRUCTIONS) {
                                mDialogMessageView.setText(R.string.general_guide);
                                mDialogPosView.setText(R.string.ok);
                                mDialogNegView.setVisibility(View.GONE);
                                showDialog();
                            }
                            break;
                        }
                    }
                }
            };
    }

    public void show() {
        try {
            // check if the view is already
            // inflated or present in the window
            if (mFloatingButtonView.getWindowToken() == null) {
                if (mFloatingButtonView.getParent() == null) {
                    mWindowManager.addView(mFloatingButtonView, mButtonLayoutParams);
                }
            }

            if (mCloseView.getWindowToken() == null) {
                if (mCloseView.getParent() == null) {
                    mWindowManager.addView(mCloseView, mCloseLayoutParams);
                }
            }

            //minimize UI by 20%
            autoMinimizeButton(20000, dpToPx(40), dpToPx(40), floatingCardView, mPowerIcon);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, e.getMessage());
            Toast.makeText(mContext, mContext.getString(R.string.floating_button_window_launch_error), Toast.LENGTH_LONG).show();
        }
    }

    public void showDialog() {
        try {
            if (mFloatingDialogView.getWindowToken() == null) {
                if (mFloatingDialogView.getParent() == null) {
                    mWindowManager.addView(mFloatingDialogView, mDialogLayoutParams);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, e.getMessage());
            Toast.makeText(mContext, mContext.getString(R.string.floating_dialog_window_launch_error), Toast.LENGTH_LONG).show();
        }
    }

    public void close() {
        try {

            // remove the floating view from the window
            mWindowManager.removeView(mFloatingButtonView);
            // invalidate the view
            mFloatingButtonView.invalidate();
            // remove all views
            //((ViewGroup) mView.getParent()).removeAllViews();

            //remove the widget close from the window
            mWindowManager.removeView(mCloseView);
            // invalidate the view
            mCloseView.invalidate();
            // remove all views
            //((ViewGroup) mLayoutClose.getParent()).removeAllViews();

            // above steps are necessary when adding and removing
            // views simultaneously, it might give some exceptions

            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mReceiver);

            //mDPM.removeActiveAdmin(mSPBDeviceAdmin);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, e.getMessage());
            Toast.makeText(mContext, mContext.getString(R.string.floating_button_window_close_error), Toast.LENGTH_LONG).show();
        }
    }

    public void closeDialog() {
        try {
            // remove the floating view from the window
            mWindowManager.removeView(mFloatingDialogView);
            // invalidate the view
            mFloatingDialogView.invalidate();
            // remove all views
            //((ViewGroup) mView.getParent()).removeAllViews();
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, e.getMessage());
            Toast.makeText(mContext, mContext.getString(R.string.floating_dialog_close_error), Toast.LENGTH_LONG).show();
        }
    }

    private View.OnTouchListener getFloatingViewTouchListener(Context context) {
        return new View.OnTouchListener() {

            int initialX, initialY;
            float initialTouchX, initialTouchY;
            final long delay = 1000;
            boolean downEvent = false;
            boolean isLongPressed = false;

            final Handler longPressedTimeHandler = new Handler();
            Runnable longPressedTimeRunnable;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {

                        //todo animate button for long press
                        //animateLongPressed();

                        initialX = mButtonLayoutParams.x;
                        initialY = mButtonLayoutParams.y;

                        //touch positions
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();

                        /*int[] screenLoc = new int[2];
                        mFloatingButtonView.getLocationOnScreen(screenLoc);

                        Log.e(TAG, "From ACTION_DOWN: initialX=" + initialX + " initialY=" + initialY
                                + " initialTouchX=" + initialTouchX + " initialTouchY=" + initialTouchY
                                + " eventRawX=" + event.getRawX() + " eventRawY=" + event.getRawY()
                                + " screenLocX=" + screenLoc[0] + " screenLocY=" + screenLoc[1]);*/

                        longPressedTimeRunnable = () -> {
                            if (downEvent && !inClosePos && !inHidePos
                                    && !hasMoved(initialX, initialY, mButtonLayoutParams.x, mButtonLayoutParams.y)) {
                                floatingCardView.performLongClick();
                                isLongPressed = true;
                            }
                        };
                        longPressedTimeHandler.postDelayed(longPressedTimeRunnable, delay);
                        downEvent = true;

                        //restore to original size of 50dp
                        restoreButton(dpToPx(50), dpToPx(50), floatingCardView, mPowerIcon);

                        return true;
                    }

                    case MotionEvent.ACTION_UP: {

                        downEvent = false;
                        longPressedTimeHandler.removeCallbacks(longPressedTimeRunnable); //remove long pressed timer
                        
                        if (!hasMoved(initialX, initialY, mButtonLayoutParams.x, mButtonLayoutParams.y)) {
                            if (!isLongPressed)
                                floatingCardView.performClick();
                            else isLongPressed = false;
                        } else {
                            // Note: the perfect behaviour of this algo may depend on the very first gravity/co-ords of the floating view.
                            // Test & Adjust accordingly
                            mButtonLayoutParams.x = initialX + (int) (initialTouchX - event.getRawX());
                            mButtonLayoutParams.y = initialY + (int) (event.getRawY() - initialTouchY);

                            //check if in close position
                            if (inClosePos) {
                                U.Companion.sendShutDownBroadcast(context);
                                Log.e(TAG, "Service shutdown Broadcast sent.");
                            } else if (inHidePos)
                                mFloatingButtonView.setVisibility(View.GONE);
                        }

                        //TODO animate slide down
                        mCloseView.setVisibility(View.GONE);

                        //minimize UI by 20%
                        autoMinimizeButton(15000, dpToPx(40), dpToPx(40), floatingCardView,
                                mPowerIcon);

                        return true;
                    }

                    case MotionEvent.ACTION_MOVE: {

                        //TODO animate slide up
                        mCloseView.setVisibility(View.VISIBLE);

                        //calculate X & Y coordinates of view

                        // Note: the perfect behaviour of this algo may depend on the very first gravity/co-ords of the floating view.
                        // Test & Adjust accordingly
                        mButtonLayoutParams.x = initialX + (int) (initialTouchX - event.getRawX());
                        mButtonLayoutParams.y = initialY + (int) (event.getRawY() - initialTouchY);

                        //update layout with new coordinates
                        mWindowManager.updateViewLayout(mFloatingButtonView, mButtonLayoutParams);

                        if (isWithinViewBounds(mCloseIcon, mFloatingButtonView)) {
                            inClosePos = true;
                            mCloseIcon.setImageResource(R.drawable.ic_action_close_red);
                        } else {
                            inClosePos = false;
                            mCloseIcon.setImageResource(R.drawable.ic_action_close);
                        }

                        if (isWithinViewBounds(mHideIcon, mFloatingButtonView)) {
                            inHidePos = true;
                            mFloatingButtonView.setAlpha(.2f);
                        } else {
                            inHidePos = false;
                            mFloatingButtonView.setAlpha(1.0f);
                        }

                        return true;
                    }
                }

                return false;
            }
        };
    }

    private boolean hasMoved(int initialX, int initialY, int curX, int curY) {
        return initialX - curX != 0 || initialY - curY != 0;
    }

    private boolean isWithinViewBounds(View viewA, View viewB) {
        Rect viewARect = new Rect();
        Rect viewBRect = new Rect();
        int[] aLocation = new int[2];
        int[] bLocation = new int[2];

        viewA.getDrawingRect(viewARect);
        viewB.getDrawingRect(viewBRect);

        int viewBHeight = viewBRect.bottom - viewBRect.top;
        int viewBWidth = viewBRect.right - viewBRect.left;
        int viewBDiagonal = pythagoras(viewBWidth, viewBHeight); //the hypotenuse is equiv. to the diagonal of the view
        int viewBArea = viewBWidth * viewBHeight;

        viewA.getLocationOnScreen(aLocation);
        viewB.getLocationOnScreen(bLocation);

        viewARect.offset(aLocation[0], aLocation[1]);

        /*Log.e(TAG, "CloseView locationX=" + aLocation[0] + " locationY=" + aLocation[1]
                + " InitialRect=" + outRectCache.bottom + "," + outRectCache.top + "," + outRectCache.left + "," + outRectCache.right
                + " Rect=" + viewARect.bottom + "," + viewARect.top + "," + viewARect.left + "," + viewARect.right
                + " TravellingX=" + bLocation[0] + " TravellingY=" + bLocation[1] + " Travelling View H&W=" + viewBHeight + "," + viewBWidth);*/

        //return viewARect.contains(bLocation[0], bLocation[1]);

        //pseudo code:
        //if viewARect.top + 0.6 x viewBHeight <= viewBRect.top <= viewARect.bottom - 0.4 x viewBHeight
        // and viewARect.left + 0.6 x viewBWidth <= viewBRect.left <= viewARect.right - 0.4 x viewBWidth
        // Note: the perfect behaviour of this algo may depend on the size of the floating view. Test & Adjust
        // accordingly

        return  ((bLocation[1] >= viewARect.top - (0.6 * viewBHeight) && bLocation[1] <= viewARect.bottom - (0.4 * viewBHeight))
                && (bLocation[0] >= viewARect.left - (0.6 * viewBWidth) && bLocation[0] <= viewARect.right - (0.4 * viewBWidth)));
    }

    private int pythagoras(int a, int b) {
        int aSqr = a * a;
        int bSqr = b * b;
        return (int) Math.sqrt(aSqr + bSqr);
    }
}
