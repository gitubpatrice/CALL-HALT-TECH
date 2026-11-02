# Relecture de code : « un appel ne survit pas à un redémarrage », dans une appli Android anti-arnaque

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. L'appli interrompt l'utilisateur (« écran
STOP ») quand une action sensible (demande de validation bancaire, SMS de code) survient pendant un appel
téléphonique ou dans les 15 minutes qui suivent. Une erreur dans un sens laisse passer l'escroc ; dans l'autre,
elle interrompt sans raison — ou, pire, elle termine un appel réellement en cours, et l'escroc passe.

## Le défaut corrigé (mesuré sur émulateur Android 16)

Un téléphone éteint en plein appel ne reçoit aucun état d'appel au démarrage. L'appel restait « en cours »
jusqu'au plafond de 8 h du moteur, et une demande bancaire de l'utilisateur lui-même montrait un faux STOP.

## Le correctif (le diff ci-dessous, et les fichiers complets)

- `PhoneRestarted` (domaine) : les appels encore en cours au redémarrage y finissent ; la fenêtre « juste après
  l'appel » court encore 15 min depuis lui (choix délibéré, côté protecteur) ; un appel déjà fermé par le plafond
  n'est pas ranimé ; les écrans montrés avant lui libèrent leur déclencheur.
- `RestartSource` (port), demandé par `ProcessSignalUseCase` avant CHAQUE signal.
- `BootCountRestarts` : compare `Settings.Global.BOOT_COUNT` au dernier vu (gardé dans le même DataStore que le
  dernier état d'appel), et rapporte une fois l'heure du démarrage (maintenant − `elapsedRealtime`).
- Tous les capteurs passent par une file unique (premier arrivé, premier traité) avant le cas d'usage.

## Ne PAS signaler

- Le choix de poser la fin au redémarrage (et non au début de l'appel) : délibéré, écrit (D-020).
- Les fins d'appel manquées SANS redémarrage (processus arrêté de force) : bornées par le plafond, écrites.
- Horloge civile (pas monotone) en général : limite écrite (P-13) — sauf si elle crée ici un défaut PROPRE au
  correctif.
- Style, nommage, commentaires, formatage, suggestions d'architecture générales.

## Ce qui est demandé (au plus 6 constats)

Des défauts RÉELS du correctif, chacun avec un **scénario d'échec concret** (état ou entrée → mauvais résultat)
et votre degré de certitude. En priorité : un redémarrage rapporté alors qu'il n'a pas eu lieu, ou un appel
réellement en cours terminé ; un redémarrage manqué ou rapporté deux fois ; le comportement quand le traitement
de `PhoneRestarted` échoue. « Aucun défaut réel » est une réponse attendue et acceptable : ne remplissez pas.
