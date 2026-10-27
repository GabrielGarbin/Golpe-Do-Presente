package com.Presente.A3.Config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Converte exceções de negócio em respostas JSON padronizadas
 * ({@code {"erro": "mensagem"}}) com o código HTTP adequado.
 *
 * <p>Assim, os serviços lançam exceções simples e os controllers ficam
 * livres de {@code try/catch}.</p>
 */
@RestControllerAdvice
public class RestExceptionHandler {

    /** 400: dado inválido (telefone malformado, campo obrigatório ausente...). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> dadoInvalido(IllegalArgumentException ex) {
        return resposta(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** 400: JSON malformado ou valor de enum inexistente (ex.: canal "FAX"). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> corpoIlegivel(HttpMessageNotReadableException ex) {
        return resposta(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido ou ilegível.");
    }

    /** 404: recurso referenciado não existe. */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> naoEncontrado(NoSuchElementException ex) {
        return resposta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** 409: conflito com o estado atual (telefone duplicado, denúncia repetida...). */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflito(IllegalStateException ex) {
        return resposta(HttpStatus.CONFLICT, ex.getMessage());
    }

    private ResponseEntity<Map<String, String>> resposta(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(Map.of("erro", mensagem));
    }
}
