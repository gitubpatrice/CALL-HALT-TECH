# Relecture de code : un service d'accessibilité activé pendant un appel, dans une appli Android anti-arnaque

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. L'appli interrompt l'utilisateur (« écran
STOP ») quand une action sensible survient pendant un appel téléphonique ou dans les 15 minutes qui suivent. Ici : une
appli reçoit l'accès « accessibilité » (voir l'écran, agir à la place de l'utilisateur), ce que le faux conseiller fait
faire pour prendre le téléphone en main.

## Le capteur (le diff ci-dessous)

- `AccessibilityGrants` (pur) : le réglage `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` lu en services (entrées
  `pkg/classe` séparées par `:`, formes courte et longue ramenées à la longue) ; un service qui apparaît, d'une autre
  appli que celle-ci et pas d'une fonction du téléphone, est « une appli qui reçoit l'accès ».
- Fonction du téléphone : un paquet d'une liste fermée (TalkBack, menu d'accessibilité, suites Samsung…) ET préinstallé
  (`FLAG_SYSTEM`, vu par des `<queries>`). Pas `isAccessibilityTool`, que l'appli déclare elle-même.
- `AccessibilitySensor` : un `ContentObserver` sur ce réglage, posé par l'application pour la vie du processus ; la base
  est le réglage au démarrage ; illisible, rien n'est décidé, et le premier changement est seulement lu.
- Le moteur : pendant un appel ou juste après → STOP ; hors appel → rien.

## Mesuré (2026-11-05)

Android 16 bloque lui-même l'activation d'un service d'une appli téléchargée pendant un appel ; juste après → STOP.
Android 15 : RustDesk activé par les Réglages pendant l'appel → STOP ; le menu d'accessibilité du système → rien.

## Ne PAS signaler

- Le choix de ne rien faire hors appel ; le choix de ne pas exempter sur `isAccessibilityTool` ; les faux positifs
  acceptés (un antivirus, « Lien avec Windows » activés juste après un appel) ; l'absence de rattrapage d'un service
  activé pendant que le processus était mort (écrit).
- Style, nommage, commentaires, formatage, suggestions d'architecture générales.

## Ce qui est demandé (au plus 6 constats)

Des défauts RÉELS, chacun avec un **scénario d'échec concret** et votre degré de certitude. En priorité : un service
activé pendant un appel et manqué (forme du réglage, écritures multiples, ordre des rappels, utilisateurs) ; une
exemption qu'un escroc pourrait obtenir ; un faux STOP sans service nouveau ; un défaut de fil d'exécution. « Aucun
défaut réel » est une réponse attendue : ne remplissez pas.
