@file:Suppress("ktlint:standard:filename")

package com.soruhavuzu.practice

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** Oturum, soru seçimi, cevap kaydı, yanlışlarım/favoriler, offline paket (mimari.md §5.1). Şema: `practice`. */
@PackageInfo
@ApplicationModule(displayName = "Practice", allowedDependencies = ["shared", "events", "content", "calibration", "taxonomy", "identity"])
class ModuleMetadata
