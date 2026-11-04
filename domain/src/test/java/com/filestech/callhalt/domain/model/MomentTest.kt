package com.filestech.callhalt.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/** The phone's two clocks (docs/ARCHITECTURE.md, P-13): the uptime within a boot, the count between boots. */
class MomentTest {

    private val noon: Instant = Instant.parse("2026-11-04T12:00:00Z")

    private fun moment(wallMinutes: Long, boot: Int?, uptimeMinutes: Long = 0) = Moment(
        noon.plus(Duration.ofMinutes(wallMinutes)),
        boot?.let { Uptime(it, Duration.ofMinutes(uptimeMinutes)) },
    )

    @Test
    fun `within one boot the uptime decides, whatever the civil clock says`() {
        val callStart = moment(wallMinutes = 0, boot = 7, uptimeMinutes = 60)
        // The civil clock set back half an hour, three minutes later.
        val code = moment(wallMinutes = -27, boot = 7, uptimeMinutes = 63)

        assertThat(code.isAfter(callStart)).isTrue()
        assertThat(callStart.until(code)).isEqualTo(Duration.ofMinutes(3))
    }

    @Test
    fun `a later boot comes after, whatever the civil clock says, and is measured on the civil clock`() {
        val before = moment(wallMinutes = 0, boot = 7, uptimeMinutes = 600)
        val after = moment(wallMinutes = -120, boot = 8, uptimeMinutes = 1)

        assertThat(after.isAfter(before)).isTrue()
        assertThat(before.until(after)).isEqualTo(Duration.ofMinutes(-120))
    }

    @Test
    fun `without an uptime on either side, the civil clock decides - as before`() {
        val known = moment(wallMinutes = 0, boot = 7, uptimeMinutes = 60)
        val unknown = moment(wallMinutes = -1, boot = null)

        assertThat(unknown.isBefore(known)).isTrue()
        assertThat(unknown.until(known)).isEqualTo(Duration.ofMinutes(1))
    }

    @Test
    fun `a duration moves both clocks`() {
        val start = moment(wallMinutes = 0, boot = 7, uptimeMinutes = 60)

        assertThat(start.plus(Duration.ofMinutes(15))).isEqualTo(moment(wallMinutes = 15, boot = 7, uptimeMinutes = 75))
        assertThat(start.minus(Duration.ofMinutes(61)).uptime?.sinceBoot).isEqualTo(Duration.ofMinutes(-1))
    }
}
