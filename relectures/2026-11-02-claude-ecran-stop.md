# Relectures Claude — l'écran STOP réel (2026-11-02)

> Deux sous-agents Claude, lecture seule, 0 $, sur la branche `feat/stop-activity` avant le premier commit : une
> relecture de sécurité (`android-code-quality-deep-dive`, exigée par le rappel de fin de tour) puis une relecture
> générale (`general-purpose` : justesse, cycle de vie, tests). Ils n'avaient que le code, la doc et la mesure du
> S24, pas mon raisonnement. Condensé fidèle ; « Suite » dit ce qui en a été fait.

## Sécurité — aucun constat critique ni élevé

| N° | Gravité | Constat | Suite |
|---|---|---|---|
| S1 | MOYENNE | Un STOP resté ouvert (ou reconstruit) pourrait raccrocher un autre appel que le sien | **Corrigé** : l'écran suit l'appel (`CallEvents`, `isInCall`) et ne propose plus de raccrocher une fois sa fin vue ; contrôle à l'appui. Limite écrite : P-17 |
| S2 | MOYENNE | PRIVACY : « aucune capture ne peut copier l'écran » promet trop ; la permission de raccrocher sert aussi à « Raccrocher et appeler ma banque » | **Corrigé** dans les deux langues |
| S3 | FAIBLE | Prise en main à distance par accessibilité (P-15) | **Ajouté** : écran réservé aux outils d'accessibilité à partir d'Android 14 ; à mesurer avec AnyDesk |
| S4 | FAIBLE | Affinité de tâche prévisible, tapjacking, écran verrouillé | Rien de plus : Android 10+ durcit le placement entre UID, Android 12+ bloque les touches traversant une superposition ; sur l'écran verrouillé, rien ne s'expose (mesuré) |

Confirmé : le récepteur de test n'existe pas dans la release (manifeste fusionné relu) ; aucune autre appli ne
peut lancer l'écran ni provoquer `endCall`.

## Générale — 2 élevés, 3 moyens, 4 faibles

| N° | Gravité | Constat | Suite |
|---|---|---|---|
| H1 | ÉLEVÉE | Quitté par l'accueil, l'écran laisse son déclencheur « affiché » 60 s : « appuyez sur l'accueil et revalidez » passe sans STOP | **Corrigé** : `StopChoice.LEFT` (moteur), l'écran quitté — pas mis en veille — se ferme et libère ses déclencheurs ; **mesuré sur l'émulateur Android 16** : la nouvelle tentative montre un nouvel écran |
| H2 | ÉLEVÉE | Un STOP remplacé par un autre n'est jamais répondu : son déclencheur absorbe ses répétitions | **Corrigé** : la réponse vaut pour tous les déclencheurs absorbés (`StopSession`) ; test |
| M1 | MOYENNE | Créé téléphone endormi, l'écran n'était confirmé qu'au réveil : journalisé « non affiché » à tort | **Corrigé** : confirmé à sa création (un lancement bloqué ne crée rien) ; **mesuré** : réveil 10 s après, journal « STOP » |
| M2 | MOYENNE | Fins d'appel perdues pendant que l'écran est caché | **Corrigé** : écoutées toute la vie de l'écran |
| M3 | MOYENNE | « Raccrocher » qui échoue fermait l'écran (et ouvrait le clavier) | **Corrigé** : l'écran reste, sans ses boutons d'appel, rien n'est journalisé ; test |
| L1 | FAIBLE | Double appui | **Corrigé** : une seule réponse |
| L2 | FAIBLE | Identifiants repartant à 1 à chaque processus ; état perdu après la mort du processus | **Corrigé** : identifiants sur l'horloge monotone ; session sauvegardée |
| L3 | FAIBLE | Fin d'un appel VoIP non suivie | Écrit : P-18 |
| L4 | FAIBLE | Textes : repli par notification promis par `Ports.kt` et non construit ; D-017 périmée ; renvoi faux à P-14 | **Corrigé** ; repli : P-19 |
| T | — | Logique de l'écran non testable ; trous dans quatre tests | **Corrigé** : logique sortie dans `StopSession` (pure, testée), tests complétés |
