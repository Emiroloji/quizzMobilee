package com.soruhavuzu

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class SoruhavuzuApiApplication

fun main(args: Array<String>) {
	runApplication<SoruhavuzuApiApplication>(*args)
}
