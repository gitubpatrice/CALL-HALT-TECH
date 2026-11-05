# Relecture de code : une appli de prise en main en marche, vue dans ses notifications, d'une appli Android anti-arnaque

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. L'appli interrompt l'utilisateur (« écran
STOP ») quand une action sensible survient pendant un appel téléphonique ou dans les 15 minutes qui suivent. Ici :
une appli qui permet à quelqu'un d'autre de voir ou de contrôler CE téléphone (AnyDesk, RustDesk…) se met en marche
pendant l'appel, ce que le faux conseiller bancaire fait faire à la victime.

## Le capteur (le diff ci-dessous, et les fichiers complets)

- `RemoteControlNotices` (pur, appelé sur le fil principal par le `NotificationListenerService` de l'appli) : chaque
  NOUVELLE notification d'une appli de la liste (49 identifiants) est « l'appli en marche » ; nouvelle = sa clé n'est
  pas parmi celles encore affichées. À la reliaison de l'écouteur, ce qui est affiché est connu, sauf ce qui a été
  posté dans les 2 dernières minutes (l'écouteur était absent). Les résumés de groupe d'Android ne comptent pas.
- `NotificationListener` : branchement (amorçage à la connexion, retrait), et le chemin existant banque et SMS réécrit
  en `bankRequestOrCode`.
- Le moteur (inchangé) : pendant un appel ou juste après → STOP ; hors appel → rien.

## Mesuré (2026-11-05, émulateur Android 16, RustDesk 1.5.0, une vraie session pilotée depuis un second émulateur)

Le service démarré poste sa notification de service au premier plan ; une connexion poste « Acceptez-vous ? » puis
« Partage d'écran Établie » ; à l'ouverture de l'appli, rien. Pendant un appel : STOP au partage démarré, puis à la
demande de connexion ; juste après : STOP ; hors appel : rien ; écouteur coupé puis relié en plein appel : STOP pour ce
qui est venu pendant la coupure, rien pour ce qui avait plus de 2 minutes. Notifications de RustDesk refusées : son
service tourne sans notification, rien n'est vu (limite écrite).

## Ne PAS signaler

- Le choix des notifications plutôt que de l'accès à l'utilisation des applis ; l'absence de STOP pour un service
  déjà en marche au début de l'appel (voulu : AirDroid, TeamViewer Host tournent tout le jour) ; la limite des
  notifications refusées (un autre capteur est prévu).
- Une appli à service permanent relancée pendant un appel qui donne un STOP : écrit, accepté.
- Style, nommage, commentaires, formatage, suggestions d'architecture générales.

## Ce qui est demandé (au plus 6 constats)

Des défauts RÉELS, chacun avec un **scénario d'échec concret** et votre degré de certitude. En priorité : une mise en
marche manquée pendant un appel (forme des notifications, clés, retraits, reliaison, redémarrage du processus) ; un
faux STOP répété ; une régression du chemin banque et SMS (le contrôle de répétition d'un code SMS) ; ce que l'écouteur
lit au-delà de l'appli, de la clé, de l'heure et du drapeau de résumé. « Aucun défaut réel » est une réponse attendue :
ne remplissez pas.
