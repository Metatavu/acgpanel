package fi.metatavu.acgpanel

import android.content.Context
import android.content.Intent
import android.content.res.Resources
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
import android.widget.TextView
import fi.metatavu.acgpanel.model.Product
import fi.metatavu.acgpanel.model.ProductPage
import kotlinx.android.synthetic.main.activity_product_browser.*
import java.util.Collections.nCopies

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
        // center horizontally, calculate width and subtract half from center
        val totalLength = itemSpacing * itemCount
        val indicatorStartX = (parent.width - totalLength) / 2f
        // center vertically in the allotted space
        val indicatorPosY = parent.height - indicatorHeight / 2f
        // find active page (which should be highlighted)
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
        private val DP = Resources.getSystem().getDisplayMetrics().density
    }
}

internal fun productPageView(context: Context): View {
    return View.inflate(context, R.layout.view_product_page, null)!!
}

class ProductPageViewHolder(context: Context) : RecyclerView.ViewHolder(productPageView(context)) {

    fun populate(item: ProductPage, onProductClick: (Product) -> Unit) {
        with (itemView) {
            for (i in 0..5) {
                if (i < item.products.size) {
                    val product = item.products[i]
                    findViewById<View>(parentViewIds[i]).visibility = View.VISIBLE
                    findViewById<TextView>(textViewIds[i]).text = product.name
                    findViewById<Button>(buttonIds[i]).setOnClickListener {
                        onProductClick(product)
                    }
                } else {
                    findViewById<View>(parentViewIds[i]).visibility = View.INVISIBLE
                }
            }
        }
    }

    companion object {

        private val parentViewIds = arrayOf(
            R.id.product0,
            R.id.product1,
            R.id.product2,
            R.id.product3,
            R.id.product4,
            R.id.product5
        )

        private val textViewIds = arrayOf(
            R.id.product0_text,
            R.id.product1_text,
            R.id.product2_text,
            R.id.product3_text,
            R.id.product4_text,
            R.id.product5_text
        )

        private val buttonIds = arrayOf(
            R.id.product0_button,
            R.id.product1_button,
            R.id.product2_button,
            R.id.product3_button,
            R.id.product4_button,
            R.id.product5_button
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
            model.currentProduct = it
            val intent = Intent(this, ProductSelectionActivity::class.java)
            startActivity(intent)
        }
        products_view.adapter = adapter;
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
        snapHelper.attachToRecyclerView(products_view)
        products_view.addItemDecoration(LinePagerIndicatorDecoration())
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
        var distance: Int
        if (edge) {
            val difference = products_view.width - itemWidth
            distance = itemWidth - difference/2
        } else {
            distance = itemWidth
        }
        products_view.stopScroll()
        products_view.smoothScrollBy(direction * distance, 0)
    }

    fun menu(@Suppress("UNUSED_PARAMETER") target: View) {
        val intent = Intent(this, MenuActivity::class.java)
        finish()
        startActivity(intent)
    }

    fun nextPage(@Suppress("UNUSED_PARAMETER") target: View) {
        val layoutManager = products_view.layoutManager as LinearLayoutManager
        val itemNumber = layoutManager.findFirstCompletelyVisibleItemPosition()
        scrollPage(layoutManager, itemNumber == 0, 1)
    }

    fun previousPage(@Suppress("UNUSED_PARAMETER") target: View) {
        val layoutManager = products_view.layoutManager as LinearLayoutManager
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

    override fun onDestroy() {
        super.onDestroy()
    }

    override val unlockButton: View
        get() = unlock_button;

}

