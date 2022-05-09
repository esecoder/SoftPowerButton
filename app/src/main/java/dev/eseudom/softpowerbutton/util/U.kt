package dev.eseudom.softpowerbutton.util

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dev.eseudom.softpowerbutton.R
import dev.eseudom.softpowerbutton.service.FloatingWindowService
import dev.eseudom.softpowerbutton.service.SPBAccessibilityService
import dev.eseudom.softpowerbutton.util.C.ACTION_ACCESSIBILITY_GLOBAL_ACTIONS_DIALOG
import dev.eseudom.softpowerbutton.util.C.ACTION_ACCESSIBILITY_LOCK_SCREEN
import dev.eseudom.softpowerbutton.util.C.ACTION_ACCESSIBILITY_SCREENSHOT
import dev.eseudom.softpowerbutton.util.C.ACTION_CLOSE_FLOATING_WIDGET
import dev.eseudom.softpowerbutton.util.C.ACTION_RESTART_ACCESSIBILITY
import dev.eseudom.softpowerbutton.util.C.INTENT_EXTRA_ACTION
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

class U {

    companion object {
        val tag = U::class.java.simpleName

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val accessibilityServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val component = ComponentName(context, SPBAccessibilityService::class.java)
            return (accessibilityServices != null
                    && (accessibilityServices.contains(component.flattenToString())
                    || accessibilityServices.contains(component.flattenToShortString())))
        }

        fun isAccessibilityServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return manager.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == SPBAccessibilityService::class.java.name }
        }

        @RequiresApi(api = Build.VERSION_CODES.P)
        fun sendAccessibilityLockScreenBroadcast(context: Context) {
            val intent = Intent(ACTION_ACCESSIBILITY_LOCK_SCREEN)
            intent.putExtra(INTENT_EXTRA_ACTION, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            context.sendBroadcast(intent)
        }

        fun sendAccessibilityPowerDialogBroadcast(context: Context) {
            val intent = Intent(ACTION_ACCESSIBILITY_GLOBAL_ACTIONS_DIALOG)
            intent.putExtra(INTENT_EXTRA_ACTION, AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            context.sendBroadcast(intent)
        }

        @RequiresApi(Build.VERSION_CODES.P)
        fun sendAccessibilityScreenshotBroadcast(context: Context) {
            val intent = Intent(ACTION_ACCESSIBILITY_SCREENSHOT)
            intent.putExtra(INTENT_EXTRA_ACTION, AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            context.sendBroadcast(intent)
        }

        fun sendStopAccessibilityServiceBroadcast(context: Context) {
            val intent = Intent(C.ACTION_ACCESSIBILITY_DISABLE_SERVICE)
            context.sendBroadcast(intent)
        }

        fun sendShutDownBroadcast(context: Context) {
            val intent = Intent(ACTION_CLOSE_FLOATING_WIDGET)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }

        fun sendAccessibilityServiceNotStartedBroadcast(context: Context) {
            val intent = Intent(ACTION_RESTART_ACCESSIBILITY)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }

        fun enableAccessibilityService(context: Context, enable: Boolean) {
            if (enable) {
                val services = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                val finalServices = services ?: ""
                val accessibilityService =
                    ComponentName(context, SPBAccessibilityService::class.java).flattenToString()
                if (!finalServices.contains(accessibilityService)) {
                    try {
                        Settings.Secure.putString(
                            context.contentResolver,
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                            if (finalServices.isEmpty()) accessibilityService else "$finalServices:$accessibilityService"
                        )
                    } catch (ex: Exception) {
                        Log.e(
                            tag,
                            "Could not add SPBAccessibilityService as enabled accessibility service"
                        )
                    }
                }
            }
        }

        fun sendShowFloatingDialogGuide(extra: Int, context: Context) {
            val intent = Intent(C.ACTION_SHOW_FLOATING_GUIDE_DIALOG)
            intent.putExtra(C.INTENT_EXTRA_DIALOG_TYPE, extra)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }

        fun disableDeviceAdmin(context: Context) {
            val mDPM = context.getSystemService(AppCompatActivity.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val mSPBDeviceAdmin = ComponentName(context, FloatingWindowService.SPBDeviceAdminReceiver::class.java)

            mDPM.removeActiveAdmin(mSPBDeviceAdmin)
        }

        fun recallEnableAccessibilityService(context: Context) {
            val services = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val finalServices = services ?: ""
            try {
                Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    finalServices
                )
            } catch (ignored: Exception) {
                Log.e(tag, "Could not re-add services")
            }
        }

        fun hasWriteSecureSettingsPermission(context: Context): Boolean {
            return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED)
        }

        fun setComponentEnabled(context: Context, clazz: Class<*>, enabled: Boolean) {
            val component = ComponentName(context, clazz)
            context.packageManager.setComponentEnabledSetting(
                component,
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        fun isXiaomi(): Boolean {
            return "xiaomi" == Build.MANUFACTURER.lowercase(Locale.ROOT) ||
                    getSystemProperty("ro.miui.ui.version.name")?.isNotBlank() == true
        }

        fun isXiaomiAPI28Above(): Boolean {
            return isXiaomi() && Build.VERSION.SDK_INT >= 28
        }

        fun launchXiaomiPermissions(context: Context) {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
            intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            intent.putExtra("extra_pkgname", context.packageName)

            context.startActivity(intent)
        }

        private fun getSystemProperty(propName: String): String? {
            val line: String
            var input: BufferedReader? = null
            try {
                val p = Runtime.getRuntime().exec("getprop $propName")
                input = BufferedReader(InputStreamReader(p.inputStream), 1024)
                line = input.readLine()
                input.close()
            } catch (ex: IOException) {
                return null
            } finally {
                if (input != null) {
                    try {
                        input.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
            return line
        }

        fun saveXiaomiPermissionRequestStatus(value: Boolean, context: Context) {
            val sharedPref = context.getSharedPreferences(context.getString(R.string.preference_file_key), Context.MODE_PRIVATE) ?: return
            with (sharedPref.edit()) {
                putBoolean(context.getString(R.string.xiaomi_permission_request_status), value)
                apply()
            }
        }

        fun getXiaomiPermissionRequestStatus(context: Context): Boolean {
            return context.getSharedPreferences(context.getString(R.string.preference_file_key), Context.MODE_PRIVATE)
                .getBoolean(context.getString(R.string.xiaomi_permission_request_status), false)
        }
    }
}