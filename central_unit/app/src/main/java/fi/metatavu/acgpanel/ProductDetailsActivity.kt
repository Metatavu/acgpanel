package fi.metatavu.acgpanel

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import fi.metatavu.acgpanel.model.getBasketModel
import fi.metatavu.acgpanel.model.getProductsModel
import kotlinx.android.synthetic.main.activity_product_details.*
import kotlinx.android.synthetic.main.view_safety_card.view.*

private fun safetyCardView(context: Context): View {
    val dp = Resources.getSystem().displayMetrics.density
    val view = View.inflate(context, R.layout.view_safety_card, null)!!
    view.layoutParams = RecyclerView.LayoutParams(
        RecyclerView.LayoutParams.WRAP_CONTENT,
        (60*dp).toInt()
    )
    return view
}

private class SafetyCardViewHolder(context: Context):
        RecyclerView.ViewHolder(safetyCardView(context)) {

    fun populate(index: Int, item: String, onClick: (Int) -> Unit) {
        with (itemView) {
            safety_card_button.text = item
            safety_card_button.setOnClickListener {
                onClick(index)
            }
        }
    }

}

private class StringCallback: DiffUtil.ItemCallback<String>() {

    override fun areItemsTheSame(oldItem: String?, newItem: String?): Boolean =
        oldItem == newItem

    override fun areContentsTheSame(oldItem: String?, newItem: String?): Boolean =
        oldItem == newItem

}

private class SafetyCardAdapter: ListAdapter<String, SafetyCardViewHolder>(StringCallback()) {

    private var clickListener: (Int) -> Unit = {}

    fun setClickListener(listener: (Int) -> Unit) {
        clickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        SafetyCardViewHolder(parent.context)

    override fun onBindViewHolder(holder: SafetyCardViewHolder, position: Int) {
        holder.populate(position, getItem(position), clickListener)
    }

}

class ProductDetailsActivity : PanelActivity() {
    private val basketModel = getBasketModel()
    private val productsModel = getProductsModel()
    private val adapter = SafetyCardAdapter()
    private val safetyCards = mutableListOf<String>()

    override val unlockButton: View
        get() = unlock_button

    fun selectSafetyCard(index: Int) {
        val card = safetyCards.getOrNull(index) ?: return
        val productImagePrefix = PreferenceManager
            .getDefaultSharedPreferences(this)
            .getString(getString(R.string.pref_key_user_asset_url),
                       DEFAULT_PRODUCT_IMAGE_PREFIX)
        product_safety_card.url = "$productImagePrefix/UserAssets/$card"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_details)
        val product = basketModel.currentBasketItem?.product ?: return
        productsModel.listProductSafetyCards(product) safetyCardCallback@{
            safetyCards.addAll(it)
            adapter.submitList(safetyCards)
            safety_card_tab_bar.adapter = adapter
            selectSafetyCard(0)
            adapter.setClickListener {
                selectSafetyCard(it)
            }
        }
    }

    fun close(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

    fun nextPage(@Suppress("UNUSED_PARAMETER") view: View) {
        if (product_safety_card.page < product_safety_card.numPages - 1) {
            product_safety_card.page++
        }
    }

    fun previousPage(@Suppress("UNUSED_PARAMETER") view: View) {
        if (product_safety_card.page > 0) {
            product_safety_card.page--
        }
    }

}
