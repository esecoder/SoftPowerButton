package dev.eseudom.softpowerbutton.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import dev.eseudom.softpowerbutton.R

class AlertDialogFragment : DialogFragment() {
    var message: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        builder.setMessage(message)
            .setPositiveButton(
                R.string.ok
            ) { dialog, _ -> dialog.dismiss() }

        return builder.create()
    }
}