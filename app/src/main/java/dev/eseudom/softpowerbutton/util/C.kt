package dev.eseudom.softpowerbutton.util

object C {

    /*Notification actions*/
    const val ACTION_NOTIFICATION_SLEEP_DEVICE_SCREEN = "dev.eseudom.softpowerbutton.util.constants.ACTION_NOTIFICATION_SLEEP_DEVICE_SCREEN"
    const val ACTION_NOTIFICATION_SHOW_GLOBAL_ACTIONS = "dev.eseudom.softpowerbutton.util.constants.ACTION_NOTIFICATION_SHOW_GLOBAL_ACTIONS"
    const val ACTION_NOTIFICATION_CLOSE_FLOATING_WIDGET = "dev.eseudom.softpowerbutton.util.constants.ACTION_NOTIFICATION_CLOSE_FLOATING_BUTTON"
    const val ACTION_NOTIFICATION_STOP_ALL = "dev.eseudom.softpowerbutton.util.constants.ACTION_NOTIFICATION_STOP_ALL"

    const val FLOATING_BUTTON_NOTIFICATION_ID = 10
    const val REQUEST_CODE_ENABLE_ADMIN = 11

    const val ACTION_CLOSE_FLOATING_WIDGET = "dev.eseudom.softpowerbutton.util.constants.ACTION_CLOSE_FLOATING_SERVICE"

    /*Accessibility*/
    const val ACTION_ACCESSIBILITY_GLOBAL_ACTIONS_DIALOG = "dev.eseudom.softpowerbutton.util.constants.ACTION_ACCESSIBILITY_GLOBAL_ACTIONS_DIALOG"
    const val ACTION_ACCESSIBILITY_LOCK_SCREEN = "dev.eseudom.softpowerbutton.util.constants.ACTION_ACCESSIBILITY_LOCK_SCREEN"
    const val ACTION_ACCESSIBILITY_SCREENSHOT = "dev.eseudom.softpowerbutton.util.constants.ACTION_ACCESSIBILITY_SCREENSHOT"
    const val ACTION_ACCESSIBILITY_DISABLE_SERVICE = "dev.eseudom.softpowerbutton.util.constants.ACTION_ACCESSIBILITY_DISABLE_SERVICE"

    const val ACTION_ACCESSIBILITY_DISABLE_SERVICE_SHORTCUT = "dev.eseudom.softpowerbutton.intent.action.STOP_ACCESSIBILITY_SERVICE"

    const val ACTION_RESTART_ACCESSIBILITY = "dev.eseudom.softpowerbutton.util.constants.ACTION_RESTART_ACCESSIBILITY_SERVICE_BROADCAST"
    const val ACTION_SHOW_FLOATING_GUIDE_DIALOG = "dev.eseudom.softpowerbutton.util.constants.ACTION_SHOW_FLOATING_GUIDE_DIALOG"

    const val NOTIFICATION_CHANNEL_ID = "dev.eseudom.softpowerbutton.floating_player_channel"
    const val INTENT_EXTRA_ACTION = "action"
    const val INTENT_EXTRA_CONFIRM_ENABLE_SERVICE = "confirm_enable_accessibility_service"
    const val INTENT_EXTRA_DIALOG_TYPE = "accessibility_guide_dialog_type"
    const val INTENT_EXTRA_RUNNING = "running"
    const val ACCESSIBILITY_SETTINGS_GUIDE = 1
    const val GENERAL_INSTRUCTIONS = 2
}