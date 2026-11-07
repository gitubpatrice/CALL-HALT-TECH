# Relecture de code : l'état réel de l'appel, lu en direct (D-035), dans une appli Android anti-arnaque

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. L'appli interrompt l'utilisateur (« écran STOP »)
quand une action sensible survient pendant un appel téléphonique ou dans les 15 minutes qui suivent : un code reçu par
SMS, une demande de validation de la banque, une appli de prise en main, l'accessibilité. Elle connaît les appels par la
diffusion `PHONE_STATE` (récepteur du manifeste) ; un moteur pur (`DetectionEngine`) tient l'état des appels, un appel
par canal, et décide.

## Le défaut corrigé (FN-1, mesuré)

Après un redémarrage, un appel décroché avant le premier déverrouillage n'atteint l'appli par aucune diffusion (aucun
composant `directBootAware` ; Android ne renvoie pas la diffusion ensuite). Une demande de validation bancaire pendant cet
appel ne montrait aucun STOP. L'escroc l'obtient d'une phrase : « éteignez et rallumez, je vous rappelle ».

## Le changement (le diff ci-dessous, et les fichiers entiers)

- Un port `LiveCallSource` ; `ProcessSignalUseCase` l'interroge avant chaque `SensitiveAction`, après le redémarrage
  (`PhoneRestarted`), et traite d'abord le signal rendu ; une lecture qui lève ne fait pas perdre l'action.
- `TelephonyLiveCall` : `TelecomManager.isInCall`, `isInManagedCall`, `TelephonyManager.getCallState`, réduits par
  `liveState` : décroché seulement pour un appel géré par le téléphone sans sonnerie ; aucun appel seulement sans aucun
  appel ; rien sinon (sonnerie, ou un appel « auto-géré » d'une appli seule, WhatsApp). Puis `callSignal` (la même
  fonction que la diffusion) : décroché → `CallStarted` (direction inconnue), aucun → `CallEnded`.
- `CallStateAtStart` : la même lecture à chaque démarrage du processus, par la file unique des capteurs, horodatée au
  démarrage.
- Le moteur : un second début sur un appel en cours garde l'heure de début, et prend la direction si elle était
  inconnue.

## Mesuré (2026-11-07, émulateur Android 16, code PIN)

Redémarrage ; appel reçu et décroché avant le déverrouillage ; déverrouillage : l'appli démarre, la lecture au démarrage
enregistre l'appel ; une demande de validation bancaire → STOP « Vous êtes au téléphone », « Raccrocher » coupe l'appel.
Sans action pendant cet appel : sa fin ouvre la fenêtre, un code reçu ensuite → STOP « Votre appel vient de se
terminer ». Contrôle négatif : sans la lecture avant l'action, 4 tests échouent.

## Ne PAS signaler (déjà écrit comme limite dans D-035, ou hors de ce changement)

- Une action horodatée juste avant un appel décroché pendant qu'elle attendait est vue pendant l'appel (côté protecteur).
- Un second appel qui sonne au moment de l'action masque un appel inconnu du moteur ; un appel décroché ET terminé avant
  le premier déverrouillage n'ouvre aucune fenêtre ; au-delà de 8 h, un téléphone toujours en appel recommence un appel ;
  un « pas d'appel » que Telecom rend sur une panne de son propre service.
- Que la diffusion compte ou non les appels auto-gérés (à mesurer, P-4) : la lecture ne les dit justement dans aucun sens.
- `getCallState` déprécié (il répond toujours, avec `READ_PHONE_STATE`) ; rendre l'appli `directBootAware` (écarté).
- Style, nommage, commentaires, formatage, architecture générale.

## Ce qui est demandé (au plus 6 constats)

Des défauts RÉELS, chacun avec un **scénario d'échec concret** et votre degré de certitude. En priorité : un faux STOP
que ce changement crée (un appel inventé, ou une fin d'appel inventée qui ouvre une fenêtre), un vrai STOP qu'il fait
manquer, un état d'appel corrompu (début déplacé, appel terminé à tort), une course entre la lecture et une diffusion
dans la file, un cas où `isInCall`, `isInManagedCall` et `getCallState` se contredisent et où `liveState` choisit mal,
une exception qui fait perdre l'action, un test qui resterait vert sur une implémentation fausse. « Aucun défaut réel » est
une réponse attendue : ne remplissez pas.
