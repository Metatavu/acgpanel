package fi.metatavu.acgpanel

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.activity_import_password.*

class ImportPasswordActivity : Activity() {

    private val passwordExtractor = PasswordExtractor(
        this,
        ::startActivityForResult,
        ::onPasswordExtracted
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_password)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        passwordExtractor.onActivityResult(requestCode, resultCode, data)
    }

    fun onLoadPasswordClick(@Suppress("UNUSED_PARAMETER") view: View) {
        passwordExtractor.requestEncryptedPassword()
    }

    fun onPasswordExtracted() {
        prompt_text.append("\n\n")
        prompt_text.append(getString(R.string.password_extracted));
    }

}
