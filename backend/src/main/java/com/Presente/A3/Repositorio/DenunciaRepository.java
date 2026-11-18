package com.Presente.A3.Repositorio;

import com.Presente.A3.Modelo.Denuncia;
import com.Presente.A3.Modelo.StatusGolpista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Acesso à tabela {@code denuncias}. */
public interface DenunciaRepository extends JpaRepository<Denuncia, Long> {

    boolean existsByClienteIdAndGolpistaId(Long clienteId, Long golpistaId);

    List<Denuncia> findByGolpistaIdOrderByIdAsc(Long golpistaId);

    /** Relatos de números com o status informado (usado para re-treinar o classificador ao iniciar). */
    @Query("select d from Denuncia d where d.golpistaId in "
            + "(select g.id from Golpista g where g.status = :status) order by d.id")
    List<Denuncia> findDeGolpistasComStatus(@Param("status") StatusGolpista status);
}
