@file:Suppress("ktlint:standard:filename")

package com.soruhavuzu.calibration

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** Elo, prior, kova hesaplama, yükleyen yanlılığı, zorluk oyları (mimari.md §5.1). Şema: `calibration`. */
@PackageInfo
@ApplicationModule(displayName = "Calibration", allowedDependencies = ["shared", "events"])
class ModuleMetadata
