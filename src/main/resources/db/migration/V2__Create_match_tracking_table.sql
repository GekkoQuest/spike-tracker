CREATE TABLE match_tracking (
                                id BIGSERIAL PRIMARY KEY,
                                match_id VARCHAR(500) NOT NULL UNIQUE,
                                team1 VARCHAR(100) NOT NULL,
                                team2 VARCHAR(100) NOT NULL,
                                start_time TIMESTAMP NOT NULL,
                                status VARCHAR(20) NOT NULL DEFAULT 'LIVE',
                                last_score1 VARCHAR(10),
                                last_score2 VARCHAR(10),
                                current_map VARCHAR(50),
                                match_event VARCHAR(200),
                                stream_link VARCHAR(500),
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_match_tracking_match_id ON match_tracking(match_id);
CREATE INDEX idx_match_tracking_status ON match_tracking(status);
CREATE INDEX idx_match_tracking_start_time ON match_tracking(start_time);

-- Add check constraint for status
ALTER TABLE match_tracking ADD CONSTRAINT chk_match_status
    CHECK (status IN ('LIVE', 'COMPLETED', 'CANCELLED'));