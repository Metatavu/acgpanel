package fi.metatavu.acgpanel

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import fi.metatavu.acgpanel.model.BasketItem
import kotlinx.android.synthetic.main.activity_basket.*
import kotlinx.android.synthetic.main.view_basket_item.view.*

internal fun productView(context: Context): View {
    val DP = Resources.getSystem().displayMetrics.density
    val view = View.inflate(context, R.layout.view_basket_item, null)!!
    view.layoutParams = RecyclerView.LayoutParams(
        RecyclerView.LayoutParams.MATCH_PARENT,
        (150*DP).toInt()
    )
    return view
}

class BasketItemViewHolder(private val context: Context) : RecyclerView.ViewHolder(productView(context)) {

    fun populate(index: Int,
                 item: BasketItem,
                 onDeleteClick: (Int) -> Unit,
                 onChangeCount: (Int, Int) -> Unit) {
        with (itemView) {
            product_name.text = item.product.name
            product_description.text =
                   context.getString(R.string.basket_product_details,
                       item.count,
                       item.expenditure,
                       item.reference)
            product_delete_button.setOnClickListener {
                onDeleteClick(index)
            }
            product_count.minValue=1
            product_count.maxValue=100
            product_count.value = item.count
            product_count.setOnValueChangedListener {_, _, value ->
                onChangeCount(index, value)
            }
            drawProduct(item.product, product_image)
        }
    }

}

class BasketItemCallback : DiffUtil.ItemCallback<BasketItem>() {

    override fun areContentsTheSame(a: BasketItem, b: BasketItem): Boolean {
        return a == b
    }

    override fun areItemsTheSame(a: BasketItem, b: BasketItem): Boolean {
        return a == b
    }

}

class BasketAdapter : ListAdapter<BasketItem, BasketItemViewHolder>(BasketItemCallback()) {

    private var deleteClickListener : (Int) -> Unit = {}
    private var changeCountListener: (Int, Int) -> Unit = {_, _ ->}

    fun setDeleteClickListener(listener: (Int) -> Unit) {
        deleteClickListener = listener
    }

    fun setChangeCountListener(listener: (Int, Int) -> Unit) {
        changeCountListener = listener
    }


    override fun onCreateViewHolder(viewGroup: ViewGroup, index: Int): BasketItemViewHolder {
        return BasketItemViewHolder(viewGroup.context)
    }

    override fun onBindViewHolder(holder: BasketItemViewHolder, index: Int) {
        val item = getItem(index)
        holder.populate(index, item, deleteClickListener, changeCountListener)
    }

}

class BasketActivity : PanelActivity() {

    private var basketAccepted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_basket)
        updateNumProducts()
        val adapter = BasketAdapter()
        basket_items_view.adapter = adapter
        adapter.submitList(model.basket)
        adapter.setDeleteClickListener {
            model.basket.removeAt(it)
            updateNumProducts()
            adapter.notifyDataSetChanged()
        }
        val handler = Handler(mainLooper)
        var callback: Runnable? = null
        adapter.setChangeCountListener {i, count ->
            if (callback != null) {
                handler.removeCallbacks(callback)
            }
            callback = Runnable {
                val old = model.basket[i]
                val new = BasketItem(old.product, count, old.expenditure, old.reference)
                model.basket[i] = new
                adapter.notifyDataSetChanged()
            }
            handler.postDelayed(callback, 500)
        }
    }

    private fun updateNumProducts() {
        num_products_label.text = getString(
            R.string.num_products,
            model.basket.size
        )
        if (basketAccepted) {
            ok_button.isEnabled = true
        } else {
            ok_button.isEnabled = model.basket.size != 0
        }
    }

    fun cancel(@Suppress("UNUSED_PARAMETER") view: View) {
        model.basket.clear()
        finish()
    }

    fun selectAnother(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        model.openLock()
        basketAccepted = true
        cancel_button.isEnabled = false
        select_another_button.isEnabled = false
        val intent = Intent(this, TakeActivity::class.java)
        startActivity(intent)
    }

    override val unlockButton: View
        get() = unlock_button

}
