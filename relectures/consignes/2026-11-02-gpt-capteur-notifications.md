# Relecture de code : le capteur des notifications d'une appli Android anti-arnaque

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. On vous soumet le premier capteur
d'« action sensible » d'une appli qui interrompt l'utilisateur (« écran STOP », une Activity plein écran)
quand une action sensible survient pendant un appel téléphonique ou dans les 15 minutes qui suivent. Ici :
la demande de validation d'une opération envoyée par l'appli d'une banque, vue dans sa notification. Une
erreur dans un sens laisse passer l'escroc ; dans l'autre, elle interrompt sans raison, ou elle lit plus que
ce que l'appli promet de lire.

## Architecture (pour lire le code)

- `:domain` (Kotlin pur) : `Signal` (les faits, horodatés à la réception par l'horloge du téléphone),
  `DetectionEngine` (fonction pure `(état, signal, réglages) → (état, journal, décisions)`), `CallWindow`
  (LA définition de « pendant ou juste après un appel »), `ProcessSignalUseCase` (seule entrée des capteurs).
- `:app` : `SensorQueue` (une file unique, premier arrivé premier traité, dans la portée de l'application ;
  aucun capteur n'appelle le moteur directement) ; le capteur d'état d'appel existant (`CallStateReceiver`)
  sert de modèle.
- **Le capteur relu** : `NotificationListener` (un `NotificationListenerService`, lié par le système une fois
  l'accès aux notifications accordé par l'utilisateur) et `notificationSignal` (traduction pure : une
  notification publiée sur le canal `securpass_id`, de n'importe quelle appli, devient une
  `SensitiveAction(BANK_VALIDATION_REQUEST)` dont la source est le nom de paquet).
- `StandInBankRequestReceiver` : version de TEST seulement (`src/debug`), pour mesurer sur émulateur.
- `tools/check-manifest-permissions.py` : garde de la CI sur le manifeste FUSIONNÉ de la version publiée,
  étendue aux « accès spéciaux » (composants protégés par une permission `android.permission.BIND_*`).
- `PRIVACY.md` : la promesse — de chaque notification, l'appli ne lit que l'appli qui l'envoie et son canal.

## Mesuré sur émulateur (Android 16 et 17) et sur un Galaxy S9 (Android 10)

Hors appel : rien. Pendant l'appel : STOP. Juste après : STOP « l'appel vient de se terminer ». Une seule
notification arrive DEUX fois à l'écouteur, à 0,3 s d'écart : le moteur n'en fait qu'un STOP. Processus tué :
le système relance l'écouteur en 1 s.

## Ne PAS signaler (choix délibérés ou limites déjà documentées)

- Reconnaissance par le CANAL, de n'importe quelle appli, sans liste de paquets, en correspondance exacte :
  choix délibéré (une appli qui imiterait le canal ne provoquerait qu'un STOP de trop, du côté protecteur).
- Une notification republiée APRÈS la réponse au STOP montre un nouveau STOP : voulu (côté protecteur).
- L'appui sur la notification (`REASON_CLICK`) n'est pas exploité : décision reportée, écrite.
- Une demande publiée pendant que l'écouteur n'est pas lié n'est pas vue : limite écrite.
- Un appel dont la fin a été manquée (téléphone éteint en plein appel) reste « en cours » jusqu'à 8 h et
  provoque de faux STOP : trouvé, c'est le prochain correctif.
- Horloge civile (pas monotone), pas de second appel en VoIP, pas d'écran d'activation : limites écrites.
- Style, nommage, commentaires, formatage, suggestions d'architecture générales.

## Ce qui est demandé (au plus 8 constats)

Des défauts RÉELS, chacun avec un **scénario d'échec concret** (entrée ou état → mauvais résultat) et votre
degré de certitude. Angles utiles : comportement d'Android d'un `NotificationListenerService` (fil
d'exécution, injection Hilt, cycle de vie, profils et utilisateurs), écart entre la promesse de
`PRIVACY.md` et ce que le code lit ou garde (y compris les journaux), sécurité des composants exportés,
trous de la garde CI (une permission `BIND_*` qu'elle ne verrait pas), tests qui ne prouvent pas ce qu'ils
disent. « Aucun défaut réel » est une réponse attendue et acceptable : ne remplissez pas.
