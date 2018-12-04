package fi.metatavu.acgpanel

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.PagerSnapHelper
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import fi.metatavu.acgpanel.model.BasketItem
import fi.metatavu.acgpanel.model.Product
import fi.metatavu.acgpanel.model.ProductPage
import kotlinx.android.synthetic.main.activity_product_browser.*

class LinePagerIndicatorDecoration : RecyclerView.ItemDecoration() {

    private val color = Color.rgb(0xFF, 0xFF, 0xFF) // dark: Color.rgb(0x00, 0x64, 0xa1)
    private val indicatorHeight = (DP * 48).toInt()
    private val itemSpacing = DP * 32
    private val itemRadius = DP * 6
    private val activeItemRadius = DP * 12
    private val paint = Paint()
    private var activePosition: Int = 0

    init {
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)

        val itemCount = parent.adapter!!.itemCount
        val totalLength = itemSpacing * itemCount
        val indicatorStartX = (parent.width - totalLength) / 2f
        val indicatorPosY = parent.height - indicatorHeight / 2f
        val layoutManager = parent.layoutManager as LinearLayoutManager
        val position = layoutManager.findFirstCompletelyVisibleItemPosition()
        if (position != RecyclerView.NO_POSITION) {
            activePosition = position
        }
        drawIndicators(c, indicatorStartX, indicatorPosY, activePosition, itemCount)
    }

    private fun drawIndicators(
        c: Canvas,
        indicatorStartX: Float,
        indicatorPosY: Float,
        currentItem: Int,
        itemCount: Int
    ) {
        paint.color = color

        var start = indicatorStartX
        for (i in 0 until itemCount) {
            // draw the line for every item
            val radius = if (currentItem == i) activeItemRadius else itemRadius
            c.drawCircle(start + itemSpacing/2, indicatorPosY, radius, paint)
            start += itemSpacing
        }
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        outRect.bottom = indicatorHeight
    }

    companion object {
        private val DP = Resources.getSystem().displayMetrics.density
    }
}

internal fun productPageView(context: Context): View {
    return View.inflate(context, R.layout.view_product_page, null)!!
}

const val PRODUCTS_PER_PAGE = 6

class ProductPageViewHolder(private val context: Context) : RecyclerView.ViewHolder(productPageView(context)) {

    private val parentViewIds = context.resources.obtainTypedArray(R.array.product_view_ids)
    private val textViewIds = context.resources.obtainTypedArray(R.array.product_view_text_ids)
    private val imageViewIds = context.resources.obtainTypedArray(R.array.product_view_image_ids)
    private val buttonIds = context.resources.obtainTypedArray(R.array.product_view_button_ids)

    private fun <T: View> getView(arr: TypedArray, i: Int): T
        = itemView.findViewById<T>(arr.getResourceId(i, -1))

    fun populate(item: ProductPage, onProductClick: (Product) -> Unit) {
        for (i in 0 until PRODUCTS_PER_PAGE) {
            if (i < item.products.size) {
                val product = item.products[i]
                getView<View>(parentViewIds, i).visibility = View.VISIBLE
                getView<TextView>(textViewIds, i).text = product.name
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_browser)
        val adapter = ProductPageAdapter()
        adapter.setProductClickListener {
            model.selectNewBasketItem(it)
            val intent = Intent(this, ProductSelectionActivity::class.java)
            startActivity(intent)
        }
        basket_items_view.adapter = adapter
        adapter.submitList(model.productPages)
        // TODO throttle/debounce
        search_box.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                model.searchTerm = s.toString()
                model.refreshProductPages {
                    adapter.submitList(model.productPages)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(basket_items_view)
        basket_items_view.addItemDecoration(LinePagerIndicatorDecoration())
        val user = model.currentUser
        if (user != null) {
            show_profile_button.visibility = View.VISIBLE
            show_profile_button.text = user.userName
        } else {
            show_profile_button.visibility = View.INVISIBLE
        }
    }

    private fun scrollPage(layoutManager: LinearLayoutManager, edge: Boolean, direction: Int) {
        val itemWidth = layoutManager.getChildAt(0)!!.width
        val distance: Int
        if (edge) {
            val difference = basket_items_view.width - itemWidth
            distance = itemWidth - difference/2
        } else {
            distance = itemWidth
        }
        basket_items_view.stopScroll()
        basket_items_view.smoothScrollBy(direction * distance, 0)
    }

    fun menu(@Suppress("UNUSED_PARAMETER") target: View) {
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
        val dialog = ProfileDialog(this, model)
        dialog.setLogoutListener {
            model.logOut()
        }
        dialog.show()
    }

    override val unlockButton: View
        get() = unlock_button

}

