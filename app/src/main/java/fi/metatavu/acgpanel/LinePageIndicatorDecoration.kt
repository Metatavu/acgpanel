package fi.metatavu.acgpanel

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View


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
