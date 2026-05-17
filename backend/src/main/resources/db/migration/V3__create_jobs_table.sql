CREATE TABLE jobs (
                      id                  UUID         PRIMARY KEY,
                      user_id             UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                      original_filename   VARCHAR(255) NOT NULL,
                      file_size_bytes     BIGINT       NOT NULL,
                      line_count          INTEGER,
                      status              VARCHAR(32)  NOT NULL,
                      error_message       TEXT,
                      created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                      started_at          TIMESTAMPTZ,
                      finished_at         TIMESTAMPTZ
);

CREATE INDEX idx_jobs_user_id_created_at ON jobs (user_id, created_at DESC);