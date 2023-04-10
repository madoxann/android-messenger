package com.github.madoxann.hw6.network

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ServerAPI {
    @GET("{channel}")
    suspend fun getMsg(
        @Path("channel") channel: String,
        @Query("limit") limit: Int,
        @Query("lastKnownId") lastKnown: Long,
    ): MutableList<Message>

    @GET("/thumb/{link}")
    suspend fun getImgThumb(
        @Path("link") link: String,
    ): ResponseBody

    @GET("/img/{link}")
    suspend fun getImgFull(
        @Path("link") link: String,
    ): ResponseBody

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    @POST("{channel}")
    suspend fun sendMsg(
        @Path("channel") channel: String,
        @Body msg: Message,
    ): Response<Int>

    @Multipart
    @POST("{channel}")
    suspend fun sendImg(
        @Path("channel") channel: String,
        @Part("msg") from: Message,
        @Part img: MultipartBody.Part,
    ): Response<Int>
}
