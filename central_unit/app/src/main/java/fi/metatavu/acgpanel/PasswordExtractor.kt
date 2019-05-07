package fi.metatavu.acgpanel

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import com.google.common.io.BaseEncoding
import fi.metatavu.acgpanel.model.getLoginModel
import java.nio.charset.StandardCharsets
import kotlin.experimental.xor

class PasswordExtractor(
        val context: Context,
        val startActivityForResult: (Intent, Int) -> Unit,
        val onPasswordExtracted: () -> Unit
) {

    private fun getKey(): ByteArray =
        context.resources.openRawResource(R.raw.key).use {
            BaseEncoding
                .base16()
                .decode(it.readBytes().toString(StandardCharsets.UTF_8).trim())
        }

    fun requestEncryptedPassword() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        startActivityForResult(intent, REQUEST_ENCRYPTED_PASSWORD_RESULT_CODE)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ENCRYPTED_PASSWORD_RESULT_CODE &&
                resultCode == RESULT_OK &&
                data != null) {
            context.contentResolver.openInputStream(data.data).use {
                val fileBytes = BaseEncoding
                    .base16()
                    .decode(it.readBytes().toString(StandardCharsets.UTF_8).trim())
                val offset = (fileBytes[0].toInt() and 0xFF) * 255 + (fileBytes[1].toInt() and 0xFF)
                val passwordBytes = fileBytes.drop(2).toByteArray()
                val keyBytes = getKey()
                val decryptedBytes = passwordBytes.mapIndexed { index, byte ->
                    keyBytes[(offset + index) % keyBytes.size] xor byte
                }.toByteArray()
                getLoginModel().password = decryptedBytes.toString(StandardCharsets.UTF_8)
                onPasswordExtracted()
            }
        }
    }

    companion object {
        private const val REQUEST_ENCRYPTED_PASSWORD_RESULT_CODE = 1
    }

}