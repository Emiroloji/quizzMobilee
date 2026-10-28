@file:Suppress("ktlint:standard:filename")

package com.soruhavuzu.moderation

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** Şikayet, inceleme kuyruğu, anahtar şüphesi, yaptırım (mimari.md §5.1). Şema: `moderation`. */
@PackageInfo
@ApplicationModule(displayName = "Moderation", allowedDependencies = ["shared", "events", "content", "identity", "reputation"])
class ModuleMetadata
