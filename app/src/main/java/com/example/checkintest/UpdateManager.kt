package com.example.checkintest

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class GitHubRelease(
    val tag_name: String,
    val body: String,
    val assets: List<ReleaseAsset>
)

data class ReleaseAsset(
    val name: String,
    val browser_download_url: String
)

interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease
}

class UpdateManager(private val context: Context) {

    companion object {
        private const val GITHUB_USER = "ImMrLee"
        private const val GITHUB_REPO = "CheckInTestAndroid"
    }

    private val api: GitHubApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApi::class.java)
    }


    private fun isNewVersion(remoteVersion: String, currentVersion: String): Boolean {
        try {
            val remoteParts = remoteVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }

            for (i in 0 until minOf(remoteParts.size, currentParts.size)) {
                if (remoteParts[i] > currentParts[i]) return true
                if (remoteParts[i] < currentParts[i]) return false
            }
            return remoteParts.size > currentParts.size
        } catch (e: Exception) {
            return false
        }
    }

    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    suspend fun checkForUpdate(
        onNewVersion: (apkUrl: String, changelog: String, versionName: String) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        try {
            val release = api.getLatestRelease(GITHUB_USER, GITHUB_REPO)
            val remoteVersion = release.tag_name
            val currentVersion = getCurrentVersion()

            if (isNewVersion(remoteVersion, currentVersion)) {
                val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                if (apkAsset != null) {
                    onNewVersion(apkAsset.browser_download_url, release.body, remoteVersion)
                } else {
                    onError("未找到 APK 文件")
                }
            }
        } catch (e: Exception) {
            onError("检查更新失败: ${e.message}")
        }
    }

    fun downloadAndInstall(apkUrl: String): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setMimeType("application/vnd.android.package-archive")
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "checkin_app_update.apk"
            )
            setTitle("打卡助手更新")
            setDescription("正在下载新版本...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }

        return downloadManager.enqueue(request)
    }

    fun installApk(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}