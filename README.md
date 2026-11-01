# Call Halt Tech

[Français](README.fr.md)

An Android app against **phone scams** — the fake bank adviser, the fake technician, the fake fraud
department — written first for elderly people and the relatives who look after them.

**Status: in development. This version protects nobody yet.** It holds the detection logic, tested, and a
demonstration of the screen the app will show.

## The method

Never trust the caller's number: it is spoofed in seconds. Rely instead on what the scammer cannot avoid:
the victim must **do something irreversible on the phone during the call or just after it** — give a code
received by SMS, confirm an operation in the banking app, install a remote-control app. When that happens
during a call, the app interrupts with a STOP screen: *your bank will never ask you to do this during a
call — hang up, then call your bank yourself.*

It blocks nothing. It is a pause at the right moment.

## Promises, checked by the build

- **No internet permission**, ever: the CI reads the merged release manifest against an exact list
  (`tools/check-manifest-permissions.py`, with its negative control).
- No account, no server, no Google Play Services. Nothing leaves the phone.
- English, French, German, Italian, Spanish.

## Building

JDK 17 and the Android SDK; then `./gradlew :app:assembleDebug`. The full local gate, identical to the CI,
is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) § 8.

## Licence

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
