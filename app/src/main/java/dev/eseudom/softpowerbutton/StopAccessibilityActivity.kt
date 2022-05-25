package dev.eseudom.softpowerbutton

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dev.eseudom.softpowerbutton.util.U

class StopAccessibilityActivity : AppCompatActivity() {

    private val tag = StopAccessibilityActivity::class.java.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        U.sendStopAccessibilityServiceBroadcast(this)
        U.sendShutDownBroadcast(this)
        U.disableDeviceAdmin(this)

        Toast.makeText(this, R.string.uninstall_ready, Toast.LENGTH_LONG).show()

        onBackPressed()
    }
}