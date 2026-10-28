@file:Suppress("ktlint:standard:filename")

package com.soruhavuzu.reputation

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** İtibar defteri ve bakiye, yetki kapıları (mimari.md §5.1). Şema: `reputation`. */
@PackageInfo
@ApplicationModule(displayName = "Reputation", allowedDependencies = ["shared", "events"])
class ModuleMetadata
