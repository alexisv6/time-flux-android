package com.timeflux.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.timeflux.db.TimeFluxDatabase
import com.timeflux.domain.model.ModuleType
import com.timeflux.domain.model.Outcome
import com.timeflux.domain.model.TimelineEntry
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Paging behaviour of module exclusion (spec 001 phase 5 — hiding a disabled module's entries).
 *
 * Lives in androidUnitTest rather than commonTest because [JdbcSqliteDriver] is JVM-only.
 */
class TimelineRepositoryPagingTest {

    private fun repository(): TimelineRepositoryImpl {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TimeFluxDatabase.Schema.create(driver)
        return TimelineRepositoryImpl(TimeFluxDatabase(driver), TimeZone.UTC)
    }

    /** Ids are zero-padded so lexical order matches chronological order, as ULIDs would. */
    private fun entry(index: Int, type: ModuleType, atMillis: Long) = TimelineEntry(
        id = "id-${index.toString().padStart(4, '0')}",
        createdAt = Instant.fromEpochMilliseconds(atMillis),
        moduleType = type,
        title = "${type.id}-$index",
        updatedAt = Instant.fromEpochMilliseconds(atMillis),
    )

    private suspend fun TimelineRepositoryImpl.seed(entries: List<TimelineEntry>) {
        entries.forEach { assertTrue(insert(it) is Outcome.Success, "seed insert failed for ${it.id}") }
    }

    private fun Outcome<List<TimelineEntry>>.orFail(): List<TimelineEntry> = when (this) {
        is Outcome.Success -> data
        is Outcome.Failure -> error("query failed: $this")
    }

    @Test
    fun excluding_a_module_omits_exactly_its_entries() = runTest {
        val repo = repository()
        // Alternating milestone / mood, oldest first.
        repo.seed(
            (0 until 10).map { i ->
                entry(i, if (i % 2 == 0) ModuleType.MILESTONE else ModuleType.MOOD, 1_000L + i)
            }
        )

        val page = repo.getPageBefore(
            beforeTs = Long.MAX_VALUE,
            beforeId = "",
            excludedModules = setOf(ModuleType.MOOD.id),
        ).orFail()

        assertEquals(5, page.size)
        assertTrue(page.all { it.moduleType == ModuleType.MILESTONE })
    }

    @Test
    fun empty_exclusion_set_behaves_like_the_plain_query() = runTest {
        val repo = repository()
        repo.seed(
            (0 until 6).map { i ->
                entry(i, if (i % 2 == 0) ModuleType.MILESTONE else ModuleType.MOOD, 2_000L + i)
            }
        )

        // The regression guard: SQLite renders an empty collection as `NOT IN ()`, a runtime
        // syntax error — so this must fall through to the non-excluding query.
        val excluded = repo.getPageBefore(Long.MAX_VALUE, "", excludedModules = emptySet()).orFail()
        val plain = repo.getPageBefore(Long.MAX_VALUE, "").orFail()

        assertEquals(6, plain.size)
        assertEquals(plain.map { it.id }, excluded.map { it.id })
    }

    @Test
    fun excluded_entries_do_not_consume_page_slots() = runTest {
        val repo = repository()
        // 10 mood entries newer than 10 milestones. A post-filter over a 5-row page would return
        // an empty first page here; query-level exclusion must return 5 milestones.
        repo.seed((0 until 10).map { i -> entry(i, ModuleType.MILESTONE, 3_000L + i) })
        repo.seed((10 until 20).map { i -> entry(i, ModuleType.MOOD, 4_000L + i) })

        val page = repo.getPageBefore(
            beforeTs = Long.MAX_VALUE,
            beforeId = "",
            limit = 5,
            excludedModules = setOf(ModuleType.MOOD.id),
        ).orFail()

        assertEquals(5, page.size)
        assertTrue(page.all { it.moduleType == ModuleType.MILESTONE })
    }

    @Test
    fun the_cursor_still_walks_to_the_oldest_entry_when_excluding() = runTest {
        val repo = repository()
        repo.seed(
            (0 until 20).map { i ->
                entry(i, if (i % 2 == 0) ModuleType.MILESTONE else ModuleType.MOOD, 5_000L + i)
            }
        )

        // Page through in 3s; every milestone should be reached exactly once.
        val seen = mutableListOf<TimelineEntry>()
        var cursorTs = Long.MAX_VALUE
        var cursorId = ""
        while (true) {
            val page = repo.getPageBefore(
                beforeTs = cursorTs,
                beforeId = cursorId,
                limit = 3,
                excludedModules = setOf(ModuleType.MOOD.id),
            ).orFail()
            if (page.isEmpty()) break
            seen += page
            cursorTs = page.last().createdAt.toEpochMilliseconds()
            cursorId = page.last().id
        }

        assertEquals(10, seen.size)
        assertEquals(seen.map { it.id }.distinct().size, seen.size, "cursor returned duplicates")
        assertTrue(seen.all { it.moduleType == ModuleType.MILESTONE })
    }

    @Test
    fun forward_paging_also_excludes() = runTest {
        val repo = repository()
        repo.seed(
            (0 until 8).map { i ->
                entry(i, if (i % 2 == 0) ModuleType.MILESTONE else ModuleType.MOOD, 6_000L + i)
            }
        )

        val page = repo.getPageAfter(
            afterTs = 0L,
            afterId = "",
            excludedModules = setOf(ModuleType.MOOD.id),
        ).orFail()

        assertEquals(4, page.size)
        assertTrue(page.all { it.moduleType == ModuleType.MILESTONE })
    }
}
