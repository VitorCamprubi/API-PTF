package br.com.ptf.api.exception;

import br.com.ptf.api.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Um handler para toda a familia NotFoundException. Entidade nova nao exige
     * handler novo: basta a excecao dela estender NotFoundException.
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex,
                                                   HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Mesma logica para os conflitos: documento duplicado, chave de idempotencia
     * reutilizada com payload diferente, e o que vier depois.
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex,
                                                   HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Falha de Bean Validation no corpo da requisicao (@Valid).
     * Devolve a lista de campos invalidos, e nao apenas "Bad Request".
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ApiError.FieldViolation(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()))
                .toList();

        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "requisicao invalida",
                request.getRequestURI(),
                violations);

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * JSON malformado ou tipo incompativel no corpo.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "corpo da requisicao ausente ou malformado", request);
    }

    /**
     * Parametro de path com tipo errado, por exemplo um id que nao e UUID.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        String message = "valor invalido para o parametro '%s': %s".formatted(ex.getName(), ex.getValue());
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * Rede de seguranca do banco: constraint violada que passou pela validacao em
     * memoria. E o caso da corrida entre duas requisicoes com o mesmo documento,
     * e tambem o do saldo negativo barrado pelo CHECK.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.warn("violacao de integridade no banco: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "operacao viola uma restricao de integridade", request);
    }

    /**
     * Ultimo recurso. Loga o stack trace completo e devolve mensagem generica,
     * para nao vazar detalhe interno da aplicacao.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("erro nao tratado em {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "erro interno inesperado", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = ApiError.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
