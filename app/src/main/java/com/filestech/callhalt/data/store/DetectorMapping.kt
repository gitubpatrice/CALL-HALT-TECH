package com.filestech.callhalt.data.store

import com.filestech.callhalt.domain.detection.DetectorState
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.JournalEntry
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.domain.model.Uptime
import com.filestech.callhalt.domain.model.WarningReason
import java.time.Duration
import java.time.Instant

/**
 * Rows ↔ domain, pure (tested on the JVM). Times keep millisecond precision, which is what the adapters
 * stamp. A row that no longer reads — an unknown enum name, an end before its start — is dropped rather
 * than allowed to crash the protection: [RoomDetectorStore] decides from what remains.
 */
internal object DetectorMapping {

    private const val HIDDEN = "HIDDEN"
    private const val UNREADABLE = "UNREADABLE"
    private const val NUMBER = "NUMBER"

    private const val TYPE_STOP = "STOP"
    private const val TYPE_NOT_SHOWN = "NOT_SHOWN"
    private const val TYPE_WARNING = "WARNING"
    private const val TYPE_ANSWER = "ANSWER"

    fun toState(calls: List<CallSessionEntity>, stops: List<PendingStopEntity>): DetectorState {
        val sessions = calls.mapNotNull { row -> row.toCallSession()?.let { it to row.longCallWarned } }
        return DetectorState(
            calls = sessions.associate { (call, _) -> call.channel to call },
            pendingStops = stops.mapNotNull { row ->
                enumOrNull<SensitiveActionKind>(row.trigger)?.let { it to moment(row.shownAt, row.shownBoot, row.shownSinceBoot) }
            }.toMap(),
            longCallWarned = sessions.filter { (_, warned) -> warned }.map { (call, _) -> call.channel }.toSet(),
        )
    }

    fun callRows(state: DetectorState): List<CallSessionEntity> =
        state.calls.values.map { call ->
            val (kind, number, inContacts) = call.counterpart.columns()
            CallSessionEntity(
                channel = call.channel.name,
                direction = call.direction.name,
                counterpartKind = kind,
                counterpartNumber = number,
                counterpartInContacts = inContacts,
                startedAt = call.startedAt.wall.toEpochMilli(),
                endedAt = call.endedAt?.wall?.toEpochMilli(),
                longCallWarned = call.channel in state.longCallWarned,
                startedBoot = call.startedAt.uptime?.boot,
                startedSinceBoot = call.startedAt.uptime?.sinceBoot?.toMillis(),
                endedBoot = call.endedAt?.uptime?.boot,
                endedSinceBoot = call.endedAt?.uptime?.sinceBoot?.toMillis(),
            )
        }

    fun stopRows(state: DetectorState): List<PendingStopEntity> =
        state.pendingStops.map { (trigger, shownAt) ->
            PendingStopEntity(trigger.name, shownAt.wall.toEpochMilli(), shownAt.uptime?.boot, shownAt.uptime?.sinceBoot?.toMillis())
        }

    fun journalRow(entry: JournalEntry): JournalEntryEntity = when (entry) {
        is JournalEntry.Stop -> {
            val (kind, number, inContacts) = entry.counterpart.columns()
            blankRow(entry.at, TYPE_STOP).copy(
                trigger = entry.trigger.name,
                source = entry.source,
                counterpartKind = kind,
                counterpartNumber = number,
                counterpartInContacts = inContacts,
            )
        }
        is JournalEntry.NotShown -> blankRow(entry.at, TYPE_NOT_SHOWN).copy(trigger = entry.trigger.name)
        is JournalEntry.Warning -> blankRow(entry.at, TYPE_WARNING).copy(warning = entry.reason.name, source = entry.source)
        is JournalEntry.Answer -> blankRow(entry.at, TYPE_ANSWER).copy(trigger = entry.trigger.name, choice = entry.choice.name)
    }

    fun journalEntry(row: JournalEntryEntity): JournalEntry? {
        val at = Instant.ofEpochMilli(row.at)
        return when (row.type) {
            TYPE_STOP -> {
                val trigger = enumOrNull<SensitiveActionKind>(row.trigger)
                val counterpart = counterpartOf(row.counterpartKind, row.counterpartNumber, row.counterpartInContacts)
                if (trigger == null || counterpart == null) null else JournalEntry.Stop(at, trigger, row.source, counterpart)
            }
            TYPE_NOT_SHOWN -> enumOrNull<SensitiveActionKind>(row.trigger)?.let { JournalEntry.NotShown(at, it) }
            TYPE_WARNING -> enumOrNull<WarningReason>(row.warning)?.let { JournalEntry.Warning(at, it, row.source) }
            TYPE_ANSWER -> {
                val trigger = enumOrNull<SensitiveActionKind>(row.trigger)
                val choice = enumOrNull<StopChoice>(row.choice)
                if (trigger == null || choice == null) null else JournalEntry.Answer(at, trigger, choice)
            }
            else -> null
        }
    }

    private fun blankRow(at: Instant, type: String) = JournalEntryEntity(
        at = at.toEpochMilli(),
        type = type,
        trigger = null,
        warning = null,
        choice = null,
        source = null,
        counterpartKind = null,
        counterpartNumber = null,
        counterpartInContacts = null,
    )

    private fun CallSessionEntity.toCallSession(): CallSession? {
        val channel = enumOrNull<CallChannel>(channel)
        val direction = enumOrNull<CallDirection>(direction)
        val counterpart = counterpartOf(counterpartKind, counterpartNumber, counterpartInContacts)
        val started = moment(startedAt, startedBoot, startedSinceBoot)
        val ended = endedAt?.let { moment(it, endedBoot, endedSinceBoot) }
        return when {
            channel == null || direction == null || counterpart == null -> null
            // An end before its start is a corrupted row (the domain refuses to build it): dropped.
            ended != null && ended.isBefore(started) -> null
            else -> CallSession(channel, direction, counterpart, started, ended)
        }
    }

    private fun Counterpart.columns(): Triple<String, String?, Boolean> = when (this) {
        Counterpart.Hidden -> Triple(HIDDEN, null, false)
        Counterpart.Unreadable -> Triple(UNREADABLE, null, false)
        is Counterpart.Number -> Triple(NUMBER, raw, inContacts)
    }

    /** A NUMBER needs both its digits and its contact flag: a missing flag is a row that no longer reads, not "unknown". */
    private fun counterpartOf(kind: String?, number: String?, inContacts: Boolean?): Counterpart? = when (kind) {
        HIDDEN -> Counterpart.Hidden
        UNREADABLE -> Counterpart.Unreadable
        NUMBER -> if (number.isNullOrBlank() || inContacts == null) null else Counterpart.Number(number, inContacts)
        else -> null
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        enumValues<E>().firstOrNull { it.name == name }
}

/** The uptime needs both its columns: one without the other reads as none, and the civil time decides (P-13). */
private fun moment(wallMillis: Long, boot: Int?, sinceBootMillis: Long?) = Moment(
    Instant.ofEpochMilli(wallMillis),
    if (boot == null || sinceBootMillis == null) null else Uptime(boot, Duration.ofMillis(sinceBootMillis)),
)
