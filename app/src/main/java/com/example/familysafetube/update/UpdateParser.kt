package com.example.familysafetube.update

import com.example.familysafetube.JsonException
import com.example.familysafetube.MiniJson
import java.util.Locale

/** What the app needs to know about a published build. */
data class ReleaseInfo(
    /** "1.3.0" - from the file name FamilySafeTube-1_3_0-build101.apk. */
    val versionName: String,
    /** The build number (= versionCode); a build is newer when this is larger than ours. */
    val build: Int,
    /** Release notes with the "[required]" marker removed. */
    val notes: String,
    /** True when the release notes contain "[required]": the app asks to install it right away. */
    val required: Boolean,
    /** Public download address (works for public repositories). */
    val apkUrl: String,
    /** API address of the same file (needed, with a token, for private repositories). */
    val apkApiUrl: String?,
    val sizeBytes: Long,
    /** The ".sha256" file published next to the APK, when present. */
    val sha256Url: String?,
    val sha256ApiUrl: String?,
    val publishedAt: String?
)

/** Pure JSON -> [ReleaseInfo] logic (unit-tested; no Android types). */
object UpdateParser {
    /** FamilySafeTube-1_3_0-build101.apk -> ("1_3_0", "101") */
    private val APK_NAME = Regex("""^.+-(\d+(?:_\d+)*)-build(\d+)\.apk$""", RegexOption.IGNORE_CASE)
    private val SHA_HEX = Regex("""\b[a-fA-F0-9]{64}\b""")
    private val REQUIRED = Regex("""\[required]""", RegexOption.IGNORE_CASE)
    private val EMPTY_LINES = Regex("""\n{3,}""")

    /** Parses GitHub's "latest release" JSON. Null when there is no APK asset (or the JSON isn't a release). */
    fun parseRelease(json: String): ReleaseInfo? {
        val root = try { MiniJson.parse(json) as? Map<*, *> } catch (_: JsonException) { null } ?: return null
        if (root["draft"] == true) return null
        val assets = (root["assets"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: return null
        fun Map<*, *>.str(key: String): String? = this[key] as? String

        val found = assets.firstNotNullOfOrNull { asset ->
            val name = asset.str("name") ?: return@firstNotNullOfOrNull null
            APK_NAME.matchEntire(name)?.let { match -> asset to match }
        } ?: return null
        val apk = found.first
        val match = found.second
        val apkName = apk.str("name") ?: return null
        val build = match.groupValues[2].toIntOrNull() ?: return null
        val sha = assets.firstOrNull { it.str("name") == "$apkName.sha256" }
        val body = root.str("body").orEmpty()

        return ReleaseInfo(
            versionName = match.groupValues[1].replace('_', '.'),
            build = build,
            notes = body.replace("\r\n", "\n").replace(REQUIRED, "").replace(EMPTY_LINES, "\n\n").trim(),
            required = REQUIRED.containsMatchIn(body),
            apkUrl = apk.str("browser_download_url") ?: return null,
            apkApiUrl = apk.str("url"),
            sizeBytes = (apk["size"] as? Number)?.toLong() ?: -1L,
            sha256Url = sha?.str("browser_download_url"),
            sha256ApiUrl = sha?.str("url"),
            publishedAt = root.str("published_at")
        )
    }

    /** First 64-hex-digit value in a ".sha256" file ("<hash>  <file name>"). */
    fun parseSha256(text: String): String? = SHA_HEX.find(text)?.value?.lowercase(Locale.ROOT)

    fun isNewer(releaseBuild: Int, currentBuild: Int) = releaseBuild > currentBuild

    /** "50.0 MB" / "512 KB" / "" (unknown). Always Latin digits, so the text is the same in every language. */
    fun humanSize(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
        else -> "${bytes / 1024} KB"
    }
}
