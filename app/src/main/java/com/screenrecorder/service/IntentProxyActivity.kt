package com.screenrecorder.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class IntentProxyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_TARGET, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Intent>(EXTRA_TARGET)
        }
        if (target == null) {
            Log.e(TAG, "No target intent provided")
            finish()
            return
        }

        Log.d(TAG, "Proxy launching: $target")
        try {
            launcher.launch(target)
        } catch (e: Exception) {
            Log.e(TAG, "Proxy start failed", e)
            finish()
        }
    }

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { finish() }

    companion object {
        private const val TAG = "IntentProxy"
        private const val EXTRA_TARGET = "target_intent"

        fun launch(context: Context, target: Intent) {
            val proxy = Intent(context, IntentProxyActivity::class.java).apply {
                putExtra(EXTRA_TARGET, target)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            context.startActivity(proxy)
        }
    }
}
