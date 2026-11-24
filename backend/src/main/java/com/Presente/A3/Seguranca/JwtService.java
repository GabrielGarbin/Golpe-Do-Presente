package com.Presente.A3.Seguranca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emissão e validação de tokens <b>JWT</b> (JSON Web Token) com assinatura
 * HMAC-SHA256 (algoritmo "HS256").
 *
 * <h3>Como um token é feito</h3>
 * <pre>
 *   cabecalho.payload.assinatura        (cada parte em Base64URL)
 *
 *   cabecalho  = {"alg":"HS256","typ":"JWT"}
 *   payload    = {"sub":"12","perfil":"USUARIO","iat":1700000000,"exp":1700007200}
 *   assinatura = HMAC-SHA256(cabecalho + "." + payload, SEGREDO)
 * </pre>
 * <p>Só quem conhece o SEGREDO consegue gerar uma assinatura válida. Se
 * alguém alterar o payload (por exemplo, trocar o perfil para ADMIN), a
 * assinatura deixa de bater e o token é rejeitado.</p>
 *
 * <h3>Decisões de segurança</h3>
 * <ul>
 *   <li>Só aceita o cabeçalho exato de HS256: tokens com {@code "alg":"none"}
 *       ou outro algoritmo são recusados (ataque clássico contra JWT).</li>
 *   <li>A assinatura é comparada em tempo constante
 *       ({@link MessageDigest#isEqual}), sem revelar quantos caracteres batem.</li>
 *   <li>A expiração ({@code exp}) é sempre verificada.</li>
 *   <li>Segredo com menos de 32 caracteres impede a aplicação de iniciar.</li>
 *   <li>Sem segredo configurado, gera um aleatório só para esta execução (útil
 *       em desenvolvimento; os tokens deixam de valer ao reiniciar). Em produção,
 *       defina {@code JWT_SECRET}.</li>
 * </ul>
 *
 * <p><b>Por que sem biblioteca externa?</b> O formato HS256 é simples e usa só
 * primitivas do JDK. Em um sistema real, o recomendado é Spring Security +
 * uma biblioteca madura (ex.: JJWT); esta classe pode ser trocada sem
 * afetar o resto, pois o restante do código só usa {@link #gerar} e
 * {@link #validar}.</p>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final int TAMANHO_MINIMO_SEGREDO = 32;
    private static final Base64.Encoder CODIFICADOR = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODIFICADOR = Base64.getUrlDecoder();

    /** Único cabeçalho aceito. Comparar o texto inteiro bloqueia "alg":"none". */
    private static final String CABECALHO = CODIFICADOR.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    // O payload só é lido DEPOIS de a assinatura ser validada, ou seja, foi
    // escrito por este servidor com o formato abaixo.
    private static final Pattern SUB = Pattern.compile("\"sub\":\"(\\d{1,18})\"");
    private static final Pattern PERFIL = Pattern.compile("\"perfil\":\"([A-Z]{1,20})\"");
    private static final Pattern EXP = Pattern.compile("\"exp\":(\\d{1,15})");

    private final byte[] segredo;
    private final long expiracaoSegundos;
    private final Clock relogio;

    /**
     * @param segredo  {@code app.jwt.secret} (variável {@code JWT_SECRET}); vazio = aleatório
     * @param minutos  validade do token, em minutos ({@code app.jwt.expiracao-minutos})
     */
    @Autowired
    public JwtService(@Value("${app.jwt.secret:}") String segredo,
                      @Value("${app.jwt.expiracao-minutos:120}") long minutos) {
        this(segredo, minutos, Clock.systemUTC());
    }

    /** Construtor com relógio injetável, usado nos testes para simular a passagem do tempo. */
    public JwtService(String segredo, long minutos, Clock relogio) {
        if (minutos < 1) {
            throw new IllegalArgumentException("app.jwt.expiracao-minutos deve ser pelo menos 1.");
        }
        if (segredo == null || segredo.isBlank()) {
            byte[] aleatorio = new byte[32];
            new SecureRandom().nextBytes(aleatorio);
            this.segredo = aleatorio;
            log.warn("app.jwt.secret não definido: usando um segredo aleatório desta execução. "
                    + "Os tokens deixarão de valer ao reiniciar. Defina JWT_SECRET em produção.");
        } else {
            byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < TAMANHO_MINIMO_SEGREDO) {
                throw new IllegalArgumentException("app.jwt.secret precisa ter pelo menos "
                        + TAMANHO_MINIMO_SEGREDO + " caracteres.");
            }
            this.segredo = bytes;
        }
        this.expiracaoSegundos = minutos * 60;
        this.relogio = relogio;
    }

    /** Validade de cada token, em segundos (devolvida ao front para ele saber quando expira). */
    public long getExpiracaoSegundos() {
        return expiracaoSegundos;
    }

    /** Emite um token para o cliente, válido pelo tempo configurado. */
    public String gerar(Long clienteId, Perfil perfil) {
        long agora = relogio.instant().getEpochSecond();
        String payload = "{\"sub\":\"" + clienteId + "\",\"perfil\":\"" + perfil.name()
                + "\",\"iat\":" + agora + ",\"exp\":" + (agora + expiracaoSegundos) + "}";
        String corpo = CABECALHO + "." + CODIFICADOR.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return corpo + "." + assinar(corpo);
    }

    /**
     * Valida um token e devolve quem ele representa.
     *
     * @return vazio se o token for nulo, malformado, de outro algoritmo,
     *         com assinatura inválida ou expirado
     */
    public Optional<UsuarioAutenticado> validar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] partes = token.split("\\.", -1);
        if (partes.length != 3 || !CABECALHO.equals(partes[0])) {
            return Optional.empty();
        }

        String esperada = assinar(partes[0] + "." + partes[1]);
        boolean assinaturaOk = MessageDigest.isEqual(
                esperada.getBytes(StandardCharsets.UTF_8), partes[2].getBytes(StandardCharsets.UTF_8));
        if (!assinaturaOk) {
            return Optional.empty();
        }

        String payload;
        try {
            payload = new String(DECODIFICADOR.decode(partes[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        Matcher sub = SUB.matcher(payload);
        Matcher perfil = PERFIL.matcher(payload);
        Matcher exp = EXP.matcher(payload);
        if (!sub.find() || !perfil.find() || !exp.find()) {
            return Optional.empty();
        }

        long expiraEm = Long.parseLong(exp.group(1));
        if (relogio.instant().getEpochSecond() >= expiraEm) {
            return Optional.empty(); // expirado
        }
        try {
            return Optional.of(new UsuarioAutenticado(
                    Long.parseLong(sub.group(1)), Perfil.valueOf(perfil.group(1)), expiraEm));
        } catch (IllegalArgumentException e) {
            return Optional.empty(); // perfil desconhecido
        }
    }

    /** HMAC-SHA256 do texto, em Base64URL. */
    private String assinar(String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); // Mac não é thread-safe: uma instância por chamada
            mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
            return CODIFICADOR.encodeToString(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível no JDK.", e);
        }
    }
}
