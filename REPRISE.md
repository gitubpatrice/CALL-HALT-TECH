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

## Reprendre ici (2026-11-02)

1. Textes de l'écran STOP (D-017) : **fusionnés** (PR #7).
2. **Prototype de mesure, étapes 1 et 2 faites** : le capteur de l'état de l'appel (PR #8), puis l'écran STOP
   réel (branche `feat/stop-activity`). **P-1 passée sur le S24 FE** (Android 16) : pendant un vrai appel,
   le STOP passe devant l'appli Crédit Coopératif, et « Raccrocher » coupe l'appel. Confirmée sur l'émulateur
   Android 11, écran verrouillé par PIN compris. **P-2 passée aussi** : la demande Sécur'Pass du Crédit
   Coopératif est lisible (canal `securpass_id`). **Les deux mesures qui décidaient sont passées : le projet
   continue** (décision 11). Suite : les capteurs d'actions sensibles — les notifications de la banque
   (étape 4, conçue d'après P-2), les SMS de code (étape 3) — puis l'activation (étape 5, P-3). Le S9 n'a pas
   de SIM : vrais appels et SMS sur le S24 FE.
3. Trust Code Tech : **pas arrêtée**, Patrice veut en reparler (`J:\applications\trust_code_tech\PROJET.md`).

## Où en est le projet (2026-11-01, complété le 2026-11-02)

- **Le squelette est posé et vérifié. L'appli ne protège encore personne**, et le dit sur son écran.
- Ce qui existe : le **moteur de détection** (la méthode, testée règle par règle), sa **mémoire** (qui
  survit quand Android arrête l'appli), les **réglages**, l'**écran STOP en démonstration**, le thème du
  portefeuille (damier bleu et rouge), **5 langues**.
- **Premier capteur branché (2026-11-02)** : l'appli sait quand un appel commence et se termine, même
  tuée entre deux appels, par une file unique où passeront tous les capteurs.
- Ce qui manque : les autres **capteurs** (lire un SMS de code, voir la notification de la banque…) et
  l'**écran STOP réel**. C'est la suite.
- Vérifications : 83 tests sur ordinateur, 6 tests sur téléphone (Galaxy S9 sous Android 10, Galaxy S24 FE
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
   (Sécur'Pass, canal `securpass_id`). Reste à construire le vrai capteur, puis à mesurer d'autres banques.
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
