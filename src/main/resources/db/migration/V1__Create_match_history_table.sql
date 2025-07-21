CREATE TABLE match_history (
                               id BIGSERIAL PRIMARY KEY,
                               team1 VARCHAR(100) NOT NULL,
                               team2 VARCHAR(100) NOT NULL,
                               flag1 VARCHAR(10),
                               flag2 VARCHAR(10),
                               team1_logo VARCHAR(500),
                               team2_logo VARCHAR(500),
                               final_score1 VARCHAR(10) NOT NULL,
                               final_score2 VARCHAR(10) NOT NULL,
                               match_event VARCHAR(200),
                               match_series VARCHAR(200),
                               current_map VARCHAR(50),
                               completed_at TIMESTAMP NOT NULL,
                               match_page VARCHAR(500) NOT NULL UNIQUE,
                               stream_link VARCHAR(500),
                               duration_minutes BIGINT,
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_match_history_completed_at ON match_history(completed_at);
CREATE INDEX idx_match_history_match_page ON match_history(match_page);
CREATE INDEX idx_match_history_teams ON match_history(team1, team2);
CREATE INDEX idx_match_history_event ON match_history(match_event);