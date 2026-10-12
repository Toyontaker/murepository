package com.toyontaker.manabikko

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * プライバシーポリシー・データセーフティの 申告(データを あつめない・通信しない)と
 * 食いちがう 権限が ふえていないか 見はる。ライブラリ追加などで ふえたら ここが 落ちる。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivacyGuardTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun onlyExpectedPermissions() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions?.toSet().orEmpty()
        val forbidden = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.READ_CONTACTS",
            "com.google.android.gms.permission.AD_ID",
        )
        for (p in forbidden) assertFalse("$p must not be requested", p in requested)
        // AndroidX が 自動で つける 内部用(外部に 出ない)以外は なし
        assertEquals(setOf("${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"), requested)
    }

    @Test fun backupDisabled() {
        val app = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertFalse(app.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }
}
