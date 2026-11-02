# Call Halt Tech — où on en est, et comment reprendre

> **À lire en premier** en reprenant le projet. Écrit le 2026-11-01, à la fin de la première journée.
> Tenu à jour à chaque étape importante : s'il contredit le code, c'est le code qui fait foi, et ce fichier
> est à corriger.

## En une phrase

Une appli Android contre les arnaques au téléphone (faux conseiller bancaire…) : quand une action
dangereuse arrive **pendant un appel ou juste après** (un code reçu par SMS, une validation dans l'appli
de la banque, une appli de prise en main à distance), elle affiche un écran **STOP** : *« Ni votre banque,
ni la police, ni les impôts, ni votre assurance ne vous appelleront jamais pour vous demander cela. Si on vous
dit d'ignorer cet écran, c'est une arnaque. Raccrochez, puis appelez vous-même un numéro que vous connaissez. »*

## Reprendre ici (fin du 2026-11-02)

**Les deux mesures qui décidaient sont passées : le projet continue** (décision 11). Fait le 2026-11-02, neuf
PR, chacune relue par un ou deux agents Claude (`relectures/`) :

1. Les textes de l'écran STOP (PR #7).
2. Le capteur de l'état de l'appel (PR #8) — vrais appels sur le S24 FE, émulateurs Android 11, 16, 17.
3. L'écran STOP réel (PR #9) — **P-1** : pendant un vrai appel, il passe devant l'appli Crédit Coopératif,
   « Raccrocher » coupe l'appel ; écran verrouillé compris (émulateur Android 11).
4. La mesure **P-2** (PR #10) — la demande Sécur'Pass est lisible : canal `securpass_id`, titre qui nomme
   l'opération, `REASON_CLICK` quand on appuie dessus.
5. **Ce que font déjà les banques** (PR #12, `PROJET.md` § 9.6) — certaines applis bancaires détectent l'appel,
   chacune pour elle ; rien de transversal en France ; la méthode ne change pas.
6. **Le capteur des notifications** (PR #13) — une demande de validation bancaire, reconnue à son canal
   (`securpass_id`, D-019), affiche le STOP pendant l'appel ou juste après. Mesuré de bout en bout sur émulateur
   (Android 16 et 17) avec une demande factice, et sur le S9 (Android 10, sans appel). L'écouteur ne lit que
   l'appli et le canal de chaque notification.
7. **Un appel ne survit pas à un redémarrage** (PR #14, D-020) — un téléphone éteint en plein appel gardait
   l'appel « en cours » jusqu'à 8 h, et la demande bancaire de l'utilisateur lui-même montrait un faux STOP.
   Mesuré sur émulateur (Android 16), appel coupé net puis redémarrage à froid.
8. **La mesure des SMS de code** (PR #15, P-20) — sur Android 16 et 17 (détecteur de Google), un code **en
   anglais** est masqué dans la notification de l'appli SMS, **un code en français ne l'est pas**, même téléphone
   réglé en français. Le masquage ne suffit donc pas en France : reconnaître un code demande de lire le texte.
9. **Les mesures sur le S24 FE, avec Patrice** (PR #17) — **de bout en bout, vrai appel, vraie demande
   Sécur'Pass** : STOP 0,12 s après la notification, devant tout ; « Raccrocher » ; journal juste ; ni republication
   ni confirmation sur le canal. **Samsung ne masque aucun code SMS**, ni français ni anglais. La banque de Patrice
   confirme l'opération par SMS, 20 s après l'appel.

**Ce qui attend Patrice : trancher P-20** (`docs/ARCHITECTURE.md` § 7) — lire le texte de la notification de
l'appli SMS (aucune permission de plus ; recommandation de Claude) ou demander la réception des SMS. La promesse
de confidentialité change dans les deux cas. Le SMS de confirmation de la banque, juste après un appel, en est une
piste de plus.

**Prochaine étape** : selon P-20, le capteur des SMS de code ; puis l'écran d'activation (P-3, qui demande les
accès un par un) et l'écran d'état de la protection (P-10).

**Avant toute publication** : P-13 (horloge monotone), P-19 (repli par notification), P-11 (élaguer le journal),
les deux SIM, la relecture des traductions par des natifs, les audits (95/100, D-016).

Trust Code Tech : **pas arrêtée**, Patrice veut en reparler (`J:\applications\trust_code_tech\PROJET.md`).

**Mesurer sur un téléphone** (version de test seulement) : appli `com.filestech.callhalt.debug`, permissions
par adb (`pm grant … READ_PHONE_STATE`, `… ANSWER_PHONE_CALLS`, `appops set … SYSTEM_ALERT_WINDOW allow`) ;
une action sensible injectée : `adb shell am broadcast -a com.filestech.callhalt.debug.SIGNAL --es kind
CODE_MESSAGE -n com.filestech.callhalt.debug/com.filestech.callhalt.platform.debug.DebugSignalReceiver` ;
l'écouteur de mesure : `adb shell cmd notification allow_listener com.filestech.callhalt.debug/com.filestech.callhalt.platform.debug.MeasureNotificationListener`
(et `disallow_listener` après). Le vrai capteur : même commande avec
`com.filestech.callhalt.debug/com.filestech.callhalt.platform.notification.NotificationListener` ; une demande
bancaire factice (après `pm grant … POST_NOTIFICATIONS`) : `adb shell am broadcast -a
com.filestech.callhalt.debug.BANK_REQUEST --ei id 1 -n com.filestech.callhalt.debug/com.filestech.callhalt.platform.debug.StandInBankRequestReceiver`
(le même `id` met la notification à jour). Sur émulateur : `adb emu gsm call|accept|cancel <numéro>`, une fois la SIM
« LOADED ». Le S9 n'a pas de SIM : vrais appels et SMS sur le S24 FE.

## Où en est le projet (2026-11-01, complété le 2026-11-02)

- **Le squelette est posé et vérifié. L'appli ne protège encore personne**, et le dit sur son écran.
- Ce qui existe : le **moteur de détection** (la méthode, testée règle par règle), sa **mémoire** (qui
  survit quand Android arrête l'appli), les **réglages**, l'**écran STOP en démonstration**, le thème du
  portefeuille (damier bleu et rouge), **5 langues**.
- **Premier capteur branché (2026-11-02)** : l'appli sait quand un appel commence et se termine, même
  tuée entre deux appels, par une file unique où passeront tous les capteurs.
- **L'écran STOP réel (2026-11-02)** : lancé depuis l'arrière-plan, devant l'appli bancaire, verrou compris ;
  « Raccrocher » coupe l'appel.
- **Premier capteur d'action sensible (2026-11-02)** : la demande de validation de la banque (Sécur'Pass),
  vue dans sa notification.
- Ce qui manque pour protéger quelqu'un : les autres **capteurs d'actions sensibles** (SMS de code, applis de
  prise en main) et l'**écran d'activation** : aujourd'hui, aucun écran ne demande les accès ; il faut les
  accorder un par un dans les réglages du téléphone (Android 13+ : « Autoriser les paramètres restreints » pour
  l'accès aux notifications d'une appli installée depuis un fichier). C'est la suite.
- Vérifications : 159 tests sur ordinateur, 6 tests sur téléphone (Galaxy S9 sous Android 10, Galaxy S24 FE
  sous Android 16), lint et detekt à zéro, CI verte, CodeQL sans alerte.
- Dépôt **public** : https://github.com/gitubpatrice/CALL-HALT-TECH — branche `main` protégée (la CI doit
  être verte avant toute fusion), analyse des secrets et signalement privé de failles activés.

## Où trouver quoi

| Fichier | Ce qu'il contient |
|---|---|
| `PROJET.md` | le **pourquoi** et les **fonctions** ; § 9 : la revue du 2026-11-01 (corrections, idées retenues et écartées, le nom) |
| `docs/ARCHITECTURE.md` | **comment** c'est construit : modules, décisions D-001 à D-014, ce qu'Android permet (vérifié sur sources officielles, § 6), points à mesurer P-1 à P-14 (§ 7), comment travailler (§ 8) |
| `relectures/` | les trois relectures (deux par GPT, une par Claude) et ce qui a été fait de chaque constat ; `README.md` = l'index et les coûts |
| `SECURITY.md`, `PRIVACY.md` | menace, signalement d'une faille ; ce que l'appli demande et garde (en anglais et en français) |

## Les décisions qui comptent (détail et raisons dans `docs/ARCHITECTURE.md`)

1. **Le nom : Call Halt Tech** (« Halt ! » = « Stop ! » dans les 5 langues). INPI : rien ; domaine libre.
   Le projet s'appelait d'abord « Call Guard Tech », un nom pris plusieurs fois.
2. **La logique de détection vit dans un module qui ne peut pas toucher à Android** : chaque règle est un
   test de quelques millisecondes.
3. **L'écran STOP sera un vrai écran (Activity), jamais une fenêtre par-dessus** : les applis bancaires
   masquent les fenêtres superposées.
4. **targetSdk 36, pas 37** : Android 17 retarde de 3 heures les SMS de code pour les applis qui le ciblent.
5. **Aucune permission sans le composant qui s'en sert**, ajoutée dans la même modification que la
   vérification CI et la politique de confidentialité. Jamais de permission Internet.
6. **Pas de service d'accessibilité** : les banques les détectent et refusent de fonctionner.
7. **Cohabite avec Saracroche** : l'appli ne prend aucun rôle exclusif (filtrage d'appels, téléphone, SMS).
8. **Le proche est prévenu par SMS** (Patrice, 2026-11-01) : quand un STOP s'affiche, quand « Continuer » est
   choisi malgré l'alerte, quand la protection est coupée.
9. **Publication sur F-Droid** (Patrice, 2026-11-01), comme Agenda Tech et SMS Tech : build reproductible,
   fiche fastlane en 5 langues, l'APK servi est le nôtre. La chaîne de build (AGP, Kotlin, Gradle) monte
   avec tout le portefeuille, jamais seule.
   ⛔ **Mais rien n'est publié tant que l'appli n'est pas fiable, solide et sécurisée au mieux, avec un score
   d'au moins 95 sur 100** (Patrice). La grille du score se fixe avant le premier audit de publication :
   aucun constat critique ou élevé ouvert, mesures faites sur de vrais téléphones, relecture externe du
   code qui décide (`docs/ARCHITECTURE.md`, D-016).
10. **Les mots de l'écran STOP** (Patrice, 2026-11-01) : ils disent ce qui vient d'arriver, puis « si on
    vous dit d'ignorer cet écran, c'est une arnaque », nomment qui ne vous appellera jamais pour cela
    (banque, police, impôts, assurance), et préviennent que le numéro ou le nom affiché ne prouve rien.
    Après l'appel, l'écran ne dit plus « vous êtes au téléphone » et ne propose plus de raccrocher ; pour
    une prise en main à distance, il dit d'éteindre le téléphone (D-017).
11. **La mesure décide de la suite** (Patrice, 2026-11-01) : si l'écran STOP ne passe pas par-dessus l'appli
    bancaire (P-1), ou si ses demandes de validation sont illisibles (P-2), **le projet s'arrête** (D-018).

## La prochaine étape : le prototype de mesure

⛔ **Elle décide si le projet continue** (décision 11 ci-dessus) : mesurer P-1 et P-2 avant tout le reste.

Brancher les capteurs un par un, chacun avec sa permission, sa vérification et son test, dans cet ordre :

1. ✅ **L'état de l'appel** (début, fin) — récepteur `PHONE_STATE`, permission « téléphone » ; **fait le
   2026-11-02**, mesuré sur émulateurs (Android 11, 16, 17) et **sur le S24 FE avec de vrais appels** (sortant,
   reçu décroché, reçu refusé). Le numéro (`READ_CALL_LOG`) viendra avec le rappel d'appel long.
2. ✅ **L'écran STOP réel** et l'exécution des décisions — la mesure **P-1** : il s'affiche par-dessus l'appli
   bancaire, **passée le 2026-11-02 sur le S24 FE** (Android 16) et sur l'émulateur Android 11, écran
   verrouillé compris. Reste Android 10 lui-même, sur un téléphone avec SIM.
3. **Les SMS de code** — réception des SMS, et le tri des messages (mots-clés, à faire relire par l'agent
   qui cherche les faux positifs).
4. **Les notifications de la banque** — mesure **P-2** : ✅ **lisibles, mesuré le 2026-11-02 sur le S24 FE**
   (Sécur'Pass, canal `securpass_id`). ✅ **Capteur branché le 2026-11-02** (PR #13). Restent la vraie demande
   pendant un vrai appel sur le S24 FE, puis d'autres banques.
5. **Le parcours d'activation** (« paramètres restreints » d'Android 13+) — mesure **P-3**.

## Ce qui attend Patrice

- ~~Renommer le dossier~~ — **fait par Patrice le 2026-11-01** : `J:\applications\call_halt_tech`.
- **Faire relire les phrases de sécurité** allemandes, italiennes et espagnoles par quelqu'un dont c'est la
  langue : une consigne mal traduite ferait faire l'inverse.
- ~~Trancher SMS au proche, F-Droid, Dependabot~~ — **tranché le 2026-11-01** : SMS au proche oui, F-Droid
  oui ; DataStore 1.3.0 fusionné, AGP 9.5.0 reporté à la prochaine montée de tout le portefeuille.
- Facultatif : la recherche de marque sur TMview (Union européenne) ; la calibration de GPT Luna
  (moins de 0,10 $) pour savoir s'il trouve autant que Sol.

## Pièges à connaître

- Les **tests sur téléphone désinstallent l'appli** à la fin : la réinstaller pour la voir.
- Toujours **viser un seul téléphone** pour ces tests (`ANDROID_SERIAL=<numéro>`, donné par `adb devices`).
- **Jamais `git add -A`** : nommer les fichiers. Message de commit dans un fichier (`git commit -F`).
- Un **nom proposé n'est pas un nom choisi** : rien ne se renomme sans un « on prend X » explicite.
- Relectures : un agent Claude d'abord (gratuit), puis **une** relecture GPT (`gpt-5.6-luna` par défaut,
  `gpt-5.6-sol` pour un code critique). Chaque relecture va dans `relectures/` avec son coût et son tri.
