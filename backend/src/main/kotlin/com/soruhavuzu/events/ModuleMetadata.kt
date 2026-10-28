@file:Suppress("ktlint:standard:filename")

package com.soruhavuzu.events

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Modüller arası olay sözleşmeleri (ADR 0013). Olaylar `events.<sahip-modul>` alt paketinde,
 * değişmez data class olarak tanımlanır; yalnızca sahibi olan modül yayınlar.
 */
@PackageInfo
@ApplicationModule(displayName = "Events", type = ApplicationModule.Type.OPEN, allowedDependencies = ["shared"])
class ModuleMetadata
