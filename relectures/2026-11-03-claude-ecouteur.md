# Relecture — l'écouteur relié, et le « Démarrage automatique » des Xiaomi (D-025) — 2026-11-03

> Agent `android-code-quality-deep-dive`, axes correction et sécurité, lecture statique ; 0 $ (sous-agent). Objet :
> `ListenerConnection` (l'écouteur relié, `rebind()`), la vérification de `ProtectionViewModel`, `Miui` (l'app-op 10008
> par réflexion), `Access.AUTOSTART` et les accès applicables, l'accueil, le manifeste (`<queries>` d'un paquet), les
> textes en 5 langues, avant commit. Lui étaient donnés comme acquis : les mesures du Redmi 9C (MIUI refuse de relier
> l'écouteur, démarrage automatique refusé, même l'appli rouverte ; autorisé, relié en moins d'1 s ; l'accès coupé et
> rallumé l'appli au premier plan le relie).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 13, 5 | Sécurité : la page de MIUI passe par `MATCH_SYSTEM_ONLY` et `setPackage` (une appli de même nom ne s'y glisse pas) ; la `<queries>` d'un paquet ne révèle que sa présence ; la réflexion est une fragilité, pas une faille | accord | rien à faire |
| 1 | Élevé : le basculement du composant (`rebind`) n'aurait pas été mesuré, ni l'accès gardé | **déjà mesuré** sur le Redmi (relié 1,3 s après, avant le réglage ; l'accès resté « Autorisé ») | **test sur téléphone ajouté** (émulateur Android 16) : accès gardé, composant revenu, écouteur relié ; contrôle négatif (laissé coupé : rouge) |
| 2 | Moyen : chaque retour au premier plan remettait l'alerte à zéro avant la vérification — 6 s de « Protection activée » sur l'écouteur délié | **réel** | **corrigé** : ce qui a été trouvé reste dit jusqu'à ce que l'écouteur soit relié |
| 4 | Moyen : la vérification continuait l'écran quitté ; MIUI refuse alors la réparation, et l'alerte tombait à tort ; deux écrans pouvaient basculer | **réel** | **corrigé** : arrêtée à la sortie de l'écran (`ON_PAUSE`) ; un seul écran au premier plan à la fois |
| 3 | Moyen : un autre MIUI ou HyperOS qui lirait mal le réglage bloquerait l'accès aux notifications derrière l'étape | **réel** (non mesuré) | **corrigé** : l'étape est passable après un essai, comme la batterie |
| 5 | Faible à moyen : sans `finally`, un échec entre la coupure et le retour laissait l'écouteur coupé | **réel** | **corrigé** : remis en service quoi qu'il arrive ; l'échec de la coupure est attrapé |
| 10, 7 | Moyen : le texte affirmait la cause (« le téléphone a arrêté l'appli ») et une réparation (« coupez puis rallumez ») non mesurée depuis les réglages ; faux « désactivée » passager possible après une mort du processus | réel | **texte adouci** dans les 5 langues : « sans doute », « si ce message reste » ; la consigne depuis les réglages reste à mesurer (D-025) |
| 8 | Moyen : la valeur lue sur MIUI non mesurée | **déjà mesuré** sur le Redmi (refusé → l'étape ; autorisé → « Autorisé ») | HyperOS à mesurer |
| 6 | Faible : une notification arrivée pendant que l'écouteur est délié n'est pas relue à la connexion | réel | **laissé**, écrit dans D-025 : relire d'anciennes notifications ferait des STOP sur des actions passées |
| 9 | Faible : `onListenerDisconnected` peut manquer | à vérifier | sans effet sur l'accueil : un accès retiré se lit manquant ; une mort du processus remet l'état à zéro |
| 11 | Faible : la réflexion lue deux fois par vérification | réel | **laissé** : quelques microsecondes, à chaque retour au premier plan seulement |
| 12 | Faible : pas de test des délais de la vérification | réel | **laissé** : les états sont testés sur la JVM (`ProtectionStateTest`), la réparation sur téléphone ; les délais l'ont été sur le Redmi |

**Bilan** : aucun constat de sécurité ; 4 réels corrigés, 2 déjà mesurés (et 1 test ajouté), 1 texte adouci, 4 faibles
laissés avec leur raison.
