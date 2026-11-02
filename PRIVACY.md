# Privacy — Call Halt Tech

[Français](PRIVACY.fr.md)

*Describes the version in development (0.1.0). Every permission the app gains is added here in the same
change that adds it to the app, and the build checks the app asks for nothing else.*

## What the app asks for

- **Phone** (`READ_PHONE_STATE`): to know when a call starts and ends. The app does not read the number
  (that would need another permission, which it does not ask for), and it never hears the call. The
  activation screen asks for it, together with "Answer phone calls" below: Android groups both in one question.
- **Display over other apps** (`SYSTEM_ALERT_WINDOW`): to put the STOP screen in front of the app you are
  using — your banking app included — when a risky action happens during a call. The app draws no window
  over other apps: the STOP screen is a full screen of its own, excluded from screenshots and screen
  sharing.
- **Answer phone calls** (`ANSWER_PHONE_CALLS`): only to end the call when you press "Hang up", or "Hang up
  and call my bank", on the STOP screen. Without it, neither button is shown during a call. The app never
  answers or places a call by itself: "call my bank" opens the phone's dialler, and you press call.
- **Notification access** (granted in the phone's settings; not a permission): to see your banking app ask you
  to approve an operation, and a validation code arrive by SMS. Of each notification on the phone, the app reads
  which app posted it and on which channel (the identifier the posting app gives a kind of notification). It
  recognises a bank's request by that channel alone: today, the requests of Sécur'Pass (banks of the BPCE group).
  **Of your SMS app's notifications only**, it also reads the latest message, in memory, to tell whether it is a
  validation code ("Votre code de validation est …"); the text is forgotten at once, never stored, never sent.
  Of every other app's notifications, it reads neither the title nor the text. The activation screen opens the
  phone's settings page for it: only you can grant it.

To show its help with Android's "restricted settings" only where it may be needed, the app also reads how it was
installed on this phone — by a store, or from a file. It keeps nothing of it.

The manifest also carries a line Android's own library adds for the app's internal use
(`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`); it gives no access to anything.

## What the app keeps

On this phone only, in the app's private storage:

- its settings;
- the memory of its detector: for the latest call only, when it started and ended, and whether it was
  incoming or outgoing — never the number; and the last call state the phone reported;
- a journal of the alerts it shows: when, why, which app the request or the code came from (its package name, such
  as `com.credit_coop.android.mobilebanking`), and what was chosen — never the text of a message. A request or a
  code outside a call, or after the minutes that follow one, is not kept.

Android backup and device-to-device transfer are **disabled** for this app: none of it leaves the phone.

## What the app sends

**Nothing.** It has no internet permission, no account, no server, no analytics, and no Google Play Services.
