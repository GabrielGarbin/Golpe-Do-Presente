package com.Presente.A3.Seguranca;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Armazenamento seguro de senhas.
 *
 * <p>A senha NUNCA é guardada. Guarda-se apenas o resultado de uma função de
 * derivação lenta (PBKDF2-HMAC-SHA256) com um <b>sal</b> aleatório por senha:</p>
 * <ul>
 *   <li><b>sal</b>: duas pessoas com a mesma senha têm hashes diferentes, e
 *       tabelas prontas de senhas (rainbow tables) não servem;</li>
 *   <li><b>muitas iterações</b>: cada tentativa de adivinhar a senha custa caro
 *       para quem tenta quebrar um vazamento do banco.</li>
 * </ul>
 *
 * <p>Formato guardado: {@code pbkdf2-sha256$iteracoes$sal$hash} (Base64). Guardar
 * as iterações junto permite aumentá-las no futuro sem invalidar hashes antigos.</p>
 */
@Service
public class SenhaService {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final String PREFIXO = "pbkdf2-sha256";
    private static final int TAMANHO_SAL_BYTES = 16;
    private static final int TAMANHO_HASH_BITS = 256;
    private static final int MINIMO_ITERACOES = 1000;

    private final int iteracoes;
    private final SecureRandom aleatorio = new SecureRandom();
    /** Hash descartável usado para gastar o mesmo tempo quando o e-mail não existe. */
    private final String hashFalso;

    /**
     * @param iteracoes {@code app.auth.pbkdf2-iteracoes}. O padrão (600 000) segue a
     *                  recomendação da OWASP; os testes usam um valor menor para rodar rápido.
     */
    @Autowired
    public SenhaService(@Value("${app.auth.pbkdf2-iteracoes:600000}") int iteracoes) {
        if (iteracoes < MINIMO_ITERACOES) {
            throw new IllegalArgumentException(
                    "app.auth.pbkdf2-iteracoes deve ser pelo menos " + MINIMO_ITERACOES + ".");
        }
        this.iteracoes = iteracoes;
        this.hashFalso = gerarHash("senha-descartavel");
    }

    /** Gera o hash de uma senha, com sal aleatório. */
    public String gerarHash(String senha) {
        byte[] sal = new byte[TAMANHO_SAL_BYTES];
        aleatorio.nextBytes(sal);
        byte[] derivado = derivar(senha, sal, iteracoes);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIXO + "$" + iteracoes + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(derivado);
    }

    /**
     * Confere uma senha contra o hash guardado.
     *
     * @return false se a senha não bate ou se o hash guardado está malformado
     */
    public boolean confere(String senha, String hashGuardado) {
        if (senha == null || hashGuardado == null) {
            return false;
        }
        String[] p = hashGuardado.split("\\$");
        if (p.length != 4 || !PREFIXO.equals(p[0])) {
            return false;
        }
        try {
            int iter = Integer.parseInt(p[1]);
            byte[] sal = Base64.getDecoder().decode(p[2]);
            byte[] esperado = Base64.getDecoder().decode(p[3]);
            byte[] calculado = derivar(senha, sal, iter);
            return MessageDigest.isEqual(esperado, calculado); // tempo constante
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Gasta o mesmo tempo de uma verificação real. Chamado quando o e-mail não
     * existe, para que o tempo de resposta não revele quais e-mails estão cadastrados.
     */
    public void gastarTempo(String senha) {
        confere(senha, hashFalso);
    }

    private static byte[] derivar(String senha, byte[] sal, int iteracoes) {
        PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), sal, iteracoes, TAMANHO_HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITMO + " indisponível no JDK.", e);
        } finally {
            spec.clearPassword();
        }
    }
}
