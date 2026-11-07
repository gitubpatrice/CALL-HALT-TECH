# Relecture de code : « Retirer l'accès » sur l'écran STOP, dans une appli Android anti-arnaque

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. L'appli interrompt l'utilisateur (« écran STOP »,
une activité plein écran à part : `singleTask`, `taskAffinity` propre, exclue des récents, affichée par-dessus le
verrou, `FLAG_SECURE`) quand une action sensible survient pendant un appel téléphonique ou dans les 15 minutes qui
suivent. L'un de ces cas : une autre appli vient de recevoir l'accès « accessibilité » (une appli de prise en main
qu'un escroc a fait installer).

## Le changement (le diff ci-dessous, et les fichiers entiers)

- Une réponse nouvelle, `StopChoice.REMOVE_ACCESS`. Sur le STOP d'accessibilité seulement : après l'appel, « Retirer
  l'accès » en premier, puis « Appeler ma banque » ; pendant un appel que l'appli peut couper, « Raccrocher et retirer
  l'accès » remplace « Raccrocher » (l'appel coupé d'abord, comme « Raccrocher et appeler ma banque ») ; pendant un
  appel qu'elle ne peut pas couper (VoIP, permission absente), aucun bouton sauf « Continuer ».
- `StopText` porte une liste ordonnée de boutons ; `StopScreen` dessine le premier plein, les autres en contour.
- `StopAnswers` : `REMOVE_ACCESS` coupe l'appel d'abord pendant un appel.
- `StopActivity` : la page Accessibilité des réglages, servie par une appli du SYSTÈME seulement
  (`AccessSettings.openOwn` : `MATCH_SYSTEM_ONLY` puis `setPackage`), avec `NEW_TASK | CLEAR_TASK` ; le composeur
  (`ACTION_DIAL`) reçoit `NEW_TASK`. Les deux passent par `unlockThen` : téléphone verrouillé,
  `requestDismissKeyguard` d'abord, puis ouverture et `finish()` (réussite, annulation ou erreur).
- `StopSession.opening` : la réponse dont la page attend le déverrouillage, gardée dans la sauvegarde de l'écran ; une
  instance recréée pendant la demande du code la redemande dans `onResume` (Android ne rappelle pas une activité
  détruite) ; la page s'ouvre une seule fois (`opened`). `StopSession.receiving` : un STOP qui arrive sur un écran déjà
  répondu est un écran à lui, et le rappel n'ouvre rien s'il a pris l'écran.
- Le manifeste déclare `android.settings.ACCESSIBILITY_SETTINGS` dans `<queries>`.

## Mesuré (2026-11-07, émulateurs)

Android 15 : pendant l'appel, le bouton coupe l'appel et ouvre la vraie page des Paramètres ; journal STOP puis
`REMOVE_ACCESS`. Android 16 : après l'appel ; verrouillé par code, le code demandé par-dessus le STOP, puis la page (ou
le composeur) ; code refusé, la page attend derrière le verrou ; pendant l'appel et verrouillé, l'appel coupé avant la
demande du code ; les réglages déjà ouverts sur une autre page, la page Accessibilité quand même. Avant `NEW_TASK`, la
page s'empilait dans la tâche du STOP et revenait après le « Continuer » d'un STOP suivant (corrigé, mesuré). Un SMS de
code pendant la demande du code : son STOP reste, ses boutons répondent. L'écran recréé (mode sombre) pendant la demande
du code : seconde demande, puis la page.

## Ne PAS signaler

- Les choix de produit : les libellés, le conseil, l'ordre des boutons, « Raccrocher » remplacé sur ce seul STOP.
- Qu'Android n'offre pas de page publique pour un seul service (`ACTION_ACCESSIBILITY_DETAILS_SETTINGS` est
  `@SystemApi`) ; qu'une appli malveillante puisse gêner sa propre désactivation (limite écrite).
- Les constats déjà traités par la relecture Claude : un second STOP perdu pendant la demande du code, l'écran recréé
  pendant cette demande (corrigés et mesurés, voir ci-dessus) ; le repli ancien de `AccessSettings.open`, hors de ce
  changement.
- Style, nommage, commentaires, formatage, suggestions d'architecture générales.

## Ce qui est demandé (au plus 6 constats)

Des défauts RÉELS, chacun avec un **scénario d'échec concret** et votre degré de certitude. En priorité : un écran
STOP dont les boutons ne font plus rien, ou plus d'écran du tout ; un appel laissé en cours pendant qu'autre chose
s'ouvre ; une réponse journalisée deux fois, ou jamais ; une page qui ne serait pas celle des réglages du téléphone ;
un plantage (rappel du verrou après `onDestroy`, `finish()` en double) ; une course entre `unlockThen` et `onStop`
(qui journalise « écran quitté » et ferme quand rien n'est répondu), `onNewIntent` (un second STOP pendant la demande
du code) ou une rotation ; un test qui resterait vert sur une implémentation fausse. « Aucun défaut réel » est une
réponse attendue : ne remplissez pas.
