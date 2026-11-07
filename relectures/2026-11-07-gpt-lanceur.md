# Relecture GPT — le lanceur de l'écran STOP, isolé et testé (audit C2) — 2026-11-07

> `gpt-5.6-luna`, effort `high` ; consigne : `consignes/2026-11-07-gpt-lanceur.md`. Envoyé : le diff du code (`app/src`,
> fichiers neufs compris), puis `StopDisplay.kt` ; 22 979 caractères. 5 636 jetons d'entrée, 7 911 de sortie :
> **0,011 $**. Après la relecture Claude.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Sans affichage par-dessus et sans notification, le lanceur rendrait `false` tout de suite, sans attendre l'écran | **faux** : la troisième branche (`!overlay && started && display.awaitShown(...)`, omise dans la citation du rapport) attend ; le test qui le dit passe | rien |
| 2 | Le test « ni l'écran ni la notification » resterait vert pour un lanceur qui ne tente rien | **réel** | **corrigé** : il vérifie aussi que l'écran et la notification ont été tentés |

## Le rapport, tel que rendu

## Constats

### 1. Le cas « notification impossible, l’écran décide seul » ne fonctionne pas — certitude très élevée

Avec le code actuel :

```kotlin
val started = starter.start(request)
val overlay = starter.mayStartFromBackground()

if (overlay && started && display.awaitShown(...)) return true
...
return notified(request)
```

Si `overlay == false`, aucun `awaitShown` n’est effectué. Si `notice.post()` échoue, `show()` retourne donc immédiatement `false`, même si l’activité a bien été acceptée par Android et signale `shown()` deux secondes plus tard.

Scénario concret :

1. `start()` retourne `true`.
2. `mayStartFromBackground()` retourne `false`.
3. `post()` retourne `false`.
4. `StopActivity` est finalement créée et appelle `display.shown(id)` après 2 s.
5. Le lanceur a déjà retourné `false`; l’action est donc journalisée comme non affichée.

Le test `with no notification either, the screen alone decides, waited for` expose directement ce défaut : avec l’implémentation fournie, il échoue, car `show(stop)` retourne `false` à t=0. Ce comportement existait déjà dans le corps du lanceur avant le découpage ; ce n’est donc pas une régression introduite par l’extraction, mais le code fourni ne respecte pas la règle annoncée.

### 2. Un test accepte une implémentation qui ne tente rien — certitude élevée

Le test :

```kotlin
fun `with neither the screen nor a notification, the STOP is not shown`()
```

ne vérifie que la valeur `false`. Une implémentation erronée telle que :

```kotlin
override suspend fun show(stop: Decision.ShowStop): Boolean = false
```

resterait verte pour ce test, alors qu’elle ne demanderait ni le démarrage de l’écran ni la notification.

Le test devrait au minimum vérifier les interactions attendues — ou être combiné à des assertions sur `starter.asked` et `notice.posted` — afin de distinguer « les deux mécanismes ont échoué » de « aucun mécanisme n’a été appelé ».

## Points contrôlés, sans défaut identifié

- **Comportement changé par le découpage :** je n’en vois pas dans la séquence réelle. L’ordre reste `start`, puis vérification de l’overlay, puis attente éventuelle et notification ; les conditions sont recopiées à l’identique.
- **Deux instances de `StopNotifier` :** non. `StopNotifier` est `@Singleton`, et le `@Binds StopNotice` délègue au même binding Hilt ; l’injection de `StopNotice` et de `StopNotifier` doit donc fournir la même instance dans le `SingletonComponent`.
- **Dépendance au temps réel des tests :** non identifiée. `delay` et `withTimeoutOrNull` utilisent le scheduler virtuel de `runTest`; `System.nanoTime()` sert seulement à initialiser un identifiant, dont la valeur absolue n’est pas vérifiée.
