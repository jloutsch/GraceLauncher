package com.galaxyrio.gracelauncher.platform

import android.content.Context
import android.content.Intent
import android.os.Process
import com.galaxyrio.gracelauncher.MainActivity

/** A restored file can reuse an icon/font path; drop process-wide caches as well as the activity. */
internal fun restartAfterRestore(context: Context) {
    context.startActivity(Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    Process.killProcess(Process.myPid())
}
