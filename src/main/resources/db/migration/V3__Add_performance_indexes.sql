-- Additional performance indexes
CREATE INDEX idx_match_history_duration ON match_history(duration_minutes) WHERE duration_minutes IS NOT NULL;
CREATE INDEX idx_match_history_recent ON match_history(completed_at DESC, id DESC);
CREATE INDEX idx_match_tracking_live_matches ON match_tracking(status, start_time) WHERE status = 'LIVE';

-- Partial index for active tracking
CREATE INDEX idx_match_tracking_active ON match_tracking(updated_at) WHERE status = 'LIVE';