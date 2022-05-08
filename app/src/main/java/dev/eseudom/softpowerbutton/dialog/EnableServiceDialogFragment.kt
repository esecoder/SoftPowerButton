package dev.eseudom.softpowerbutton.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import dev.eseudom.softpowerbutton.R

class EnableServiceDialogFragment : DialogFragment() {

    private lateinit var listenerPos: DialogInterface.OnClickListener
    private lateinit var listenerNeg: DialogInterface.OnClickListener
    private var message: String = ""
    private var shown: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        builder.setMessage(message)
            .setPositiveButton(
                R.string.enable_text,
                listenerPos)
            .setNegativeButton(
                R.string.cancel,
                listenerNeg)

        return builder.create()
    }

    @JvmName("setConfirmListener")
    fun setListenerPos(listener: DialogInterface.OnClickListener) {
        this.listenerPos = listener
    }

    @JvmName("setCancelListener")
    fun setListenerNeg(listener: DialogInterface.OnClickListener) {
        this.listenerNeg = listener
    }

    fun isShown() = shown

    fun setShown(shown: Boolean) {
        this.shown = shown
    }

    fun setMessage(message: String){
        this.message = message
    }
}