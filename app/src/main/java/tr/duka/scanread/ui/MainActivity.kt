package tr.duka.scanread.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import tr.duka.scanread.BuildConfig
import tr.duka.scanread.ui.support.TipJar
import tr.duka.scanread.ui.theme.ScanReadTheme

class MainActivity : ComponentActivity() {

    private var purchaseRecovery: TipJar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanReadTheme {
                AppNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        finishPendingTips()
    }

    /**
     * Completes tips that were paid but not yet consumed, e.g. when the support screen was left
     * right after paying or a pending payment went through later. Unconsumed purchases are refunded
     * by Google Play after three days.
     */
    private fun finishPendingTips() {
        if (!BuildConfig.TIPS_ENABLED || purchaseRecovery != null) return
        val tipJar = TipJar(this, lifecycleScope)
        purchaseRecovery = tipJar
        tipJar.connect(loadTips = false) {
            tipJar.close()
            purchaseRecovery = null
        }
    }
}
