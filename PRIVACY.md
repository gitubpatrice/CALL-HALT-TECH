# Privacy — Call Halt Tech

[Français](PRIVACY.fr.md)

*Describes the version in development (0.1.0). Every permission the app gains is added here in the same
change that adds it to the app, and the build checks the app asks for nothing else.*

## What the app asks for

- **Phone** (`READ_PHONE_STATE`): to know when a call starts and ends. The app does not read the number
  (that would need another permission, which it does not ask for), and it never hears the call. This
  version does not ask for it on screen yet: nothing is detected until it is granted in the phone's
  settings and the app has been opened once.

The manifest also carries a line Android's own library adds for the app's internal use
(`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`); it gives no access to anything.

## What the app keeps

On this phone only, in the app's private storage:

- its settings;
- the memory of its detector: for the latest call only, when it started and ended, and whether it was
  incoming or outgoing — never the number; and the last call state the phone reported;
- a journal of the alerts it shows (when, why, what was chosen). In this version, nothing writes to it yet.

Android backup and device-to-device transfer are **disabled** for this app: none of it leaves the phone.

## What the app sends

**Nothing.** It has no internet permission, no account, no server, no analytics, and no Google Play Services.
