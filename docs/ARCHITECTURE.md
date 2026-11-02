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
- l'**écran STOP** en démonstration (fiche, § 4.9), aux couleurs du damier, en 5 langues ; ses textes
  suivent le déclencheur et l'état de l'appel (D-017) ;
- la chaîne de build, detekt, lint, la CI et ses gardes (zéro réseau, parité des traductions), chacune
  avec son contrôle négatif.

**Premier capteur branché le 2026-11-02 : l'état de l'appel** (`platform/telephony/`, permission
`READ_PHONE_STATE`, qui n'est pas encore demandée à l'écran). Mesuré sur l'émulateur Android 16 avec le vrai
service téléphonique : appel entrant décroché, appel sortant, processus tué entre deux appels. Les autres
capteurs sont la suite (§ 6).

**L'écran STOP réel, le même jour** (`platform/stop/`, permissions `SYSTEM_ALERT_WINDOW` et
`ANSWER_PHONE_CALLS`) : **mesure P-1 passée sur le S24 FE** — pendant un vrai appel, il passe devant l'appli
bancaire, et « Raccrocher » coupe l'appel. Faute de capteur d'action sensible, seul un signal injecté par adb
(version de test) le déclenche : la version publiée ne montre encore aucun STOP.

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
 capteur Android                 :domain                                  :app
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

- **Un seul point d'entrée, par une seule file** : tout signal finit dans `ProcessSignalUseCase(signal)`,
  mais aucun capteur ne l'appelle en direct. Chacun dépose ce qu'il observe dans `platform/SensorQueue`
  (P-12), traité un élément à la fois, dans l'ordre d'arrivée. Un récepteur garde sa diffusion (`goAsync()`)
  jusqu'à ce que son tour soit passé, 8 s au plus ; un élément qui échoue, ou s'annule de lui-même,
  n'arrête pas les suivants.
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
  test l'écrit noir sur blanc (`…theKnownLimit`). Ce qui la fermera : une interrogation en direct de l'état
  de l'appel (P-7). Le capteur téléphonique branché le 2026-11-02 ne la fait pas encore.
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
  ui/          AppRoot, Routes, home/, stop/ (StopScreen, StopText), theme/ (palettes du portefeuille, Damier)
  platform/    SensorQueue (la file unique, P-12), AndroidDecisionExecutor (le STOP d'abord ; le reste au
               fil des étapes) ; telephony/ — CallStateReceiver (manifeste, exporté : diffusion protégée),
               CallStateSensor, PhoneState (+ callSignal, pur), PhoneStateMemory (DataStore à part), CallEvents
               (fin d'appel, pour l'écran STOP) ; stop/ — StopActivity, StopRequest, StopDisplay (l'écran
               confirme qu'il est devant), StopLauncher, CallControl (raccrocher), StopAnswers (réponse → moteur)
  src/debug/   platform/debug/DebugSignalReceiver — action sensible injectée par adb (DUMP), version de test
               seulement, pour mesurer l'écran STOP avant les capteurs
  (à venir)    platform/<capteur>/ pour sms, notifications, usage, packages, accessibility
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
| D-012 | 5 langues dès le squelette, contrôle de parité de RFT repris sans changement de logique ; vouvoiement en IT et ES | le contrôle et son contrôle négatif (51 cas) supposent 5 langues ; 26 textes à ce stade (13 jusqu'à D-017). **Les phrases de sécurité DE/IT/ES sont à faire relire par un natif** : une consigne mal traduite ferait faire l'inverse. |
| D-013 | Thème du portefeuille (palettes de RFT 3) ; bleu et rouge du **damier** (`#0B60C5`, `#CA331F`) ; STOP sur le rouge du damier (blanc dessus : 5,25:1) ; icône = damier + bouclier | demande de Patrice, 2026-11-01 : qu'on reconnaisse une appli Files Tech |
| D-014 | Le moteur ne lit aucune horloge ; un appel suivi plus de 8 h est une fin manquée | décisions reproductibles en test ; une fin perdue ne garde pas tous les SMS suivants « pendant un appel » |
| D-015 | **SMS au proche : oui** (Patrice, 2026-11-01) | Fonction 7 de la fiche : le proche est prévenu quand un STOP s'affiche, quand « Continuer » est choisi malgré l'alerte, quand la protection est coupée. Le moteur décide déjà quand (`NotifyGuardian`) ; `SEND_SMS` arrivera avec le composant qui envoie (D-007), dans PRIVACY et la garde CI en même temps. C'est une permission soumise aux paramètres restreints (§ 6). Désactivé tant qu'aucun proche n'est configuré. |
| D-016 | **Publication sur F-Droid : oui — mais rien n'est publié tant que l'appli n'est pas fiable, solide et sécurisée au mieux, avec un score d'au moins 95 sur 100** (Patrice, 2026-11-01) | **Condition de publication** : la grille qui donne ce score se fixe AVANT le premier audit de publication, sur trois éléments. **(1)** Aucun constat critique ou élevé ouvert : audit en 3 axes, audit de sécurité avec panel adverse, faux positifs des règles de détection, motifs de défaut. **(2)** Mesures P-1 à P-3 faites sur de vrais téléphones. **(3)** Une relecture externe du code qui décide. Un score sous 95, ou un critère non tenu : pas de publication. Comme Agenda Tech et SMS Tech. **Build reproductible** : déjà préparé (pas de foojay, bloc de dépendances de Play exclu). **Métadonnées fastlane** dans les 5 langues, changelog ≤ 500 caractères. **APK servi = notre APK**, signé de notre clé (`Binaries` + `AllowedAPKSigningKeys`) ; mises à jour par tag. **Toute montée de la chaîne de build** (AGP, Kotlin, Gradle) se vérifie côté F-Droid : elle se fait avec tout le portefeuille (Dependabot n° 3, AGP 9.5.0, fermée le 2026-11-01 pour cette raison). À mesurer : une installation par le client F-Droid échappe-t-elle aux paramètres restreints (P-3) ? |
| D-017 | **Les mots de l'écran STOP** (Patrice, 2026-11-01), par ordre d'importance : où l'on est (« au téléphone » / « l'appel vient de se terminer »), ce qui vient d'arriver (un texte par déclencheur), « **si on vous dit d'ignorer cet écran, c'est une arnaque** », la règle (« ne vous appelleront JAMAIS pour vous demander un code ou de valider une opération »), « le numéro ou le nom affiché ne prouve rien », que faire. Choisis par `ui/stop/StopText.kt` selon le déclencheur, l'appel (en cours ou fini, téléphone ou VoIP) et la permission de raccrocher, vérifiés par `StopTextTest` | Les escrocs ne jouent pas que la banque : police, impôts, URSSAF, mutuelle, assurance, crédit (Patrice). La règle **nomme qui ne demande jamais** au lieu de dire « personne » : le service client d'un opérateur peut faire relire un code qu'il vient d'envoyer, un proche peut aider par une appli de prise en main. Elle dit « **vous appelleront** » et non « vous demanderont au téléphone » : qui appelle lui-même sa vraie banque peut avoir à valider ce qu'il a demandé (relecture Claude, H2). Une règle qu'on sait fausse rendrait tout l'écran douteux. **Pas de question** « connaissez-vous votre interlocuteur ? » : la victime croit le connaître (numéro usurpé, son nom, ses opérations) et répondrait oui ; l'écran affirme. L'escroc est encore en ligne quand l'écran s'affiche (« c'est normal, appuyez sur Continuer ») : « ignorer cet écran » le désarme d'avance, et reste catégorique — si c'était la vraie banque, l'erreur ne coûte qu'un rappel. **Ordre mesuré sur le S9** (police normale puis 1,3) : ce qui suit la règle sortait de l'écran ; on ne fait pas défiler sous pression. **Prise en main à distance** : raccrocher ne coupe pas la session, le conseil est d'éteindre le téléphone. **Boutons** : « Raccrocher » seulement si l'appli peut le faire (appel téléphonique en cours, permission accordée : `endCall` ne raccroche pas la VoIP) ; pendant l'appel, « Raccrocher et appeler ma banque » (D-011 : raccrocher AVANT de composer) ; après, « Appeler ma banque » en premier ; **pendant un appel que l'appli ne peut pas couper, aucun bouton d'appel** (le conseil dit de raccrocher). Si « Raccrocher » échoue, l'écran reste, sans ses boutons d'appel. Ils restent en bas, visibles ; les textes défilent au-dessus. **L'écran quitté sans réponse** (accueil, autre appli ; pas la mise en veille) se ferme et libère ses déclencheurs (`StopChoice.LEFT`, journalisé, sans SMS au proche) : une nouvelle tentative montre un nouvel écran. |
| D-018 | **La mesure décide de la suite** (Patrice, 2026-11-01, sur recommandation de Claude) : si l'écran STOP ne s'affiche pas par-dessus l'appli bancaire (P-1), ou si ses demandes de validation sont illisibles (P-2), **le projet s'arrête** | La concurrence n'est pas la raison : en France, rien ne fait la même chose en français, sans Google ni Internet (fiche, § 9.1). Les vrais risques sont techniques (P-1 à P-3 ; Android resserre ces accès à chaque version : codes masqués dans les notifications depuis Android 15, SMS de code retardés par Android 17) et stratégiques (Google ou Samsung, s'ils couvrent un jour la France, la rendraient inutile au plus grand nombre). La journée déjà investie ne pèse pas : seuls les vrais téléphones tranchent. **P-1 passé le 2026-11-02** (S24 FE, Android 16) ; reste P-2. |

## 6. Les capteurs à brancher — ce qu'Android permet (vérifié le 2026-11-01)

Sources primaires (developer.android.com, AOSP par branche de release) relues par un agent de
recherche ; statut entre parenthèses. « Restreint » = bloqué par les **paramètres restreints** pour une
appli installée par APK, jusqu'à « Autoriser les paramètres restreints » dans les infos de l'appli.

| Signal | Moyen | Permission | Restreint | Statut |
|---|---|---|---|---|
| Appel téléphonique : début, fin, numéro | récepteur **déclaré au manifeste** `PHONE_STATE` (exception aux broadcasts implicites, sans condition de targetSdk) | `READ_PHONE_STATE` ; numéro : + `READ_CALL_LOG` (broadcast reçu deux fois) | non | **branché le 2026-11-02** (début et fin, sans le numéro) ; **mesuré sur émulateur, Android 11, 16 et 17** (vrai service téléphonique, `adb emu gsm`), d'abord non exporté : le récepteur reçoit la diffusion du système ; processus tué (Android 16) : réveillé, mémoire intacte ; **appli en cache (Android 17) : livrée en 0,15 à 0,6 s**, aucun report. Une appli « arrêtée » (`force-stop`) ne reçoit plus rien jusqu'à sa réouverture : comportement d'Android, à dire dans l'aide. Juste après le démarrage, rien tant que la SIM n'est pas prête (normal). **Exporté** (pour qu'un téléphone qui l'émettrait depuis son processus téléphonique soit entendu) : PHONE_STATE est une diffusion protégée, **mesuré sur le S9 (Samsung, Android 10) et l'émulateur Android 17** : même le shell d'adb est refusé, que la diffusion vise le récepteur ou non. Toute autre action est ignorée. Pas de permission exigée de l'expéditeur : elle risquerait de couper la livraison par le système (relecture de sécurité). **Vrai réseau, mesuré sur le S24 FE (Samsung, Android 16, SFR) le 2026-11-02** : appel sortant, appel reçu décroché (entrant), appel reçu refusé (aucun appel) ; traités 0,05 à 0,07 s après réception ; la base garde l'appel entrant, sans numéro, et l'appel refusé n'y change rien. Le premier appel après l'installation a un sens inconnu (aucun état connu avant lui). Le S24 envoie une seule diffusion par changement d'état, **sans indice de SIM** (le S9 n'a pas de SIM). **Double SIM, à mesurer** (relecture du 2026-11-02, M2) : si un téléphone envoie un IDLE par SIM, celui d'une SIM au repos fermerait l'appel en cours sur l'autre ; la trace de débogage note la SIM de chaque diffusion. Parade possible, à ne poser qu'après mesure : interroger l'état en direct à l'IDLE (P-7), sans risquer un appel qui ne se ferme jamais |
| Appel VoIP (WhatsApp, Signal…) | `AudioManager.getMode()` global sans permission (`MODE_IN_COMMUNICATION`), `addOnModeChangedListener` (API 31) ; ou notification `CATEGORY_CALL` | aucune / accès aux notifications | — | code confirmé, **comportement des applis à mesurer (P-4)** |
| SMS de code | récepteur `SMS_RECEIVED` | `RECEIVE_SMS` | oui | confirmé, **sous réserve de D-008** |
| SMS de code, repli Android 15+ | la notification de l'appli SMS par défaut arrive **masquée** (« Sensitive notification content hidden ») : le masquage lui-même signale un code | accès aux notifications | oui | probable, à mesurer |
| Demande de validation bancaire | `NotificationListenerService` — sur AOSP, le masquage des codes ne vise que l'appli SMS par défaut ; assistants Samsung et Pixel inconnus | accès aux notifications | oui | **à mesurer (P-2)** |
| Appli bancaire ou de prise en main au premier plan | `UsageStatsManager.queryEvents` (**aucun rappel : interrogation**, pendant l'appel seulement) | `PACKAGE_USAGE_STATS` | oui | confirmé ; tenir le processus : P-5 |
| Installation d'une appli | `getChangedPackages` (au réveil) + `PACKAGE_ADDED` dynamique ; **filtrés par la visibilité des paquets** | `QUERY_ALL_PACKAGES` (`normal`) | non | confirmé |
| Renvoi d'appel inconditionnel activé | `CallForwardingIndicatorListener` / `LISTEN_CALL_FORWARDING_INDICATOR` (pas les renvois conditionnels) | `READ_PHONE_STATE` | non | confirmé |
| Service d'accessibilité activé | `AccessibilityManager.getEnabledAccessibilityServiceList` | aucune | — | confirmé |
| Afficher STOP | Activity (D-006), lancée de l'arrière-plan ; l'écran confirme qu'il est au premier plan (`StopDisplay`), sinon « non affiché » | `SYSTEM_ALERT_WINDOW` | oui | **branché et MESURÉ le 2026-11-02 sur le S24 FE (Samsung, Android 16)** : pendant un vrai appel, l'appli Crédit Coopératif au premier plan, le STOP passe devant en ~1 s (P-1) ; capture d'écran par adb : noire (`FLAG_SECURE`) |
| Raccrocher | `TelecomManager.endCall` (déprécié, fonctionne) — ni appel d'urgence, ni VoIP | `ANSWER_PHONE_CALLS` | non | **mesuré le 2026-11-02 sur le S24 FE** : « Raccrocher » coupe l'appel 0,56 s après l'appui (targetSdk 36) |
| Prévenir le proche | `SmsManager` | `SEND_SMS` | oui | décision de Patrice (fiche, § 7) |
| Survivre à l'oubli | l'**hibernation** épargne une appli dont l'écouteur de notifications est actif (AOSP `HibernationPolicy`) ; sinon `IntentCompat.createManageUnusedAppRestrictionsIntent` | — | — | confirmé |

## 7. Points ouverts (à mesurer, pas à deviner)

- **P-1** — L'Activity STOP s'affiche-t-elle par-dessus l'appli bancaire, déclenchée depuis un récepteur,
  écran verrouillé compris ? Sur le S9 (Android 10) **et** un Android 14+ (le comportement change à 12 et à 15).
  ✅ **Oui sur Android 16 (S24 FE), le 2026-11-02** : vrai appel, appli Crédit Coopératif au premier plan,
  signal injecté par adb (version de test, `DebugSignalReceiver`) dans la même file et le même moteur qu'un
  capteur ; STOP au premier plan en ~1 s, confirmé par l'écran lui-même ; « Raccrocher » a coupé l'appel ;
  journal : STOP puis réponse. ✅ **Et sur Android 11 (émulateur API 30, le plus proche du plancher ; le S9 n'a
  pas de SIM)** : devant l'appli au premier plan ; lancé écran éteint, présent au réveil ; **écran verrouillé
  par code PIN : le STOP passe devant le verrou**, « Raccrocher » coupe l'appel sans déverrouiller, puis le
  verrou revient. Reste Android 10 lui-même, sur un téléphone avec SIM.
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
  en release. L'écran « état de la protection » (fiche, § 9.2, idée 5) doit l'afficher (D3). Il doit aussi
  dire les **arrêts silencieux d'Android** (relecture du 2026-11-02, L4) : appli installée mais jamais ouverte
  (état « arrêté » : aucune diffusion, même avec la permission accordée), permissions retirées après des mois
  sans usage (Android 11+), hibernation (Android 12+) ; `PackageManagerCompat.getUnusedAppRestrictionsStatus`
  dit les deux derniers.
- **P-11** — Élaguer le journal (durée de conservation) ; dédoublonner l'avertissement « appli de prise en
  main installée » hors appel, qu'une diffusion répétée ferait écrire plusieurs fois (D8).
- ~~**P-12** — **Contrat des capteurs : une file unique, dans l'ordre de réception.**~~ **Fait le 2026-11-02** :
  `platform/SensorQueue`, un seul consommateur, premier arrivé premier traité (`SensorQueueTest`). Le
  `Mutex` du magasin sérialisait les signaux sans les garder dans l'ordre : une action estampillée une
  milliseconde avant le début d'un nouvel appel, et traitée après lui, perdait la fenêtre de l'appel
  précédent (GPT, 4).
- **P-13** — **Horloge monotone.** L'heure civile peut reculer, par la main de l'utilisateur ou par une
  correction du réseau. Un appel en cours « disparaît » alors (GPT, 5), et un nouvel appel estampillé avant
  la fin du précédent est pris pour une répétition (règle de GPT, 6). Les capteurs fourniront aussi un temps
  monotone depuis le démarrage (`elapsedRealtime`) ; un redémarrage termine de toute façon les appels.
  **Atteignable depuis le capteur téléphonique** (relecture du 2026-11-02, M1) : l'horloge recule entre deux
  appels, le début du second, estampillé avant la fin du premier, est absorbé. Le STOP n'est manqué qu'au-delà
  des 15 premières minutes du second appel. Écrit dans `CallStateSensorTest` (« the known limit »). Avec le
  capteur, la règle de GPT 6 ne protège plus que d'un capteur qui violerait le contrat (heure de réception,
  ordre d'arrivée) : à revoir avec ce point, **avant toute publication**.
- **P-14** — **Identifiant de génération du STOP**, porté par `ShowStop`, `StopAnswered` et `StopNotShown`.
  Aujourd'hui, la réponse ou l'échec tardif d'un ancien STOP efface le STOP plus récent du même
  déclencheur (GPT, 8). L'erreur va du côté protecteur : un STOP de plus, jamais un de moins.
- **P-9** — Pont avec SMS Tech (clés de signature différentes) : permission `signature|knownSigner` avec
  `knownCerts` (API 31+), et contrôle `PackageManager.hasSigningCertificate` en dessous. Seule l'appli SMS
  par défaut échappe au délai de D-008 : c'est aussi un repli pour les codes.
- **P-15** — **Prise en main à distance ouverte pendant le STOP** (relecture Claude du 2026-11-01, H1) :
  l'escroc voit l'écran (capture d'écran) et peut appuyer lui-même sur « Continuer » une fois les 10 s
  passées. **Fait le 2026-11-02** : `FLAG_SECURE` (capture par adb sur le S24 : noire) ; à partir d'Android 14,
  écran réservé aux outils d'accessibilité déclarés (`setAccessibilityDataSensitive`) : un service de prise en
  main qui n'en est pas un ne peut ni le lire ni appuyer. **Reste à mesurer avec AnyDesk** sur le S24 ; et
  avant Android 14, rien n'empêche un service d'accessibilité d'appuyer sur « Continuer ».
- **P-16** — **Appel sortant vers le numéro de banque enregistré** (même relecture, H2) : l'utilisateur qui
  appelle lui-même sa vraie banque et valide une opération qu'il a demandée reçoit un STOP. Le texte reste
  vrai (« ne vous appelleront jamais », D-017), mais l'interruption est inutile. Piste : pas de STOP, ou un
  texte propre, pour un appel SORTANT composé vers ce numéro (un appel sortant ne s'usurpe pas ; un renvoi
  d'appel ou un numéro enregistré par l'escroc, si). À peser avant d'écrire la règle. ⚠️ Le capteur ne
  **déduit** le sens qu'à partir de l'état précédent : une sonnerie perdue fait lire un appel entrant comme
  sortant (relecture du 2026-11-02, L3). Le sens ne peut pas décider seul de taire un STOP.
- **P-17** — **Écran STOP reconstruit après la mort du processus** : il reprend sa session sauvegardée, mais
  ne peut pas distinguer l'appel qui l'a déclenché d'un appel plus récent (celui de la vraie banque) s'il n'a
  pas vu la fin du premier. Piste : un identifiant d'appel porté par la décision (même famille que P-14).
- **P-18** — **Fin d'un appel VoIP** : seule la téléphonie signale une fin à l'écran (`CallEvents`) ; sur un
  appel WhatsApp, l'écran garderait « vous êtes au téléphone ». Sans effet tant qu'aucun capteur VoIP n'existe
  (P-4).
- **P-19** — **Repli quand l'écran STOP ne passe pas** : le contrat de l'exécuteur (`Ports.kt`) prévoit une
  notification plein écran ; elle n'est pas construite. Aujourd'hui un STOP bloqué est journalisé « non
  affiché » et son déclencheur réarmé. À construire avant publication (permissions `POST_NOTIFICATIONS`,
  `USE_FULL_SCREEN_INTENT`, restreinte depuis Android 14).

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

**Ajouter un capteur** : un sous-paquet `platform/<source>/`, sur le modèle de `telephony/`. Il traduit un
événement Android en `Signal` horodaté à la réception, et le confie à `SensorQueue` (jamais à
`ProcessSignalUseCase` en direct). Un récepteur prend `goAsync()` par `queueAsync`, et reçoit ce qui touche la
base par un `Provider`, résolu dans la file (leçon F2 d'Agenda Tech). Sa permission va dans le manifeste,
dans la garde et dans PRIVACY, **dans le même commit** (D-007). La traduction est une fonction pure, testée
sur la JVM ; le capteur se mesure sur l'émulateur puis sur un vrai téléphone.

**Ajouter un texte** : en anglais dans `values/strings.xml` et dans les quatre autres langues ; le contrôle
de parité refuse un texte manquant.

**Changer le schéma Room** : tant qu'aucun APK n'est publié, la version 1 peut changer. Ensuite, migration
additive, schéma exporté committé, test de migration, et `room-testing` à ajouter au catalogue avec la
contrainte `kotlinx-serialization` d'App Manager Tech (voir l'en-tête de `gradle/libs.versions.toml`).
