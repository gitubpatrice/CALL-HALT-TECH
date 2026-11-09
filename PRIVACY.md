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
- **Answer phone calls** (`ANSWER_PHONE_CALLS`): only to end the call when you press "Hang up", "Hang up and
  call my bank" or "Hang up and remove the access" on the STOP screen. Without it, none of these buttons is shown
  during a call. The app never
  answers or places a call by itself: "call my bank" opens the phone's dialler, and you press call.
- **Ask to run in the background without restriction** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`): only to show the
  phone's own question, "Let app always run in background?", during the activation. A protection app is hardly ever
  opened, and phones put the apps nobody opens to sleep; asleep, it would see nothing. This permission reads nothing;
  you answer, and you can take it back in the app's battery settings.
- **Notification access** (granted in the phone's settings; not a permission): to see your banking app ask you
  to approve an operation, and a validation code arrive by SMS. Of each notification on the phone, the app reads
  which app posted it and on which channel (the identifier the posting app gives a kind of notification). It
  recognises a bank's request by that channel alone: today, the requests of Sécur'Pass (banks of the BPCE group).
  **Of your SMS app's notifications only**, it also reads the latest message, in memory, to tell whether it is a
  validation code ("Votre code de validation est …"); the text is forgotten at once, never stored, never sent.
  To recognise a burst of SMS, which may hide a code, and to say on the home screen when the SMS app shows too many,
  it also counts the SMS app's notifications shown: their identifier, and when each one came, in memory, as long as
  they are shown; never the text.
  Of the notifications of the remote-control apps of its list (AnyDesk, RustDesk…), it also notes whether each one
  is new, never what it says: during a call, such an app at work opens the STOP screen. Of every other app's
  notifications, it reads neither the title nor the text. The activation screen opens the phone's settings page
  for it: only you can grant it.
- **Notifications** (`POST_NOTIFICATIONS`, and `USE_FULL_SCREEN_INTENT`): if the phone prevents the STOP screen from
  showing, the app warns you with a notification that opens it; on a locked phone or a sleeping screen, it opens it at
  once, as long as the phone lets it (an Android setting, "full-screen notifications", can take that back). It says
  what just happened (a code received, a request from your bank) — the STOP
  screen says it too, over the lock screen — but neither the number calling, nor the code, nor your bank's name. From
  Android 13, the phone asks for your consent; you can take it back in the app's notification settings. The app
  sends one other notification: when an app that lets someone else control the phone has just been installed
  (AnyDesk, TeamViewer QuickSupport, RustDesk…), it names that app and opens its page in the phone's settings, where
  it is uninstalled; during a call or just after it, the STOP screen says it first, and that notification, silent,
  stays for after the call. It is taken away once the app is uninstalled.
- **Vibration** (`VIBRATE`): two short buzzes as the STOP screen opens, to feel it with the phone at your ear. Granted
  at install, nothing asked; it reads nothing.
- **Send SMS** (`SEND_SMS`): only if you choose someone close to alert, and asked on that screen, not by the
  activation. The app uses it to send, never to read: Android's question says "send and view SMS messages", the name of
  the whole group, but the app asks for no permission to read your messages (what it reads of a code goes through the
  notification access, above). It texts **only the number you chose**, from your own number, like any text — Android
  files each one in your SMS app, in your conversation with that person:
  - when you choose them, that this phone will text them if the STOP screen shows during a call;
  - when the STOP screen shows during a call or just after it, or when "Continue" is chosen despite it: what happened
    in a few words (a code received by SMS, a request from the bank, a remote-control app…), whether the call was
    still going on — **never the code, nor any word of a message**, nor the number calling. One text per kind of
    STOP screen in a quarter of an hour, one for each "Continue", and never more than 20 a day;
  - when you choose someone else, or no one any more, that this phone will no longer alert them.

  Without that permission, the STOP screen works all the same; only the texts do not go.

When you pick that person among your contacts, your contacts app hands the app that one contact's name and number,
and nothing else: the app has no access to your contacts.

To see those installations, the app may see whether one of the remote-control apps of its list (about fifty, named
in its code and its manifest) is installed, and Android tells it when one is installed or uninstalled (uninstalled,
its warning is taken away; nothing is kept). It does not see the phone's other apps, and does not ask to.

To show its help with Android's "restricted settings" only where it may be needed, the app also reads how it was
installed on this phone — by a store, or from a file. It keeps nothing of it. On a Xiaomi phone, it reads in the same
way whether the phone lets it "start automatically", to ask for that setting only if it is missing. It also reads the
list of the accessibility services turned on that Android keeps, no permission needed: during a call, an app given that
access opens the STOP screen; of it the app keeps only the app's package name, in the journal. And it knows whether
its own notification access is at work, to say so on the home screen. When the phone switches off or restarts, Android
tells the apps that are running: the app then records the end of the call in progress, if any, and nothing else about
the shutdown.

The manifest also carries a line Android's own library adds for the app's internal use
(`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`); it gives no access to anything.

## What the app keeps

On this phone only, in the app's private storage:

- its settings;
- the number of the person close to you that it texts, and their name when you picked them among your contacts, until
  you choose someone else or no one;
- the memory of its detector: for the latest call only, when it started and ended, and whether it was
  incoming or outgoing — never the number; and the last call state the phone reported. Its times are kept twice: by
  the phone's clock, and as the time since the phone started, with Android's count of its starts — so that a clock
  set back or forward changes nothing;
- a journal of the alerts it shows: when, why, which app the request or the code came from, or which remote-control
  app was installed (its package name, such as `com.credit_coop.android.mobilebanking`), and what was chosen — never
  the text of a message. A request or a code outside a call, or after the minutes that follow one, is not kept; a
  remote-control app installed then is, with its warning. Each line is kept **14 months**, then
  erased: the 13 months the law leaves you to contest a payment with your bank, counted from its debit, and one more
  for a card debited at the end of the month — the journal shows that a call was in progress, or had just ended,
  when it was asked for.
  Never more than 10,000 lines: past that, the oldest go first. You read it in the "Alert history" screen, from the
  home screen; its "Share" button hands it, as text, to the app you pick in Android's share menu (a text, an e-mail,
  for your bank or the police) — only when you touch it, and only where you send it;
- when your SMS app hides the text of messages in its notifications, its package name (such as
  `com.filestech.sms`), to say so on the home screen while it is your SMS app; never the text of a message.

Uninstalling the app, or clearing its data in the phone's settings, erases all of it at once.

Android backup and device-to-device transfer are **disabled** for this app: none of it leaves the phone.

## What the app sends

**Nothing over the internet.** It has no internet permission, no account, no server, no analytics, and no Google Play
Services.

The only thing that leaves the phone is the texts to the person close to you, if you chose one — by SMS, from your
number, to theirs alone, as described above.
