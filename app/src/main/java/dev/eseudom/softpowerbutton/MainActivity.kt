package dev.eseudom.softpowerbutton

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import dev.eseudom.softpowerbutton.dialog.EnableServiceDialogFragment
import dev.eseudom.softpowerbutton.dialog.OverlayPermissionDialogFragment
import dev.eseudom.softpowerbutton.dialog.XiaomiPermissionDialogFragment
import dev.eseudom.softpowerbutton.service.FloatingWindowService
import dev.eseudom.softpowerbutton.util.C
import dev.eseudom.softpowerbutton.util.C.ACCESSIBILITY_SETTINGS_GUIDE
import dev.eseudom.softpowerbutton.util.C.GENERAL_INSTRUCTIONS
import dev.eseudom.softpowerbutton.util.C.INTENT_EXTRA_CONFIRM_ENABLE_SERVICE
import dev.eseudom.softpowerbutton.util.U

class MainActivity : AppCompatActivity() {

    private val tag = MainActivity::class.java.simpleName

    private val mOverlayConfirmDialogFragment = OverlayPermissionDialogFragment()
    private val mEnableServiceDialogFragment = EnableServiceDialogFragment()
    private val mXiaomiDialogFragment = XiaomiPermissionDialogFragment()
    private var mServiceConnection: ServiceConnection? = null
    private var mFloatingWindowService: FloatingWindowService? = null

    private lateinit var mDPM: DevicePolicyManager
    private lateinit var mSPBDeviceAdmin: ComponentName

    private var overlaySettingsResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            //val data: Intent? = result.data
            startFloatingButton()
            //onBackPressed()
        } else //todo show interactive dialogs instead
            Toast.makeText(this, getString(R.string.overlay_permission_rejected), Toast.LENGTH_LONG).show()
    }

    private var deviceAdminResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            //onBackPressed() // this causes a crash for some reason. After device admin is enabled, another screen is shown to warn
            //users of this permission which is why obBackPressed shouldn't be invoked immediately here.
        } else //todo show interactive dialogs instead
            Toast.makeText(this, getString(R.string.device_admin_permission_rejected_warning), Toast.LENGTH_LONG).show()
    }

    private var accessibilityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            //onBackPressed()
        } //else //todo show interactive dialogs instead
            //Toast.makeText(this, getString(R.string.accessibility_not_enabled_warning), Toast.LENGTH_LONG).show()
    }

    private var xiaomiSettingsResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mDPM = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        mSPBDeviceAdmin = ComponentName(this, FloatingWindowService.SPBDeviceAdminReceiver::class.java)
    }

    override fun onResume() {
        super.onResume()

        //todo check system internal memory. if low, warn users because services will be axed anytime anyhow

        val confirmEnableServiceIntent = intent.getBooleanExtra(INTENT_EXTRA_CONFIRM_ENABLE_SERVICE, false)
        if (!confirmEnableServiceIntent) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                checkAndRequestOverlay()

                if (Settings.canDrawOverlays(this))
                    checkForAccessibility()
            } else {
                startFloatingButton()

                checkForAccessibility()

                if (!isActiveAdmin())
                    startDeviceAdminForResult()
            }

        } else {
            Log.e(tag, "showing enable service dialog")
            showEnableAccessibilityDialog(getString(R.string.accessibility_service_not_enabled))
        }

        checkToExitActivity()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.e(tag, "New Intent")
        setIntent(intent)
        onResume()
    }

    private fun checkToExitActivity() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) { //less then android P devices

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //greater then android M devices
                if (Settings.canDrawOverlays(this) && isActiveAdmin() && U.isAccessibilityServiceRunning(this)) {
                    finalActions()
                }
            } else { //less than android M devices
                if (isActiveAdmin() && U.isAccessibilityServiceRunning(this)) {
                    finalActions()
                }
            }
        } else { //greater then android P devices
            if (Settings.canDrawOverlays(this) && U.isAccessibilityServiceRunning(this)) {
                finalActions()
            }
        }
    }

    private fun finalActions() {
        if (U.isXiaomiAPI28Above()) {
            if (!U.getXiaomiPermissionRequestStatus(this))
                showXiaomiPermissionDialog()
            else {
                onBackPressed()
                checkToShowGeneralGuide()
            }
        } else {
            onBackPressed()
            checkToShowGeneralGuide()
        }
    }

    private fun checkForAccessibility() {
        if (!U.isAccessibilityServiceRunning(this))
            showEnableAccessibilityDialog(getString(R.string.accessibility_service_instr))
    }

    private fun checkToShowGeneralGuide() {
        //User clicking app icon again to get more info. show instructions for uninstall and usage of the app
        if (isFloatingServiceRunning())
            U.sendShowFloatingDialogGuide(GENERAL_INSTRUCTIONS, this)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getDrawOverlaysPermission() {
        // send user to the device settings
        //val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        //startActivity(intent)
        startOverlaySettingsForResult()
    }

    /**
     * Helper to determine if we are an active admin
     */
    private fun isActiveAdmin(): Boolean {
        return mDPM.isAdminActive(mSPBDeviceAdmin)
    }

    private fun startDeviceAdminForResult() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mSPBDeviceAdmin)
        intent.putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            getString(R.string.add_admin_extra_app_explanation)
        )
        deviceAdminResultLauncher.launch(intent)
    }

    private fun startAccessibilityServiceForResult() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        accessibilityResultLauncher.launch(intent)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun startOverlaySettingsForResult() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        overlaySettingsResultLauncher.launch(intent)
    }

    private fun showEnableAccessibilityDialog(message: String) {
        if (!mEnableServiceDialogFragment.isShown() || !mEnableServiceDialogFragment.isAdded
            || !mEnableServiceDialogFragment.isVisible) {

            mEnableServiceDialogFragment.setMessage(message)
            mEnableServiceDialogFragment.setListenerPos { _, _ ->
                startAccessibilityServiceForResult()

                U.sendShowFloatingDialogGuide(ACCESSIBILITY_SETTINGS_GUIDE, this)
            }
            mEnableServiceDialogFragment.setListenerNeg { _, _ ->
                onBackPressed()
            }
            mEnableServiceDialogFragment.show(supportFragmentManager, "AlertDialog")
            mEnableServiceDialogFragment.setShown(true)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkAndRequestOverlay() {
        // Is Draw over other apps permission granted?
        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog()
        } else {
            startFloatingButton()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                if (!isActiveAdmin())
                    startDeviceAdminForResult()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun showOverlayPermissionDialog() {
        if (!mOverlayConfirmDialogFragment.isShown() || !mOverlayConfirmDialogFragment.isAdded
            || !mOverlayConfirmDialogFragment.isVisible) {

            mOverlayConfirmDialogFragment.setListenerPos { _, _ ->
                getDrawOverlaysPermission()
            }
            mOverlayConfirmDialogFragment.setListenerNeg { _, _ ->
                onBackPressed()
            }
            mOverlayConfirmDialogFragment.show(supportFragmentManager, "AlertDialog")
            mOverlayConfirmDialogFragment.setShown(true)
        }
    }

    private fun showXiaomiPermissionDialog() {
        if (!mXiaomiDialogFragment.isShown() || !mXiaomiDialogFragment.isAdded
            || !mXiaomiDialogFragment.isVisible) {

            mXiaomiDialogFragment.setListenerPos { _, _ ->
                U.launchXiaomiPermissions(this)
            }
            mXiaomiDialogFragment.setListenerNeg { _, _ ->
                onBackPressed()
            }
            mXiaomiDialogFragment.show(supportFragmentManager, "AlertDialog")
            mXiaomiDialogFragment.setShown(true)

            U.saveXiaomiPermissionRequestStatus(true, this)
        }
    }

    private fun startFloatingButton() {
        if (!isFloatingServiceRunning())
            startFloatingWindowService()
    }

    private fun isFloatingServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == FloatingWindowService::class.java.name }
    }

    //todo start service normally without binder
    private fun startFloatingWindowService() {
        val serviceIntent = Intent(this@MainActivity, FloatingWindowService::class.java)
        //Service connection to bind the service to this context because of startForegroundService issues
        mServiceConnection = object : ServiceConnection {

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                Log.d(tag, "Service connected")
                val binder: FloatingWindowService.ServiceBinder =
                    service as FloatingWindowService.ServiceBinder
                mFloatingWindowService = binder.service
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(
                    serviceIntent
                )
                else startService(serviceIntent)

                mFloatingWindowService?.startForeground(
                    C.FLOATING_BUTTON_NOTIFICATION_ID,
                    mFloatingWindowService?.softPowerButtonNotification
                )

                //build media object (alternative architecture)
                //val mediaData = MediaData(currentVideoURL, currentAudioURL, currentMediaPos, currentMediaTitle)
                //mFloatingWindowService.startFloatingPlayer(mediaData)
            }

            override fun onBindingDied(name: ComponentName) {
                Log.w(tag, "Binding has died.")
            }

            override fun onNullBinding(name: ComponentName) {
                Log.w(tag, "Binding was null.")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Log.w(tag, "Service is disconnected..")
            }
        }
        try {
            mServiceConnection?.let { bindService(serviceIntent, it, BIND_AUTO_CREATE) }
        } catch (re: RuntimeException) {
            re.printStackTrace()
            //Use the normal way and accept it will fail sometimes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
        }
    }
}