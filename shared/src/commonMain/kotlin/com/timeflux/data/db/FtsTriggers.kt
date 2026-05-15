package com.timeflux.data.db

internal val FTS_TRIGGER_SQL = listOf(
    """
    CREATE TRIGGER IF NOT EXISTS fts_after_insert
    AFTER INSERT ON timeline_entries BEGIN
        INSERT INTO entry_fts(entry_id, title, note, body)
        VALUES (NEW.id, NEW.title, NEW.note, json_extract(NEW.payload, '$.body'));
    END
    """.trimIndent(),

    """
    CREATE TRIGGER IF NOT EXISTS fts_after_update
    AFTER UPDATE ON timeline_entries BEGIN
        INSERT INTO entry_fts(entry_fts, entry_id) VALUES ('delete', OLD.id);
        INSERT INTO entry_fts(entry_id, title, note, body)
        VALUES (NEW.id, NEW.title, NEW.note, json_extract(NEW.payload, '$.body'));
    END
    """.trimIndent(),

    """
    CREATE TRIGGER IF NOT EXISTS fts_after_delete
    AFTER DELETE ON timeline_entries BEGIN
        INSERT INTO entry_fts(entry_fts, entry_id) VALUES ('delete', OLD.id);
    END
    """.trimIndent(),
)
