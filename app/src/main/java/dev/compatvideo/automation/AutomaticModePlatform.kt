package dev.compatvideo.automation

import android.os.Build
import android.os.ext.SdkExtensions
import androidx.annotation.ChecksSdkIntAtLeast

internal object AutomaticModePlatform {
    @ChecksSdkIntAtLeast(api = 1, extension = Build.VERSION_CODES.R)
    fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 1
}
