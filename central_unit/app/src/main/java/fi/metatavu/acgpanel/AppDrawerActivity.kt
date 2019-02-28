package fi.metatavu.acgpanel

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.activity_app_drawer.*
import kotlinx.android.synthetic.main.view_app.view.*

data class App (
    val label: CharSequence,
    val packageName: CharSequence,
    val name: CharSequence,
    val icon: Drawable
)

internal fun appView(context: Context): View =
    View.inflate(context, R.layout.view_app, null)

class AppViewHolder(val context: Context): RecyclerView.ViewHolder(appView(context)) {

    fun populate(app: App) {
        with (itemView) {
            app_image.setImageDrawable(app.icon)
            app_name.text = app.label
            app_button.setOnClickListener {
                val intent = Intent()
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
                intent.action = Intent.ACTION_MAIN
                intent.component = ComponentName(
                    app.packageName.toString(),
                    app.name.toString())
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        }
    }

}

class AppItemCallback : DiffUtil.ItemCallback<App>() {
    override fun areContentsTheSame(a: App, b: App): Boolean {
        return a == b
    }

    override fun areItemsTheSame(a: App, b: App): Boolean {
        return a == b
    }
}

class AppAdapter : ListAdapter<App, AppViewHolder>(AppItemCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        return AppViewHolder(parent.context)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.populate(getItem(position))
    }
}

class AppDrawerActivity : Activity() {

    private val adapter = AppAdapter()

    private val PACKAGE_BLACKLIST = listOf(
        "com.android.calendar",
        "com.android.chrome",
        "com.android.contacts",
        "com.android.deskclock",
        "com.android.dialer",
        "com.android.gallery3d",
        "com.android.vending",
        "com.example.android.rssreader",
        "com.google.android.gm",
        "com.google.android.youtube",
        "org.lineageos.eleven",
        "com.android.calculator2",
        "com.example.android.notepad",
        "com.google.android.googlequicksearchbox"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_drawer)
        apps_view.layoutManager = GridLayoutManager(this, 7)
        apps_view.adapter = adapter
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        adapter.submitList(
            packageManager
                .queryIntentActivities(intent, 0)
                .map {
                    Log.i(javaClass.name, it.activityInfo.packageName)
                    Log.i(javaClass.name, it.activityInfo.name)
                    App(
                        it.loadLabel(packageManager),
                        it.activityInfo.packageName,
                        it.activityInfo.name,
                        it.activityInfo.loadIcon(packageManager)
                    )
                }
                .filter {
                    !(it.packageName in PACKAGE_BLACKLIST)
                }
        )
    }

    override fun onPause() {
        super.onPause()
        Handler(mainLooper).postDelayed({
            finish()
        }, 1)
    }

}
