package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.media.RingtoneManager
import android.widget.Toast
import com.example.domain.LocalSong

object MediaHelper {
    fun setRingtone(context: Context, song: LocalSong, type: Int) {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:" + context.packageName)
            }
            context.startActivity(intent)
            Toast.makeText(context, "Please grant permission and try again", Toast.LENGTH_LONG).show()
            return
        }
        try {
            RingtoneManager.setActualDefaultRingtoneUri(context, type, song.uri)
            Toast.makeText(context, "Successfully set!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to set: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
