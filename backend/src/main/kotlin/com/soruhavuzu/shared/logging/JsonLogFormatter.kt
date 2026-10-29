package com.soruhavuzu.shared.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import org.springframework.boot.json.JsonWriter
import org.springframework.boot.logging.structured.StructuredLogFormatter

/**
 * mimari.md §18 log biçimi: timestamp, level, traceId, spanId, module, event, userId (hash).
 * Kişisel veri, token ve içerik gövdesi loglanmaz; olaya ait değerler SLF4J anahtar-değer çiftleriyle verilir:
 * `log.atInfo().addKeyValue("event", "user_registered").log("…")`.
 */
class JsonLogFormatter : StructuredLogFormatter<ILoggingEvent> {
    override fun format(event: ILoggingEvent): String {
        val fields = linkedMapOf<String, Any?>()
        fields["timestamp"] = event.instant.toString()
        fields["level"] = event.level.toString()
        fields["module"] = moduleOf(event.loggerName)
        fields["logger"] = event.loggerName
        val keyValues = event.keyValuePairs.orEmpty().associate { it.key to it.value?.toString() }
        keyValues[EVENT_KEY]?.let { fields["event"] = it }
        fields["message"] = event.formattedMessage
        val mdc = event.mdcPropertyMap.orEmpty()
        (mdc["trace_id"] ?: mdc["traceId"])?.let { fields["traceId"] = it }
        (mdc["span_id"] ?: mdc["spanId"])?.let { fields["spanId"] = it }
        mdc[USER_ID_HASH_MDC]?.let { fields["userId"] = it }
        keyValues.filterKeys { it != EVENT_KEY }.takeIf { it.isNotEmpty() }?.let { fields["fields"] = it }
        event.throwableProxy?.let {
            fields["error"] = mapOf("type" to it.className, "stack" to ThrowableProxyUtil.asString(it))
        }
        return JsonWriter.standard<Map<String, Any?>>().writeToString(fields) + "\n"
    }

    companion object {
        const val EVENT_KEY = "event"
        const val USER_ID_HASH_MDC = "user_id_hash"
        private const val BASE_PACKAGE = "com.soruhavuzu."

        /** com.soruhavuzu.<modul>... → <modul>; uygulama dışı kaydediciler "framework". */
        fun moduleOf(loggerName: String): String =
            if (loggerName.startsWith(BASE_PACKAGE)) loggerName.removePrefix(BASE_PACKAGE).substringBefore('.') else "framework"
    }
}
