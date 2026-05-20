package io.github.ganyuke.bedFallback.compaction

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent
import io.github.ganyuke.bedFallback.RespawnRecord
import io.github.ganyuke.bedFallback.WorldCoord
import org.bukkit.Location
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class CompactionStrategyTest {
    abstract class AbstractCompactionStrategyTest {
        abstract val strategy: CompactionStrategy

        fun worldId(): UUID = UUID.randomUUID()

        // need the counter so that genericRecords don't all match each other when
        // I'm doing `in` checks. the one time when structural equality sucks...
        private var coordCounter = 0

        fun genericRecord(world: UUID = worldId()): RespawnRecord =
            RespawnRecord(
                worldCoord = WorldCoord(coordCounter++, 64, 0, world),
                cause = PlayerSetSpawnEvent.Cause.PLUGIN,
                playerYaw = 0f
            )

        fun historyOf(vararg records: RespawnRecord) = ArrayDeque(records.toList())

        @Test
        fun `limit zero clears all records`() {
            val history = historyOf(genericRecord(), genericRecord(), genericRecord())
            strategy.compact(history, 0)
            assertTrue(history.isEmpty())
        }

        @Test
        fun `negative limit clears all records`() {
            val history = historyOf(genericRecord(), genericRecord())
            strategy.compact(history, -1)
            assertTrue(history.isEmpty())
        }

        @Test
        fun `empty history doesn't break anything`() {
            val history = historyOf()
            strategy.compact(history, 3)
            assertTrue(history.isEmpty())
        }
    }

    abstract class AbstractCompactionStrategyTestWithValidation : AbstractCompactionStrategyTest() {
        val validLocation = Location(null, 0.0, 0.0, 0.0)

        fun resolverFor(vararg valid: RespawnRecord): (RespawnRecord) -> Location? {
            val validSet = valid.toSet()
            return { record -> if (record in validSet) validLocation else null }
        }
    }

    // =========================================================================
    // LastNCompaction
    // =========================================================================

    @Nested
    inner class LastNCompactionTest : AbstractCompactionStrategyTest() {
        override val strategy = LastNCompaction

        @Test
        fun `history smaller than limit is unchanged`() {
            val r1 = genericRecord(); val r2 = genericRecord()
            val history = historyOf(r1, r2)
            strategy.compact(history, 3)
            assertEquals(listOf(r1, r2), history.toList())
        }

        @Test
        fun `history equal to limit is unchanged`() {
            val r1 = genericRecord(); val r2 = genericRecord(); val r3 = genericRecord()
            val history = historyOf(r1, r2, r3)
            strategy.compact(history, 3)
            assertEquals(listOf(r1, r2, r3), history.toList())
        }

        @Test
        fun `keeps only the most recent N records`() {
            val r1 = genericRecord(); val r2 = genericRecord()
            val r3 = genericRecord(); val r4 = genericRecord()
            val history = historyOf(r1, r2, r3, r4)
            strategy.compact(history, 3)
            assertEquals(listOf(r2, r3, r4), history.toList())
        }

        @Test
        fun `limit of 1 keeps only the last record`() {
            val r1 = genericRecord(); val r2 = genericRecord(); val r3 = genericRecord()
            val history = historyOf(r1, r2, r3)
            strategy.compact(history, 1)
            assertEquals(listOf(r3), history.toList())
        }
    }

    // =========================================================================
    // LastNValidCompaction
    // =========================================================================

    @Nested
    inner class LastNValidCompactionTest : AbstractCompactionStrategyTestWithValidation() {
        override val strategy = LastNValidCompaction

        @Test
        fun `invalid records below limit do not get compacted`() {
            val r1 = genericRecord(); val r2 = genericRecord()
            val history = historyOf(r1, r2)
            strategy.compact(history, 3)
            assertEquals(listOf(r1, r2), history.toList())
        }

        @Test
        fun `invalid records equal to limit do not get compacted`() {
            val r1 = genericRecord(); val r2 = genericRecord()
            val history = historyOf(r1, r2)
            strategy.compact(history, 2)
            assertEquals(listOf(r1, r2), history.toList())
        }

        @Test
        fun `preserve invalid records between valid records`() {
            val r1 = genericRecord(); val r2 = genericRecord(); val r3 = genericRecord()
            val r4 = genericRecord(); val r5 = genericRecord(); val r6 = genericRecord()
            val r7 = genericRecord(); val r8 = genericRecord()
            val history = historyOf(r1, r2, r3, r4, r5, r6, r7, r8)
            strategy.compact(history, 2, resolverFor(r4, r8))
            assertEquals(listOf(r4, r5, r6, r7, r8), history.toList())
        }

        @Test
        fun `valid at tail drops all records to the left`() {
            val r1 = genericRecord(); val r2 = genericRecord(); val r3 = genericRecord()
            val history = historyOf(r1, r2, r3)
            strategy.compact(history, 1, resolverFor(r3))
            assertEquals(listOf(r3), history.toList())
        }

        @Test
        fun `valid at head preserves all records to the right`() {
            val r1 = genericRecord(); val r2 = genericRecord(); val r3 = genericRecord()
            val r4 = genericRecord(); val r5 = genericRecord(); val r6 = genericRecord()
            val history = historyOf(r1, r2, r3, r4, r5, r6)
            strategy.compact(history, 1, resolverFor(r1))
            assertEquals(listOf(r1, r2, r3, r4, r5, r6), history.toList())
        }

        @Test
        fun `valid records above limit are discarded`() {
            val r1 = genericRecord(); val r2 = genericRecord()
            val r3 = genericRecord(); val r4 = genericRecord()
            val history = historyOf(r1, r2, r3, r4)
            strategy.compact(history, 2, resolverFor(r1, r2, r3, r4))
            assertEquals(listOf(r3, r4), history.toList())
        }

        @Test
        fun `valid records at limit are kept`() {
            val r1 = genericRecord(); val r2 = genericRecord()
            val r3 = genericRecord(); val r4 = genericRecord()
            val history = historyOf(r1, r2, r3, r4)
            strategy.compact(history, 4, resolverFor(r1, r2, r3, r4))
            assertEquals(listOf(r1, r2, r3, r4), history.toList())
        }

        @Test
        fun `valid records below limit keep invalid records`() {
            val r1 = genericRecord(); val r2 = genericRecord()
            val r3 = genericRecord(); val r4 = genericRecord()
            val history = historyOf(r1, r2, r3, r4)
            strategy.compact(history, 2, resolverFor(r4))
            assertEquals(listOf(r1, r2, r3, r4), history.toList())
        }

        @Test
        fun `valid in sea of invalid drops all left and keeps right`() {
            val r1 = genericRecord(); val r2 = genericRecord()
            val r3 = genericRecord(); val r4 = genericRecord()
            val history = historyOf(r1, r2, r3, r4)
            strategy.compact(history, 1, resolverFor(r2))
            assertEquals(listOf(r2, r3, r4), history.toList())
        }
    }

    // =========================================================================
    // LastNDimensionCompaction
    // =========================================================================

    @Nested
    inner class LastNDimensionCompactionTest : AbstractCompactionStrategyTest() {
        override val strategy = LastNDimensionCompaction

        @Test
        fun `single dimension smaller than limit is unchanged`() {
            val dim = worldId()
            val r1 = genericRecord(dim); val r2 = genericRecord(dim)
            val history = historyOf(r1, r2)
            strategy.compact(history, 3)
            assertEquals(listOf(r1, r2), history.toList())
        }

        @Test
        fun `single dimension equal to limit is unchanged`() {
            val dim = worldId()
            val r1 = genericRecord(dim); val r2 = genericRecord(dim)
            val history = historyOf(r1, r2)
            strategy.compact(history, 2)
            assertEquals(listOf(r1, r2), history.toList())
        }

        @Test
        fun `single dimension above limit drops oldest`() {
            val dim = worldId()
            val r1 = genericRecord(dim); val r2 = genericRecord(dim); val r3 = genericRecord(dim)
            val history = historyOf(r1, r2, r3)
            strategy.compact(history, 2)
            assertEquals(listOf(r2, r3), history.toList())
        }

        @Test
        fun `limit of 1 keeps only most recent per dimension`() {
            val dim1 = worldId(); val dim2 = worldId()
            val d1r1 = genericRecord(dim1); val d1r2 = genericRecord(dim1)
            val d2r1 = genericRecord(dim2)
            val history = historyOf(d1r1, d1r2, d2r1)
            strategy.compact(history, 1)
            assertEquals(listOf(d1r2, d2r1), history.toList())
        }

        @Test
        fun `tracks limits independently per dimension`() {
            val dim1 = worldId(); val dim2 = worldId()
            val d1r1 = genericRecord(dim1)
            val d2r1 = genericRecord(dim2)
            val d1r2 = genericRecord(dim1)
            val d2r2 = genericRecord(dim2)
            val d1r3 = genericRecord(dim1)
            val history = historyOf(d1r1, d2r1, d1r2, d2r2, d1r3)
            strategy.compact(history, 2)
            // dim1: d1r1 dropped, d1r2 and d1r3 kept
            // dim2: both kept (only 2)
            assertFalse(d1r1 in history)
            assertTrue(d1r2 in history)
            assertTrue(d1r3 in history)
            assertTrue(d2r1 in history)
            assertTrue(d2r2 in history)
            assertEquals(4, history.size)
        }

        @Test
        fun `interleaved dimensions each respect their own limit`() {
            val dim1 = worldId(); val dim2 = worldId()
            val d1r1 = genericRecord(dim1); val d2r1 = genericRecord(dim2)
            val d1r2 = genericRecord(dim1); val d2r2 = genericRecord(dim2)
            val d1r3 = genericRecord(dim1); val d2r3 = genericRecord(dim2)
            val history = historyOf(d1r1, d2r1, d1r2, d2r2, d1r3, d2r3)
            strategy.compact(history, 2)
            assertFalse(d1r1 in history)
            assertFalse(d2r1 in history)
            assertEquals(4, history.size)
        }
    }

    // =========================================================================
    // LastNValidDimensionCompaction
    // =========================================================================

    @Nested
    inner class LastNValidDimensionCompactionTest : AbstractCompactionStrategyTestWithValidation() {
        override val strategy = LastNValidDimensionCompaction

        @Test
        fun `single dimension invalid records below limit are unchanged`() {
            val dim = worldId()
            val r1 = genericRecord(dim); val r2 = genericRecord(dim)
            val history = historyOf(r1, r2)
            strategy.compact(history, 3)
            assertEquals(listOf(r1, r2), history.toList())
        }

        @Test
        fun `single dimension all invalid never hits limit`() {
            val dim = worldId()
            val r1 = genericRecord(dim); val r2 = genericRecord(dim); val r3 = genericRecord(dim)
            val history = historyOf(r1, r2, r3)
            strategy.compact(history, 2)
            assertEquals(listOf(r1, r2, r3), history.toList())
        }

        @Test
        fun `single dimension valid records above limit drops oldest`() {
            val dim = worldId()
            val r1 = genericRecord(dim); val r2 = genericRecord(dim)
            val r3 = genericRecord(dim); val r4 = genericRecord(dim)
            val history = historyOf(r1, r2, r3, r4)
            strategy.compact(history, 2, resolverFor(r1, r2, r3, r4))
            assertEquals(listOf(r3, r4), history.toList())
        }

        @Test
        fun `single dimension invalid records between valid are preserved`() {
            val dim = worldId()
            val r1 = genericRecord(dim); val r2 = genericRecord(dim); val r3 = genericRecord(dim)
            val r4 = genericRecord(dim); val r5 = genericRecord(dim)
            val history = historyOf(r1, r2, r3, r4, r5)
            // r2 and r5 are valid, r1/r3/r4 are invalid
            // limit 2: 2nd valid from end is r2, drop r1, keep r2..r5
            strategy.compact(history, 2, resolverFor(r2, r5))
            assertEquals(listOf(r2, r3, r4, r5), history.toList())
        }

        @Test
        fun `invalid records do not count toward per-dimension limit`() {
            val dim = worldId()
            val r1 = genericRecord(dim); val r2 = genericRecord(dim)
            val r3 = genericRecord(dim); val r4 = genericRecord(dim)
            // only r4 is valid, limit 2 — never hits limit so nothing dropped
            val history = historyOf(r1, r2, r3, r4)
            strategy.compact(history, 2, resolverFor(r4))
            assertEquals(listOf(r1, r2, r3, r4), history.toList())
        }

        @Test
        fun `tracks valid counts independently per dimension`() {
            val dim1 = worldId(); val dim2 = worldId()
            val d1r1 = genericRecord(dim1); val d2r1 = genericRecord(dim2)
            val d1r2 = genericRecord(dim1); val d2r2 = genericRecord(dim2)
            val d1r3 = genericRecord(dim1); val d2r3 = genericRecord(dim2)
            val history = historyOf(d1r1, d2r1, d1r2, d2r2, d1r3, d2r3)
            // all valid, limit 2 per dimension — oldest of each should drop
            strategy.compact(history, 2, resolverFor(d1r1, d2r1, d1r2, d2r2, d1r3, d2r3))
            assertFalse(d1r1 in history)
            assertFalse(d2r1 in history)
            assertEquals(4, history.size)
        }

        @Test
        fun `invalid records in one dimension do not affect another dimension`() {
            val dim1 = worldId(); val dim2 = worldId()
            val d1r1 = genericRecord(dim1); val d1r2 = genericRecord(dim1)
            val d2r1 = genericRecord(dim2); val d2r2 = genericRecord(dim2); val d2r3 = genericRecord(dim2)
            val history = historyOf(d1r1, d1r2, d2r1, d2r2, d2r3)
            // dim1: all invalid, nothing dropped
            // dim2: all valid, limit 2, d2r1 dropped
            strategy.compact(history, 2, resolverFor(d2r1, d2r2, d2r3))
            assertTrue(d1r1 in history)
            assertTrue(d1r2 in history)
            assertFalse(d2r1 in history)
            assertTrue(d2r2 in history)
            assertTrue(d2r3 in history)
        }

        @Test
        fun `limit of 1 keeps only most recent valid and everything after per dimension`() {
            val dim = worldId()
            val r1 = genericRecord(dim); val r2 = genericRecord(dim)
            val r3 = genericRecord(dim); val r4 = genericRecord(dim)
            val history = historyOf(r1, r2, r3, r4)
            // r2 is the most recent (only) valid, limit 1 — drop r1, keep r2..r4
            strategy.compact(history, 1, resolverFor(r2))
            assertEquals(listOf(r2, r3, r4), history.toList())
        }
    }
}