package com.Presente.A3.Repositorio;

import com.Presente.A3.Modelo.Golpista;
import com.Presente.A3.Modelo.StatusGolpista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Acesso à tabela {@code golpistas} (base colaborativa). */
public interface GolpistaRepository extends JpaRepository<Golpista, Long> {

    /** @param telefone já normalizado (só dígitos) */
    Optional<Golpista> findByTelefone(String telefone);

    /** Blacklist: confirmados, dos mais denunciados para os menos. */
    List<Golpista> findByStatusOrderByTotalDenunciasDescIdAsc(StatusGolpista status);

    /**
     * Soma +1 denúncia direto no banco, numa única instrução UPDATE, e promove
     * a CONFIRMADO ao atingir o limite. Por ser atômica, duas denúncias
     * simultâneas contra o mesmo número nunca perdem a contagem, mesmo com
     * várias instâncias da API usando o mesmo banco.
     *
     * @return quantas linhas foram alteradas (0 se o id não existe)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Golpista g set "
            + "g.totalDenuncias = g.totalDenuncias + 1, "
            + "g.dataUltimaDenuncia = :agora, "
            + "g.dataPrimeiraDenuncia = coalesce(g.dataPrimeiraDenuncia, :agora), "
            + "g.status = case when g.totalDenuncias + 1 >= :limite then :confirmado else g.status end "
            + "where g.id = :id")
    int somarDenuncia(@Param("id") Long id,
                      @Param("agora") LocalDateTime agora,
                      @Param("limite") int limite,
                      @Param("confirmado") StatusGolpista confirmado);
}
