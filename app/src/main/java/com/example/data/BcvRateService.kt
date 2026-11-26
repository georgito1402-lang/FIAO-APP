package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

data class DolarApiOficialResponse(
    val fuente: String? = null,
    val nombre: String? = null,
    val promedio: Double? = null,
    val fechaActualizacion: String? = null
)

interface DolarApiService {
    @GET("v1/dolares/oficial")
    suspend fun getOficialRate(): DolarApiOficialResponse
}

class BcvRateService {
    private val api: DolarApiService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        Retrofit.Builder()
            .baseUrl("https://ve.dolarapi.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DolarApiService::class.java)
    }

    suspend fun fetchOfficialBcvPromedio(): Double? {
        val response = api.getOficialRate()
        val promedio = response.promedio
        return if (promedio != null && !promedio.isNaN() && !promedio.isInfinite() && promedio > 0.0) {
            promedio
        } else {
            null
        }
    }
}
