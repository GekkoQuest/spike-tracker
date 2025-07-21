package quest.gekko.spiketracker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import quest.gekko.spiketracker.entity.MatchTrackingEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchTrackingRepository extends JpaRepository<MatchTrackingEntity, Long> {
    Optional<MatchTrackingEntity> findByMatchId(final String matchId);

    List<MatchTrackingEntity> findByStatus(final MatchTrackingEntity.MatchStatus status);

    List<MatchTrackingEntity> findByStatusOrderByStartTimeDesc(final MatchTrackingEntity.MatchStatus status);

    @Modifying
    @Query("UPDATE MatchTrackingEntity m SET m.status = :newStatus WHERE m.matchId = :matchId")
    int updateMatchStatus(@Param("matchId") String matchId, @Param("newStatus") final MatchTrackingEntity.MatchStatus newStatus);

    @Modifying
    @Query("UPDATE MatchTrackingEntity m SET m.lastScore1 = :score1, m.lastScore2 = :score2, " +
            "m.currentMap = :currentMap, m.streamLink = :streamLink WHERE m.matchId = :matchId")
    int updateMatchDetails(@Param("matchId") final String matchId,
                           @Param("score1") final String score1,
                           @Param("score2") final String score2,
                           @Param("currentMap") final String currentMap,
                           @Param("streamLink") final String streamLink);

    boolean existsByMatchId(String matchId);

    void deleteByStatusAndStartTimeBefore(final MatchTrackingEntity.MatchStatus status, final LocalDateTime cutoffDate);

    @Query("SELECT COUNT(m) FROM MatchTrackingEntity m WHERE m.status = 'LIVE'")
    Long countLiveMatches();
}