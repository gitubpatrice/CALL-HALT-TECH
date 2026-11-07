package com.filestech.callhalt.di

import com.filestech.callhalt.platform.guardian.ChoiceTexts
import com.filestech.callhalt.platform.guardian.ContactReader
import com.filestech.callhalt.platform.guardian.ContextGuardianWords
import com.filestech.callhalt.platform.guardian.GuardianNotifier
import com.filestech.callhalt.platform.guardian.GuardianSource
import com.filestech.callhalt.platform.guardian.GuardianWords
import com.filestech.callhalt.platform.guardian.PhoneContacts
import com.filestech.callhalt.platform.guardian.RepositoryGuardianSource
import com.filestech.callhalt.platform.guardian.ResourceChoiceTexts
import com.filestech.callhalt.platform.guardian.SmsGuardianNotifier
import com.filestech.callhalt.platform.guardian.SmsTextSender
import com.filestech.callhalt.platform.guardian.TextSender
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The relative (docs/ARCHITECTURE.md, D-034): what texts them, reached through interfaces tested on the JVM. */
@Module
@InstallIn(SingletonComponent::class)
abstract class GuardianModule {

    @Binds
    abstract fun guardianNotifier(impl: SmsGuardianNotifier): GuardianNotifier

    @Binds
    abstract fun guardianSource(impl: RepositoryGuardianSource): GuardianSource

    @Binds
    internal abstract fun guardianWords(impl: ContextGuardianWords): GuardianWords

    @Binds
    abstract fun textSender(impl: SmsTextSender): TextSender

    @Binds
    abstract fun choiceTexts(impl: ResourceChoiceTexts): ChoiceTexts

    @Binds
    abstract fun contactReader(impl: PhoneContacts): ContactReader
}
