# Security — Call Halt Tech

English · 🇫🇷 [Version française](SECURITY.fr.md)

## Status

**In development (0.1.0). No version has been released, and this one protects nobody yet:** it holds the
detection logic and a demonstration of the STOP screen. The design, its decisions and what remains to be
measured are in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Threat model

Call Halt Tech stops the user when a sensitive action — a code received by SMS, a banking app's validation
request, a remote-control app — happens during a phone call or just after it. It never trusts the caller's
number, which is spoofed in seconds.

- **No network.** The app declares no `INTERNET` permission, and none may be: the CI checks the merged
  release manifest against an exact list, with a negative control (`tools/check-manifest-permissions.py`).
  The network attack surface is zero by construction.
- **Data at rest.** The detector's memory and the journal of alerts (when, why, what the user chose, the
  number that called) live in the app's private storage, protected by Android's file-based encryption.
  They are not encrypted a second time (docs/ARCHITECTURE.md, D-009). Android backup and device-to-device
  transfer are **disabled**.
- **Out of scope, by design.** The app does not hear the call, does not record it, and uses no
  accessibility service. An action made on another device escapes it.

## Reporting a vulnerability

Think you have found a vulnerability? **Please do not open a public issue.** Two channels, either one:

- **GitHub private reporting**: the repository's *Security* tab, then *Report a vulnerability*
  (https://github.com/gitubpatrice/CALL-HALT-TECH/security/advisories/new);
- **email**: **contact@files-tech.com**, subject `[SECURITY] Call Halt Tech — <summary>`.

Please allow 90 days before any public disclosure of an unfixed issue.
