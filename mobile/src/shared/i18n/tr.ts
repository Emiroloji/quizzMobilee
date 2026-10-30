/** Kullanıcıya görünen tüm metinler; JSX içinde düz metin yazılmaz (kurallar.md §6). */
export const tr = {
  common: {
    appName: "SoruHavuzu",
    loading: "Yükleniyor…",
    retry: "Tekrar dene",
    error: "Bir şeyler ters gitti.",
  },
  auth: {
    title: "Giriş yap",
    subtitle: "Seviyene uygun soruları çözmeye başla.",
    continue: "Giriş yap veya kayıt ol",
    devSubjectLabel: "Geliştirici kimliği",
    devSubjectHint: "Yerel backend'in sahte JWT üreticisi için herhangi bir kimlik",
    devSignIn: "Geliştirici olarak giriş yap",
    signOut: "Çıkış yap",
    failed: "Giriş yapılamadı. Lütfen tekrar deneyin.",
  },
  profile: {
    greeting: "Merhaba, {handle}",
    role: "Rol: {role}",
    memberSince: "Üyelik: {date}",
  },
  dev: {
    sampleQuestion: "Örnek soru (KaTeX)",
  },
} as const;

export type Dictionary = typeof tr;
