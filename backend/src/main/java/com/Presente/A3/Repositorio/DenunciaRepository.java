package com.Presente.A3.Repositorio;

import com.Presente.A3.Modelo.Denuncia;
import com.Presente.A3.Modelo.StatusGolpista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Acesso à tabela {@code denuncias}. */
public interface DenunciaRepository extends JpaRepository<Denuncia, Long> {

    boolean existsByClienteIdAndGolpistaId(Long clienteId, Long golpistaId);

    List<Denuncia> findByGolpistaIdOrderByIdAsc(Long golpistaId);

    /** Relatos de números com o status informado (usado para re-treinar o classificador ao iniciar). */
    @Query("select d from Denuncia d where d.golpistaId in "
            + "(select g.id from Golpista g where g.status = :status) order by d.id")
    List<Denuncia> findDeGolpistasComStatus(@Param("status") StatusGolpista status);

    long countByGolpistaId(Long golpistaId);

    long countByDataAfter(LocalDateTime desde);

    List<Denuncia> findByClienteId(Long clienteId);

    /** Moderação: mais recentes primeiro. */
    List<Denuncia> findAllByOrderByDataDescIdDesc();

    /** Quantas denúncias cada cliente fez: pares [clienteId, total]. */
    @Query("select d.clienteId, count(d) from Denuncia d where d.clienteId is not null group by d.clienteId")
    List<Object[]> contarPorCliente();

    /**
     * Ao excluir um cliente mantendo as denúncias, elas ficam anônimas (o banco faria
     * o mesmo com "on delete set null"; aqui fica explícito e funciona em qualquer banco).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Denuncia d set d.clienteId = null where d.clienteId = :clienteId")
    int desvincularCliente(@Param("clienteId") Long clienteId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Denuncia d where d.golpistaId = :golpistaId")
    int apagarDoGolpista(@Param("golpistaId") Long golpistaId);
}
