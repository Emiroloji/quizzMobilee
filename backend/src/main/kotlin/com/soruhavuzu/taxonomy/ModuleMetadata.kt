@file:Suppress("ktlint:standard:filename")

package com.soruhavuzu.taxonomy

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** Sınav ve konu ağacı (mimari.md §5.1). Şema: `taxonomy`. */
@PackageInfo
@ApplicationModule(displayName = "Taxonomy", allowedDependencies = ["shared", "events"])
class ModuleMetadata
