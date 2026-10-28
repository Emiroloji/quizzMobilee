@file:Suppress("ktlint:standard:filename")

package com.soruhavuzu.media

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** Varlık kaydı, pre-signed URL, medya işleme sonuçları (mimari.md §5.1). Şema: `media`. */
@PackageInfo
@ApplicationModule(displayName = "Media", allowedDependencies = ["shared", "events"])
class ModuleMetadata
