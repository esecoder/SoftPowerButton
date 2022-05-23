package dev.eseudom.softpowerbutton

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import dev.eseudom.softpowerbutton.util.C.ACCESSIBILITY_SETTINGS_GUIDE
import dev.eseudom.softpowerbutton.util.C.GENERAL_INSTRUCTIONS
import dev.eseudom.softpowerbutton.util.C.INTENT_EXTRA_CONFIRM_ENABLE_SERVICE
import dev.eseudom.softpowerbutton.util.U

class MainActivity : AppCompatActivity() {

    private val tag = MainActivity::class.java.simpleName

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
        val enableServiceDialogFragment = EnableServiceDialogFragment()
        if (!enableServiceDialogFragment.isShown() || !enableServiceDialogFragment.isAdded
            || !enableServiceDialogFragment.isVisible) {

            enableServiceDialogFragment.setMessage(message)
            enableServiceDialogFragment.setListenerPos { dialog, _ ->
                startAccessibilityServiceForResult()

                U.sendShowFloatingDialogGuide(ACCESSIBILITY_SETTINGS_GUIDE, this)
                dialog.dismiss()
            }
            enableServiceDialogFragment.setListenerNeg { dialog, _ ->
                onBackPressed()
                dialog.dismiss()
            }
            enableServiceDialogFragment.show(supportFragmentManager, "AlertDialog")
            enableServiceDialogFragment.setShown(true)
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
        val overlayConfirmDialogFragment = OverlayPermissionDialogFragment()
        if (!overlayConfirmDialogFragment.isShown() || !overlayConfirmDialogFragment.isAdded
            || !overlayConfirmDialogFragment.isVisible) {

            overlayConfirmDialogFragment.setListenerPos { dialog, _ ->
                getDrawOverlaysPermission()
                dialog.dismiss()
            }
            overlayConfirmDialogFragment.setListenerNeg { dialog, _ ->
                onBackPressed()
                dialog.dismiss()
            }
            overlayConfirmDialogFragment.show(supportFragmentManager, "AlertDialog")
            overlayConfirmDialogFragment.setShown(true)
        }
    }

    private fun showXiaomiPermissionDialog() {
        val xiaomiDialogFragment = XiaomiPermissionDialogFragment()
        if (!xiaomiDialogFragment.isShown() || !xiaomiDialogFragment.isAdded
            || !xiaomiDialogFragment.isVisible) {

            xiaomiDialogFragment.setListenerPos { dialog, _ ->
                U.launchXiaomiPermissions(this)
                dialog.dismiss()
            }
            xiaomiDialogFragment.setListenerNeg { dialog, _ ->
                onBackPressed()
                dialog.dismiss()
            }
            xiaomiDialogFragment.show(supportFragmentManager, "AlertDialog")
            xiaomiDialogFragment.setShown(true)

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

    private fun startFloatingWindowService() {
        val serviceIntent = Intent(this@MainActivity, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
        else startService(serviceIntent)
    }
}