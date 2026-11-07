# Relecture de code : le lanceur de l'écran STOP, isolé et testé, dans une appli Android anti-arnaque

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. L'appli interrompt l'utilisateur par un écran STOP
plein écran (une activité). Le lanceur la démarre depuis l'arrière-plan (permis avec « afficher par-dessus les autres
applis »), attend 5 s qu'elle dise être devant, et sinon poste une notification de repli qui l'ouvre ; sans cette
permission, la notification tout de suite ; sans notification possible, l'écran seul décide.

## Le changement (le diff ci-dessous)

Un audit a relevé que cette logique, la plus dense de l'appli, n'avait aucun test : elle dépendait de `Context` et d'une
classe concrète. Le démarrage passe par `ScreenStarter`, la notification par `StopNotice` (que `StopNotifier`, un
`@Singleton`, implémente), liés par Hilt ; le code du lanceur est recopié à l'identique ; 10 tests en temps virtuel.
Contrôle négatif fait : sans le report des déclencheurs et sans le retrait R-02, 2 tests échouent.

## Ne PAS signaler

- Que le `catch` du démarrage n'est pas testé (code Android, comme avant).
- Style, nommage, commentaires, formatage, architecture générale.

## Ce qui est demandé (au plus 4 constats)

Des défauts RÉELS, chacun avec un **scénario d'échec concret** et votre degré de certitude : un comportement changé par le
découpage (ordre des appels, conditions, attente), deux instances du notifier là où une seule doit exister, un test qui
resterait vert sur une implémentation fausse, un test qui dépendrait du temps réel. « Aucun défaut réel » est une réponse
attendue : ne remplissez pas.
