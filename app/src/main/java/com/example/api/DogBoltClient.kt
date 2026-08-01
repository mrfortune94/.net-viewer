package com.example.api

import android.webkit.MimeTypeMap
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class MyCookieJar : CookieJar {
    private val cookies = mutableMapOf<String, Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            this.cookies[cookie.name] = cookie
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookies.values.toList()
    }

    fun getCsrfToken(): String? {
        return cookies["csrftoken"]?.value
    }
}

object DogBoltClient {
    private const val BASE_URL = "https://dogbolt.org/"

    private val cookieJar = MyCookieJar()

    private val okHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val api: DogBoltApi = retrofit.create(DogBoltApi::class.java)

    suspend fun uploadFile(file: File): Pair<UploadResponse?, String?> {
        try {
            // 1. Get CSRF Token
            val homeResponse = api.getHome()
            homeResponse.close()
            
            val csrfToken = cookieJar.getCsrfToken()
                ?: return Pair(null, "Failed to get CSRF token")

            // 2. Upload File
            val extension = MimeTypeMap.getFileExtensionFromUrl(file.path)
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
            
            val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
            
            val response = api.uploadBinary(csrfToken = csrfToken, file = part)
            return Pair(response, null)
        } catch (e: retrofit2.HttpException) {
            e.printStackTrace()
            val errorBody = e.response()?.errorBody()?.string()
            return Pair(null, "HTTP ${e.code()}: $errorBody")
        } catch (e: Exception) {
            e.printStackTrace()
            return Pair(null, e.message ?: "Unknown error")
        }
    }
}
