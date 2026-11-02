package com.example.familysafetube

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** One shared client: single-connection HTTP/1.1 keeps heap usage flat on big mp4s. */
object DriveHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }
}

/** Minimal Drive v3 REST client. Every request carries the signed-in user's OAuth token. */
class DriveApi(private val http: OkHttpClient, private val tokens: DriveTokens) {

    companion object {
        const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val USER_AGENT = "FamilySafeTube"
    }

    /** Runs a request with the bearer token; on 401 (expired token) gets a fresh token and tries once more. */
    fun call(url: String, post: RequestBody? = null): Response {
        for (attempt in 0..1) {
            val b = Request.Builder().url(url)
                .header("Authorization", "Bearer ${tokens.get()}")
                .header("User-Agent", USER_AGENT)
            if (post != null) b.post(post)
            val resp = http.newCall(b.build()).execute()
            if (resp.code == 401 && attempt == 0) {
                resp.close()
                tokens.invalidate()
                continue
            }
            return resp
        }
        error("unreachable")
    }

    /** Every file matching [query], following nextPageToken. [fields] is the per-file field list. */
    fun list(query: String, fields: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var pageToken: String? = null
        do {
            val token = pageToken
            val url = buildString {
                append(FILES_URL)
                append("?q=").append(URLEncoder.encode(query, "UTF-8"))
                append("&pageSize=1000")
                append("&fields=").append(URLEncoder.encode("nextPageToken,files($fields)", "UTF-8"))
                if (token != null) append("&pageToken=").append(URLEncoder.encode(token, "UTF-8"))
            }
            call(url).use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} listing Drive")
                val json = JSONObject(resp.body.string())
                val arr: JSONArray? = json.optJSONArray("files")
                if (arr != null) for (i in 0 until arr.length()) out.add(arr.getJSONObject(i))
                pageToken = json.optString("nextPageToken", "").ifEmpty { null }
            }
        } while (pageToken != null)
        return out
    }

    fun listFolders(parentId: String): List<DriveLayout.Folder> =
        list("'$parentId' in parents and mimeType = '$FOLDER_MIME' and trashed = false", "id,name,createdTime")
            .map { DriveLayout.Folder(it.getString("id"), it.getString("name"), it.optString("createdTime", "")) }

    fun createFolder(name: String, parentId: String): DriveLayout.Folder {
        val body = JSONObject()
            .put("name", name)
            .put("mimeType", FOLDER_MIME)
            .put("parents", JSONArray().put(parentId))
            .toString()
            .toRequestBody("application/json".toMediaType())
        call("$FILES_URL?fields=id,name,createdTime", body).use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} creating folder $name")
            val j = JSONObject(resp.body.string())
            return DriveLayout.Folder(j.getString("id"), j.getString("name"), j.optString("createdTime", ""))
        }
    }

    fun resolveLayout(): DriveLayout.Resolved = DriveLayout.resolve(::listFolders)

    fun ensureLayout(): DriveLayout.Resolved = DriveLayout.ensure(::listFolders, ::createFolder)
}
