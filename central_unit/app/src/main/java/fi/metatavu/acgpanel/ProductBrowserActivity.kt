package fi.metatavu.acgpanel

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.content.res.TypedArray
import android.os.Bundle
import android.os.Handler
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.PagerSnapHelper
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import fi.metatavu.acgpanel.model.*
import kotlinx.android.synthetic.main.activity_product_browser.*

internal fun productPageView(context: Context): View {
    val dp = Resources.getSystem().displayMetrics.density
    val view = View.inflate(context, R.layout.view_product_page, null)!!
    view.layoutParams = RecyclerView.LayoutParams(
        (930*dp).toInt(),
        RecyclerView.LayoutParams.MATCH_PARENT
    )
    return view
}

const val PRODUCTS_PER_PAGE = 6

class ProductPageViewHolder(context: Context) : RecyclerView.ViewHolder(productPageView(context)) {

    private val parentViewIds = context.resources.obtainTypedArray(R.array.product_view_ids)
    private val textViewIds = context.resources.obtainTypedArray(R.array.product_view_text_ids)
    private val imageViewIds = context.resources.obtainTypedArray(R.array.product_view_image_ids)
    private val lineViewIds = context.resources.obtainTypedArray(R.array.product_view_line_ids)
    private val buttonIds = context.resources.obtainTypedArray(R.array.product_view_button_ids)

    private fun <T: View> getView(arr: TypedArray, i: Int): T
        = itemView.findViewById(arr.getResourceId(i, -1))

    fun populate(item: ProductPage, onProductClick: (Product) -> Unit) {
        for (i in 0 until PRODUCTS_PER_PAGE) {
            if (i < item.products.size) {
                val product = item.products[i]
                getView<View>(parentViewIds, i).visibility = View.VISIBLE
                getView<TextView>(textViewIds, i).text = product.name
                getView<TextView>(lineViewIds, i).text = product.line
                getView<Button>(buttonIds, i).setOnClickListener {
                    onProductClick(product)
                }
            } else {
               getView<View>(parentViewIds, i).visibility = View.INVISIBLE
            }
        }
        drawProducts(
            item.products.withIndex().map {
                Pair(it.value, getView<ImageView>(imageViewIds, it.index))
            }
        )
    }

}

class ProductPageItemCallback : DiffUtil.ItemCallback<ProductPage>() {

    override fun areContentsTheSame(a: ProductPage, b: ProductPage): Boolean {
        return a == b
    }

    override fun areItemsTheSame(a: ProductPage, b: ProductPage): Boolean {
        return a == b
    }

}

class ProductPageAdapter : ListAdapter<ProductPage, ProductPageViewHolder>(ProductPageItemCallback()) {

    private var productClickListener : ((Product) -> Unit)? = null

    fun setProductClickListener(listener: (Product) -> Unit) {
        productClickListener = listener
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, index: Int): ProductPageViewHolder {
        return ProductPageViewHolder(viewGroup.context)
    }

    override fun onBindViewHolder(holder: ProductPageViewHolder, index: Int) {
        val item = getItem(index)
        val listener = productClickListener
        if (listener != null) {
            holder.populate(item, listener)
        } else {
            holder.populate(item) {}
        }
    }

}

class ProductBrowserActivity : PanelActivity() {

    private val loginModel = getLoginModel()
    private val lockModel = getLockModel()
    private val basketModel = getBasketModel()
    private val productsModel = getProductsModel()

    @SuppressLint("SetTextI18n")
    private val logInListener = {
        val user = loginModel.currentUser
        show_profile_button.visibility = View.VISIBLE
        show_profile_button.text = user?.userName ?: ""
        if (user?.canShelve == true) {
            bottom_bar.background = getDrawable(R.color.error)
            show_profile_button.text = "HYLLYTYS: ${user.userName}"
        } else {
            bottom_bar.background = getDrawable(R.color.colorPrimaryDark)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_browser)
        val adapter = ProductPageAdapter()
        adapter.setProductClickListener {
            if (loginModel.currentUser?.canShelve == true) {
                lockModel.openLineLock(it.line)
            } else {
                selectProduct(it)
            }
        }
        basket_items_view.adapter = adapter
        adapter.submitList(productsModel.productPages)
        // TODO throttle/debounce
        enableSoftKeyboard(search_box)
        search_box.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                productsModel.searchTerm = s.toString()
                productsModel.refreshProductPages {
                    adapter.submitList(productsModel.productPages)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })
        search_box.setOnKeyListener { _, _, keyEvent ->
            val productPage = productsModel.productPages.firstOrNull()
            val product = productPage?.products?.firstOrNull()
            if (keyEvent.action == KeyEvent.ACTION_UP
                    && keyEvent.keyCode == KeyEvent.KEYCODE_ENTER
                    && product != null) {
                if (loginModel.currentUser?.canShelve == true) {
                    lockModel.openLineLock(product.line)
                    search_box.text.clear()
                    productsModel.searchTerm = ""
                    productsModel.refreshProductPages {
                        adapter.submitList(productsModel.productPages)
                    }
                    true
                } else {
                    selectProduct(product)
                    true
                }
            } else {
                false
            }
        }
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(basket_items_view)
        basket_items_view.addItemDecoration(LinePagerIndicatorDecoration())
        val user = loginModel.currentUser
        if (user != null) {
            show_profile_button.visibility = View.VISIBLE
            show_profile_button.text = user.userName
            if (user.canShelve) {
                bottom_bar.background = getDrawable(R.color.error)
                show_profile_button.text = getString(
                    R.string.shelving,
                    user.userName
                )
            } else {
                bottom_bar.background = getDrawable(R.color.colorPrimaryDark)
            }
        } else {
            show_profile_button.visibility = View.INVISIBLE
        }
        loginModel.addLogInListener(logInListener)
    }

    override fun onDestroy() {
        loginModel.removeLogInListener(logInListener)
        super.onDestroy()
    }

    private fun selectProduct(it: Product) {
        basketModel.selectNewBasketItem(it)
        val intent = Intent(this, ProductSelectionActivity::class.java)
        Handler(mainLooper).postDelayed({
            search_box.text.clear()
            productsModel.searchTerm = ""
        }, 50)
        startActivity(intent)
    }

    override fun onBackPressed() {
        if (search_box.text.isNotEmpty()) {
            search_box.text.clear()
            productsModel.searchTerm = ""
            search_box.requestFocus()
        } else {
            loginModel.logOut()
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        search_box.requestFocus()
    }

    private fun scrollPage(layoutManager: LinearLayoutManager, edge: Boolean, direction: Int) {
        if (layoutManager.childCount == 0) {
            return
        }
        val itemWidth = layoutManager.getChildAt(0)!!.width
        val distance = if (edge) {
            val difference = basket_items_view.width - itemWidth
            itemWidth - difference/2
        } else {
            itemWidth
        }
        basket_items_view.stopScroll()
        basket_items_view.smoothScrollBy(direction * distance, 0)
    }

    fun menu(@Suppress("UNUSED_PARAMETER") target: View) {
        productsModel.searchTerm = ""
        productsModel.refreshProductPages {  }
        val intent = Intent(this, MenuActivity::class.java)
        finish()
        startActivity(intent)
    }

    fun nextPage(@Suppress("UNUSED_PARAMETER") target: View) {
        val layoutManager = basket_items_view.layoutManager as LinearLayoutManager
        val itemNumber = layoutManager.findFirstCompletelyVisibleItemPosition()
        scrollPage(layoutManager, itemNumber == 0, 1)
    }

    fun previousPage(@Suppress("UNUSED_PARAMETER") target: View) {
        val layoutManager = basket_items_view.layoutManager as LinearLayoutManager
        val itemNumber = layoutManager.findFirstCompletelyVisibleItemPosition()
        scrollPage(layoutManager, itemNumber == layoutManager.itemCount - 1, -1)
    }

    fun showProfileDialog(@Suppress("UNUSED_PARAMETER") target: View) {
        val dialog = ProfileDialog(this, loginModel)
        dialog.setLogoutListener {
            loginModel.logOut()
        }
        dialog.show()
    }

    override val unlockButton: View
        get() = unlock_button

}

