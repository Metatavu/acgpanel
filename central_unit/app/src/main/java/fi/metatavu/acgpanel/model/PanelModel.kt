package fi.metatavu.acgpanel.model

import android.arch.persistence.room.*
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface GiptoolService {
    @GET("products/vendingMachine/{vendingMachineId}")
    fun listProducts(@Path("vendingMachineId") vendingMachineId: String): Call<GiptoolProducts>

    @GET("users/vendingMachine/{vendingMachineId}")
    fun listUsers(@Path("vendingMachineId") vendingMachineId: String): Call<GiptoolUsers>

    @POST("productTransactions/")
    fun sendProductTransaction(@Body productTransaction: GiptoolProductTransaction): Call<GiptoolProductTransaction>

    @POST("logInAttempts/")
    fun sendLogInAttempt(@Body productTransaction: GiptoolLogInAttempt): Call<GiptoolLogInAttempt>
}

@Dao
interface SystemPropertiesDao {

    @Insert
    fun insert(systemProperties: SystemProperties)

    @Query("DELETE FROM systemproperties")
    fun clear()

    @Query("SELECT * FROM systemproperties WHERE id=1")
    fun getSystemProperties(): SystemProperties?

    @Query("SELECT * FROM compartmentmapping WHERE line=:line")
    fun getCompartmentMapping(line: String): CompartmentMapping?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun mapCompartments(vararg compartmentMappings: CompartmentMapping)

}

