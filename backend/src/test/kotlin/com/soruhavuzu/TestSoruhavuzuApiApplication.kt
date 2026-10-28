package com.soruhavuzu

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<SoruhavuzuApiApplication>().with(TestcontainersConfiguration::class).run(*args)
}
