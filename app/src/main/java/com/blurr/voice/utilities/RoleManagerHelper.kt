package com.blurr.voice.utilities

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.provider.Settings

object RoleManagerHelper {

    fun isDefaultAssistant(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            rm?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        } else {
            // Fallback for older versions
            val flat = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
            flat?.startsWith(context.packageName) ?: false
        }
    }
}
