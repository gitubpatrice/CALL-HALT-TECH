# Relecture de code : l'écran STOP parle du plus grave de ses déclencheurs (N-1), dans une appli Android anti-arnaque

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. L'appli interrompt l'utilisateur (« écran STOP »,
une activité plein écran à part : `singleTask`, exclue des récents, par-dessus le verrou, `FLAG_SECURE`) quand une action
sensible survient pendant un appel ou dans les 15 minutes qui suivent : un code reçu par SMS, une demande de validation
de la banque, une appli de prise en main installée ou en marche, une appli qui reçoit l'accès « accessibilité ». Quand le
téléphone bloque l'écran lancé depuis l'arrière-plan, une notification de repli (une seule à la fois, la suivante prend
sa place) l'ouvre au toucher.

## Le défaut corrigé (audit de publication, N-1)

Un second STOP arrivé avant la réponse prenait l'écran, et le texte, le conseil et les boutons ne suivaient que le
dernier déclencheur. Un SMS de code envoyé exprès par l'escroc pendant le STOP d'accessibilité lui ôtait « Raccrocher et
retirer l'accès » et le conseil « éteignez » ; ce STOP ne revient jamais (seul un service nouveau le déclenche). Même
chose par la notification de repli remplacée avant d'être touchée.

## Le changement (le diff ci-dessous, et les fichiers entiers)

- `Gravest.kt` : `gravity` (accessibilité 2, prise en main 1, le reste 0), `gravest` (le plus grave, le plus récent à
  égalité, dans l'ordre d'arrivée), `inArrivalOrder`.
- `StopSession.shown` : le déclencheur dont l'écran parle ; `triggers` garde l'ordre d'arrivée (aussi à la recréation).
  L'état d'appel reste celui de la requête la plus récente.
- `StopRequest.earlier` : les déclencheurs d'une notification encore affichée, passés à la suivante, à l'écran qu'elle
  ouvre, ou à l'écran que le STOP suivant lance (`StopLauncher` : `notifier.pending()`).
- `StopNotifier` : retient les déclencheurs de la notification affichée (`absorbed`, sous verrou), en parle du plus
  grave, les oublie à `cancel()`.

## Mesuré (2026-11-07, émulateur Android 16, pendant un appel)

Écran ouvert : STOP d'accessibilité, puis un SMS de code → l'écran garde l'accessibilité, « Raccrocher et retirer l'accès »
répond pour les deux et ouvre la page Accessibilité. Affichage par-dessus retiré (écran bloqué) : notification
d'accessibilité, puis celle du code → elle parle toujours de l'accessibilité ; touchée, l'écran d'accessibilité, même
réponse.

## Ne PAS signaler

- Le choix de n'afficher que le plus grave (sans la phrase du moindre) : choisi et écrit (D-017).
- Les mots de l'écran, le rang des déclencheurs.
- Style, nommage, commentaires, formatage, architecture générale.

## Ce qui est demandé (au plus 6 constats)

Des défauts RÉELS, chacun avec un **scénario d'échec concret** et votre degré de certitude. En priorité : un chemin où
l'écran ou la notification perd encore le déclencheur le plus grave ; une course entre `post`, `pending`, `cancel` et le
toucher de la notification (la notification touchée ou balayée, `setAutoCancel`, un écran créé pendant un `post`) ; un
`StopRequest.earlier` qui fait répondre pour un déclencheur jamais montré, ou qui ressuscite un déclencheur déjà répondu ;
un état qui ne survit pas à la recréation de l'écran ; une réponse journalisée deux fois ou jamais ; un test qui resterait
vert sur une implémentation fausse. « Aucun défaut réel » est une réponse attendue : ne remplissez pas.
