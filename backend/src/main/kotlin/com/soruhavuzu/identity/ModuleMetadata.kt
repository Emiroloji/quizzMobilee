@file:Suppress("ktlint:standard:filename")

package com.soruhavuzu.identity

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** Kullanıcı, IdP eşleme, rol, cihaz/push token, hesap silme (mimari.md §5.1). Şema: `identity`. */
@PackageInfo
@ApplicationModule(displayName = "Identity", allowedDependencies = ["shared", "events"])
class ModuleMetadata
