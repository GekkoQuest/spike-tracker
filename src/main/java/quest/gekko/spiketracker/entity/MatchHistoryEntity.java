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
@Table(name = "match_history", indexes = {
        @Index(name = "idx_completed_at", columnList = "completedAt"),
        @Index(name = "idx_match_page", columnList = "matchPage"),
        @Index(name = "idx_teams", columnList = "team1, team2")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Team1 name is required")
    @Column(nullable = false, length = 100)
    private String team1;

    @NotBlank(message = "Team2 name is required")
    @Column(nullable = false, length = 100)
    private String team2;

    @Column(length = 10)
    private String flag1;

    @Column(length = 10)
    private String flag2;

    @Column(name = "team1_logo", length = 500)
    private String team1Logo;

    @Column(name = "team2_logo", length = 500)
    private String team2Logo;

    @NotBlank(message = "Final score is required")
    @Column(name = "final_score1", nullable = false, length = 10)
    private String finalScore1;

    @NotBlank(message = "Final score is required")
    @Column(name = "final_score2", nullable = false, length = 10)
    private String finalScore2;

    @Column(name = "match_event", length = 200)
    private String matchEvent;

    @Column(name = "match_series", length = 200)
    private String matchSeries;

    @Column(name = "current_map", length = 50)
    private String currentMap;

    @NotNull(message = "Completion time is required")
    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    @Column(name = "match_page", unique = true, nullable = false, length = 500)
    private String matchPage;

    @Column(name = "stream_link", length = 500)
    private String streamLink;

    @Column(name = "duration_minutes")
    private Long durationMinutes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        final LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (completedAt == null) {
            completedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getWinner() {
        try {
            int score1 = Integer.parseInt(finalScore1);
            int score2 = Integer.parseInt(finalScore2);

            if (score1 > score2) return team1;
            if (score2 > score1) return team2;
            return "Draw";
        } catch (NumberFormatException e) {
            return "Unknown";
        }
    }
}