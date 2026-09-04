package com.luaspets.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.luaspets.model.Notificacion;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    long countByDestinatarioIdAndLeidaFalse(Long destinatarioId);

    List<Notificacion> findTop10ByDestinatarioIdOrderByFechaCreacionDesc(Long destinatarioId);

    List<Notificacion> findByDestinatarioIdOrderByFechaCreacionDesc(Long destinatarioId);

    @Modifying
    @Query("UPDATE Notificacion n SET n.leida = true WHERE n.destinatario.id = :usuarioId AND n.leida = false")
    int marcarTodasComoLeidas(@Param("usuarioId") Long usuarioId);
}
