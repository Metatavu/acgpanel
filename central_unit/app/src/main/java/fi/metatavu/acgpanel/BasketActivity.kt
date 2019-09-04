package fi.metatavu.acgpanel

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.support.v4.content.ContextCompat
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.view.menu.MenuView
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.view.ViewGroup
import fi.metatavu.acgpanel.model.BasketItem
import fi.metatavu.acgpanel.model.BasketItemType
import fi.metatavu.acgpanel.model.getBasketModel
import fi.metatavu.acgpanel.model.getLockModel
import kotlinx.android.synthetic.main.activity_basket.*
import kotlinx.android.synthetic.main.view_basket_item.view.*
import kotlinx.android.synthetic.main.view_basket_item.view.product_description
import kotlinx.android.synthetic.main.view_basket_item.view.product_image
import kotlinx.android.synthetic.main.view_basket_item.view.product_name

private fun basketItemView(context: Context): View {
    val dp = Resources.getSystem().displayMetrics.density
    val view = View.inflate(context, R.layout.view_basket_item, null)!!
    view.layoutParams = RecyclerView.LayoutParams(
        RecyclerView.LayoutParams.MATCH_PARENT,
        (150*dp).toInt()
    )
    return view
}

private class BasketItemViewHolder(context: Context) : RecyclerView.ViewHolder(basketItemView(context)) {

    fun populate(index: Int,
                 item: BasketItem,
                 onDeleteClick: (Int) -> Unit,
                 onModifyClick: (Int) -> Unit) {
        with (itemView) {
            if (item.count == 1) {
                product_name.text = item.product.name
            } else {
                product_name.text =
                        context.getString(
                            R.string.basket_product_title,
                            item.product.name,
                            item.count,
                            item.product.unit.trim()
                        )
            }
            Log.d(javaClass.name, "ITEM TYPE: ${item.type}")
            when (item.type) {
                BasketItemType.Purchase -> {
                    product_description.text =
                        context.getString(
                            R.string.basket_product_details,
                            item.expenditure,
                            item.reference
                        )
                    product_name.setTextColor(ContextCompat.getColor(itemView.context, R.color.colorPurchase))
                }
                BasketItemType.Borrow -> {
                    product_description.text =
                        context.getString(
                            R.string.basket_product_details_borrow,
                            item.expenditure,
                            item.reference
                        )
                    product_name.setTextColor(ContextCompat.getColor(itemView.context, R.color.colorBorrow))
                    product_modify_button.visibility = View.GONE
                    product_modify_button_decal.visibility = View.GONE
                }
                BasketItemType.Return -> {
                    product_description.text =
                        context.getString(
                            R.string.basket_product_details_return,
                            item.expenditure,
                            item.reference
                        )
                    product_name.setTextColor(ContextCompat.getColor(itemView.context, R.color.colorReturn))
                    product_modify_button.visibility = View.GONE
                    product_modify_button_decal.visibility = View.GONE
                }
            }
            if (item.enabled) {
                disabled_overlay.visibility = View.GONE
                product_delete_button.setOnClickListener {
                    onDeleteClick(index)
                }
                product_modify_button.setOnClickListener {
                    onModifyClick(index)
                }
            } else {
                disabled_overlay.visibility = View.VISIBLE
                product_delete_button.setOnClickListener { }
                product_modify_button.setOnClickListener { }
            }
            drawProduct(item.product, product_image)
        }
    }

}

private class BasketItemCallback : DiffUtil.ItemCallback<BasketItem>() {

    override fun areContentsTheSame(a: BasketItem, b: BasketItem): Boolean {
        return a == b
    }

    override fun areItemsTheSame(a: BasketItem, b: BasketItem): Boolean {
        return a == b
    }

}

private class BasketAdapter : ListAdapter<BasketItem, BasketItemViewHolder>(BasketItemCallback()) {

    private var deleteClickListener : (Int) -> Unit = {}
    private var modifyClickListener: (Int) -> Unit = {}

    fun setDeleteClickListener(listener: (Int) -> Unit) {
        deleteClickListener = listener
    }

    fun setModifyClickListener(listener: (Int) -> Unit) {
        modifyClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, index: Int): BasketItemViewHolder {
        return BasketItemViewHolder(parent.context)
    }

    override fun onBindViewHolder(holder: BasketItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.populate(position, item, deleteClickListener, modifyClickListener)
    }

}

class BasketActivity : PanelActivity() {

    private var basketAccepted = false
    private val adapter = BasketAdapter()
    private val basketModel = getBasketModel()
    private val lockModel = getLockModel()
    private val lockOpenListener = {
        adapter.notifyDataSetChanged()
    }
    private lateinit var handler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handler = Handler(mainLooper)
        setContentView(R.layout.activity_basket)
        updateNumProducts()
        basket_items_view.adapter = adapter
        adapter.submitList(basketModel.basket)
        adapter.setDeleteClickListener { index ->
            val basketItem = basketModel.basket[index]
            if (basketItem.type == BasketItemType.Purchase) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.out_of_products_title)
                    .setMessage(R.string.out_of_products_message)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        basketModel.markProductEmpty(index)
                        basketModel.deleteBasketItem(index)
                        updateNumProducts()
                        adapter.notifyDataSetChanged()
                    }
                    .setNegativeButton(R.string.no) { _, _ ->
                        basketModel.deleteBasketItem(index)
                        updateNumProducts()
                        adapter.notifyDataSetChanged()
                    }
                    .create()
                    .show()
            } else {
                basketModel.deleteBasketItem(index)
                updateNumProducts()
                adapter.notifyDataSetChanged()
            }
        }
        adapter.setModifyClickListener {
            basketModel.selectExistingBasketItem(it)
            val intent = Intent(this, ProductSelectionActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onPause() {
        lockModel.removeLockOpenedListener(lockOpenListener)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        lockModel.addLockOpenedListener(lockOpenListener)
        ok_button.requestFocus()
    }

    override fun onPostResume() {
        super.onPostResume()
        adapter.notifyDataSetChanged()
    }

    private fun updateNumProducts() {
        num_products_label.text = getString(
            R.string.num_products,
            basketModel.basket.size
        )
        if (basketAccepted) {
            ok_button.isEnabled = true
        } else {
            ok_button.isEnabled = basketModel.basket.isNotEmpty()
        }
    }

    override fun onBackPressed() {
        if (!basketAccepted) {
            super.onBackPressed()
        }
    }

    fun cancel(@Suppress("UNUSED_PARAMETER") view: View) {
        basketModel.clearBasket()
        finish()
    }

    fun selectAnother(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        if (!basketAccepted) {
            basketModel.acceptBasket()
            basketAccepted = true
            handler.postDelayed({
                num_products_label.text = getString(R.string.hint_only_edit_open_locker)
            }, 500)
        }
        cancel_button.isEnabled = false
        select_another_button.isEnabled = false
        val intent = Intent(this, TakeActivity::class.java)
        startActivity(intent)
    }

    override val unlockButton: View
        get() = unlock_button

}
