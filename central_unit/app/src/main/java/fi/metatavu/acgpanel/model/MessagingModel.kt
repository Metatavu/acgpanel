package fi.metatavu.acgpanel.model

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

data class GiptoolMessage(
    var content: String
)

interface GiptoolMessagingService {
    @POST("messaging/{id}/messages")
    fun sendMessage(@Path("id") id: String, @Body message: GiptoolMessage): Call<GiptoolProductTransaction>
}

abstract class MessagingModel {

    abstract val messagingService: GiptoolMessagingService
    abstract val vendingMachineId: String
    abstract val emptyVendingMachineMessageTemplate: String

    fun sendMessage(message: String) {
        messagingService.sendMessage(
            vendingMachineId,
            GiptoolMessage(message)
        )
    }

    fun sendVendingMachineEmptyMessage(product: Product) {
        sendMessage(String.format(emptyVendingMachineMessageTemplate, vendingMachineId, product.code))
    }

}