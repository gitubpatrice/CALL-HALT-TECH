# Privacy — Call Halt Tech

[Français](PRIVACY.fr.md)

*Describes the version in development (0.1.0). Every permission the app gains is added here in the same
change that adds it to the app, and the build checks the app asks for nothing else.*

## What the app asks for

**No permission.** The only line in its manifest is one Android's own library adds for the app's internal
use (`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`); it gives no access to anything.

## What the app keeps

On this phone only, in the app's private storage:

- its settings;
- the memory of its detector and a journal of the alerts it shows (when, why, what was chosen). In this
  version, nothing writes to them yet.

Android backup and device-to-device transfer are **disabled** for this app: none of it leaves the phone.

## What the app sends

**Nothing.** It has no internet permission, no account, no server, no analytics, and no Google Play Services.
