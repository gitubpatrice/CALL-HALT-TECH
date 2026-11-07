# Call Halt Tech

[Français](README.fr.md)

An Android app against **phone scams** — the fake bank adviser, the fake technician, the fake fraud
department — written first for elderly people and the relatives who look after them.

**Status: in development, not published.** Once its accesses are granted (it asks for them one by one), it
shows the STOP screen when, during a call or just after it, a code arrives by SMS, a banking app asks to approve an
operation (Sécur'Pass), a remote-control app is installed or set to work, or an app is given the accessibility
access. It can text someone close to you when that happens. Nothing is published before the app is reliable.

## The method

Never trust the caller's number: it is spoofed in seconds. Rely instead on what the scammer cannot avoid:
the victim must **do something irreversible on the phone during the call or just after it** — give a code
received by SMS, confirm an operation in the banking app, install a remote-control app. When that happens
during a call or just after it, the app interrupts with a STOP screen: *your bank, the police, the tax
office, your insurer: none of them will ever call you to ask for this. If you are told to ignore this
screen, it is a scam. Hang up, then call them yourself, on a number you know.*

It blocks nothing. It is a pause at the right moment.

It sees only phone calls on this phone, not calls over the internet (WhatsApp…): a call on the landline or on another
phone is not seen. The app's home screen says so, whenever it protects, with the rule that still holds: if someone
asks you, during a landline call, for a code you received by text message, it is a scam.

## Promises, checked by the build

- **No internet permission**, ever: the CI reads the merged release manifest against an exact list
  (`tools/check-manifest-permissions.py`, with its negative control).
- No account, no server, no Google Play Services. Nothing leaves the phone but the texts to the person close to you,
  if you choose one: by SMS, from your number, never a code nor a message's content ([PRIVACY.md](PRIVACY.md)).
- English, French, German, Italian, Spanish.

## Building

JDK 17 and the Android SDK; then `./gradlew :app:assembleDebug`. The full local gate, identical to the CI,
is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) § 8.

## Licence

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
