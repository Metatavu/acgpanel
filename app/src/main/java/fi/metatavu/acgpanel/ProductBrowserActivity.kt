package fi.metatavu.acgpanel

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.PagerSnapHelper
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import kotlinx.android.synthetic.main.activity_product_browser.*
import java.util.Collections.nCopies

class LinePagerIndicatorDecoration : RecyclerView.ItemDecoration() {

    private val colorActive = -0x1
    private val colorInactive = 0x66FFFFFF

    /**
     * Height of the space the indicator takes up at the bottom of the view.
     */
    private val mIndicatorHeight = (DP * 16).toInt()

    /**
     * Indicator stroke width.
     */
    private val mIndicatorStrokeWidth = DP * 2

    /**
     * Indicator width.
     */
    private val mIndicatorItemLength = DP * 16
    /**
     * Padding between indicators.
     */
    private val mIndicatorItemPadding = DP * 4

    /**
     * Some more natural animation interpolation
     */
    private val mInterpolator = AccelerateDecelerateInterpolator()

    private val mPaint = Paint()

    init {
        mPaint.setStrokeCap(Paint.Cap.ROUND)
        mPaint.setStrokeWidth(mIndicatorStrokeWidth)
        mPaint.setStyle(Paint.Style.STROKE)
        mPaint.setAntiAlias(true)
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)

        val itemCount = parent.adapter!!.itemCount
        // center horizontally, calculate width and subtract half from center
        val totalLength = mIndicatorItemLength * itemCount
        val paddingBetweenItems = Math.max(0, itemCount - 1) * mIndicatorItemPadding
        val indicatorTotalWidth = totalLength + paddingBetweenItems
        val indicatorStartX = (parent.width - indicatorTotalWidth) / 2f
        // center vertically in the allotted space
        val indicatorPosY = parent.height - mIndicatorHeight / 2f
        drawInactiveIndicators(c, indicatorStartX, indicatorPosY, itemCount)
        // find active page (which should be highlighted)
        val layoutManager = parent.layoutManager as GridLayoutManager?
        val activePosition = layoutManager!!.findFirstVisibleItemPosition()
        if (activePosition == RecyclerView.NO_POSITION) {
            return
        }
        // find offset of active page (if the user is scrolling)
        val activeChild = layoutManager!!.findViewByPosition(activePosition)
        val left = activeChild!!.getLeft()
        val width = activeChild!!.getWidth()
        // on swipe the active item will be positioned from [-width, 0]
        // interpolate offset for smooth animation
        val progress = mInterpolator.getInterpolation(left * -1 / width.toFloat())
        drawHighlights(c, indicatorStartX, indicatorPosY, activePosition, progress, itemCount)
    }

    private fun drawInactiveIndicators(
        c: Canvas,
        indicatorStartX: Float,
        indicatorPosY: Float,
        itemCount: Int
    ) {
        mPaint.setColor(colorInactive)

        // width of item indicator including padding
        val itemWidth = mIndicatorItemLength + mIndicatorItemPadding

        var start = indicatorStartX
        for (i in 0 until itemCount) {
            // draw the line for every item
            c.drawLine(start, indicatorPosY, start + mIndicatorItemLength, indicatorPosY, mPaint)
            start += itemWidth
        }
    }

    private fun drawHighlights(
        c: Canvas, indicatorStartX: Float, indicatorPosY: Float,
        highlightPosition: Int, progress: Float, itemCount: Int
    ) {
        mPaint.setColor(colorActive)
        // width of item indicator including padding
        val itemWidth = mIndicatorItemLength + mIndicatorItemPadding
        if (progress == 0f) {
            // no swipe, draw a normal indicator
            val highlightStart = indicatorStartX + itemWidth * highlightPosition
            c.drawLine(
                highlightStart, indicatorPosY,
                highlightStart + mIndicatorItemLength, indicatorPosY, mPaint
            )
        } else {
            var highlightStart = indicatorStartX + itemWidth * highlightPosition
            // calculate partial highlight
            val partialLength = mIndicatorItemLength * progress
            // draw the cut off highlight
            c.drawLine(
                highlightStart + partialLength, indicatorPosY,
                highlightStart + mIndicatorItemLength, indicatorPosY, mPaint
            )
            // draw the highlight overlapping to the next item as well
            if (highlightPosition < itemCount - 1) {
                highlightStart += itemWidth
                c.drawLine(
                    highlightStart, indicatorPosY,
                    highlightStart + partialLength, indicatorPosY, mPaint
                )
            }
        }
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        outRect.bottom = mIndicatorHeight
    }

    companion object {
        private val DP = Resources.getSystem().getDisplayMetrics().density
    }
}

data class Product(val name: String = "")

fun productView(context: Context, product: Product): View {
    return View.inflate(context, R.layout.view_product, null)!!
}

class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {

}

class ProductItemCallback : DiffUtil.ItemCallback<Product>() {
    override fun areContentsTheSame(a: Product, b: Product): Boolean {
        return a == b
    }

    override fun areItemsTheSame(a: Product, b: Product): Boolean {
        return a == b
    }
}

class ProductAdapter : ListAdapter<Product, ProductViewHolder>(ProductItemCallback()) {
    override fun onCreateViewHolder(viewGroup: ViewGroup, index: Int): ProductViewHolder {
        return ProductViewHolder(productView(viewGroup.context, getItem(index)))
    }

    override fun onBindViewHolder(holder: ProductViewHolder, index: Int) {
    }
}

class ProductBrowserActivity : KioskActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_browser)
        val adapter = ProductAdapter()
        products_view.adapter = adapter;
        adapter.submitList(nCopies(20, Product()))
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(products_view)
        products_view.addItemDecoration(LinePagerIndicatorDecoration())
    }

    override val unlockButton: View
        get() = unlock_button;
}

