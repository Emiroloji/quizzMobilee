@file:Suppress("ktlint:standard:filename")

package com.soruhavuzu.shared

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Ortak çekirdek: ID tipleri, Clock/RandomSource portları, hata tipleri, sayfalama.
 * İş mantığı içermez (mimari.md §5.1). Tüm modüller kullanabilir.
 */
@PackageInfo
@ApplicationModule(displayName = "Shared", type = ApplicationModule.Type.OPEN)
class ModuleMetadata
