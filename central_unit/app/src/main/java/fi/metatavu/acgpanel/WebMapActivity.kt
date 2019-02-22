package fi.metatavu.acgpanel

import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.android.synthetic.main.activity_web_map.*

class WebMapActivity : PanelActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_map)
        val creds = PreferenceManager
            .getDefaultSharedPreferences(this)
            .getString(getString(R.string.pref_key_browser_credentials), "")
        webmap.loadUrl("http://tuotetiedot.metatavu.io/VendingMachineSearch/?$creds")
        webmap.webViewClient = object: WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
            override fun onLoadResource(view: WebView?, url: String?) {
                if (url?.contains(creds) != true) {
                    super.onLoadResource(view, "$url&$creds")
                } else {
                    super.onLoadResource(view, url)
                }
            }
        }
    }

    override val unlockButton: View
        get() = unlock_button

}
