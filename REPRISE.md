# Call Halt Tech — où on en est, et comment reprendre

> **À lire en premier** en reprenant le projet. Écrit le 2026-11-01, à la fin de la première journée.
> Tenu à jour à chaque étape importante : s'il contredit le code, c'est le code qui fait foi, et ce fichier
> est à corriger.

## En une phrase

Une appli Android contre les arnaques au téléphone (faux conseiller bancaire…) : quand une action
dangereuse arrive **pendant un appel ou juste après** (un code reçu par SMS, une validation dans l'appli
de la banque, une appli de prise en main à distance), elle affiche un écran **STOP** : *« Votre banque ne
vous demandera jamais cela pendant un appel. Raccrochez, puis rappelez-la vous-même. »*

## Où en est le projet (2026-11-01)

- **Le squelette est posé et vérifié. L'appli ne protège encore personne**, et le dit sur son écran.
- Ce qui existe : le **moteur de détection** (la méthode, testée règle par règle), sa **mémoire** (qui
  survit quand Android arrête l'appli), les **réglages**, l'**écran STOP en démonstration**, le thème du
  portefeuille (damier bleu et rouge), **5 langues**.
- Ce qui manque : les **capteurs** (savoir qu'un appel est en cours, lire un SMS de code, voir la
  notification de la banque…) et l'**écran STOP réel**. C'est la prochaine étape.
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

## La prochaine étape : le prototype de mesure

Brancher les capteurs un par un, chacun avec sa permission, sa vérification et son test, dans cet ordre :

1. **L'état de l'appel** (début, fin, numéro) — récepteur `PHONE_STATE`, permission « téléphone ».
2. **L'écran STOP réel** et l'exécution des décisions — c'est la mesure **P-1** : s'affiche-t-il par-dessus
   l'appli bancaire, sur le S9 et le S24 ?
3. **Les SMS de code** — réception des SMS, et le tri des messages (mots-clés, à faire relire par l'agent
   qui cherche les faux positifs).
4. **Les notifications de la banque** — mesure **P-2** : sont-elles lisibles ? D'abord sur le S9 (non rooté).
5. **Le parcours d'activation** (« paramètres restreints » d'Android 13+) — mesure **P-3**.

## Ce qui attend Patrice

- **Renommer le dossier** `J:\applications\call_guard_tech` en `call_halt_tech`, VS Code fermé (impossible
  depuis une session ouverte dedans). La mémoire du projet est déjà copiée sous le nouveau nom.
- **Faire relire les phrases de sécurité** allemandes, italiennes et espagnoles par quelqu'un dont c'est la
  langue : une consigne mal traduite ferait faire l'inverse.
- **Trancher** : SMS au proche (oui, non, en option) ; F-Droid ou GitHub seul ; les mises à jour Dependabot
  n° 1 (DataStore 1.3.0) et n° 3 (AGP 9.5.0), pour tout le portefeuille à la fois.
- Facultatif : la recherche de marque sur TMview (Union européenne) ; la calibration de GPT Luna
  (moins de 0,10 $) pour savoir s'il trouve autant que Sol.

## Pièges à connaître

- Les **tests sur téléphone désinstallent l'appli** à la fin : la réinstaller pour la voir.
- Toujours **viser un seul téléphone** pour ces tests (`ANDROID_SERIAL=<numéro>`, donné par `adb devices`).
- **Jamais `git add -A`** : nommer les fichiers. Message de commit dans un fichier (`git commit -F`).
- Un **nom proposé n'est pas un nom choisi** : rien ne se renomme sans un « on prend X » explicite.
- Relectures : un agent Claude d'abord (gratuit), puis **une** relecture GPT (`gpt-5.6-luna` par défaut,
  `gpt-5.6-sol` pour un code critique). Chaque relecture va dans `relectures/` avec son coût et son tri.
