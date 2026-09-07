package com.luaspets.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.luaspets.model.Notificacion;
import com.luaspets.model.TipoNotificacion;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    long countByDestinatarioIdAndLeidaFalse(Long destinatarioId);

    // Control de duplicados de RecordatorioService: la url de cada recordatorio
    // incluye el id de la cita, asi que la combinacion destinatario+tipo+url es
    // unica por cita y persona notificada. Se consulta la tabla en cada
    // ejecucion (no una marca en Cita), para que sea robusto ante reinicios.
    boolean existsByDestinatarioIdAndTipoAndUrl(Long destinatarioId, TipoNotificacion tipo, String url);

    List<Notificacion> findTop10ByDestinatarioIdOrderByFechaCreacionDesc(Long destinatarioId);

    List<Notificacion> findByDestinatarioIdOrderByFechaCreacionDesc(Long destinatarioId);

    // Version paginada para /notificaciones. Se mantiene tambien la version sin
    // paginar de arriba porque la usa el dropdown de la campana (top 10 mas
    // recientes via listarRecientes), que no necesita paginacion.
    Page<Notificacion> findByDestinatarioIdOrderByFechaCreacionDesc(Long destinatarioId, Pageable pageable);

    @Modifying
    @Query("UPDATE Notificacion n SET n.leida = true WHERE n.destinatario.id = :usuarioId AND n.leida = false")
    int marcarTodasComoLeidas(@Param("usuarioId") Long usuarioId);
}
