package com.filestech.callhalt.platform.install

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind

/**
 * The apps that let someone else see or control THIS phone — the side a fake adviser has the victim install
 * (docs/ARCHITECTURE.md, D-031) — by application id, with the name the warning gives them: ours, never the one an app
 * gives itself (any app may take a listed id that is not installed, and any name with it).
 *
 * Each id checked on 2026-11-05 on its Google Play page (French store, HTTP 200, the title below), RustDesk in its APK
 * and on F-Droid (gone from Google Play since 2023, because of scams): a wrong id protects nothing, and nothing says
 * so. Their add-ons too, which the main app asks for to be controlled on a given make (Doro: phones for older people).
 * Not the apps that only control ANOTHER device (TeamViewer Remote Control, Chrome Remote Desktop, AirMirror…):
 * installing one hands nobody this phone. Nor Microsoft's Remote Help, which works on a company-enrolled phone only.
 *
 * Declared one by one in the manifest's `<queries>` as well: Android shows this app no other one (package visibility,
 * Android 11+), and tells it of no other installation. `RemoteControlAppsTest` keeps both lists equal.
 */
internal val REMOTE_CONTROL_APPS: Map<String, String> = mapOf(
    "com.anydesk.anydeskandroid" to "AnyDesk",
    "com.anydesk.adcontrol.ad1" to "AnyDesk plugin ad1",
    "com.anydesk.adcontrol.htc1" to "AnyDesk plugin htc1",
    "com.teamviewer.quicksupport.market" to "TeamViewer QuickSupport",
    "com.teamviewer.host.market" to "TeamViewer Host",
    "com.teamviewer.quicksupport.addon.universal" to "TeamViewer Universal Add-On",
    "com.teamviewer.quicksupport.addon.mobilebase" to "TeamViewer Add-On: MobileBase",
    "com.teamviewer.quicksupport.addon.aosp7" to "TeamViewer Add-On: Android 7",
    "com.teamviewer.quicksupport.addon.doro" to "TeamViewer Add-On: Doro",
    "com.teamviewer.quicksupport.addon.nokia_4_2" to "TeamViewer Add-On: Nokia 4.2",
    "com.carriez.flutter_hbb" to "RustDesk",
    "com.hoptodesk.app" to "HopToDesk",
    "com.sand.airdroid" to "AirDroid",
    "com.sand.airsos" to "AirDroid Remote Support",
    "com.sand.airdroidbiz" to "AirDroid Business Daemon",
    "com.sand.aircast" to "AirDroid Cast",
    "com.sand.remotesupportaddon" to "AirDroid Control Add-on",
    "com.splashtop.sos" to "Splashtop SOS",
    "com.splashtop.sos.onprem" to "Splashtop SOS On-Prem",
    "com.splashtop.streamer.csrs" to "Splashtop Streamer",
    "com.splashtop.streamer.csrs.enterprise" to "Splashtop Streamer for On-Prem",
    "com.splashtop.streamer.addon.knox" to "Splashtop Add-on: Samsung",
    "com.zoho.assist.agent" to "Zoho Assist",
    "com.zoho.remoteplugin.universal" to "Zoho Assist Add-On: Universal",
    "com.logmein.rescuemobile" to "LogMeIn Rescue",
    "com.goto.resolve.customer" to "LogMeIn Resolve",
    "com.logmein.rescueassist" to "GoToAssist",
    "com.logmein.g2acorpcustomer" to "GoToAssist Corporate",
    "com.islonline.isllight.mobile.android" to "ISL Light",
    "com.islonline.isllight.mobile.android.universaladdon" to "ISL Light Add-On: Universal",
    "com.islonline.isllight.mobile.android.addon.LG" to "ISL Light Add-On: LG",
    "com.bomgar.thinclient.android" to "BeyondTrust Support",
    "com.bomgar.thinpin.android" to "BeyondTrust Jump Client",
    "com.aomei.anyviewer" to "AnyViewer",
    "it.nanosystems.supremo.host" to "Supremo Mobile Assist",
    "it.nanosystems.supremo.addon" to "Supremo Mobile Add-On",
    "me.getscreen.agent" to "Getscreen.me",
    "com.awesun.client.service" to "AweSun Host",
    "com.remotepc.host" to "RemotePC Host",
    "com.screenconnect.androidclient" to "ScreenConnect",
    "com.zuler.deskin" to "DeskIn",
    "com.raywing.avica" to "Avica",
    "com.entersoftware.iperiusremotedesktop" to "Iperius Remote Desktop",
    "net.tsplus.remotesupport" to "TSplus Remote Support",
    "com.projector.screenmeet.support" to "ScreenMeet Support",
    "com.simplehelp.remotesupport" to "SimpleHelp Remote Support",
    "com.rsupport.rs.activity.rsupport" to "RemoteCall",
    "com.realvnc.server" to "RealVNC Server",
    "net.christianbeier.droidvnc_ng" to "droidVNC-NG",
)

/**
 * What the installation of [packageName] is to the engine, if anything: a remote-control app's first install. An
 * update ([replacing]) is not one — the app was there before, and was warned about then. Stamped [at], the phone's
 * clocks at reception (Signal's contract); the package goes to the journal, which app it was.
 */
internal fun installSignal(packageName: String, replacing: Boolean, at: Moment): SensitiveAction? =
    if (replacing || packageName !in REMOTE_CONTROL_APPS) {
        null
    } else {
        SensitiveAction(at, SensitiveActionKind.REMOTE_CONTROL_INSTALLED, source = packageName)
    }

/**
 * Whether the removal of [packageName] takes its warning away (D-039): an app of the list uninstalled — not updated
 * ([replacing]: Android removes it, then adds it back), which would leave the app in place and its warning gone.
 */
internal fun withdrawsWarning(packageName: String, replacing: Boolean): Boolean = !replacing && packageName in REMOTE_CONTROL_APPS

/**
 * Where a remote-control app just installed is uninstalled, from its STOP screen and its warning (D-031, D-039): its page
 * in the phone's settings, else the settings — to be opened as the phone's OWN settings serve them
 * (`platform.access.systemSettings`): the app itself could declare the same page.
 */
internal fun uninstallPages(packageName: String): List<Intent> = listOf(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    Intent(Settings.ACTION_SETTINGS),
)
