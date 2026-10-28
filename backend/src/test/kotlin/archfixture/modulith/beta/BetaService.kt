package archfixture.modulith.beta

import archfixture.modulith.alpha.internal.AlphaSecret

/** KASITLI İHLAL (A1): başka modülün internal paketine erişim. */
class BetaService(
    val secret: AlphaSecret,
)
