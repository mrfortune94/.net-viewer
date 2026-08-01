package com.example.api

import com.squareup.moshi.JsonClass
import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Url

interface DogBoltApi {
    @GET("/")
    suspend fun getHome(): okhttp3.ResponseBody

    @Multipart
    @POST("api/binaries/")
    suspend fun uploadBinary(
        @Header("X-CSRFToken") csrfToken: String,
        @Header("Referer") referer: String = "https://dogbolt.org/",
        @Header("Origin") origin: String = "https://dogbolt.org",
        @Part file: MultipartBody.Part
    ): UploadResponse

    @GET
    suspend fun getDecompilations(@Url url: String): DecompilationsResponse
}

@JsonClass(generateAdapter = true)
data class DecompilationsResponse(
    val results: List<DecompilationResult>
)

@JsonClass(generateAdapter = true)
data class UploadResponse(
    val id: String,
    val download_url: String?,
    val decompilations_url: String
)

@JsonClass(generateAdapter = true)
data class DecompilationResult(
    val id: String,
    val decompiler: DecompilerInfo,
    val error: String?,
    val download_url: String?,
    val analysis_time: Double?
)

@JsonClass(generateAdapter = true)
data class DecompilerInfo(
    val name: String,
    val version: String
)
