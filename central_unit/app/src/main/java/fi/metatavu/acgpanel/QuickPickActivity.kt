package fi.metatavu.acgpanel

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import fi.metatavu.acgpanel.model.BasketItem
import fi.metatavu.acgpanel.model.getBasketModel
import fi.metatavu.acgpanel.model.getLoginModel
import kotlinx.android.synthetic.main.activity_quick_pick.*
import kotlinx.android.synthetic.main.view_quick_pick_item.view.*

internal fun quickPickItemView(context: Context): View {
    val dp = Resources.getSystem().displayMetrics.density
    val view = View.inflate(context, R.layout.view_quick_pick_item, null)!!
    view.layoutParams = RecyclerView.LayoutParams(
        RecyclerView.LayoutParams.MATCH_PARENT,
        (60*dp).toInt()
    )
    return view
}

class QuickPickItemViewHolder(context: Context) : RecyclerView.ViewHolder(quickPickItemView(context)) {

    var disableCountListener = false
    var index = -1
    var onCountUpdated: (Int, Int) -> Unit = {_,_ ->}
    private val basketModel = getBasketModel()
    private val countListener = object: TextWatcher {
        override fun afterTextChanged(text: Editable?) {
            if (disableCountListener) {
                return
            }
            val count = text.toString().toIntOrNull() ?: 0
            onCountUpdated(index, count)
            setBackgroundColor(index, count)
        }
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        }
        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        }
    }

    fun setBackgroundColor(index: Int, count: Int) {
        with (itemView) {
            background = when (Pair(index % 2 == 0, count > 0)) {
                Pair(true, true) -> context.getDrawable(R.color.highlight)
                Pair(false, true) -> context.getDrawable(R.color.highlightDark)
                Pair(true, false) -> context.getDrawable(R.color.lightZebraStripe1)
                Pair(false, false) -> context.getDrawable(R.color.lightZebraStripe2)
                else -> context.getDrawable(R.color.lightBackground)
            }
        }
    }

    fun populate(index: Int,
                 item: BasketItem,
                 onDeleteClick: (Int) -> Unit,
                 onCountUpdated: (Int, Int) -> Unit,
                 onExpenditureClick: (Int, ((String) -> Unit)) -> Unit,
                 onReferenceClick: (Int, ((String) -> Unit)) -> Unit) {
        with (itemView) {
            setBackgroundColor(index, item.count)
            count_input.transformationMethod = null
            disableCountListener = true
            count_input.text.clear()
            if (item.count > 0) {
                count_input.text.insert(0, item.count.toString())
            }
            disableCountListener = false
            this@QuickPickItemViewHolder.onCountUpdated = onCountUpdated
            this@QuickPickItemViewHolder.index = index
            count_input.removeTextChangedListener(countListener)
            count_input.addTextChangedListener(countListener)
            product_line.text = item.product.line
            product_name.text = item.product.name
            product_expenditure.text = item.expenditure
            if (basketModel.lockUserExpenditure) {
                product_expenditure.isEnabled = false
            } else {
                product_expenditure.setOnClickListener {
                    onExpenditureClick(index) { expenditure ->
                        product_expenditure.text = expenditure
                    }
                }
            }
            product_reference.text = item.reference
            if (basketModel.lockUserReference) {
                product_reference.isEnabled = false
            } else {
                product_reference.setOnClickListener {
                    onReferenceClick(index) { reference ->
                        product_reference.text = reference
                    }
                }
            }
            product_delete_button.setOnClickListener {
                disableCountListener = true
                count_input.text.clear()
                disableCountListener = false
                setBackgroundColor(index, 0)
                onDeleteClick(index)
            }
        }
    }

}

class QuickPickItemCallback : DiffUtil.ItemCallback<BasketItem>() {

    override fun areContentsTheSame(a: BasketItem, b: BasketItem): Boolean {
        return a == b
    }

    override fun areItemsTheSame(a: BasketItem, b: BasketItem): Boolean {
        return a == b
    }

}

class QuickPickAdapter : ListAdapter<BasketItem, QuickPickItemViewHolder>(QuickPickItemCallback()) {

    private var deleteClickListener: (Int) -> Unit = {}
    private var countUpdatedListener: (Int, Int) -> Unit = {_, _ ->}
    private var expenditureClickListener: (Int, ((String)->Unit)) -> Unit = {_, _ ->}
    private var referenceClickListener: (Int, ((String)->Unit)) -> Unit = {_, _ ->}

    fun setDeleteClickListener(listener: (Int) -> Unit) {
        deleteClickListener = listener
    }

    fun setCountUpdatedListener(listener: (Int, Int) -> Unit) {
        countUpdatedListener = listener
    }

    fun setExpenditureClickListener(listener: (Int, ((String)->Unit)) -> Unit) {
        expenditureClickListener = listener
    }

    fun setReferenceClickListener(listener: (Int, ((String)->Unit)) -> Unit) {
        referenceClickListener = listener
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, index: Int): QuickPickItemViewHolder {
        return QuickPickItemViewHolder(viewGroup.context)
    }

    override fun onBindViewHolder(holder: QuickPickItemViewHolder, index: Int) {
        val item = getItem(index)
        holder.populate(
            index,
            item,
            deleteClickListener,
            countUpdatedListener,
            expenditureClickListener,
            referenceClickListener)
    }

}

class QuickPickActivity : PanelActivity() {

    private var basketAccepted = false
    private val adapter = QuickPickAdapter()
    private val basketModel = getBasketModel()
    private val loginModel = getLoginModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_pick)
        updateNumProducts()
        basket_items_view.adapter = adapter
        show_profile_button.text = loginModel.currentUser?.userName
        adapter.submitList(basketModel.basket)
        adapter.setDeleteClickListener {
            basketModel.selectExistingBasketItem(it)
            basketModel.saveSelectedItem(
                0,
                loginModel.currentUser?.expenditure ?: "",
                loginModel.currentUser?.reference ?: ""
            )
            updateNumProducts()
        }
        adapter.setCountUpdatedListener {i, count ->
            basketModel.selectExistingBasketItem(i)
            val item = basketModel.currentBasketItem
            basketModel.saveSelectedItem(
                count,
                item?.expenditure ?: "",
                item?.reference ?: ""
            )
            updateNumProducts()
        }
        adapter.setExpenditureClickListener { i, updateExpenditure ->
            showEditDialog(getString(R.string.input_expenditure)) {
                basketModel.selectExistingBasketItem(i)
                val item = basketModel.currentBasketItem
                basketModel.saveSelectedItem(
                    item?.count ?: 0,
                    it,
                    item?.reference ?: ""
                )
                updateNumProducts()
                updateExpenditure(it)
            }
        }
        adapter.setReferenceClickListener { i, updateReference ->
            showEditDialog(getString(R.string.input_reference)) {
                basketModel.selectExistingBasketItem(i)
                val item = basketModel.currentBasketItem
                basketModel.saveSelectedItem(
                    item?.count ?: 0,
                    item?.expenditure ?: "",
                    it
                )
                updateNumProducts()
                updateReference(it)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        basket_items_view.requestFocus()
    }

    override fun onPostResume() {
        super.onPostResume()
        adapter.notifyDataSetChanged()
    }

    private fun updateNumProducts() {
        num_products_label.text = getString(
            R.string.num_products,
            basketModel.basket.filter{it.count>0}.size
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

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_UP &&
                event.keyCode == KeyEvent.KEYCODE_ENTER) {
            proceed(root)
        }
        return super.dispatchKeyEvent(event)
    }

    fun menu(@Suppress("UNUSED_PARAMETER") view: View) {
        val intent = Intent(this, MenuActivity::class.java)
        basketModel.clearBasket()
        startActivity(intent)
        finish()
    }

    fun showProfileDialog(@Suppress("UNUSED_PARAMETER") view: View) {
        val dialog = ProfileDialog(this, loginModel)
        dialog.setLogoutListener {
            loginModel.logOut()
        }
        dialog.show()
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        if (!basketAccepted) {
            basketModel.removeZeroCountItems()
            basketModel.acceptBasket()
            basketAccepted = true
        }
        bottom_bar.isEnabled = false
        menu_button.isEnabled = false
        show_profile_button.isEnabled = false
        val intent = Intent(this, TakeActivity::class.java)
        startActivity(intent)
    }

    override val unlockButton: View
        get() = unlock_button

}
