package com.filestech.callhalt.platform

import com.filestech.callhalt.platform.guardian.GuardianNotifier

/** For a test with no relative set up: a text asked for fails it. */
internal val noGuardian = GuardianNotifier { error("no text to the relative expected") }
