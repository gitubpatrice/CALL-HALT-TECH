# Relecture GPT — la suite d'accessibilité de Google installée depuis Play (audit M4) — 2026-11-07

> `gpt-5.6-luna`, effort `high` ; consigne : `consignes/2026-11-07-gpt-suite-play.md`. Envoyé : le diff de la branche
> (`origin/main..18f8bae`), puis `AccessibilitySensor.kt` et le manifeste ; 79 860 caractères. 20 245 jetons d'entrée,
> 8 099 de sortie : **0,014 $**. Après la relecture Claude (`2026-11-07-claude-suite-play.md`).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | `hasSigningCertificate` (API 28) appelée sans garde de version : `NoSuchMethodError` sur Android 8 | **faux** : `minSdk = 29` (`app/build.gradle.kts:76`) | rien |
| 2 | Switch Access de Google est un paquet à part, absent de la liste : l'activer fait un STOP | **réel**, et plus large que dit : sur l'émulateur Android 16, Switch Access est **préinstallé** et faisait déjà un STOP (mesuré avec la version de `main`) | **corrigé** : ajouté à la liste, aux `<queries>`, avec ses deux clés (`8d1c9c43…` sur Play, recoupée sur APKMirror 1.15 et 1.17 ; `eb9bab3f…`, la copie préinstallée, servie aussi par Play) ; mesuré : « fonction du téléphone : rien ». Les autres applis d'accessibilité de Google, absentes des images, sont écrites en limite dans D-033 |
| 3 | Le test sur appareil resterait vert pour une implémentation qui ne reconnaîtrait pas la clé de Play | **déjà écrit** : la copie Play n'est mesurée sur aucun téléphone (limite de D-033 et du KDoc du test) ; le test garde la transcription et l'appel | rien |

Le rapport conclut : aucun moyen, pour une appli installée par la victime, d'obtenir l'exemption.

## Le rapport, tel que rendu

### 1. Appel d’une API indisponible avant Android 9 — certitude moyenne à élevée

`PackageManager.hasSigningCertificate(..., CERT_INPUT_SHA256)` est une API ajoutée au niveau 28. L’appel n’est protégé par aucun test de version.

Scénario concret : sur un appareil Android 8 ou 9 où TalkBack est installé depuis Play et n’est pas `FLAG_SYSTEM`, l’utilisateur active TalkBack pendant un appel. Le callback appelle `isPhoneFeature`, puis `hasSigningCertificate`. La résolution de méthode peut provoquer un `NoSuchMethodError`, non intercepté par le `catch` sur `NameNotFoundException`. Le processus du capteur meurt au lieu d’afficher le STOP — et potentiellement les activations suivantes ne sont plus surveillées.

Le défaut est conditionnel à `minSdk < 28` et à la présence d’une suite non préinstallée sur ces versions. Si l’application exige déjà Android 9 ou ultérieur, ce constat disparaît ; si elle prend en charge Android 8/9, il est réel. Il faut au minimum un garde `SDK_INT >= 28`, avec une stratégie explicite pour les anciennes versions.

---

### 2. Une vraie fonction d’accessibilité Google reste traitée comme une application — certitude élevée

La liste ne contient que TalkBack et Voice Access côté Google. Elle omet notamment **Google Switch Access**, dont le paquet Play est généralement `com.google.android.accessibility.switchaccess`.

Scénario concret : sur un Samsung ou un appareil AOSP, la victime installe « Switch Access » depuis Play, puis l’active pendant l’appel — éventuellement parce qu’elle en a réellement besoin. Le paquet n’est pas dans `SYSTEM_ACCESSIBILITY_SUITES`; la condition `packageName in SYSTEM_ACCESSIBILITY_SUITES` échoue avant même l’examen du certificat. L’activation déclenche donc le STOP.

Ce n’est pas un contournement par un escroc, mais un faux positif sur une fonction d’accessibilité Google authentique. Si la politique est d’exempter les fonctions propres au téléphone, il faut inventorier explicitement tous les paquets concernés, ou maintenir une source/version de référence plus complète.

---

### 3. Le test instrumenté peut rester vert avec une implémentation qui ne reconnaît pas la clé Play — certitude élevée

`SuiteCertificatesOnDeviceTest` teste `PackageManager.isSignedWith`, c’est-à-dire la fonction de production elle-même, et ne dispose vraisemblablement que de la copie préinstallée de l’émulateur Android 16.

Une fausse implémentation qui n’accepterait que la clé préinstallée, par exemple :

```kotlin
if (!sha256.contentEquals(TALKBACK_PREINSTALLED_BYTES)) return false
return hasSigningCertificate(packageName, sha256, CERT_INPUT_SHA256)
```

resterait verte :

- l’émulateur porte la clé préinstallée ;
- `own` contiendrait exactement cette clé ;
- le certificat altéré serait refusé ;
- les tests JVM utilisent des lambdas simulées et n’appellent pas réellement `PackageManager.isSignedWith`.

Mais sur un Samsung où TalkBack vient de Play, signé par `TALKBACK_PLAY`, cette implémentation provoquerait encore le STOP. Le test ne valide donc pas réellement la branche « copie Play » contre le `PackageManager` Android ; il valide surtout la table et la logique abstraite. Il faudrait un test instrumenté avec un APK de test signé par la clé Play attendue, ou au minimum un test d’intégration qui vérifie séparément les deux cas.

---

Je ne vois pas de moyen normal pour une application installée par la victime d’obtenir l’exemption : le nom de paquet seul ne suffit pas, `sharedUserId` impose les signatures compatibles, la visibilité est traitée par les `<queries>`, et l’historique de signature reconnu par `hasSigningCertificate` ne devient exploitable qu’en possession d’une clé Google ou d’une lignée de signature valide.
