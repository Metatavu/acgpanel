package fi.metatavu.acgpanel

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import fi.metatavu.acgpanel.model.getLoginModel

class LoginLessShelvingActivity: Activity() {
    override fun onStart() {
        super.onStart()

        getLoginModel().loginLessShelving()
        val intent = Intent(this, ProductBrowserActivity::class.java)
        startActivity(intent)
        finish()
    }
}