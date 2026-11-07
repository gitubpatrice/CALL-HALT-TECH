package com.filestech.callhalt.data.store

import com.filestech.callhalt.domain.detection.DetectorState
import com.filestech.callhalt.domain.detection.StopReturn
import com.filestech.callhalt.domain.model.SensitiveActionKind

/** The STOP screens left that come back (FN-4), between the detector's state and their rows. */
internal object StopReturnMapping {

    /** A row of an unknown trigger (written by another version) is left out. */
    fun returns(rows: List<StopReturnEntity>): Map<SensitiveActionKind, StopReturn> =
        rows.mapNotNull { row ->
            SensitiveActionKind.entries.firstOrNull { it.name == row.trigger }?.let { trigger ->
                trigger to StopReturn(
                    leftAt = moment(row.leftAt, row.leftBoot, row.leftSinceBoot),
                    returnedAt = row.returnedAt?.let { moment(it, row.returnedBoot, row.returnedSinceBoot) },
                )
            }
        }.toMap()

    fun rows(state: DetectorState): List<StopReturnEntity> =
        state.stopReturns.map { (trigger, back) ->
            StopReturnEntity(
                trigger = trigger.name,
                leftAt = back.leftAt.wall.toEpochMilli(),
                leftBoot = back.leftAt.uptime?.boot,
                leftSinceBoot = back.leftAt.uptime?.sinceBoot?.toMillis(),
                returnedAt = back.returnedAt?.wall?.toEpochMilli(),
                returnedBoot = back.returnedAt?.uptime?.boot,
                returnedSinceBoot = back.returnedAt?.uptime?.sinceBoot?.toMillis(),
            )
        }
}
