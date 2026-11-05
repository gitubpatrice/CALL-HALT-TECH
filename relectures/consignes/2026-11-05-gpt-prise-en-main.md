# Relecture de code : le capteur des installations d'applis de prise en main d'une appli Android anti-arnaque

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. L'appli interrompt l'utilisateur (« écran
STOP ») quand une action sensible survient pendant un appel téléphonique ou dans les 15 minutes qui suivent. Ici :
l'installation d'une appli qui permet à quelqu'un d'autre de voir ou de contrôler CE téléphone (AnyDesk, TeamViewer
QuickSupport, RustDesk…), que le faux conseiller bancaire fait installer à la victime.

## Le capteur (le diff ci-dessous, et les fichiers complets)

- `InstallSensor` : un récepteur `ACTION_PACKAGE_ADDED` enregistré à l'exécution par l'application (aucun récepteur
  du manifeste ne le reçoit depuis Android 8), `RECEIVER_NOT_EXPORTED`, pour la vie du processus — que l'écouteur de
  notifications de l'appli, lié par le système, tient en vie. Il passe par une file unique (`SensorQueue`), puis le
  moteur.
- `RemoteControlApps` : 49 identifiants d'applis (vérifiés un à un sur Google Play), avec le nom que l'avertissement
  leur donne ; `installSignal` (pur) : une première installation d'un identifiant de la liste, pas une mise à jour
  (`EXTRA_REPLACING`). Les mêmes identifiants dans les `<queries>` du manifeste : l'appli ne voit qu'eux (pas de
  `QUERY_ALL_PACKAGES`, par choix).
- Le moteur (inchangé sauf l'appli portée par l'avertissement) : pendant un appel ou juste après → STOP ; hors appel
  → `Decision.ShowWarning(raison, appli)` et une ligne de journal.
- `WarningNotifier` : la notification hors appel, qui nomme l'appli (notre nom, jamais celui qu'elle se donne) et
  ouvre sa page dans les Réglages DU SYSTÈME (`MATCH_SYSTEM_ONLY`). `AndroidDecisionExecutor` : seul un STOP non
  affiché lève (le moteur le journalise « non affiché ») ; un avertissement qui échoue ne lève rien.

## Mesuré (2026-11-05, émulateurs Android 11 et 16)

RustDesk installé par adb pendant un appel → STOP en 0,5 s ; juste après → STOP « l'appel vient de se terminer » ;
mise à jour → rien ; appli hors liste → rien ; hors appel → la notification, un toucher ouvre la page de RustDesk ;
notifications refusées → rien d'affiché, rien de cassé, la ligne de journal gardée.

## Ne PAS signaler

- Le choix d'une liste plutôt que `QUERY_ALL_PACKAGES`, ni l'absence de rattrapage des installations faites processus
  mort (limite écrite : sans l'écouteur, l'accueil dit déjà « Protection désactivée »).
- Une seconde appli de la liste installée dans la minute d'un STOP sans réponse, absorbée : voulu, écrit.
- Les faux positifs de la liste elle-même : un autre relecteur s'en charge.
- Style, nommage, commentaires, formatage, suggestions d'architecture générales.

## Ce qui est demandé (au plus 6 constats)

Des défauts RÉELS, chacun avec un **scénario d'échec concret** et votre degré de certitude. En priorité : une
installation manquée (forme de la diffusion, installateur par session, Play Store, archive restaurée, profil de
travail, Android 10 sans visibilité des paquets) ; un STOP ou un avertissement forgeable par une autre appli ; une
notification ou une page ouverte qui pourrait servir l'escroc ; le contrat de l'exécuteur (un STOP affiché journalisé
« non affiché »). « Aucun défaut réel » est une réponse attendue : ne remplissez pas.
