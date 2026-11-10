package com.campus.viral.repository.jpa;

import com.campus.viral.model.domain.PlayerStatus;
import com.campus.viral.model.entity.PlayerNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerNodeRepository extends JpaRepository<PlayerNode, Long> {

    Optional<PlayerNode> findByPlayerUuid(String playerUuid);

    Optional<PlayerNode> findBySessionIdAndDeviceMacHash(Long sessionId, String deviceMacHash);

    long countBySessionId(Long sessionId);

    long countBySessionIdAndStatusIn(Long sessionId, Collection<PlayerStatus> statuses);

    @Query("SELECT p.fcmToken FROM PlayerNode p WHERE p.session.id = :sessionId AND p.status = 'HEALTHY' AND p.fcmToken IS NOT NULL")
    List<String> findHealthyFcmTokensBySessionId(@Param("sessionId") Long sessionId);

    @Modifying
    @Query("UPDATE PlayerNode p SET p.status = :newStatus, p.infectedAt = :now WHERE p.playerUuid = :uuid AND p.status = 'HEALTHY'")
    int markInfectedIfHealthy(
        @Param("uuid") String uuid,
        @Param("newStatus") PlayerStatus newStatus,
        @Param("now") Instant now
    );

    List<PlayerNode> findBySessionId(Long sessionId);
}
