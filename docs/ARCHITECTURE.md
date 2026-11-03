# Call Halt Tech — architecture

> Écrit le 2026-11-01, en posant le squelette. La fiche du projet (le pourquoi, les fonctions) est
> `PROJET.md` ; ce document dit **comment** le code est organisé, **quelles décisions** le tiennent et
> **ce qui reste à mesurer**. Le code renvoie ici par les numéros D-xxx et P-x.
>
> Nom retenu par Patrice le 2026-11-01 : **Call Halt Tech** (D-002). Le projet s'est d'abord appelé « Call Guard
> Tech » ; les rapports de `relectures/` portent encore ce nom.

## 1. Ce que fait le squelette aujourd'hui

**Depuis le 2026-11-02, elle demande ses accès elle-même** (D-022) : l'écran d'activation les prend un par un, et
l'accueil dit l'état de la protection. Accès accordés, elle affiche le STOP pendant un appel ou juste après, sur un
SMS de code ou une demande Sécur'Pass (mesuré de bout en bout sur l'émulateur Android 16, appli installée depuis un
fichier). Elle n'est pas publiée : la liste de ce qui manque avant est dans `REPRISE.md`. Le squelette contient :

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
bancaire, et « Raccrocher » coupe l'appel. Il est déclenché par le premier capteur d'action sensible (la demande
de validation bancaire, `platform/notification`, D-019), ou par un signal injecté par adb (version de test).

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
  port/        SettingsSource, RestartSource, DetectorStore, DecisionExecutor — ce que :app doit fournir, avec
               leur contrat
  usecase/     ProcessSignalUseCase

app/src/main/java/com/filestech/callhalt/
  CallHaltApplication, MainActivity
  di/          ApplicationScope, StorageModule (Room, DataStore ; PhoneStateStore, le DataStore des faits du
               téléphone), DetectionModule (ports → implémentations), PlatformModule (ce que la plate-forme
               atteint par une interface, pour tester la logique autour sur la JVM)
  data/settings/  SettingsRepository + SettingsMapping (valeur hors bornes → défaut protecteur)
  data/store/     CallHaltDatabase, entités, DetectorDao, DetectorMapping (pur), RoomDetectorStore
  ui/          AppRoot, Routes, home/ (l'état de la protection), activation/ (un accès par étape, D-022), protection/
               (ProtectionViewModel + ProtectionState, AccessText), stop/ (StopScreen, StopText), theme/ (palettes du
               portefeuille, Damier)
  platform/    access/ — Access (les accès, dans l'ordre de l'activation ; mayBeRestricted, pur), AccessChecker (lus en
               direct), AccessSettings (les pages des réglages, la plus précise d'abord) ; SensorQueue (la file unique, P-12), Restarts (les redémarrages du téléphone, D-020 : Boot,
               BootReader + AndroidBootReader, BootCountRestarts), AndroidDecisionExecutor (le STOP d'abord ; le reste au
               fil des étapes) ; telephony/ — CallStateReceiver (manifeste, exporté : diffusion protégée),
               CallStateSensor, PhoneState (+ callSignal, pur), PhoneStateMemory (DataStore à part), CallEvents
               (fin d'appel, pour l'écran STOP) ; stop/ — StopActivity, StopRequest, StopDisplay (l'écran
               confirme qu'il est devant), StopLauncher, CallControl (raccrocher), StopAnswers (réponse → moteur) ;
               notification/ — NotificationListener (lié par le système, accès aux notifications : l'appli et le
               canal de chaque notification ; le dernier message de l'appli SMS par défaut, en mémoire, D-021),
               NotificationSignal (+ notificationSignal, SmsMessage, purs, D-019), ValidationCode (la règle d'un
               code de validation, pure, D-021)
  src/debug/   platform/debug/ — versions de test seulement, réservées au shell d'adb (DUMP) : DebugSignalReceiver
               (action sensible injectée, pour mesurer l'écran STOP avant les capteurs), StandInBankRequestReceiver
               (une demande bancaire factice sur le canal reconnu, pour mesurer tout le chemin sur émulateur),
               MeasureNotificationListener (ce qu'une notification laisse lire : la banque mesurée, P-2, et
               l'appli SMS par défaut, P-20)
  (à venir)    platform/<capteur>/ pour usage, packages, accessibility
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
| D-008 | **targetSdk 36, pas 37** | Android 17 retarde de **3 heures** les SMS de code pour toute appli ciblant l'API 37 ou plus, sauf l'appli SMS par défaut (developer.android.com, behavior-changes-17). Monter le targetSdk tuerait la fonction 4.3. À revoir si la règle change. **Raison affaiblie par D-021 (2026-11-02)** : les codes sont lus dans la notification de l'appli SMS par défaut, que ce retard ne touche pas. targetSdk reste 36 tant que les autres changements d'Android 17 ne sont pas mesurés (écran STOP lancé depuis l'arrière-plan, accès restreints). |
| D-009 | Pas de SQLCipher | le chiffrement de fichiers d'Android couvre déjà la base ; un Keystore muet un matin (Agenda Tech, Android 8-11) ne doit pas coûter la protection |
| D-010 | **Pas de service d'accessibilité** | les applis bancaires les détectent et refusent de fonctionner ; les antivirus les signalent ; Android 16 en bloque l'octroi pendant un appel avec un inconnu |
| D-011 | « Appeler ma banque » = `ACTION_DIAL` (numéro composé, l'utilisateur appuie), **après la fin de l'appel** | pas de permission `CALL_PHONE` ; appeler pendant l'appel mettrait l'escroc en attente au lieu de le couper (relecture GPT, A11) |
| D-012 | 5 langues dès le squelette, contrôle de parité de RFT repris sans changement de logique ; vouvoiement en IT et ES | le contrôle et son contrôle négatif (51 cas) supposent 5 langues ; 26 textes à ce stade (13 jusqu'à D-017). **Les phrases de sécurité DE/IT/ES sont à faire relire par un natif** : une consigne mal traduite ferait faire l'inverse. |
| D-013 | Thème du portefeuille (palettes de RFT 3) ; bleu et rouge du **damier** (`#0B60C5`, `#CA331F`) ; STOP sur le rouge du damier (blanc dessus : 5,25:1) ; icône = damier + bouclier | demande de Patrice, 2026-11-01 : qu'on reconnaisse une appli Files Tech |
| D-014 | Le moteur ne lit aucune horloge ; un appel suivi plus de 8 h est une fin manquée | décisions reproductibles en test ; une fin perdue ne garde pas tous les SMS suivants « pendant un appel » |
| D-015 | **SMS au proche : oui** (Patrice, 2026-11-01) | Fonction 7 de la fiche : le proche est prévenu quand un STOP s'affiche, quand « Continuer » est choisi malgré l'alerte, quand la protection est coupée. Le moteur décide déjà quand (`NotifyGuardian`) ; `SEND_SMS` arrivera avec le composant qui envoie (D-007), dans PRIVACY et la garde CI en même temps. C'est une permission soumise aux paramètres restreints (§ 6). Désactivé tant qu'aucun proche n'est configuré. |
| D-016 | **Publication sur F-Droid : oui — mais rien n'est publié tant que l'appli n'est pas fiable, solide et sécurisée au mieux, avec un score d'au moins 95 sur 100** (Patrice, 2026-11-01) | **Condition de publication** : la grille qui donne ce score se fixe AVANT le premier audit de publication, sur trois éléments. **(1)** Aucun constat critique ou élevé ouvert : audit en 3 axes, audit de sécurité avec panel adverse, faux positifs des règles de détection, motifs de défaut. **(2)** Mesures P-1 à P-3 faites sur de vrais téléphones. **(3)** Une relecture externe du code qui décide. Un score sous 95, ou un critère non tenu : pas de publication. Comme Agenda Tech et SMS Tech. **Build reproductible** : déjà préparé (pas de foojay, bloc de dépendances de Play exclu). **Métadonnées fastlane** dans les 5 langues, changelog ≤ 500 caractères. **APK servi = notre APK**, signé de notre clé (`Binaries` + `AllowedAPKSigningKeys`) ; mises à jour par tag. **Toute montée de la chaîne de build** (AGP, Kotlin, Gradle) se vérifie côté F-Droid : elle se fait avec tout le portefeuille (Dependabot n° 3, AGP 9.5.0, fermée le 2026-11-01 pour cette raison). À mesurer : une installation par le client F-Droid échappe-t-elle aux paramètres restreints (P-3) ? |
| D-017 | **Les mots de l'écran STOP** (Patrice, 2026-11-01), par ordre d'importance : où l'on est (« au téléphone » / « l'appel vient de se terminer »), ce qui vient d'arriver (un texte par déclencheur), « **si on vous dit d'ignorer cet écran, c'est une arnaque** », la règle (« ne vous appelleront JAMAIS pour vous demander un code ou de valider une opération »), « le numéro ou le nom affiché ne prouve rien », que faire. Choisis par `ui/stop/StopText.kt` selon le déclencheur, l'appel (en cours ou fini, téléphone ou VoIP) et la permission de raccrocher, vérifiés par `StopTextTest` | Les escrocs ne jouent pas que la banque : police, impôts, URSSAF, mutuelle, assurance, crédit (Patrice). La règle **nomme qui ne demande jamais** au lieu de dire « personne » : le service client d'un opérateur peut faire relire un code qu'il vient d'envoyer, un proche peut aider par une appli de prise en main. Elle dit « **vous appelleront** » et non « vous demanderont au téléphone » : qui appelle lui-même sa vraie banque peut avoir à valider ce qu'il a demandé (relecture Claude, H2). Une règle qu'on sait fausse rendrait tout l'écran douteux. **Pas de question** « connaissez-vous votre interlocuteur ? » : la victime croit le connaître (numéro usurpé, son nom, ses opérations) et répondrait oui ; l'écran affirme. L'escroc est encore en ligne quand l'écran s'affiche (« c'est normal, appuyez sur Continuer ») : « ignorer cet écran » le désarme d'avance, et reste catégorique — si c'était la vraie banque, l'erreur ne coûte qu'un rappel. **Ordre mesuré sur le S9** (police normale puis 1,3) : ce qui suit la règle sortait de l'écran ; on ne fait pas défiler sous pression. **Prise en main à distance** : raccrocher ne coupe pas la session, le conseil est d'éteindre le téléphone. **Boutons** : « Raccrocher » seulement si l'appli peut le faire (appel téléphonique en cours, permission accordée : `endCall` ne raccroche pas la VoIP) ; pendant l'appel, « Raccrocher et appeler ma banque » (D-011 : raccrocher AVANT de composer) ; après, « Appeler ma banque » en premier ; **pendant un appel que l'appli ne peut pas couper, aucun bouton d'appel** (le conseil dit de raccrocher). Si « Raccrocher » échoue, l'écran reste, sans ses boutons d'appel. Ils restent en bas, visibles ; les textes défilent au-dessus. **L'écran quitté sans réponse** (accueil, autre appli ; pas la mise en veille) se ferme et libère ses déclencheurs (`StopChoice.LEFT`, journalisé, sans SMS au proche) : une nouvelle tentative montre un nouvel écran. |
| D-018 | **La mesure décide de la suite** (Patrice, 2026-11-01, sur recommandation de Claude) : si l'écran STOP ne s'affiche pas par-dessus l'appli bancaire (P-1), ou si ses demandes de validation sont illisibles (P-2), **le projet s'arrête** | La concurrence n'est pas la raison : en France, rien ne fait la même chose en français, sans Google ni Internet (fiche, § 9.1). Les vrais risques sont techniques (P-1 à P-3 ; Android resserre ces accès à chaque version : codes masqués dans les notifications depuis Android 15, SMS de code retardés par Android 17) et stratégiques (Google ou Samsung, s'ils couvrent un jour la France, la rendraient inutile au plus grand nombre). La journée déjà investie ne pèse pas : seuls les vrais téléphones tranchent. **P-1 et P-2 passées le 2026-11-02** (S24 FE, Android 16) : **le projet continue.** |
| D-019 | **Une demande bancaire se reconnaît à son CANAL de notification, de n'importe quelle appli** (`platform/notification/NotificationSignal.kt`, liste `BANK_REQUEST_CHANNELS` : `securpass_id` aujourd'hui) ; l'écouteur ne lit que l'appli et le canal de chaque notification | Le canal est un identifiant du code de l'appli : le même dans toutes les langues et toutes les versions de ses textes, et le lire ne lit rien de ce que dit la notification (PRIVACY). **Pas de liste de paquets** : Sécur'Pass est commun aux banques du groupe BPCE, et un nom de paquet que personne n'a mesuré ferait manquer leurs demandes ; un canal qu'aucune autre appli n'emploie ne coûte rien là où il n'apparaît pas. Une appli qui l'imiterait ne provoquerait qu'un STOP de trop, du côté protecteur. Postée juste AVANT la vraie demande, pendant un appel, elle la ferait absorber par le STOP déjà affiché (règle des 60 s du moteur) : sans perte, puisque ce STOP est devant la victime, et l'écran quitté libère son déclencheur (`LEFT`). Seul un écran tué sans réponse laisserait passer la vraie demande pendant 60 s (relecture de sécurité du 2026-11-02, faible). **Correspondance exacte** (ni casse, ni sous-chaîne : `NotificationSignalTest`). Le paquet va au journal (de quelle appli venait la demande). **L'accès aux notifications est un accès spécial, pas une permission** : la garde CI (`tools/check-manifest-permissions.py`) tient désormais aussi la liste exacte des composants que le système lie par une permission `BIND_*`, et refuse toujours un service d'accessibilité (D-010) ou un VPN. Elle lit la permission **qu'Android applique** : celle du composant, sinon celle de `<application>` (une valeur vide l'annule), celle de sa cible pour un alias ; une permission donnée par référence (`@string/…`) échoue. La relecture indépendante du 2026-11-02 avait fait passer un service d'accessibilité par l'héritage de `<application>` dans la première version (contrôle négatif : 19 cas, dont celui-là, vérifié rouge sur l'ancienne garde). |
| D-020 | **Un appel ne survit pas à un redémarrage du téléphone ; sa fin, inconnue, est posée au redémarrage** (`PhoneRestarted`, `platform/Restarts.kt`) | **Mesuré le 2026-11-02 (émulateur Android 16)** : un téléphone éteint en plein appel ne reçoit aucun état d'appel au démarrage ; l'appel restait « en cours » jusqu'au plafond de 8 h, et une demande bancaire de l'utilisateur lui-même montrait un faux STOP. **Détection** : Android compte les démarrages (`Settings.Global.BOOT_COUNT`, API 24+, sans permission) ; un compte différent du dernier vu est un redémarrage, survenu il y a `elapsedRealtime`. Demandé par `ProcessSignalUseCase` **avant chaque signal** : aucun capteur ne peut l'oublier. **Dans le doute, rapporté** (relecture Claude du 2026-11-02, qui a corrigé la première version) : le redémarrage rapporté est le démarrage EN COURS, il ne termine que les appels commencés avant lui, qui ne peuvent plus être en cours, et le rapporter deux fois ne change rien. Se taire, au contraire (compteur jamais vu, mémoire en panne), laissait l'appel fantôme « en cours » et y absorbait le vrai appel suivant : un STOP manqué. Il n'est **retenu qu'une fois traité** par le moteur (un processus tué entre les deux le rapporte de nouveau) ; jamais placé **après le signal qui le suit** (une horloge corrigée en avant entre-temps terminerait l'appel que ce signal commence). Seul un compteur qu'Android ne donne pas n'est pas rapporté : le plafond de 8 h reste. **La fin posée** : la vraie fin est entre le début de l'appel et l'extinction, que rien ne dit. Posée au redémarrage, la fenêtre « juste après l'appel » court encore 15 min après lui (au lieu de 8 h) : le côté protecteur, si l'escroc a fait redémarrer le téléphone. Écartée : la fin au début de l'appel, qui ôterait cette protection. Seuls les appels encore en cours au redémarrage sont terminés (un appel déjà fermé par le plafond n'est pas ranimé), et les écrans montrés avant lui libèrent leur déclencheur. **Affinage possible**, à mesurer : l'heure de mort du processus à l'extinction (`ApplicationExitInfo`, API 30+), que l'écouteur de notifications tient en vie. **Mesuré sur émulateur (Android 16)**, version relue : appel en cours, émulateur coupé net, démarrage à froid ; au premier signal, le redémarrage est rapporté, l'appel fantôme terminé à l'heure du démarrage, puis le compteur retenu ; 15 min après, une demande de l'utilisateur ne montre rien (avant le correctif : un faux STOP). Sur une installation neuve, le redémarrage rapporté dans le doute n'a rien terminé. **Limites** : l'heure du démarrage se calcule avec l'horloge du moment ; reculée pendant le redémarrage (horloge remise à zéro par une batterie vide, avant la synchronisation réseau), elle tombe avant le début du fantôme, qui n'est pas terminé (P-13 : la parade propre est de porter le numéro de démarrage avec chaque appel, indépendant de l'horloge, au prix d'une colonne de plus dans la base). Un « redémarrage léger » d'Android (le système seul, sans le noyau) ne remet pas `elapsedRealtime` à zéro : à vérifier. **Observé le 2026-11-02 sur émulateur** : un appel ouvert 1 h 30 avant un démarrage a été fermé au démarrage, et un code de l'utilisateur, 7 min après, a montré un STOP « l'appel vient de se terminer » : la fenêtre de 15 min court du redémarrage, pas de l'extinction. **L'affinage par l'heure de l'extinction est donc à faire avant publication.** |
| D-021 | **Un SMS de code se reconnaît au texte de la notification de l'appli SMS par défaut** (Patrice, 2026-11-02 : « ok a », voie (a) de P-20), par l'accès aux notifications déjà demandé : **aucune permission de plus** | Mesuré le 2026-11-02 : le masquage d'Android 15+ ne voit pas les codes français (Google), et Samsung ne masque rien : les mots sont le signal. **Ce qui est lu** : pour l'appli SMS par défaut seulement, le DERNIER message de la notification (le dernier de la conversation, sinon le texte), jamais les messages d'avant ; en mémoire, comparé, oublié ; ni gardé, ni journalisé (le journal garde le paquet de l'appli SMS). **Un message masqué par Android** (le texte du système `redacted_notification_message`, lu à son nom, vérifié dans les ressources d'Android 16) compte comme un code : son détecteur en a trouvé un. **La règle** (`ValidationCode.kt`), **réécrite en deux niveaux après l'agent des faux positifs** (« pas prête » : 72 erreurs sur 126 SMS construits mot à mot) : effacer les nombres qui ne sont jamais un code (« STOP au 36111 », dates écrites, numéro à appeler, fin de carte, IBAN, référence) ; puis un nombre en forme de code (4 à 8 chiffres, ou 3 + 3) ET soit une **expression que seul un code emploie** (« code de validation », « usage unique », « Certicode », « pour valider votre achat »…), soit un **« code » nu** que rien ne dit être un autre code (livraison, promo, porte, réservation, QR…) — sauf **mise en garde ou paiement** (« jamais », « personne », « paiement »), qui en refait un code : « ne le donnez jamais, même au livreur », c'est l'arnaque au faux livreur. **Les 126 SMS sont son test** (`validation-code-sms.txt`, sur la JVM par `ValidationCodeTest` et sur téléphone par `ValidationCodeOnDeviceTest` : le moteur d'expressions d'Android n'est pas celui de la JVM) ; ce qui a été décidé contre le premier verdict est écrit en tête du fichier. Dans le doute, c'est un code : un STOP de trop pendant un appel coûte moins qu'un code lu à l'escroc. Écartée : la réception des SMS (`RECEIVE_SMS`), permission de plus, restreinte. **La lecture, après trois relectures (2026-11-02)** (`LatestSms.kt`, testé sur téléphone par `LatestSmsOnDeviceTest`) : la dernière entrée de la conversation seule, lue directement — les messages d'avant ne sont même pas construits — et rien d'autre si elle n'a pas de texte (une photo) ; sinon le grand texte, puis le texte ; jamais un résumé de groupe ; un message de plus de **10 min** n'est pas un code reçu maintenant (un code reposté des heures plus tard) ; le même message reposté (même conversation, même heure) ne compte qu'une fois ; l'appli SMS par défaut relue une fois par minute (la chercher coûte à Android plusieurs requêtes) ; **elle n'est trouvée que si l'appli peut la voir** (Android 11+) : sans les `<queries>` du manifeste, « aucune » pendant 5 min sur le S24 FE, et le code n'a été lu que parce qu'Android montre à l'écouteur l'appli de chaque notification qu'il lui remet (mesuré le 2026-11-03). La requête que nomme la documentation de `getDefaultSmsPackage` (`SMS_DELIVER`) ne suffit pas : c'est une diffusion protégée (`dumpsys package`, émulateur Android 16), et pour un récepteur, la visibilité d'Android ignore les diffusions protégées (`AppsFilterUtils`, sources d'AOSP) : toujours « aucune » sur Android 16 ; « écrire un SMS » (`SENDTO`, `smsto:`), que toute appli SMS par défaut doit savoir faire, la trouve du premier coup (Android 16, S24 FE ; `DefaultSmsAppOnDeviceTest`, rouge sans elle) ; le texte de masquage lu dans la langue du TÉLÉPHONE, pas celle de l'appli ; une notification illisible écartée sans arrêter l'écouteur ; `SmsMessage.Text` n'affiche jamais son texte. **Non lu** : une appli SMS qui met ses messages dans une vue à elle ou en « boîte de réception » (`InboxStyle`). **Mesuré sur émulateur Android 16** (français, Google Messages) : pendant un appel, un code français → STOP « Vous venez de recevoir un code. » ; un SMS ordinaire → rien ; un code anglais masqué par Android → STOP ; sans appel → rien. **Sur le S24 FE, vrai appel (messagerie), le 2026-11-03** : un code envoyé à soi-même → STOP 27 ms après la notification de SMS Tech, « Raccrocher » a coupé l'appel. **Samsung Messages, non mesuré** (le S24 de Patrice a SMS Tech). |
| D-022 | **L'activation demande les accès un par un ; l'accueil dit l'état de la protection** (2026-11-02, P-3 et P-10 ; « carte blanche » de Patrice sur la façon) | **Trois accès, dans cet ordre** (`platform/access/Access.kt`), chacun utile seulement avec ceux d'avant : le téléphone (`READ_PHONE_STATE` et `ANSWER_PHONE_CALLS` : même groupe, un seul dialogue), l'affichage par-dessus les autres applis, l'accès aux notifications ; **quatre depuis D-023** (« active même quand vous ne l'ouvrez pas », avant les notifications), **cinq depuis D-024** (« alerte si l'écran STOP est bloqué », la dernière). **Une étape par écran** : pourquoi on le demande, ce que le téléphone va montrer, un bouton. Les mots du téléphone sont cités tels qu'il les affiche, lus dans les ressources d'Android 16 pour les 5 langues ; pas ceux des interrupteurs, qui changent d'un fabricant à l'autre, et chaque page n'en a qu'un. Les accès sont relus à chaque retour au premier plan : l'étape accordée laisse place à la suivante. « Plus tard » ramène à l'accueil. **Après un refus du téléphone**, l'étape garde « Autoriser » et ajoute « Ouvrir les réglages » (« Autorisations », « Téléphone », « Autoriser ») : Android n'affiche plus son dialogue après deux refus, et l'appli ne distingue pas ce cas d'un dialogue fermé par Retour ou par un appel entrant (relecture générale du 2026-11-02, n° 4 ; mesuré sur Android 16 : après Retour, « Autoriser » rouvre le dialogue). **L'accueil** : « Protection activée » (« La protection est en marche » jusqu'au 2026-11-03 ; Patrice : « en marche, ça ne se dit pas », et « désactivée » pour l'inverse, qui disait « ne fonctionne pas » ; puis, le même jour, la forme courte, sans verbe, comme « Protection affaiblie », P-10) seulement si les trois sont accordés, et alors ce qu'elle surveille, rien de plus : les appels téléphoniques, Sécur'Pass nommé (la seule demande bancaire reconnue, D-019), les codes « en français ou en anglais » dans les textes allemand, italien et espagnol (la règle ne lit que ces deux langues, D-021) ; puis « elle ne voit pas tout, ni les appels par Internet ». Sinon, combien d'accès manquent. Chaque accès en mots, pas en couleur seule. Un téléphone Android Go ne peut pas être protégé, et l'accueil le dit (P-21). **Paramètres restreints** (mesurés, P-3) : l'aide (essayer d'abord, car c'est le refus qui fait apparaître le menu ; fermer le message ; infos de l'appli ; ⋮ ; « Autoriser les paramètres restreints » ; taper soi-même le code du téléphone ; recommencer) s'affiche pour l'accès aux notifications à partir d'Android 13 et pour l'affichage par-dessus à partir d'Android 15, quelle que soit la source d'installation sauf une boutique (`InstallSourceInfo.getPackageSource`). Absente là où le téléphone refuse, elle laisserait l'utilisateur bloqué ; présente là où il ne refuse pas, elle coûte quelques lignes. Les escrocs font faire la même chose pour LEUR appli : l'avertissement vient en tête, en gras (« ne le faites que pour Call Halt Tech, depuis cet écran… si on vous le demande au téléphone, raccrochez »), et chaque étape nomme l'appli (relecture de sécurité du 2026-11-02, S1). Le bouton « Ouvrir les infos de l'appli » est épinglé en bas avec les autres : à grande police, l'aide sort de l'écran (relecture générale, n° 3). **Les pages des réglages** s'ouvrent depuis l'activité, sans nouvelle tâche : la touche Retour ramène à l'appli (mesuré : lancée en nouvelle tâche, la demande est avalée par des réglages déjà ouverts). La plus précise d'abord (page de l'écouteur à partir d'Android 11), puis les infos de l'appli, puis les réglages du téléphone. **Toujours servies par les Réglages du système** (`MATCH_SYSTEM_ONLY`, puis `setPackage` ; les actions nommées dans les `<queries>` du manifeste) : une appli qui déclare la même action ne peut ni s'y glisser ni les imiter (relecture de sécurité, S2 ; mesuré sur Android 14 et 16 : `com.android.settings`). Une page qu'un fabricant refuse (`SecurityException`) passe à la suivante. **L'accès aux notifications se lit pour l'écouteur de la protection** (`isNotificationListenerAccessGranted`), pas pour le paquet : la version de test a un second écouteur, de mesure (`AccessCheckerOnDeviceTest` ; contrôle négatif vérifié rouge). **Pas d'étape « applis inutilisées »** : l'hibernation épargne une appli dont l'écouteur est actif (§ 6). La **veille profonde de Samsung**, mesurée le 2026-11-03, coupe la protection : D-023. |
| D-023 | **Une quatrième étape : « Active même quand vous ne l'ouvrez pas »** (Patrice, 2026-11-03 : « ok pour tous bien sûr », pour tous les téléphones et non Samsung seul) — l'appli exemptée de l'optimisation de la batterie, **avant** l'accès aux notifications | **Mesuré sur le S24 FE (One UI 8, Android 16)** : la **veille profonde** de Samsung (« Applis en veille profonde ») tue l'appli et délie son écouteur en moins de 2 min, écran éteint ; une demande factice n'est plus vue ; l'accès aux notifications se lit alors refusé (l'accueil le dit) et, tant que l'appli reste en veille profonde, l'interrupteur rallumé retombe. **« Non restreinte »** (page batterie de l'appli ; la liste blanche `deviceidle`, que lit `PowerManager.isIgnoringBatteryOptimizations`) **sort l'appli de la veille profonde**, et elle n'apparaît plus dans aucune liste de mise en veille de Samsung ; remise en « Optimisée », elle y réapparaît (contrôle négatif). Samsung coche par défaut « Mise en veille applis inutilisées », et une appli de protection, on ne l'ouvre presque jamais. **L'étape** : le dialogue du téléphone (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, permission `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, qui ne lit rien), servi par les Réglages du système comme les autres pages ; ses mots ne sont pas cités, ils changent d'un fabricant à l'autre (Android 16 : « Toujours autoriser en arrière-plan ? », « Autoriser » ; Samsung : « Arrêter optimisation utilisation batterie ? », « OK ») ; puis, après un refus, les infos de l'appli (« Sans restriction », « Non restreinte » sur Samsung). Mesuré : un « OK » met l'appli en « Non restreinte », l'accueil passe à « activée » ; l'exemption survit à une mise à jour. **Avant les notifications** : sortie de la veille profonde d'abord, sinon l'interrupteur des notifications retombe. **« Passer cette étape »**, après un refus seulement (relecture du 2026-11-03, 1) : un téléphone qui refuse son dialogue ne doit pas bloquer l'accès aux notifications, qui fait l'essentiel ; l'accueil dit toujours l'étape manquante. Lint (`BatteryLife`) avertit d'une règle du Play Store ; l'appli est publiée sur F-Droid (D-016). Écartés : la liste « Apps jamais mises en veille auto. » de Samsung (sa page est réservée au système : `READ_SEARCH_INDEXABLES`), quatre écrans à faire suivre. |
| D-024 | **Le repli de l'écran STOP : une notification qui l'ouvre, et une cinquième étape d'activation** (Patrice, 2026-11-03 : « ok », P-19) | Quand le téléphone bloque l'écran STOP lancé depuis l'arrière-plan (attendu sur Xiaomi, P-21 ; ou l'affichage par-dessus retiré), rien ne s'affichait : le STOP était journalisé « non affiché », en silence. Maintenant, faute d'écran créé dans les 5 s (`StopDisplay`), une notification sur un canal « Écran STOP bloqué » (importance haute) : « STOP — Vous venez de recevoir un code. Touchez ici et lisez l'écran STOP avant de faire quoi que ce soit. » Téléphone verrouillé ou écran éteint, c'est une notification plein écran : elle **rallume l'écran et ouvre le STOP d'elle-même** ; sinon elle s'affiche en haut de l'écran, et un toucher l'ouvre. Mesuré sur l'émulateur Android 16, l'affichage par-dessus retiré, pendant un appel : Réglages devant → notification 5 s après, toucher → STOP, notification retirée ; écran éteint → STOP ouvert seul, écran rallumé. **Mesuré sur le S24 FE avec Patrice** (vrai appel, le 123) : Samsung bloque l'écran (« Background activity launch blocked »), la notification s'affiche par-dessus l'écran d'appel, un toucher ouvre le STOP. **Téléphone à l'oreille, un trou trouvé et fermé** : l'écran éteint par le capteur de proximité, le téléphone n'est ni endormi ni verrouillé ; Android n'ouvre alors pas le plein écran et n'affiche pas la bannière (« NO_FSI_NO_HUN_OR_KEYGUARD », journal de Samsung) : la notification attendait dans le volet, invisible quand on regarde à nouveau. Maintenant, postée écran éteint, elle est reposée dès que l'écran se rallume (`DisplayManager`) : mesuré, la bannière paraît quand on éloigne le téléphone, un toucher ouvre le STOP. L'émulateur ne sait pas reproduire ce cas (son capteur de proximité n'éteint pas l'écran). **Postée vaut montrée** : l'utilisateur est prévenu (le moteur garde le STOP 60 s, comme un écran ignoré). Une seule à la fois ; tout écran STOP créé la retire. Publique sur l'écran verrouillé : elle ne dit que ce qui vient d'arriver, rien de personnel. **La cinquième étape**, « Alerte si l'écran STOP est bloqué », dernière (utile seulement quand un STOP peut être décidé) : à partir d'Android 13 le téléphone demande « Autoriser Call Halt Tech à vous envoyer des notifications ? » (mots lus dans les ressources d'Android 16, mesuré) ; avant, rien n'est demandé, et l'étape ne paraît que si l'utilisateur a coupé les notifications de l'appli (elle ouvre alors leurs réglages). L'accès se lit sur quatre choses (relecture du 2026-11-03) : la permission, les notifications de l'appli, le canal resté urgent (importance haute : en silencieux, ou « par défaut » sans « afficher sur l'écran », Android ne l'affiche pas en haut de l'écran ni ne l'ouvre en plein écran — mesuré), et, à partir d'Android 14, l'autorisation plein écran (`canUseFullScreenIntent` ; coupée, l'accès manque — mesuré), sans laquelle le cas du téléphone à l'oreille ne marche plus. Accordée mais coupée ou baissée depuis, l'étape ouvre les réglages de notifications de l'appli (on y trouve le canal et l'autorisation plein écran, Android 16) au lieu de redemander une permission déjà là. `USE_FULL_SCREEN_INTENT` : accordée d'office à une appli installée par fichier (Android 16, mesuré) ; le Play Store la retire aux applis qui ne sont ni réveil ni téléphone, l'appli est publiée sur F-Droid (D-016). Deux permissions de plus, dans la garde CI et la politique de confidentialité ; aucune autre notification n'est envoyée. **L'accueil, quand seule cette étape manque** (Patrice, « ok pour B ») : « Protection affaiblie » — « Si le téléphone empêche l'écran STOP de s'afficher, l'appli ne pourra pas vous prévenir », et « Activer la protection » ; pas « désactivée » : l'écran STOP marche sur la plupart des téléphones (relecture du 2026-11-03, R-05). Tout autre accès manquant reste « Protection désactivée ». **Non mesuré** : un Xiaomi (aucun ici) ; un téléphone verrouillé par un code au moment où l'écran se rallume (le plein écran devrait alors ouvrir le STOP de lui-même). |

## 6. Les capteurs à brancher — ce qu'Android permet (vérifié le 2026-11-01)

Sources primaires (developer.android.com, AOSP par branche de release) relues par un agent de
recherche ; statut entre parenthèses. « Restreint » = bloqué par les **paramètres restreints** pour une
appli installée par APK, jusqu'à « Autoriser les paramètres restreints » dans les infos de l'appli.

| Signal | Moyen | Permission | Restreint | Statut |
|---|---|---|---|---|
| Appel téléphonique : début, fin, numéro | récepteur **déclaré au manifeste** `PHONE_STATE` (exception aux broadcasts implicites, sans condition de targetSdk) | `READ_PHONE_STATE` ; numéro : + `READ_CALL_LOG` (broadcast reçu deux fois) | non | **branché le 2026-11-02** (début et fin, sans le numéro) ; **mesuré sur émulateur, Android 11, 16 et 17** (vrai service téléphonique, `adb emu gsm`), d'abord non exporté : le récepteur reçoit la diffusion du système ; processus tué (Android 16) : réveillé, mémoire intacte ; **appli en cache (Android 17) : livrée en 0,15 à 0,6 s**, aucun report. Une appli « arrêtée » (`force-stop`) ne reçoit plus rien jusqu'à sa réouverture : comportement d'Android, à dire dans l'aide. Juste après le démarrage, rien tant que la SIM n'est pas prête (normal). **Exporté** (pour qu'un téléphone qui l'émettrait depuis son processus téléphonique soit entendu) : PHONE_STATE est une diffusion protégée, **mesuré sur le S9 (Samsung, Android 10) et l'émulateur Android 17** : même le shell d'adb est refusé, que la diffusion vise le récepteur ou non. Toute autre action est ignorée. Pas de permission exigée de l'expéditeur : elle risquerait de couper la livraison par le système (relecture de sécurité). **Vrai réseau, mesuré sur le S24 FE (Samsung, Android 16, SFR) le 2026-11-02** : appel sortant, appel reçu décroché (entrant), appel reçu refusé (aucun appel) ; traités 0,05 à 0,07 s après réception ; la base garde l'appel entrant, sans numéro, et l'appel refusé n'y change rien. Le premier appel après l'installation a un sens inconnu (aucun état connu avant lui). Le S24 envoie une seule diffusion par changement d'état, **sans indice de SIM** (le S9 n'a pas de SIM). **Double SIM, à mesurer** (relecture du 2026-11-02, M2) : si un téléphone envoie un IDLE par SIM, celui d'une SIM au repos fermerait l'appel en cours sur l'autre ; la trace de débogage note la SIM de chaque diffusion. Parade possible, à ne poser qu'après mesure : interroger l'état en direct à l'IDLE (P-7), sans risquer un appel qui ne se ferme jamais |
| Appel VoIP (WhatsApp, Signal…) | `AudioManager.getMode()` global sans permission (`MODE_IN_COMMUNICATION`), `addOnModeChangedListener` (API 31) ; ou notification `CATEGORY_CALL` | aucune / accès aux notifications | — | code confirmé, **comportement des applis à mesurer (P-4)** |
| SMS de code | récepteur `SMS_RECEIVED` | `RECEIVE_SMS` | oui | confirmé, sous réserve de D-008 — **écarté par D-021** : le code est lu dans la notification de l'appli SMS (ligne suivante) |
| SMS de code, repli Android 15+ | la notification de l'appli SMS par défaut arrive **masquée** (« Sensitive notification content hidden ») : le masquage lui-même signale un code | accès aux notifications | oui | **MESURÉ le 2026-11-02, émulateurs Android 16 et 17 (détecteur de codes de Google), écouteur de mesure** : un code **en anglais** (« Your verification code is … ») est masqué — titre vide, texte et conversation remplacés par « Sensitive notification content hidden » (« Le contenu sensible de la notification a été masqué » en français) — avec Google Messages **et** avec SMS Tech comme appli SMS ; canal et catégorie gardés. **Un code en français n'est PAS masqué** (« Votre code de validation est … », « Code de confirmation : … »), même téléphone réglé en français : l'écouteur reçoit le texte entier. ⇒ **Le masquage n'est pas un signal fiable en France** avec ce détecteur ; reconnaître un code français demande de lire le texte du SMS (réception des SMS, ou texte de la notification de l'appli SMS) : la promesse de confidentialité change (P-20, à trancher avec Patrice). Même résultat sur Android 17 (SMS Tech comme appli SMS). **Samsung (S24 FE, Android 16, SMS Tech comme appli SMS), le 2026-11-02 : rien n'est masqué**, ni le code français ni le code anglais (deux SMS que Patrice s'est envoyés : l'écouteur reçoit les deux textes entiers). ⇒ **Le masquage n'est utilisable nulle part en France** ; il faut lire le texte (P-20) |
| Demande de validation bancaire | `NotificationListenerService` — sur AOSP, le masquage des codes ne vise que l'appli SMS par défaut ; assistants Samsung et Pixel inconnus | accès aux notifications | oui | **MESURÉ le 2026-11-02 sur le S24 FE (P-2)** : la demande Sécur'Pass du Crédit Coopératif est lue en clair — canal **`securpass_id`**, titre « Vous allez enregistrer un compte externe », texte vide, aucun bouton ; l'appui dessus la retire avec `REASON_CLICK` (1). **Capteur branché le 2026-11-02** (`platform/notification`, D-019), **mesuré de bout en bout sur émulateur, Android 16 et 17**, avec une demande factice sur le canal reconnu (version de test, `StandInBankRequestReceiver`) : hors appel, reconnue mais rien d'affiché ni de gardé ; pendant un appel, STOP devant tout ; juste après, STOP « l'appel vient de se terminer », « Appeler ma banque » en premier. **Une seule notification arrive DEUX fois** à l'écouteur, à 0,26-0,3 s d'écart (sans doute l'assistant de notifications de Google qui l'ajuste) : le moteur en fait un seul STOP (`NotificationSignalTest`). Mise à jour de la même notification après l'écran quitté : nouveau STOP, comme voulu (D-017). **Processus tué** (`kill`, pas `force-stop`) : le système relance l'écouteur en 1 s et le tient au rang de service lié (`BFGS`). **Android 10 (S9)** : lié, demande reconnue, rien hors appel (pas de SIM : pas d'appel). **MESURÉ DE BOUT EN BOUT SUR LE S24 FE le 2026-11-02** (Samsung, Android 16) : vrai appel reçu (le fixe de Patrice), vrai ajout d'un bénéficiaire depuis le site ; notification reçue **une seule fois** (pas de double livraison chez Samsung) ; **STOP affiché 0,12 s après elle**, devant tout ; « Raccrocher » ; journal : STOP (source `com.credit_coop.android.mobilebanking`) puis réponse ; l'appui sur la notification la retire (`REASON_CLICK`), l'opération est validée dans l'appli ; **aucune republication ni confirmation sur le canal** ; la banque confirme ensuite par **SMS** (« Ajout d'un bénéficiaire … Si vous n'avez pas initié cette opération, contactez votre agence »), 20 s après la fin de l'appel |
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
  verrou revient. ✅ **Téléphone à l'oreille, S24 FE, le 2026-11-03** (vrai appel, la messagerie ; écran éteint par le
  capteur de proximité) : une demande Sécur'Pass factice, reçue par l'écouteur, démarre le STOP 35 ms plus tard ; il
  prend le premier plan devant l'écran d'appel de Samsung, écran toujours éteint ; le téléphone éloigné, il est là ;
  « Raccrocher » coupe l'appel. Reste Android 10 lui-même, sur un téléphone avec SIM.
- **P-2** — Les demandes de validation des applis bancaires françaises sont-elles lisibles ? Le S9 n'est
  pas rooté (mesuré le 2026-11-01), mais son chargeur d'amorçage est déverrouillé, ce que certaines
  banques refusent : essayer d'abord sur le S9. Si la banque refuse, le S24 FE, **à la décision de Patrice**.
  ✅ **Oui, sur le S24 FE (Samsung, Android 16), le 2026-11-02** : ajout d'un bénéficiaire depuis le site du
  Crédit Coopératif (groupe BPCE), aucun argent engagé. Un écouteur de mesure (version de test seulement,
  `MeasureNotificationListener`, chiffres masqués dans le journal, accès retiré aussitôt après) a reçu la
  notification en clair : appli `com.credit_coop.android.mobilebanking`, **canal `securpass_id`**, titre
  « Vous allez enregistrer un compte externe », texte vide, sans bouton ni catégorie ; l'appui de
  l'utilisateur l'a retirée avec `REASON_CLICK` (1). **Conséquences pour le capteur** : (1) le canal
  `securpass_id` reconnaît une demande Sécur'Pass sans dépendre des mots — Sécur'Pass est commun aux banques
  BPCE (Banque Populaire, Caisse d'Épargne…), **à vérifier sur une autre banque du groupe** ; (2) le titre dit
  l'opération, utile au journal et au relevé d'incident ; (3) l'appui sur la notification est observable
  (idée 15 de la fiche). Après l'appui, l'appli bancaire demande elle-même « êtes-vous au téléphone ? » :
  elle compte sur la réponse de la victime, que l'escroc dicte ; Call Halt, lui, sait l'appel en cours et
  intervient dès l'arrivée de la notification. **Restent** : les autres banques (chaque appli à mesurer), et
  le masquage éventuel d'un code dans le texte sur Android 15+ (ici, aucun code dans la notification).
  ✅ **Avec le capteur branché (D-019), mesuré de bout en bout sur le S24 FE le 2026-11-02** (§ 6) : vraie demande
  pendant un vrai appel, STOP 0,12 s après la notification ; Samsung la livre une seule fois ; ni republication
  ni confirmation sur le canal, avant ou après la réponse. Si une autre banque répète ses notifications, les
  distinguer (par la clé de la notification) devient nécessaire **avant le SMS au proche** (D-015) : chaque
  nouveau STOP le préviendrait à nouveau. Une demande — ou un SMS de code (D-021) — publiée pendant que l'écouteur n'est pas lié (accès tout
  juste accordé, relance après la mort du processus : 1 s mesurée) n'est pas vue. **Ni une demande reçue mais
  pas encore traitée** quand le processus meurt (la file est en mémoire ; quelques millisecondes, le processus
  étant alors au rang de service lié) : relecture GPT du 2026-11-02, n° 1. Parade possible, non construite :
  relire à la connexion les notifications actives et récentes ; elle rejouerait aussi une demande déjà traitée,
  donc un STOP de trop. **L'écouteur de mesure** (`MeasureNotificationListener`, version de test seulement) est
  le seul code qui lise le titre et le texte d'une notification (chiffres masqués ; l'appli bancaire mesurée et
  l'appli SMS par défaut) : à retirer une fois les autres banques et les SMS de code mesurés.
- **P-3** — Paramètres restreints : ✅ **parcours construit et mesuré le 2026-11-02** (D-022), appli installée
  depuis un fichier téléchargé, par l'installateur du téléphone (source `LOCAL_FILE`) :
  - **Android 14** (émulateur, en anglais) : seul l'accès aux notifications est bloqué (« Restricted setting — For
    your security, this setting is currently unavailable », bouton OK) ; l'affichage par-dessus s'accorde directement ;
  - **Android 16** (émulateur, en français) : **les deux sont bloqués** (« L'appli s'est vu refuser l'accès », bouton
    Fermer). Le menu ⋮ des infos de l'appli, avec « Autoriser les paramètres restreints », **n'existe qu'après un
    premier refus**. Un seul déblocage ouvre les deux accès. Il passe aussi pendant un appel : Android 16 a un texte
    pour bloquer un réglage pendant un appel, qui ne s'est pas déclenché ici. Installée par adb (source `OTHER`),
    l'appli n'a rien de bloqué ;
  - **parcours complet dans l'appli, Android 16** : téléphone refusé deux fois puis accordé dans les réglages ;
    affichage par-dessus refusé, débloqué, accordé ; accès aux notifications ; l'accueil passe à « en marche ».
    Pendant un appel, un SMS de code → STOP, avec « Raccrocher ». Un accès retiré → l'accueil repasse au rouge ;
  - **refait après les relectures**, même émulateur, nouvelle installation depuis un fichier : dialogue du téléphone
    fermé par Retour → « Autoriser » le rouvre, « Ouvrir les réglages » est proposé à côté ; aide en tête de
    l'avertissement, « Ouvrir les infos de l'appli » épinglé et suivi jusqu'au déblocage ; étape 3 affichée en haut ;
    pages ouvertes dans `com.android.settings` ; accueil « en marche » avec Sécur'Pass nommé.

  - **Samsung, S24 FE (One UI 8, Android 16), le 2026-11-03, par Patrice** : ancienne version désinstallée, nouvelle
    installée depuis un fichier ouvert dans Read Files Tech (source `LOCAL_FILE`). **Play Protect** d'abord : « Analyse
    d'appli recommandée … Cette appli est inconnue de Play Protect », « Analyser l'appli », puis « Cette appli semble
    sûre ». **Rien n'est bloqué** : l'affichage par-dessus (que One UI nomme « Vue en premier plan », page de l'appli
    ouverte directement) et l'accès aux notifications (One UI montre la liste entière, puis « Autoriser Call Halt
    Tech ? … pourra lire toutes les notifications… ») s'accordent sans refus ; aucun message affiché, alors que le
    système a noté un refus (`ACCESS_RESTRICTED_SETTINGS` : `rejectTime`). L'aide s'affiche quand même : elle ne gêne
    pas. Du premier écran à « en marche » : moins d'une minute. Dans la version de test, la liste montre deux « Call
    Halt Tech » (le second est l'écouteur de mesure, désactivé ensuite) ;
  - **Android 10, Galaxy S9, le même jour** : parcours complet (installée par adb ; pas de paramètres restreints) :
    un dialogue « Autoriser l'appli Call Halt Tech à passer et gérer des appels téléphoniques ? » accorde les deux
    permissions ; « Vue en premier plan » ouverte sur l'appli ; l'accès aux notifications est une liste
    d'interrupteurs (la page de l'écouteur n'existe qu'à partir d'Android 11), confirmé par « Autoriser Call Halt
    Tech » ; accueil « en marche ».

  Restent : **Android 15** (pas d'émulateur ; l'aide y est supposée nécessaire), Android 11, et **la source d'une
  installation par le client F-Droid** (`dumpsys package <appli>` → `packageSource`). S'il se déclare boutique,
  l'aide disparaît là où elle ne sert pas.
- **P-4** — VoIP : `getMode()` voit-il WhatsApp, Signal, Telegram en appel ?
- **P-5** — Pendant l'appel, qu'est-ce qui garde le processus en vie pour interroger UsageStats ? Un
  service de premier plan lancé depuis l'arrière-plan est restreint (targetSdk ≥ 35 : `SYSTEM_ALERT_WINDOW`
  **et** une superposition visible). Piste : l'écouteur de notifications, lié par le système, tient le processus.
  **Confirmé sur émulateur (Android 16), le 2026-11-02** : écouteur lié, le processus est au rang de service lié
  (`BFGS`), et relancé en 1 s s'il est tué. Reste à mesurer sur le S24 FE, que Samsung gère plus durement.
- **P-6** — Un code reçu pendant la sonnerie ou juste avant l'appel (l'escroc déclenche l'opération puis
  appelle) n'est pas « pendant l'appel » pour le moteur. Faut-il une fenêtre *avant* ? **Pour une demande
  bancaire, l'appui sur la notification est une piste plus précise** (idée 15 de la fiche ; observable, P-2) :
  posée avant l'appel, appuyée pendant (« appuyez sur la notification de votre banque »). Pas branché le
  2026-11-02, et pas comme un déclencheur de plus : l'appui ouvre aussi l'écran où l'on **refuse**
  l'opération. Après un STOP, un second écran gênerait la victime au moment où elle se défend. À trancher avec
  ce point : n'arrêter que l'appui d'une demande arrivée **avant** le début de l'appel en cours.
- **P-7** — L'état réel de l'appel, interrogé en direct par le capteur téléphonique, contre l'état mémorisé.
  Trois défauts qu'il fermera, aujourd'hui écrits et non corrigés :
  - le processus démarré après une panne de stockage ne voit pas l'appel (relecture du 2026-11-01, D2). Et si la
    base se répare, il y réécrit son état, parti de rien : les appels qu'elle gardait et qu'il n'a jamais lus sont
    effacés (au signal suivant, et depuis P-10 dès le retour à l'accueil ; relecture du 2026-11-03, n° 1) ;
  - une fin d'appel manquée n'est bornée que par le plafond de 8 h. **Mesuré le 2026-11-02 (émulateur
    Android 16)** : un appareil éteint en plein appel ne reçoit aucun état d'appel au redémarrage. L'appel reste
    « en cours » dans la mémoire, et une demande bancaire de l'utilisateur lui-même, sans appel, a montré un
    STOP. Avec le capteur des notifications, c'est **un faux STOP réel** : une batterie vide en plein appel
    suffit. **Corrigé pour le redémarrage le 2026-11-02 (D-020)** : un appel ne survit pas à un redémarrage.
    Restent les fins manquées sans redémarrage (processus arrêté de force, diffusion perdue) et, à vérifier, un
    « redémarrage léger » d'Android (le système seul) : toujours bornées par le plafond de 8 h ;
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
  dit les deux derniers. **Les accès sont faits le 2026-11-02 (D-022)** : l'accueil dit chacun, relu à chaque
  retour au premier plan, donc aussi une permission retirée faute d'usage ; l'hibernation épargne une appli dont
  l'écouteur est actif (§ 6) ; une appli jamais ouverte n'a pas pu être activée. **La panne du stockage est faite le
  2026-11-03** (D3) : tant que le magasin échoue (`RoomDetectorStore.failing`), l'accueil dit « Protection affaiblie »
  (Patrice), la cause probable (le téléphone plein) et un bouton vers la page de stockage du téléphone (sur le S24 FE,
  la « Gestion du stockage » de Samsung, mesuré). Seulement une fois les accès tous accordés : un problème à la fois,
  comme l'activation. À chaque retour au premier plan, le magasin réessaie (`check`) : il réécrit l'état qu'il n'a pas
  pu garder, sinon celui qu'il vient de lire — une écriture, pas une lecture seule, qu'un stockage passé en lecture
  seule laisserait passer (relecture du 2026-11-03) ; l'ouverture de la base en fait partie (une migration manquante
  échoue là). Le message disparaît dès que l'écriture repasse, sans attendre un signal. **Limite** : un téléphone plein
  n'est pas toujours vu, réécrire les mêmes lignes peut ne demander aucune place nouvelle ; l'accueil dit alors
  « activée » jusqu'au premier signal dont l'écriture échoue. Mesuré sur le S24 FE (la base remplacée par un dossier, puis rendue). **Reste** : prévenir sans que
  l'utilisateur ouvre l'appli (fiche, § 9.2, idée 5 : le proche).
- **P-11** — Élaguer le journal (durée de conservation) ; dédoublonner l'avertissement « appli de prise en
  main installée » hors appel, qu'une diffusion répétée ferait écrire plusieurs fois (D8). **Atteignable depuis
  le capteur des notifications** (relecture de sécurité du 2026-11-02, faible) : une appli qui imite le canal
  `securpass_id` pendant un appel ajoute deux lignes par STOP auquel on répond, sans fin (le débit est borné par
  Android, quelques notifications par seconde). À poser avant publication : une durée ou un nombre de lignes, dans
  la même transaction ; la politique de confidentialité dira combien de temps le journal est gardé.
- ~~**P-12** — **Contrat des capteurs : une file unique, dans l'ordre de réception.**~~ **Fait le 2026-11-02** :
  `platform/SensorQueue`, un seul consommateur, premier arrivé premier traité (`SensorQueueTest`). Le
  `Mutex` du magasin sérialisait les signaux sans les garder dans l'ordre : une action estampillée une
  milliseconde avant le début d'un nouvel appel, et traitée après lui, perdait la fenêtre de l'appel
  précédent (GPT, 4).
- **P-13** — **Horloge monotone.** L'heure civile peut reculer, par la main de l'utilisateur ou par une
  correction du réseau. Un appel en cours « disparaît » alors (GPT, 5), et un nouvel appel estampillé avant
  la fin du précédent est pris pour une répétition (règle de GPT, 6). Les capteurs fourniront aussi un temps
  monotone depuis le démarrage (`elapsedRealtime`) ; un redémarrage termine de toute façon les appels (D-020).
  **Le redémarrage lui-même en dépend** (relectures du 2026-11-02) : son heure se calcule avec l'horloge du
  moment, et une horloge reculée pendant l'extinction laisse l'appel fantôme ouvert. Avec ce point : porter le
  numéro de démarrage avec chaque appel.
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
- ~~**P-19** — **Repli quand l'écran STOP ne passe pas**~~ **Fait le 2026-11-03 : D-024** (la notification qui
  l'ouvre, plein écran sur un téléphone verrouillé ou endormi ; la cinquième étape d'activation). Mesuré sur
  l'émulateur Android 16 et sur le S24 FE (écran allumé, et à l'oreille). **Reste à mesurer** : un Xiaomi (P-21). Un STOP que ni l'écran ni la notification n'atteignent (notifications refusées) reste journalisé
  « non affiché », son déclencheur réarmé.
- ~~**P-20**~~ **Tranché le 2026-11-02 par Patrice : voie (a), D-021.** — **Reconnaître un SMS de code en France** (mesure du 2026-11-02, § 6) : le masquage d'Android 15+ ne voit
  pas les codes français (détecteur de Google, émulateurs Android 16 et 17 réglés en français), et **Samsung ne
  masque rien** (S24 FE, mesuré le 2026-11-02 : codes français et anglais reçus en entier). Il faut donc lire le
  texte, et la promesse de confidentialité change : **décision de Patrice**. Trois voies :
  - **(a) la notification de l'appli SMS par défaut**, par l'accès déjà demandé : aucune permission de plus. Le texte
    est lu en mémoire pour cette seule appli, comparé à des formes de code, puis oublié ; un message masqué par
    Android compte lui-même comme un code. Limites : une appli SMS qui cache le contenu de ses notifications, ou ne
    notifie pas (conversation ouverte) ;
  - **(b) la réception des SMS** (`RECEIVE_SMS`) : tout SMS, quelle que soit l'appli, même sans notification ; une
    permission de plus, restreinte, et lourde à faire accepter (targetSdk 36 obligatoire, D-008) ;
  - **(c) les deux**, (a) d'abord.

  **Recommandation de Claude : (a)**. Il n'ajoute aucune permission, et ne lit que ce qu'il faut. La règle des
  formes de code passera par l'agent des faux positifs (« code promo », « code postal », « code d'accès à
  l'immeuble » ne sont pas des codes de validation).

  **Piste trouvée en mesurant** (S24 FE, 2026-11-02) : la banque de Patrice ne lui envoie aucun code par SMS (elle
  valide par Sécur'Pass), mais **confirme par SMS** l'opération faite (« Ajout d'un bénéficiaire … Si vous n'avez pas
  initié cette opération, contactez votre agence »), ici 20 s après la fin de l'appel. Pendant un appel ou juste
  après, une telle alerte est un signal de plus, et le bon moment pour dire « appelez vous-même votre banque ».
  Même lecture du texte que (a) : à trancher avec elle.
- **P-21** — **« En marche » sur la foi des accès seuls** (relectures du 2026-11-02, D-022). L'accueil dit la
  protection activée quand les accès sont accordés ; trois cas la feraient mentir, **à mesurer avant
  publication** :
  - **Xiaomi (MIUI, HyperOS)** : lancer une activité depuis l'arrière-plan demande, en plus de l'affichage par-dessus,
    l'autorisation propre « afficher des fenêtres pop-up en arrière-plan », fermée pour une appli installée par fichier.
    Le STOP serait bloqué ; depuis D-024, la notification qui l'ouvre prend le relais — si MIUI ne la retient pas
    elle aussi (à mesurer : aucun Xiaomi ici) ;
  - **un écouteur accordé mais pas relié** (arrêt forcé, « applis en veille » de Samsung, appli de nettoyage) : l'accès
    se lit accordé, aucune notification n'arrive. Piste : l'écouteur note sa connexion (`onListenerConnected` /
    `onListenerDisconnected`), l'accueil la montre, et la redemande au retour au premier plan (`requestRebind`, qui
    d'après les sources d'AOSP ne relie qu'un écouteur délié par `requestUnbind` : à vérifier avant de s'y fier).
    **Mesuré le 2026-11-03** : après un **arrêt forcé**, l'appli est relancée et l'écouteur relié de nouveau en moins
    d'une seconde, sur l'émulateur Android 16 comme sur le S24 FE ; ce cas ne rend pas la protection sourde. **Veille
    ordinaire** (S24 FE, écran éteint 8 min, `Dozing`) : processus vivant, écouteur relié, une demande factice reçue en
    1 s. **🔴 Veille profonde de Samsung** (« Applis en veille profonde », appli ajoutée par Patrice), mesurée le même
    jour : moins de 2 min après, l'écran éteint, Samsung **tue l'appli et délie l'écouteur** ; une demande factice
    réveille l'appli (son récepteur poste la notification), mais l'écouteur ne la voit pas, et l'appli est gelée 2 s
    plus tard (`FreecessController`). **L'accès se lit alors refusé** (`isNotificationListenerAccessGranted` faux,
    interrupteur éteint dans les réglages) alors que le réglage du système (`enabled_notification_listeners`) le garde :
    l'accueil dit « il manque 1 autorisation », il ne ment pas. **Mais le parcours ne répare pas** : l'interrupteur
    rallumé se remet à « éteint », l'utilisateur tourne en rond. **Seule la sortie de la veille profonde répare** :
    dans « Applis jamais mises en veille », écran éteint 2 min, demande factice reçue en 1 s. ✅ **Construit le même
    jour : D-023** — l'étape « active même quand vous ne l'ouvrez pas » met l'appli en « Non restreinte », ce qui la
    sort de la veille profonde et l'écarte des listes de mise en veille de Samsung ; placée avant l'accès aux
    notifications, l'activation répare dans le bon ordre. Le décalage « accès gardé par le système, refusé à l'appli »
    n'a donc pas à être lu (le réglage `enabled_notification_listeners` n'est pas public). **Restent** : la liaison de
    l'écouteur sur l'accueil, pour les cas non mesurés (applis de nettoyage, autres fabricants), et si une appli en
    « Non restreinte » échappe aussi à la mise en veille automatique de Samsung sur plusieurs jours ;
  - **Android Go** : aucun accès à l'affichage par-dessus à partir d'Android 10 (documentation d'Android Go) ; l'accueil
    le dit sans proposer l'activation (`AccessChecker.phoneCanProtect`, `isLowRamDevice`). **Non mesuré** : ni
    téléphone ni image Go ici.

  Et, latent : l'accueil ignore le réglage « protection activée » (`ProtectionSettings.enabled`), que le moteur
  respecte ; rien ne l'écrit aujourd'hui. L'écran des réglages devra le montrer.

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
