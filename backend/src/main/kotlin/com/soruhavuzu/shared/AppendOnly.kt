package com.soruhavuzu.shared

/**
 * Değişmez/append-only tabloların repository'lerini işaretler (kurallar.md A5).
 * İşaretli tip CrudRepository olamaz ve update/delete/remove/upsert metodu tanımlayamaz (ArchitectureTests).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AppendOnly
