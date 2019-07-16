package fi.metatavu.acgpanel

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.widget.ImageView
import java.io.File
import java.io.Serializable

class FileSystemImageView: ImageView, Serializable {

    private var sourceImage: String? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet): super(context, attrs) {
        sourceImage = attrs?.getAttributeValue("http://www.metatavu.fi/acgpanel", "source") ?: null
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr) {
        sourceImage = attrs?.getAttributeValue("http://www.metatavu.fi/acgpanel", "source") ?: null
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int): super(context, attrs, defStyleAttr, defStyleRes) {
        sourceImage = attrs?.getAttributeValue("http://www.metatavu.fi/acgpanel", "source") ?: null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val image = sourceImage
        if (image != null) {
            val file = File(context.filesDir, sourceImage)
            if (file.exists()) {
                setImageURI(Uri.fromFile(file))
            }
        }
    }

}