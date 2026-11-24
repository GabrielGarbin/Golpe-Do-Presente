package com.Presente.A3.Seguranca;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Tradutor de erros da API: converte exceções em JSON {@code {"erro": "..."}}
 * com o código HTTP correto, para o front mostrar a mensagem certa.
 *
 * <pre>
 *   dado inválido ............ 400      sem login / token inválido ... 401
 *   recurso inexistente ...... 404      conflito (duplicado) ......... 409
 *   excesso de tentativas .... 429
 * </pre>
 *
 * <p>Fica no pacote {@code Seguranca} (e não em {@code Config}) porque o
 * {@code .gitignore} original ignorava qualquer pasta chamada "config", o que
 * deixava de fora do GitHub as classes colocadas ali. Se o projeto tiver outro
 * {@code @RestControllerAdvice}, os dois convivem sem erro: este tem prioridade
 * máxima e trata as mesmas exceções do mesmo modo.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErrosApiHandler {

    /** 400: telefone malformado, campo obrigatório ausente, cadastro inválido... */
    @ExceptionHandler({IllegalArgumentException.class, DadosInvalidosException.class})
    public ResponseEntity<Map<String, String>> dadoInvalido(RuntimeException ex) {
        return resposta(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** 400: JSON malformado ou valor de enum inexistente (ex.: canal "FAX"). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> corpoIlegivel(HttpMessageNotReadableException ex) {
        return resposta(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido ou ilegível.");
    }

    /** 401: credenciais ou token inválidos. */
    @ExceptionHandler(AutenticacaoException.class)
    public ResponseEntity<Map<String, String>> naoAutenticado(AutenticacaoException ex) {
        return resposta(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /** 404: o recurso referenciado não existe (ex.: cliente denunciante). */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> naoEncontrado(NoSuchElementException ex) {
        return resposta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** 409: conflito com o estado atual (e-mail repetido, denúncia repetida...). */
    @ExceptionHandler({IllegalStateException.class, ConflitoException.class})
    public ResponseEntity<Map<String, String>> conflito(RuntimeException ex) {
        return resposta(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** 429: excesso de tentativas de login. */
    @ExceptionHandler(MuitasTentativasException.class)
    public ResponseEntity<Map<String, String>> muitasTentativas(MuitasTentativasException ex) {
        return resposta(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    private ResponseEntity<Map<String, String>> resposta(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(Map.of("erro", mensagem == null ? status.getReasonPhrase() : mensagem));
    }
}
