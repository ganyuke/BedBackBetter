package io.github.ganyuke.bedFallback.compaction

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent
import io.github.ganyuke.bedFallback.RespawnRecord
import io.github.ganyuke.bedFallback.WorldCoord
import io.github.ganyuke.bedFallback.validation.BedValidator
import io.github.ganyuke.bedFallback.validation.RespawnAnchorValidator
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.bukkit.Location
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.util.UUID

/**
 * Tests for all CompactionStrategy implementations.
 *
 * Record validity is controlled via cause:
 *   - PlayerSetSpawnEvent.Cause.PLUGIN  -> resolveLocation() returns null -> "invalid"
 *   - PlayerSetSpawnEvent.Cause.BED     -> resolveLocation() hits BedValidator -> "valid"
 *     (requires MockBukkit world setup; for compaction tests we avoid this by only
 *      testing strategies that don't call resolveLocation, or by using PLUGIN records)
 *
 * For LastNValidCompaction and LastNValidDimensionCompaction, validity is needed.
 * We use PLUGIN (invalid) freely, and for "valid" records we rely on the else->null
 * branch being absent — instead we use a test double approach: subclass RespawnRecord
 * isn't possible (data class), so we use MockBukkit and a real world where the bed
 * validator can succeed, OR we restructure to test only the compaction logic by
 * stubbing via cause = PLUGIN for invalid and noting that the "valid" path requires
 * integration. See the ValidCompaction tests for the integration approach.
 */
class CompactionStrategyTest {
    abstract class AbstractCompactionStrategyTest {
        abstract val strategy: CompactionStrategy

        fun worldId(): UUID = UUID.randomUUID()

        fun genericRecord(world: UUID = worldId()): RespawnRecord =
            RespawnRecord(
                worldCoord = WorldCoord(0, 64, 0, world),
                cause = PlayerSetSpawnEvent.Cause.PLUGIN, // vanilla resolver makes this always return null location
                playerYaw = 0f
            )

        fun historyOf(vararg records: RespawnRecord) = ArrayDeque(records.toList())

        @Test
        fun `limit zero clears all records`() {
            val history = historyOf(genericRecord(), genericRecord(), genericRecord())
            LastNCompaction.compact(history, 0)
            assertTrue(history.isEmpty())
        }

        @Test
        fun `negative limit clears all records`() {
            val history = historyOf(genericRecord(), genericRecord())
            LastNCompaction.compact(history, -1)
            assertTrue(history.isEmpty())
        }

        @Test
        fun `empty history doesn't break anything`() {
            val history = historyOf()
            LastNCompaction.compact(history, 3)
            assertTrue(history.isEmpty())
        }
    }

    abstract class AbstractCompactionStrategyTestWithValidation : AbstractCompactionStrategyTest() {
        @BeforeEach
        fun setup() {
            mockkObject(BedValidator)
            mockkObject(RespawnAnchorValidator)
        }

        @AfterEach
        fun teardown() {
            unmockkObject(BedValidator)
            unmockkObject(RespawnAnchorValidator)
        }
    }


    @Nested
    inner class LastNCompactionTest : AbstractCompactionStrategyTest() {
        override val strategy = LastNCompaction

        @Test
        fun `history smaller than limit is unchanged`() {
            val r1 = genericRecord()
            val r2 = genericRecord()

            val history = historyOf(r1, r2)
            LastNCompaction.compact(history, 3)
            assertEquals(listOf(r1, r2), history.toList())
        }

        @Test
        fun `history equal to limit is unchanged`() {
            val r1 = genericRecord()
            val r2 = genericRecord()
            val r3 = genericRecord()

            val history = historyOf(r1, r2, r3)
            LastNCompaction.compact(history, 3)
            assertEquals(listOf(r1, r2, r3), history.toList())
        }

        @Test
        fun `keeps only the most recent N records`() {
            val r1 = genericRecord()
            val r2 = genericRecord()
            val r3 = genericRecord()
            val r4 = genericRecord()

            val history = historyOf(r1, r2, r3, r4)
            LastNCompaction.compact(history, 3)
            // most recent are at the end
            assertEquals(listOf(r2, r3, r4), history.toList())
        }

        @Test
        fun `limit of 1 keeps only the last record`() {
            val r1 = genericRecord()
            val r2 = genericRecord()
            val r3 = genericRecord()

            val history = historyOf(r1, r2, r3)
            LastNCompaction.compact(history, 1)
            assertEquals(listOf(r3), history.toList())
        }
    }

    @Nested
    inner class LastNValidCompactionTest : AbstractCompactionStrategyTestWithValidation() {
        override val strategy = LastNValidCompaction

        @Test
        fun `invalid records below limit do not get compacted`() {
            val r1 = genericRecord()
            val r2 = genericRecord()

            val history = historyOf(r1, r2)
            strategy.compact(history, 3)
            assertEquals(listOf(r1, r2), history.toList())
        }

        @Test
        fun `invalid records equal to the limit do not get compacted`() {
            val r1 = genericRecord()
            val r2 = genericRecord()

            val history = historyOf(r1, r2)
            strategy.compact(history, 2)
            assertEquals(listOf(r1, r2), history.toList())
        }

        @Test
        fun `preserve invalid records between valid records`() {
            val r1 = genericRecord()
            val r2 = genericRecord()
            val r3 = genericRecord()
            val r4 = genericRecord()
            val r5 = genericRecord()
            val r6 = genericRecord()
            val r7 = genericRecord()
            val r8 = genericRecord()

            // limit of 2 means that everything to the left of the 2nd valid
            // gets dropped, then we check if the remaining in the middle are invalid
            val history = historyOf(r1, r2, r3, r4, r5, r6, r7, r8)
            val validRecords = setOf(r4, r8)
            val validLocation = Location(null, 0.0, 0.0, 0.0)
            val resolver = { record: RespawnRecord -> if (record in validRecords) validLocation else null }

            strategy.compact(history, 2, resolver)
            assertEquals(listOf(r4, r5, r6, r7, r8), history.toList())
        }

        @Test
        fun `valid at tail drops all records to the left`() {
            val r1 = genericRecord()
            val r2 = genericRecord()
            val r3 = genericRecord()

            val history = historyOf(r1, r2, r3)
            val validRecords = setOf(r3)
            val validLocation = Location(null, 0.0, 0.0, 0.0)
            val resolver = { record: RespawnRecord -> if (record in validRecords) validLocation else null }

            strategy.compact(history, 1, resolver)
            assertEquals(listOf(r3), history.toList())
        }

        @Test
        fun `valid at head preserves all records to the right`() {
            val r1 = genericRecord()
            val r2 = genericRecord()
            val r3 = genericRecord()
            val r4 = genericRecord()
            val r5 = genericRecord()
            val r6 = genericRecord()

            // r1 should be preserved despite the limit of 2
            // all invalid records ahead are preserved because they could be valid later
            val history = historyOf(r1, r2, r3, r4, r5, r6)
            val validRecords = setOf(r1)
            val validLocation = Location(null, 0.0, 0.0, 0.0)
            val resolver = { record: RespawnRecord -> if (record in validRecords) validLocation else null }

            strategy.compact(history, 1, resolver)
            assertEquals(listOf(r1, r2, r3, r4, r5, r6), history.toList())
        }

        @Test
        fun `valid records above limit are discarded`() {
            val r1 = genericRecord()
            val r2 = genericRecord()
            val r3 = genericRecord()
            val r4 = genericRecord()

            val history = historyOf(r1, r2, r3, r4)
            val validRecords = setOf(r1, r2, r3, r4)
            val validLocation = Location(null, 0.0, 0.0, 0.0)
            val resolver = { record: RespawnRecord -> if (record in validRecords) validLocation else null }

            strategy.compact(history, 2, resolver)
            assertEquals(listOf(r3, r4), history.toList())
        }

        @Test
        fun `valid records at limit are kept`() {
            val r1 = genericRecord()
            val r2 = genericRecord()
            val r3 = genericRecord()
            val r4 = genericRecord()

            val history = historyOf(r1, r2, r3, r4)
            val validRecords = setOf(r1, r2, r3, r4)
            val validLocation = Location(null, 0.0, 0.0, 0.0)
            val resolver = { record: RespawnRecord -> if (record in validRecords) validLocation else null }

            strategy.compact(history, 4, resolver)
            assertEquals(listOf(r1, r2, r3, r4), history.toList())
        }

        @Test
        fun `valid records below limit keep invalid records`() {
            val r1 = genericRecord()
            val r2 = genericRecord()
            val r3 = genericRecord()
            val r4 = genericRecord()

            val history = historyOf(r1, r2, r3, r4)
            val validRecords = setOf(r4)
            val validLocation = Location(null, 0.0, 0.0, 0.0)
            val resolver = { record: RespawnRecord -> if (record in validRecords) validLocation else null }

            strategy.compact(history, 2, resolver)
            assertEquals(listOf(r1, r2, r3, r4), history.toList())
        }

        @Test
        fun `valid in sea of invalid drops all left and keeps right`() {
            val r1 = genericRecord()
            val r2 = genericRecord()
            val r3 = genericRecord()
            val r4 = genericRecord()

            val history = historyOf(r1, r2, r3, r4)
            val validRecords = setOf(r2)
            val validLocation = Location(null, 0.0, 0.0, 0.0)
            val resolver = { record: RespawnRecord -> if (record in validRecords) validLocation else null }

            strategy.compact(history, 1, resolver)
            assertEquals(listOf(r2, r3, r4), history.toList())
        }
    }
}