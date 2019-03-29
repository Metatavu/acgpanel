package fi.metatavu.acgpanel

import android.content.Context
import android.content.Intent

class PasswordExtractor(val context: Context) {

    private fun getKey(): ByteArray =
        context.resources.openRawResource(R.raw.key).use {
            it.readBytes()
        }

    private fun getEncryptedPassword(): ByteArray {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/octet-stream"
        context.startActivity(intent)
        return byteArrayOf()
    }

}