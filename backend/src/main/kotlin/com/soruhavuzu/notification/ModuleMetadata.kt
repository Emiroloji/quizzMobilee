@file:Suppress("ktlint:standard:filename")

package com.soruhavuzu.notification

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** Push gönderimi, bildirim kaydı (mimari.md §5.1). Şema: `notification`. */
@PackageInfo
@ApplicationModule(displayName = "Notification", allowedDependencies = ["shared", "events", "identity"])
class ModuleMetadata
