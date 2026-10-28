package com.soruhavuzu.shared.web

import com.soruhavuzu.shared.ProblemCode
import com.soruhavuzu.shared.ProblemException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/** Tüm hatalar application/problem+json ve `code` alanıyla döner (mimari.md §15). */
@RestControllerAdvice
class ProblemHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ProblemException::class)
    fun handleProblem(e: ProblemException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.status), e.message).withCode(e.code)

    // Güvenlik filtresindeki hatalar da HandlerExceptionResolver üzerinden buraya yönlendirilir (identity modülü)
    @ExceptionHandler(DisabledException::class)
    fun handleDisabled(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Hesap aktif değil").withCode(ProblemCode.ACCOUNT_INACTIVE)

    @ExceptionHandler(AuthenticationException::class)
    fun handleUnauthenticated(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Kimlik doğrulanamadı").withCode(ProblemCode.UNAUTHORIZED)

    @ExceptionHandler(AccessDeniedException::class)
    fun handleForbidden(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz yok").withCode(ProblemCode.FORBIDDEN)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ProblemDetail {
        // Gövde/PII loglanmaz; yalnızca tip ve iz (kurallar.md §9)
        log.error("unexpected_error type={}", e.javaClass.name, e)
        return ProblemDetail
            .forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Beklenmeyen hata")
            .withCode(ProblemCode.INTERNAL_ERROR)
    }

    /** Spring'in kendi ürettiği hatalara (404, 405, 415…) da `code` eklenir: HTTP durum adı. */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? =
        super.handleExceptionInternal(ex, body, headers, statusCode, request)?.also { response ->
            val problem = response.body as? ProblemDetail
            if (problem != null && problem.properties?.containsKey("code") != true) {
                problem.withCode(ProblemCode(HttpStatus.resolve(problem.status)?.name ?: "HTTP_${problem.status}"))
            }
        }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? =
        super.handleMethodArgumentNotValid(ex, headers, status, request)?.also { response ->
            (response.body as? ProblemDetail)?.withCode(ProblemCode.VALIDATION_FAILED)
        }

    private fun ProblemDetail.withCode(code: ProblemCode): ProblemDetail = apply { setProperty("code", code.value) }
}
