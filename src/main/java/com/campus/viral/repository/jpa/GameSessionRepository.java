package com.campus.viral.repository.jpa;

import com.campus.viral.model.domain.SessionStatus;
import com.campus.viral.model.entity.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    Optional<GameSession> findBySessionCode(String sessionCode);

    List<GameSession> findByStatus(SessionStatus status);
}
