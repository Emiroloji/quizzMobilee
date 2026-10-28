@file:Suppress("ktlint:standard:filename")

package com.soruhavuzu.content

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** Soru, versiyon, durum, cevap değerlendirme, içerik doğrulama, duplicate (mimari.md §5.1). Şema: `content`. */
@PackageInfo
@ApplicationModule(displayName = "Content", allowedDependencies = ["shared", "events", "identity", "taxonomy", "media", "reputation"])
class ModuleMetadata
