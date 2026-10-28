@file:Suppress("ktlint:standard:filename")

package com.soruhavuzu.stats

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** Kullanıcı istatistikleri, günlük aktivite (mimari.md §5.1). Şema: `stats`. */
@PackageInfo
@ApplicationModule(displayName = "Stats", allowedDependencies = ["shared", "events", "taxonomy"])
class ModuleMetadata
