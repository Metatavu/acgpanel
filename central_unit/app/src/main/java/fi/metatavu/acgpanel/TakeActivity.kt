package fi.metatavu.acgpanel

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.Interpolator
import fi.metatavu.acgpanel.model.BasketItem
import fi.metatavu.acgpanel.model.getBasketModel
import fi.metatavu.acgpanel.model.getLockModel
import fi.metatavu.acgpanel.model.getLoginModel
import kotlinx.android.synthetic.main.activity_take.*
import kotlinx.android.synthetic.main.view_take_item.view.*

private fun takeItemView(context: Context): View {
    val dp = Resources.getSystem().displayMetrics.density
    val view = View.inflate(context, R.layout.view_take_item, null)!!
    view.layoutParams = RecyclerView.LayoutParams(
        RecyclerView.LayoutParams.MATCH_PARENT,
        (150*dp).toInt()
    )
    return view
}

private class TakeItemViewHolder(context: Context)
        : RecyclerView.ViewHolder(takeItemView(context)) {

    fun populate(item: BasketItem) {
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
            product_description.text =
                context.getString(
                    R.string.basket_product_details,
                    item.expenditure,
                    item.reference
                )
            drawProduct(item.product, product_image)
        }
    }
}

private class TakeItemCallback: DiffUtil.ItemCallback<BasketItem>() {

    override fun areContentsTheSame(a: BasketItem, b: BasketItem): Boolean {
        return a == b
    }

    override fun areItemsTheSame(a: BasketItem, b: BasketItem): Boolean {
        return a == b
    }
}

private class TakeItemAdapter : ListAdapter<BasketItem, TakeItemViewHolder>(TakeItemCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, index: Int): TakeItemViewHolder {
        return TakeItemViewHolder(parent.context)
    }

    override fun onBindViewHolder(holder: TakeItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.populate(item)
    }

}

class TakeActivity : PanelActivity() {

    private val lockModel = getLockModel()
    private val basketModel = getBasketModel()
    private val loginModel = getLoginModel()
    private val takeOutAdapter = TakeItemAdapter()
    private val putInAdapter = TakeItemAdapter()

    private val alarmCallback = Runnable {
        alarm_overlay_back.visibility = View.VISIBLE
        alarm_overlay_front.visibility = View.VISIBLE
    }

    var handler = Handler(Looper.getMainLooper())

    val onLockOpenedListener = listener@{
        status_text.text = getString(
            R.string.complete_by_closing_door,
            lockModel.currentLock,
            lockModel.numLocks)
        handler.removeCallbacks(alarmCallback)
        handler.postDelayed(alarmCallback, ALARM_TIMEOUT)
        val takeOutItems = basketModel.takeOutItems
        takeOutAdapter.submitList(takeOutItems)
        if (takeOutItems.isEmpty()) {
            takeout_products_title.visibility = View.GONE
        } else {
            takeout_products_title.visibility = View.VISIBLE
        }
        val putInItems = basketModel.putInItems
        if (putInItems.isEmpty()) {
            putin_products_title.visibility = View.GONE
        } else {
            putin_products_title.visibility = View.VISIBLE
        }
        putInAdapter.submitList(putInItems)
        return@listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_take)
    }

    override fun onStart() {
        super.onStart()
        val blink = AlphaAnimation(1f, 0f)
        blink.duration = 1000
        blink.interpolator = Interpolator {
            if (it < 0.5f) {0f} else {1f}
        }
        blink.repeatCount = Animation.INFINITE
        alarm_overlay_front.animation = blink
        alarm_overlay_front.visibility = View.GONE
        alarm_overlay_back.visibility = View.GONE
        takeout_products_list.adapter = takeOutAdapter
        putin_products_list.adapter = putInAdapter
        takeOutAdapter.submitList(basketModel.takeOutItems)
        putInAdapter.submitList(basketModel.putInItems)
        onLockOpenedListener()
    }

    override fun onResume() {
        super.onResume()
        lockModel.addLockOpenedListener(onLockOpenedListener)
        handler.postDelayed(alarmCallback, ALARM_TIMEOUT)
    }

    override fun onPause() {
        handler.removeCallbacks(alarmCallback)
        lockModel.removeLockOpenedListener(onLockOpenedListener)
        super.onPause()
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        basketModel.completeProductTransaction {
            loginModel.logOut()
        }
    }

    fun back(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

    override val unlockButton: View
        get() = unlock_button

    companion object {
        private const val ALARM_TIMEOUT = 80L*1000L
    }

}
