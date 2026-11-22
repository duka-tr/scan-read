package tr.duka.scanread.ui.support

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Optional tips through Google Play Billing. Tips are consumable one-time products, so a user can
 * tip more than once; they unlock nothing.
 *
 * Tips are only available when the app is installed from Google Play and the products exist in
 * Play Console ([PRODUCT_IDS]).
 */
class TipJar(context: Context, private val scope: CoroutineScope) : PurchasesUpdatedListener {

    data class Tip(val productId: String, val price: String, internal val details: ProductDetails)

    sealed interface State {
        data object Loading : State
        data object Unavailable : State
        data class Ready(val tips: List<Tip>) : State
    }

    enum class Event { THANKS, FAILED }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    _state.value = State.Unavailable
                    return
                }
                scope.launch {
                    loadTips()
                    finishInterruptedPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = State.Unavailable
            }
        })
    }

    fun tip(activity: Activity, tip: Tip) {
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(tip.details).build()),
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { purchases.orEmpty().forEach { consume(it) } }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _events.tryEmit(Event.FAILED)
        }
    }

    fun close() = client.endConnection()

    private suspend fun loadTips() {
        val products = PRODUCT_IDS.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val result = client.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(products).build())
        val tips = result.productDetailsList.orEmpty()
            .mapNotNull { details ->
                details.oneTimePurchaseOfferDetails?.let { offer ->
                    Tip(details.productId, offer.formattedPrice, details) to offer.priceAmountMicros
                }
            }
            .sortedBy { it.second }
            .map { it.first }
        _state.value = if (tips.isEmpty()) State.Unavailable else State.Ready(tips)
    }

    /** Consumes purchases that completed while the app was closed or the screen was gone. */
    private suspend fun finishInterruptedPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params).purchasesList.forEach { consume(it) }
    }

    private suspend fun consume(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val result = client.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _events.tryEmit(Event.THANKS)
        } else {
            Log.w(TAG, "Could not consume tip: ${result.billingResult.debugMessage}")
        }
    }

    companion object {
        private const val TAG = "TipJar"

        /** One-time products to create in Play Console, smallest to largest. */
        val PRODUCT_IDS = listOf("tip_small", "tip_medium", "tip_large")
    }
}
