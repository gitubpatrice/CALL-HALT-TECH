# Call Halt Tech — architecture

> Écrit le 2026-11-01, en posant le squelette. La fiche du projet (le pourquoi, les fonctions) est
> `PROJET.md` ; ce document dit **comment** le code est organisé, **quelles décisions** le tiennent et
> **ce qui reste à mesurer**. Le code renvoie ici par les numéros D-xxx et P-x.
>
> Nom retenu par Patrice le 2026-11-01 : **Call Halt Tech** (D-002). Le projet s'est d'abord appelé « Call Guard
> Tech » ; les rapports de `relectures/` portent encore ce nom.

## 1. Ce que fait le squelette aujourd'hui

**Rien ne protège encore**, et l'appli le dit sur son écran d'accueil. Le squelette contient :

- le **moteur de détection** complet et testé (module `:domain`), c'est-à-dire la méthode de la fiche,
  § 3 : appel en cours ou terminé depuis moins de N minutes, **et** action sensible → STOP ;
- sa **mémoire** (Room) et ses **réglages** (DataStore), testés, dont un test sur un vrai SQLite (S9) ;
- l'**écran STOP** en démonstration (fiche, § 4.9), aux couleurs du damier, en 5 langues ;
- la chaîne de build, detekt, lint, la CI et ses gardes (zéro réseau, parité des traductions), chacune
  avec son contrôle négatif.

Aucun capteur Android n'est branché, et aucune permission n'est demandée. Les capteurs sont la suite (§ 6).

## 2. Les modules

```
:domain   Kotlin/JVM pur. AUCUNE dépendance hors bibliothèque standard Kotlin.
          Ne peut atteindre ni Android, ni une horloge, ni un disque.
:app      Android : points d'entrée, capteurs, stockage, interface, injection (Hilt).
          Dépend de :domain.
```

**Pourquoi deux modules et pas un** (D-001) : la logique qui décide d'interrompre quelqu'un est la partie
la plus sensible de l'appli. Dans un module qui ne *peut pas* importer Android, elle ne peut pas lire
l'heure en douce, ni dépendre d'un état du téléphone qu'un test ne reproduirait pas. Chaque règle devient
un test JVM de quelques millisecondes. Pas plus de modules : un `:data` séparé (comme SMS Tech) n'apporterait
rien tant que l'appli tient dans un `:app` lisible.

## 3. Le flux d'un signal

```
 capteur Android (à venir)        :domain                                  :app
 ─────────────────────────        ───────                                  ────
 PHONE_STATE, SMS, notif.   ──►   Signal (horodaté par le capteur)
                                    │
                                    ▼
                              ProcessSignalUseCase ──► SettingsSource ◄── SettingsRepository (DataStore)
                                    │
                                    ▼
                              DetectorStore.apply { état →                RoomDetectorStore
                                  DetectionEngine.process(état, signal,   (une transaction : état + journal,
                                  réglages) }                              un signal à la fois)
                                    │
                                    ▼  décisions, APRÈS la validation
                              DecisionExecutor ──────────────────────►   (à venir) écran STOP, notification,
                                                                          SMS au proche, réveil
```

- **Un seul point d'entrée** : tout capteur appelle `ProcessSignalUseCase(signal)`. Aucun ne parle au
  moteur directement.
- **Le moteur est une fonction pure** : `(état, signal, réglages) → (nouvel état, journal, décisions)`.
  Il ne lit aucune horloge. L'heure est celle du signal (D-014).
- **L'état survit au processus** (D-004). Android tue l'appli entre le début de l'appel et l'arrivée du
  SMS, quelques minutes plus tard. L'état est donc relu en base à chaque signal, jamais gardé en mémoire
  seulement. Le test instrumenté `RoomDetectorStoreTest` prouve ce point sur un vrai SQLite.
- **Les décisions ne se perdent pas si le stockage tombe en cours de route** (disque plein, base fermée).
  Une écriture qui échoue *après* la lecture garde la décision déjà calculée. Un échec *avant* la lecture
  décide à partir du dernier état que ce processus connaît. Et tant que le stockage n'a pas repris cet
  état, c'est la mémoire qui fait foi, puis elle est réécrite. Trois tests instrumentés couvrent ces cas :
  `aClosedDatabaseStillYieldsTheDecisions`, `aWriteThatFailsAfterTheReadKeepsTheDecision`,
  `theStateStorageFailedToKeepIsTheTruthUntilWrittenBack`.
  ⚠️ **Limite** : un processus démarré *après* la panne ne connaît aucun appel, et le STOP est manqué. Un
  test l'écrit noir sur blanc (`…theKnownLimit`) ; ce qui la fermera, c'est le capteur téléphonique (P-7).
- **Un STOP non affiché ne passe pas pour affiché** : si l'exécuteur échoue à montrer l'écran, le cas
  d'usage le rend au moteur (`StopNotShown`). Le déclencheur est réarmé tout de suite, et le journal note
  « non affiché ». Sans cela, la relance de l'escroc dans la minute était absorbée sans aucun écran.

### Les règles du moteur (toutes testées, `DetectionEngineTest`)

| Règle | Fiche | Comportement |
|---|---|---|
| STOP | § 3, § 4.1 | toute action sensible pendant un appel ou dans la fenêtre qui suit (15 min, bornes comprises) → STOP + journal ; **un contact enregistré ne la fait pas taire** (numéro usurpé) |
| Rafale | — | une même action répétée pendant qu'un STOP est affiché sans réponse → un seul écran (60 s au plus) ; **une réponse réarme aussitôt** (« ça n'a pas marché, revalidez » est arrêté une 2ᵉ fois) ; **un échec d'affichage aussi** |
| Hors appel | § 4.4 | seule l'installation d'une appli de prise en main avertit (notification) ; le reste est ordinaire |
| Appel long | § 4.2 | inconnu (numéro masqué, ou absent des contacts) au-delà du seuil → un rappel par appel ; le moteur demande lui-même son réveil (`ScheduleTick`) |
| Proche | § 4.7 | prévenu au STOP et au choix « Continuer », seulement s'il est configuré |
| Protection coupée | — | rien n'est décidé, mais les appels restent suivis : rallumée en plein appel, elle le connaît |
| Fin manquée | — | un appel « en cours » depuis plus de 8 h est une fin d'appel perdue, plus un appel |

## 4. Les paquets

```
domain/src/main/java/com/filestech/callhalt/domain/
  model/       Call (canal, sens, interlocuteur, CallSession), Signal, Decision, JournalEntry, ProtectionSettings
  detection/   CallWindow (LA définition de « pendant ou juste après un appel »), DetectorState, DetectionEngine
  port/        SettingsSource, DetectorStore, DecisionExecutor — ce que :app doit fournir, avec leur contrat
  usecase/     ProcessSignalUseCase

app/src/main/java/com/filestech/callhalt/
  CallHaltApplication, MainActivity
  di/          ApplicationScope, StorageModule (Room, DataStore), DetectionModule (ports → implémentations)
  data/settings/  SettingsRepository + SettingsMapping (valeur hors bornes → défaut protecteur)
  data/store/     CallHaltDatabase, entités, DetectorDao, DetectorMapping (pur), RoomDetectorStore
  ui/          AppRoot, Routes, home/, stop/, theme/ (palettes du portefeuille, Damier)
  (à venir)    platform/<capteur>/ — un sous-paquet par source : telephony, sms, notifications, usage,
               packages, accessibility ; platform/stop/ — l'Activity STOP et l'exécution des décisions
```

## 5. Décisions

| N° | Décision | Pourquoi |
|---|---|---|
| D-001 | Deux modules : `:domain` pur, `:app` Android | § 2 |
| D-002 | **Nom : Call Halt Tech** ; `applicationId` et paquet `com.filestech.callhalt` (Patrice, 2026-11-01) | « Halt ! » se reconnaît dans les 5 langues (*Halt!*, *Halte !*, *Alt!*, *¡Alto!*, le mot des panneaux stop espagnols) : c'est l'écran STOP de l'appli. Rien trouvé sous ce nom (recherche web). Le nom de départ, « Call Guard », était pris : SmarTone (`com.smartone.callguard`, +100 000), Xfinity Call Guard (Comcast), Cybora CallGUARD, callguard.tech, Eckoh CallGuard, marque US CALLGUARD n° 6011194. Une trentaine d'autres noms vérifiés et écartés : liste dans la mémoire du projet. L'`applicationId` est figé au premier APK publié. **INPI : rien ; nom de domaine libre (ICANN)** — vérifiés par Patrice le 2026-11-01. Reste, facultatif : TMview (marques de l'Union européenne). |
| D-003 | Chaîne de build de Read Files Tech 3 : Gradle 9.8.2, AGP 9.4.1, Kotlin 2.4.10 (`strictly`), KSP 2.3.12, compileSdk 37, minSdk 29, JUnit 6.1.3, detekt 1.23.8 bloquant sans baseline | aucune version choisie ici, toutes lues dans une appli voisine verte |
| D-004 | État du détecteur **et** journal dans Room, une transaction par signal ; réglages dans DataStore ; migrations additives seulement, jamais de `fallbackToDestructiveMigration` | l'état doit survivre au processus ; le journal et l'état ne doivent jamais se contredire |
| D-005 | Le cœur ne prend **aucun rôle exclusif** (filtrage d'appels, téléphone, SMS) | `ROLE_CALL_SCREENING` n'a qu'un détenteur (`roles.xml` : `exclusive="true"`) : le prendre retirerait Saracroche ou SpamBlocker. Le filtrage (fiche, § 4.10-12) reste une option, pour qui n'a pas d'autre bloqueur. Il ne voit d'ailleurs ni les numéros masqués, ni les contacts sans `READ_CONTACTS`. |
| D-006 | L'écran STOP est une **Activity**, jamais une fenêtre superposée | une appli bancaire masque les superpositions (`setHideOverlayWindows`, permission `normal`). Une Activity lancée en arrière-plan reste permise à qui détient `SYSTEM_ALERT_WINDOW`, sans condition, jusqu'à Android 17 (code AOSP `android17-release`). |
| D-007 | Une permission arrive **avec** son composant, dans le même commit que sa ligne dans `tools/check-manifest-permissions.py` et dans PRIVACY | la liste, la politique et l'APK décrivent la même appli |
| D-008 | **targetSdk 36, pas 37** | Android 17 retarde de **3 heures** les SMS de code pour toute appli ciblant l'API 37 ou plus, sauf l'appli SMS par défaut (developer.android.com, behavior-changes-17). Monter le targetSdk tuerait la fonction 4.3. À revoir si la règle change. |
| D-009 | Pas de SQLCipher | le chiffrement de fichiers d'Android couvre déjà la base ; un Keystore muet un matin (Agenda Tech, Android 8-11) ne doit pas coûter la protection |
| D-010 | **Pas de service d'accessibilité** | les applis bancaires les détectent et refusent de fonctionner ; les antivirus les signalent ; Android 16 en bloque l'octroi pendant un appel avec un inconnu |
| D-011 | « Appeler ma banque » = `ACTION_DIAL` (numéro composé, l'utilisateur appuie), **après la fin de l'appel** | pas de permission `CALL_PHONE` ; appeler pendant l'appel mettrait l'escroc en attente au lieu de le couper (relecture GPT, A11) |
| D-012 | 5 langues dès le squelette, contrôle de parité de RFT repris sans changement de logique ; vouvoiement en IT et ES | le contrôle et son contrôle négatif (51 cas) supposent 5 langues ; 13 textes seulement à ce stade. **Les phrases de sécurité DE/IT/ES sont à faire relire par un natif** : une consigne mal traduite ferait faire l'inverse. |
| D-013 | Thème du portefeuille (palettes de RFT 3) ; bleu et rouge du **damier** (`#0B60C5`, `#CA331F`) ; STOP sur le rouge du damier (blanc dessus : 5,25:1) ; icône = damier + bouclier | demande de Patrice, 2026-11-01 : qu'on reconnaisse une appli Files Tech |
| D-014 | Le moteur ne lit aucune horloge ; un appel suivi plus de 8 h est une fin manquée | décisions reproductibles en test ; une fin perdue ne garde pas tous les SMS suivants « pendant un appel » |

## 6. Les capteurs à brancher — ce qu'Android permet (vérifié le 2026-11-01)

Sources primaires (developer.android.com, AOSP par branche de release) relues par un agent de
recherche ; statut entre parenthèses. « Restreint » = bloqué par les **paramètres restreints** pour une
appli installée par APK, jusqu'à « Autoriser les paramètres restreints » dans les infos de l'appli.

| Signal | Moyen | Permission | Restreint | Statut |
|---|---|---|---|---|
| Appel téléphonique : début, fin, numéro | récepteur **déclaré au manifeste** `PHONE_STATE` (exception aux broadcasts implicites, sans condition de targetSdk) | `READ_PHONE_STATE` ; numéro : + `READ_CALL_LOG` (broadcast reçu deux fois) | non | confirmé |
| Appel VoIP (WhatsApp, Signal…) | `AudioManager.getMode()` global sans permission (`MODE_IN_COMMUNICATION`), `addOnModeChangedListener` (API 31) ; ou notification `CATEGORY_CALL` | aucune / accès aux notifications | — | code confirmé, **comportement des applis à mesurer (P-4)** |
| SMS de code | récepteur `SMS_RECEIVED` | `RECEIVE_SMS` | oui | confirmé, **sous réserve de D-008** |
| SMS de code, repli Android 15+ | la notification de l'appli SMS par défaut arrive **masquée** (« Sensitive notification content hidden ») : le masquage lui-même signale un code | accès aux notifications | oui | probable, à mesurer |
| Demande de validation bancaire | `NotificationListenerService` — sur AOSP, le masquage des codes ne vise que l'appli SMS par défaut ; assistants Samsung et Pixel inconnus | accès aux notifications | oui | **à mesurer (P-2)** |
| Appli bancaire ou de prise en main au premier plan | `UsageStatsManager.queryEvents` (**aucun rappel : interrogation**, pendant l'appel seulement) | `PACKAGE_USAGE_STATS` | oui | confirmé ; tenir le processus : P-5 |
| Installation d'une appli | `getChangedPackages` (au réveil) + `PACKAGE_ADDED` dynamique ; **filtrés par la visibilité des paquets** | `QUERY_ALL_PACKAGES` (`normal`) | non | confirmé |
| Renvoi d'appel inconditionnel activé | `CallForwardingIndicatorListener` / `LISTEN_CALL_FORWARDING_INDICATOR` (pas les renvois conditionnels) | `READ_PHONE_STATE` | non | confirmé |
| Service d'accessibilité activé | `AccessibilityManager.getEnabledAccessibilityServiceList` | aucune | — | confirmé |
| Afficher STOP | Activity (D-006) | `SYSTEM_ALERT_WINDOW` | oui | confirmé (doc et code), **à mesurer (P-1)** |
| Raccrocher | `TelecomManager.endCall` (déprécié, fonctionne) — ni appel d'urgence, ni VoIP | `ANSWER_PHONE_CALLS` | non | confirmé (code) |
| Prévenir le proche | `SmsManager` | `SEND_SMS` | oui | décision de Patrice (fiche, § 7) |
| Survivre à l'oubli | l'**hibernation** épargne une appli dont l'écouteur de notifications est actif (AOSP `HibernationPolicy`) ; sinon `IntentCompat.createManageUnusedAppRestrictionsIntent` | — | — | confirmé |

## 7. Points ouverts (à mesurer, pas à deviner)

- **P-1** — L'Activity STOP s'affiche-t-elle par-dessus l'appli bancaire, déclenchée depuis un récepteur,
  écran verrouillé compris ? Sur le S9 (Android 10) **et** un Android 14+ (le comportement change à 12 et à 15).
- **P-2** — Les demandes de validation des applis bancaires françaises sont-elles lisibles ? Le S9 n'est
  pas rooté (mesuré le 2026-11-01), mais son chargeur d'amorçage est déverrouillé, ce que certaines
  banques refusent : essayer d'abord sur le S9. Si la banque refuse, le S24 FE, **à la décision de Patrice**.
- **P-3** — Paramètres restreints : parcours guidé pas à pas ; une installation par F-Droid (installateur
  par session) y échappe-t-elle ? Non vérifié.
- **P-4** — VoIP : `getMode()` voit-il WhatsApp, Signal, Telegram en appel ?
- **P-5** — Pendant l'appel, qu'est-ce qui garde le processus en vie pour interroger UsageStats ? Un
  service de premier plan lancé depuis l'arrière-plan est restreint (targetSdk ≥ 35 : `SYSTEM_ALERT_WINDOW`
  **et** une superposition visible). Piste : l'écouteur de notifications, lié par le système, tient le processus.
- **P-6** — Un code reçu pendant la sonnerie ou juste avant l'appel (l'escroc déclenche l'opération puis
  appelle) n'est pas « pendant l'appel » pour le moteur. Faut-il une fenêtre *avant* ?
- **P-7** — L'état réel de l'appel, interrogé en direct par le capteur téléphonique, contre l'état mémorisé.
  Trois défauts qu'il fermera, aujourd'hui écrits et non corrigés :
  - le processus démarré après une panne de stockage ne voit pas l'appel (relecture du 2026-11-01, D2) ;
  - une fin d'appel manquée n'est bornée que par le plafond de 8 h ;
  - un nouvel appel arrivé pendant cet appel « fantôme » est pris pour un double appel. Il hérite alors du
    début et de l'interlocuteur de l'ancien, et le rappel d'appel long à l'inconnu se perd (D5). Le STOP,
    lui, n'est pas touché.

  Piste : le journal d'appels (`READ_CALL_LOG`) et l'état du téléphone au moment du signal.
- **P-8** — Réveil du rappel « appel long » : `AlarmManager` inexact ou WorkManager. Et **tout changement
  de réglage envoie un `Tick`** (protection rallumée en plein appel, seuil abaissé) : sinon le rappel ne
  part jamais pour l'appel en cours (D6).
- **P-10** — Une panne durable du stockage (migration manquante, base corrompue) est aujourd'hui
  silencieuse. Le magasin décide en mémoire, le journal ne s'écrit plus, et aucun journal système n'existe
  en release. L'écran « état de la protection » (fiche, § 9.2, idée 5) doit l'afficher (D3).
- **P-11** — Élaguer le journal (durée de conservation) ; dédoublonner l'avertissement « appli de prise en
  main installée » hors appel, qu'une diffusion répétée ferait écrire plusieurs fois (D8).
- **P-12** — **Contrat des capteurs : une file unique, dans l'ordre de réception.** Le `Mutex` du magasin
  sérialise les signaux, mais pas dans l'ordre de leur heure. Une action estampillée une milliseconde
  avant le début d'un nouvel appel, et traitée après lui, perd la fenêtre de l'appel précédent (GPT, 4).
  Les capteurs passeront tous par un seul consommateur, premier arrivé premier traité.
- **P-13** — **Horloge monotone.** L'heure civile peut reculer, par la main de l'utilisateur ou par une
  correction du réseau. Un appel en cours « disparaît » alors (GPT, 5), et un nouvel appel estampillé avant
  la fin du précédent est pris pour une répétition (règle de GPT, 6). Les capteurs fourniront aussi un temps
  monotone depuis le démarrage (`elapsedRealtime`) ; un redémarrage termine de toute façon les appels.
- **P-14** — **Identifiant de génération du STOP**, porté par `ShowStop`, `StopAnswered` et `StopNotShown`.
  Aujourd'hui, la réponse ou l'échec tardif d'un ancien STOP efface le STOP plus récent du même
  déclencheur (GPT, 8). L'erreur va du côté protecteur : un STOP de plus, jamais un de moins.
- **P-9** — Pont avec SMS Tech (clés de signature différentes) : permission `signature|knownSigner` avec
  `knownCerts` (API 31+), et contrôle `PackageManager.hasSigningCertificate` en dessous. Seule l'appli SMS
  par défaut échappe au délai de D-008 : c'est aussi un repli pour les codes.

## 8. Travailler dans ce dépôt

**Gate local = CI** (une seule commande, la même) :

```
python3 tools/check-translations-negative-control.py && python3 tools/check-translations.py
./gradlew :app:assembleDebug :domain:test :app:testDebugUnitTest :app:lintDebug detekt
python3 tools/check-test-results.py          # « BUILD SUCCESSFUL » ne dit pas combien de tests ont tourné
./gradlew :app:assembleRelease :app:processReleaseMainManifest
python3 tools/check-manifest-permissions-negative-control.py && python3 tools/check-manifest-permissions.py
```

Tests instrumentés : **jamais `connectedDebugAndroidTest` avec le S24 FE branché**. La tâche vise tous
les appareils et efface les données de l'appli testée. Toujours épingler l'appareil par son numéro de
série (`adb devices` le donne) : `ANDROID_SERIAL=<numéro> ./gradlew :app:connectedDebugAndroidTest`.
À la fin du passage, Gradle **désinstalle** l'appli testée : la réinstaller pour la voir sur le téléphone.

**Ajouter une règle** : dans `DetectionEngine`, en interrogeant `CallWindow` (jamais une seconde définition
de « pendant l'appel »), avec ses tests dans `DetectionEngineTest`, y compris le cas où elle doit se taire.

**Ajouter un capteur** : un sous-paquet `platform/<source>/`. Il traduit un événement Android en `Signal`
horodaté et appelle `ProcessSignalUseCase`. Sa permission va dans le manifeste, dans la garde et dans
PRIVACY, **dans le même commit** (D-007).

**Ajouter un texte** : en anglais dans `values/strings.xml` et dans les quatre autres langues ; le contrôle
de parité refuse un texte manquant.

**Changer le schéma Room** : tant qu'aucun APK n'est publié, la version 1 peut changer. Ensuite, migration
additive, schéma exporté committé, test de migration, et `room-testing` à ajouter au catalogue avec la
contrainte `kotlinx-serialization` d'App Manager Tech (voir l'en-tête de `gradle/libs.versions.toml`).
