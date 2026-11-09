# Relecture GPT : l'historique des alertes (fonction 8, audit #1, D-041) — 2026-11-09

> `gpt-5.6-luna`, effort `high` ; consigne : `consignes/2026-11-09-gpt-historique.md`. Envoyé : le diff de la branche
> (`origin/main..e388ceb`, `app/src`, `domain/src`, PRIVACY), puis `DetectorMapping.kt`, `StopReturns.kt` et
> `StopAnswers.kt` entiers ; 62 324 caractères. 15 472 jetons d'entrée, 9 781 de sortie : **0,015 $**. Après la
> relecture Claude (`2026-11-09-claude-historique.md`).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Le séparateur `history_then` (`", puis "`) mettrait des guillemets dans le texte | **non retenu, mesuré** : Android retire les guillemets extérieurs d'une ressource (ils ne gardent que les espaces) ; l'écran a affiché « Quitté sans réponse, puis Raccrocher » | le test comparait la ressource à elle-même : **une vérification littérale ajoutée** (pas de guillemet, une virgule, des espaces) |
| 2 | Le fuseau figé à la construction des mots : un fuseau changé pendant que l'écran est ouvert n'est pas dans sa configuration, et le texte partagé gardait l'ancien | **réel** | **corrigé** : le fuseau du téléphone lu à chaque date ; test du décalage sur l'appareil |

## Vérifications après correction

Gate de la CI vert en local (669 tests) ; `HistoryTextOnDeviceTest` 4 sur 4 sur l'émulateur Android 16.

## Le rapport, tel que rendu

### 1. Le séparateur des réponses ajoute des guillemets — certitude : certaine

**Scénario :** un STOP est quitté puis l’utilisateur choisit « Raccrocher ». Le texte affiché et partagé devient, en français :

> `Réponse : Quitté sans réponse", puis "Raccrocher`

alors que le résultat mesuré attendu est :

> `Réponse : Quitté sans réponse, puis Raccrocher`

Les guillemets sont littéralement présents dans toutes les ressources `history_then` :

```xml
<string name="history_then">", puis "</string>
```

Le test reste vert parce qu’il construit son résultat attendu avec cette même ressource :

```kotlin
val answers = resources.getString(R.string.history_answer_left) +
    resources.getString(R.string.history_then) +
    resources.getString(R.string.stop_hang_up)
```

Il valide donc l’implémentation fautive au lieu de valider le texte attendu.

---

### 2. Le fuseau horaire peut devenir faux après un changement de fuseau pendant l’écran — certitude : élevée

**Scénario :**

1. L’utilisateur ouvre l’historique à Paris : `10:07 (UTC+01:00)`.
2. Le téléphone change de fuseau pour Londres, ou l’utilisateur modifie manuellement le fuseau.
3. Il partage ensuite l’historique, ou une nouvelle alerte provoque un rafraîchissement.

`HistoryText` est mémorisé avec :

```kotlin
remember(LocalConfiguration.current) { HistoryText(context.resources) }
```

Or le fuseau horaire ne fait pas partie de `Configuration`. Le formatter est donc conservé avec le fuseau capturé lors de sa construction :

```kotlin
.withZone(ZoneId.systemDefault())
```

Le texte peut continuer à afficher l’ancien décalage et l’ancienne heure locale, par exemple `10:07 (UTC+01:00)` au lieu de `09:07 (UTC)`. Le partage peut donc contenir une date localement fausse après un changement de fuseau tant que l’écran n’est pas recréé.