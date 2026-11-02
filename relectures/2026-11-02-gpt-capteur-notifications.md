# Relecture du code par GPT — capteur des notifications — 2026-11-02

> Modèle **gpt-5.6-luna** (Patrice : « gpt 5.6 luna le moins cher »), effort high, **0,017 $** (13 707 tokens
> d'entrée, 12 118 de sortie dont 10 876 de raisonnement) ; 14 fichiers complets (~60 Ko de consigne + code),
> commit `2b5bd0d`. Consigne : `consignes/2026-11-02-gpt-capteur-notifications.md`. Première relecture par Luna
> sur ce projet. Rapport brut ci-dessous, après le tri.

## Tri (chaque constat vérifié dans le code)

| N° | Verdict | Suite |
|---|---|---|
| 1 | réel, mais une fenêtre de quelques millisecondes : la demande reçue puis perdue si le processus meurt avant que le moteur l'ait validée (la file est en mémoire) ; le processus est alors au rang de service lié (`BFGS`, mesuré) | **écrit** (`docs/ARCHITECTURE.md`, P-2), avec la parade possible — relire à la connexion les notifications actives et récentes — et sa raison d'attendre : elle rejouerait une demande déjà traitée, donc un STOP de trop |
| 2 | faux pour l'essentiel : deux demandes rapprochées pendant un STOP sans réponse n'en font qu'un, **par la règle voulue du moteur** — l'écran est devant l'utilisateur. Sa variante « un STOP du premier appel absorbe une demande du second » est **fausse** : `isSameBurst` exige un écran montré pendant CET appel (`DetectionEngine.kt:104`) | rien |
| 3 | faux : `readPermission` / `writePermission` d'un `provider` disent qui peut le lire ; le système n'y lie rien, ce n'est pas un accès spécial | rien |
| 4 | vrai pour la version de TEST seulement : le paquet d'une demande hors appel va au journal système (`Timber.d`). La version publiée ne plante aucun arbre de journal (`CallHaltApplication.kt:14`) ; la version de test n'est pas distribuée, et ce journal est l'outil de mesure | rien |
| 5 | vrai mais sans défaut derrière : le composant Android n'a pas de test automatique, il est mesuré à la main (Android 10, 16, 17). Le scénario donné (une lecture du texte ajoutée par erreur) ne serait pas non plus vu par un test sur téléphone ; c'est la relecture qui le garde | rien |

**Bilan** : aucun défaut à corriger, une limite écrite. Les relectures Claude du même commit ont été menées en
même temps, sans que l'une voie l'autre : elles ont trouvé le trou de la garde et la KDoc trop large, que Luna a
manqués (`2026-11-02-claude-capteur-notifications.md`). Luna a vu, seule, la demande perdue à la mort du processus.

---

## Constats

### 1. Perte d'une demande après la mort du processus — **certitude élevée**

`SensorQueue` est entièrement en mémoire. Une notification déjà reçue par le listener peut donc disparaître sans être traitée.

Scénario :

1. un appel est en cours ;
2. `onNotificationPosted()` reçoit une notification sur `securpass_id` ;
3. `queue.submit(...)` réussit, mais la tâche est encore dans le `Channel` — ou le moteur est en train de l'exécuter ;
4. Android tue le processus ;
5. le système recrée et relie le `NotificationListenerService` ;
6. `onListenerConnected()` ne fait qu'écrire un log. Le service n'appelle pas `getActiveNotifications()` et ne reconstitue aucune notification déjà active.

Résultat : la notification reste éventuellement visible, mais aucun `SensitiveAction` n'est envoyé au moteur et aucun STOP n'est affiché.

Le fait que le système relance effectivement le listener en une seconde ne garantit donc pas la conservation de l'événement. Le test décrit le redémarrage du service, pas la conservation d'une notification en attente.

---

### 2. La déduplication confond deux demandes différentes — **certitude élevée**

Le moteur déduplique par `SensitiveActionKind` uniquement :

```kotlin
pendingStops[signal.kind]
```

Ni l'identifiant de notification, ni son `source`, ni une autre identité de demande ne sont conservés.

Scénario :

1. appel en cours ;
2. notification A sur `securpass_id` à `t=30` : STOP affiché ;
3. notification B, correspondant à une autre opération bancaire, à `t=31` ;
4. le STOP A n'a pas encore reçu de réponse.

`isSameBurst()` retourne vrai pour B. La deuxième demande ne produit donc ni nouveau STOP ni nouvelle entrée de journal.

Le problème existe même avec deux applications différentes qui imitent le canal, ou avec deux opérations distinctes de la même banque. Il est encore plus visible avec deux appels rapprochés : un STOP non répondu pendant le premier appel peut absorber une vraie demande au début du second appel.

Le test :

```kotlin
post(at(30))
post(at(31))
```

prouve seulement que deux signaux de même type sont dédupliqués ; il ne prouve pas qu'il s'agit de la même notification ou de la même demande.

---

### 3. La garde CI ne contrôle pas toutes les protections `BIND_*` d'un composant — **certitude élevée sur le trou de parsing**

`bindings()` ne lit que l'attribut :

```python
android:permission
```

sur les composants. Il ignore notamment `android:readPermission` et `android:writePermission` d'un `provider`.

Scénario de régression introduite par une dépendance :

```xml
<provider
    android:name=".SomeProvider"
    android:exported="true"
    android:readPermission="android.permission.BIND_SOME_SERVICE" />
```

Cette protection n'apparaît :

- ni dans `uses-permission` ;
- ni dans `bound`, puisque le script ne lit pas `readPermission`.

Le script peut donc afficher `OK`, alors que le manifeste publié contient un composant protégé par une permission `BIND_*` non revue.

Cela ne correspond pas à un accès spécial Android classique comme le notification listener — qui est bien porté par `android:permission` sur un `service` — mais cela viole la règle annoncée par le script : contrôler exactement les composants protégés par une permission `BIND_*`.

---

### 4. La promesse de non-conservation hors appel n'est pas vraie pour les builds debug — **certitude élevée, impact release faible**

Pour toute notification reconnue, le listener écrit :

```kotlin
Timber.d("Bank request from %s", sbn.packageName)
```

Dans un build debug, le `DebugTree` est installé. Une demande publiée hors appel produit donc une entrée dans le buffer système de logs, alors que `PRIVACY.md` indique :

> Une demande hors appel [...] n'est pas gardée.

Scénario :

1. aucun appel n'est en cours ;
2. une application publie une notification sur `securpass_id` ;
3. le listener écrit le nom du paquet dans logcat ;
4. le journal du détecteur reste vide, mais le nom du paquet est tout de même conservé temporairement hors du stockage privé de l'application.

Le code ne révèle pas le titre ou le texte, et le build release ne plante effectivement pas de `DebugTree`. Ce n'est donc pas une fuite supplémentaire du contenu de notification dans la version release, mais la promesse est trop large telle qu'elle est formulée pour les builds debug/mesure.

---

### 5. Les tests ne couvrent pas le composant Android ni la propriété de confidentialité annoncée — **certitude élevée**

`NotificationSignalTest` teste une fonction pure et le moteur avec des signaux construits directement. Il ne teste pas :

- l'extraction depuis un vrai `StatusBarNotification` ;
- le cycle de vie et la reconnexion du `NotificationListenerService` ;
- la perte de tâches lors de la mort du processus ;
- le comportement de `SensorQueue` ;
- la liste des champs réellement lus ;
- les logs générés par le listener ;
- le manifeste fusionné et sa permission `BIND_NOTIFICATION_LISTENER_SERVICE`.

Scénario : on ajoute par erreur dans `onNotificationPosted()` :

```kotlin
sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)
```

Tous les tests fournis restent verts, alors que `PRIVACY.md` est désormais fausse.

De même, le test de double publication ne distingue pas une mise à jour de la même notification de deux notifications différentes ; il masque donc précisément le défaut de déduplication décrit au point 2.