package quest.gekko.spiketracker.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import quest.gekko.spiketracker.entity.MatchHistoryEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchHistoryRepository extends JpaRepository<MatchHistoryEntity, Long> {
    Optional<MatchHistoryEntity> findByMatchPage(final String matchPage);

    List<MatchHistoryEntity> findTop20ByOrderByCompletedAtDesc();

    Page<MatchHistoryEntity> findByOrderByCompletedAtDesc(Pageable pageable);

    List<MatchHistoryEntity> findByCompletedAtBetweenOrderByCompletedAtDesc(final LocalDateTime startDate, final LocalDateTime endDate);

    @Query("SELECT COUNT(m) FROM MatchHistoryEntity m WHERE m.team1 = :teamName OR m.team2 = :teamName")
    Long countMatchesForTeam(@Param("teamName") final String teamName);

    @Query("SELECT m FROM MatchHistoryEntity m WHERE " +
            "(m.team1 LIKE %:teamName% OR m.team2 LIKE %:teamName%) " +
            "ORDER BY m.completedAt DESC")
    List<MatchHistoryEntity> findMatchesForTeam(@Param("teamName") final String teamName, final Pageable pageable);

    @Query("SELECT m.matchEvent, COUNT(m) FROM MatchHistoryEntity m " + "GROUP BY m.matchEvent ORDER BY COUNT(m) DESC")
    List<Object[]> getEventStatistics();

    @Query("SELECT AVG(m.durationMinutes) FROM MatchHistoryEntity m WHERE m.durationMinutes > 0")
    Double getAverageMatchDuration();

    boolean existsByMatchPage(final String matchPage);

    void deleteByCompletedAtBefore(final LocalDateTime cutoffDate);
}