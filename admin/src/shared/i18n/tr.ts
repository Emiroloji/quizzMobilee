/** Kullanıcıya görünen tüm metinler; JSX içinde düz metin yazılmaz (kurallar.md §6). */
export const tr = {
  common: {
    appName: "SoruHavuzu Moderasyon",
    loading: "Yükleniyor…",
    error: "Bir şeyler ters gitti.",
  },
  nav: { home: "Ana sayfa", preview: "İçerik önizleme" },
  auth: {
    title: "Moderasyon paneline giriş",
    oidc: "Giriş yap",
    devSubjectLabel: "Geliştirici kimliği",
    devSignIn: "Geliştirici olarak giriş yap",
    signOut: "Çıkış yap",
    failed: "Giriş yapılamadı.",
  },
  profile: {
    signedInAs: "{handle} olarak giriş yapıldı ({role})",
    notModerator: "Bu hesabın moderasyon yetkisi yok; moderasyon ekranları Faz 1'de bu role göre açılacak.",
  },
  preview: {
    title: "Blok-JSON önizleme",
    inputLabel: "İçerik (blok-JSON)",
    invalidJson: "JSON ayrıştırılamadı.",
    issuesTitle: "Doğrulama hataları",
    frameTitle: "Önizleme",
  },
} as const;

export type Dictionary = typeof tr;
