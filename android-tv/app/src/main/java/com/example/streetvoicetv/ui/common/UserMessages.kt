package com.example.streetvoicetv.ui.common

import android.util.Log
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Fixed, user-facing (繁體中文) messages. Raw exception details are only logged. */
object UserMessages {
    const val TIMEOUT = "連線逾時，請稍後再試"
    const val NETWORK = "網路連線失敗，請檢查網路後再試"
    const val LOAD_FAILED = "無法載入內容，請稍後再試"
    const val SEARCH_FAILED = "搜尋失敗，請稍後再試"
    const val PLAYBACK_FAILED = "無法播放這首歌曲，請稍後再試"
    const val NOT_FOUND = "找不到這個內容"
    const val PROFILE_FAILED = "無法載入個人資料，請稍後再試"
    const val UPDATE_FAILED = "無法下載更新，請檢查網路連線後再試"
    const val INSTALL_FAILED = "無法啟動安裝程式，請稍後再試"
    const val INSTALL_PERMISSION = "請在設定中允許本應用程式「安裝不明應用程式」，返回後按「安裝」。"
    const val SESSION_EXPIRED = "登入資訊已失效，請重新登入"
    const val PARTIAL_LOAD_FAILED = "部分內容無法載入"
    const val FOLLOW_FAILED = "無法更新追蹤狀態，請稍後再試"
    const val LIKE_FAILED = "無法更新喜歡狀態，請稍後再試"
}

/**
 * Maps [error] to a fixed user-facing message and logs the technical details.
 * [fallback] is used for errors that are not network related.
 */
fun userMessageFor(
    tag: String,
    error: Throwable?,
    fallback: String = UserMessages.LOAD_FAILED,
): String {
    logError(tag, error)
    return when (error) {
        is SocketTimeoutException -> UserMessages.TIMEOUT
        is UnknownHostException, is ConnectException, is SSLException -> UserMessages.NETWORK
        is IOException -> UserMessages.NETWORK
        else -> fallback
    }
}

private fun logError(tag: String, error: Throwable?) {
    // android.util.Log is a stub on plain JVM unit tests; never let logging crash.
    try {
        Log.w(tag, "Request failed", error)
    } catch (_: RuntimeException) {
    }
}
