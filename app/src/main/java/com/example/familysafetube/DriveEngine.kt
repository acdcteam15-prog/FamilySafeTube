package com.example.familysafetube

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

object DriveEngine {

    // IMPORTANT: If you see "403 Forbidden", ensure the Google Drive folder has "Anyone with the link can view" enabled.
    private const val API_KEY = "AIzaSyB4Yizp5GYUdWuEKI__x1XBN9c_hekb4QA"
    private val client = OkHttpClient()

    // --- EXISTING VIDEO FUNCTION ---
    fun fetchVideoUrls(callback: (List<String>, String?) -> Unit) {
        val folderId = "1KRE3OHgNn4GCiuw_1P5jTWmaHsepsxjS"
        // Broadened search: looks for video mimeType OR common video extensions in the name
        val rawQuery = "'$folderId' in parents and (mimeType contains 'video/' or name contains '.mp4' or name contains '.mkv') and trashed = false"
        executeFetch(rawQuery, isVideo = true, callback)
    }

    // --- NEW DYNAMIC IMAGE FUNCTION ---
    fun fetchImageUrls(targetFolderId: String, callback: (List<String>, String?) -> Unit) {
        // FIXED: Added mimeType constraint so Google Drive ONLY returns image files inside this specific target folder
        val rawQuery = "'$targetFolderId' in parents and mimeType contains 'image/' and trashed = false"
        executeFetch(rawQuery, isVideo = false, callback)
    }

    private fun executeFetch(rawQuery: String, isVideo: Boolean, callback: (List<String>, String?) -> Unit) {
        val urls = mutableListOf<String>()
        val encodedQuery = URLEncoder.encode(rawQuery, "UTF-8").replace("+", "%20")
        val urlString = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&key=$API_KEY"

        val request = Request.Builder()
            .url(urlString)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(emptyList(), "Network Failure: ${e.localizedMessage}")
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val responseData = response.body?.string()
                    if (response.isSuccessful && !responseData.isNullOrEmpty()) {
                        try {
                            val jsonResponse = JSONObject(responseData)
                            val filesArray = jsonResponse.getJSONArray("files")

                            if (filesArray.length() == 0) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    callback(emptyList(), "No files found for query: $rawQuery")
                                }
                                return
                            }

                            for (i in 0 until filesArray.length()) {
                                val fileObj = filesArray.getJSONObject(i)
                                val fileId = fileObj.getString("id")
                                // Directly use the media download link with the API key
                                urls.add("https://www.googleapis.com/drive/v3/files/$fileId?alt=media&key=$API_KEY")
                            }
                            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(urls, null) }
                            return
                        } catch (e: Exception) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(emptyList(), "Parsing Error: ${e.localizedMessage}") }
                            return
                        }
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        callback(emptyList(), "Google API Error: ${responseData ?: "Status Code ${response.code}"}")
                    }
                }
            }
        })
    }

    // --- YOUTUBE CONFIGURATION PARSER ---
    fun fetchYoutubeIds(callback: (List<String>, String?) -> Unit) {
        val folderId = "1KRE3OHgNn4GCiuw_1P5jTWmaHsepsxjS"
        val rawQuery = "'$folderId' in parents and name = 'Youtube.txt' and trashed = false"
        val encodedQuery = URLEncoder.encode(rawQuery, "UTF-8").replace("+", "%20")

        // Simplified URL: Removed the fields parameter temporarily to rule out field-mask issues causing 404
        val urlString = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&key=$API_KEY"

        val request = Request.Builder()
            .url(urlString)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(emptyList(), "Metadata Network Failure: ${e.localizedMessage}")
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            callback(emptyList(), "Google API Error (Metadata): Status Code ${response.code}")
                        }
                        return
                    }
                    try {
                        val json = JSONObject(bodyString)
                        val files = json.getJSONArray("files")
                        if (files.length() > 0) {
                            val fileId = files.getJSONObject(0).getString("id")
                            val fileName = files.getJSONObject(0).getString("name")

                            val fileRequest = Request.Builder()
                                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media&key=$API_KEY")
                                .header("Referer", "https://www.youtube.com")
                                .build()

                            client.newCall(fileRequest).enqueue(object : okhttp3.Callback {
                                override fun onFailure(fileCall: okhttp3.Call, fileEx: IOException) {
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        callback(emptyList(), "File Download Failure ($fileName): ${fileEx.localizedMessage}")
                                    }
                                }

                                override fun onResponse(fileCall: okhttp3.Call, fileResponse: okhttp3.Response) {
                                    fileResponse.use {
                                        val textContent = fileResponse.body?.string() ?: ""
                                        if (!fileResponse.isSuccessful) {
                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                callback(emptyList(), "Error downloading $fileName: Status ${fileResponse.code}")
                                            }
                                            return
                                        }

                                        val idList = textContent.lines()
                                            .map { it.trim() }
                                            .filter { it.isNotEmpty() }
                                            .map { line ->
                                                // Extract 11-char ID from various YouTube URL formats
                                                when {
                                                    line.contains("shorts/") -> line.substringAfter("shorts/").take(11)
                                                    line.contains("v=") -> line.substringAfter("v=").take(11)
                                                    line.contains("youtu.be/") -> line.substringAfter("youtu.be/").take(11)
                                                    else -> line // Assume it's already an ID
                                                }
                                            }

                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            callback(idList, null)
                                        }
                                    }
                                }
                            })
                        } else {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                callback(emptyList(), "File 'Youtube.txt' not found in folder $folderId")
                            }
                        }
                    } catch (e: Exception) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            callback(emptyList(), "Parsing Error: ${e.localizedMessage}")
                        }
                    }
                }
            }
        })
    }
}
