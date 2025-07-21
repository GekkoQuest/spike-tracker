package quest.gekko.spiketracker.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "match_tracking", indexes = {
        @Index(name = "idx_match_id", columnList = "matchId"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_start_time", columnList = "startTime")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchTrackingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Match ID is required")
    @Column(name = "match_id", unique = true, nullable = false, length = 500)
    private String matchId;

    @NotBlank(message = "Team1 name is required")
    @Column(nullable = false, length = 100)
    private String team1;

    @NotBlank(message = "Team2 name is required")
    @Column(nullable = false, length = 100)
    private String team2;

    @NotNull(message = "Start time is required")
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status;

    @Column(name = "last_score1", length = 10)
    private String lastScore1;

    @Column(name = "last_score2", length = 10)
    private String lastScore2;

    @Column(name = "current_map", length = 50)
    private String currentMap;

    @Column(name = "match_event", length = 200)
    private String matchEvent;

    @Column(name = "stream_link", length = 500)
    private String streamLink;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        final LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (startTime == null) {
            startTime = now;
        }

        if (status == null) {
            status = MatchStatus.LIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum MatchStatus {
        LIVE, COMPLETED, CANCELLED
    }
}