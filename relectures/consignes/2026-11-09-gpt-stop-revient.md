# Relecture de code : un écran STOP quitté sans réponse revient une fois, puis le proche est prévenu (D-038)

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. L'appli interrompt l'utilisateur (« écran STOP »)
quand une action sensible survient pendant un appel téléphonique ou dans les 15 minutes qui suivent : un code reçu par
SMS, une demande de validation de la banque, une appli de prise en main, l'accessibilité. Un moteur pur
(`DetectionEngine`) tient l'état (appels, STOP en attente) et rend des décisions ; `ProcessSignalUseCase` lit l'état,
appelle le moteur, exécute les décisions, enregistre l'état (Room). Les signaux passent par une file unique.

## Le défaut corrigé (FN-4)

Un STOP quitté sans réponse (bouton Accueil, une autre appli, sa notification balayée) libérait seulement son
déclencheur. L'escroc obtient ce geste d'une phrase (« fermez ce message, c'est un bug ») et l'utilisateur n'était plus
jamais interrompu pour cette action.

## Le changement (le diff ci-dessous, et les fichiers entiers)

- Quitté pendant un appel ou juste après : le moteur garde un retour (`StopReturns.left`, `DetectorState.stopReturns`,
  table `stop_return`, migration additive Room 2 → 3) et demande un réveil 15 s plus tard (`ScheduleTick`).
- Au réveil (`StopReturns.due`) : si l'appel s'est terminé depuis le départ du STOP, rien (la personne est allée
  raccrocher) ; si la fenêtre de l'appel est passée, ou si l'appel n'est plus celui du départ, rien ; sinon le STOP
  revient une fois, par sa notification plein écran (`ShowStop(returning = true)`), et un réveil 2 min plus tard.
- Revenu : quitté, balayé, sans réponse 2 min, ou sa notification refusée (`StopNotShown`) → SMS au proche « l'écran
  STOP a été quitté sans réponse ». Une vraie réponse, un nouveau STOP du même déclencheur, l'arrêt de la protection ou
  un redémarrage annulent le retour. Plus de 15 min de retard : abandonné, ni STOP ni SMS.
- Les réveils (`ScheduleTick`, qu'aucun code n'exécutait avant) : une coroutine dans le processus (`ProcessWakeUps`),
  qui soumet un `Tick` à la file ; un réveil immédiat à chaque démarrage du processus relit les retours en base.
- La notification balayée : `setDeleteIntent` vers un récepteur non exporté (`StopNotificationSwiped`), qui soumet
  `StopAnswered(LEFT)` pour ses déclencheurs.
- Dans `ProcessSignalUseCase`, quand l'affichage d'un STOP lève, les décisions du « non affiché » (hors STOP) sont
  exécutées avant de relayer l'erreur.

## Mesuré (2026-11-07, émulateur Android 16, appel décroché, proche choisi)

STOP quitté par Accueil → notification STOP 16 s après ; balayée → SMS « quitté sans réponse » envoyé ; STOP quitté
puis appel raccroché dans les 5 s → 22 s après, ni notification ni SMS. Une relecture Claude a déjà fait corriger :
la décision jetée sur une notification refusée, un retour qui ressortait le lendemain (processus tué), un appel fini
juste avant le départ pris pour un raccrochage.

## Ne PAS signaler (limites écrites dans D-038, ou hors de ce changement)

- L'attente suit l'horloge monotone (elle s'arrête en veille profonde) ; un processus tué pendant l'attente perd le
  réveil jusqu'au prochain démarrage (borné par les 15 min) ; ne pas utiliser `AlarmManager` (choix fait).
- Que toucher la notification revenue ne doive pas compter comme la quitter (Android n'envoie l'intent de suppression
  que pour un geste de l'utilisateur ; mesure prévue).
- Le rappel d'appel long qui ne part jamais (P-8, autre point) ; l'envoi réel du SMS par l'opérateur.
- Style, nommage, commentaires, formatage, architecture générale.

## Ce qui est demandé (au plus 6 constats)

Des défauts RÉELS, chacun avec un **scénario d'échec concret** (suite de signaux, avec les heures) et votre degré de
certitude. En priorité : un SMS au proche envoyé à tort (la personne a répondu, raccroché, ou le STOP n'a jamais été
montré) ; un retour ou un SMS qui manque alors que le STOP revenu a été ignoré ; un STOP qui revient deux fois, ou
pendant un autre appel ; un état corrompu entre le moteur, la base et la migration (retour qui reste, qui disparaît, ou
qui survit à un redémarrage) ; une course entre le réveil, la file et une réponse ; une exception qui fait perdre une
décision ou enregistre un état faux ; un test qui resterait vert sur une implémentation fausse. « Aucun défaut réel »
est une réponse attendue : ne remplissez pas.
