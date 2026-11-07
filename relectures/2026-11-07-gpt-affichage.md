# Relecture GPT — l'écran STOP lisible à toute taille de police, et senti à l'oreille (U1 à U4) — 2026-11-07

> `gpt-5.6-luna`, effort `high` ; consigne : `consignes/2026-11-07-gpt-affichage.md`. Envoyé : le diff de la branche
> (`origin/main..8228180`), puis `StopActivity.kt` et `StopScreen.kt` ; 79 649 caractères. 19 993 jetons d'entrée,
> 10 678 de sortie : **0,017 $**. Après la relecture Claude (`2026-11-07-claude-affichage.md`).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Les tests ne couvrent que le calcul pur : une boucle de placement qui prendrait la position d'une autre pièce (`ys[index]` au lieu de `ys[at]`) resterait verte ; aucun test Compose (petite hauteur, grande police, défilement, bouton sûr) | **réel** (bas : le code était juste, mesuré à l'écran) | **corrigé par construction** : `stopPlacement` rend la position de chaque pièce dans l'ordre des pièces (`tops`), et la boucle place la pièce i à `tops[i]` — l'erreur décrite n'est plus possible ; le test vérifie aussi que l'ordre de haut en bas est celui des positions. **Pas de test Compose** : le projet n'a pas la bibliothèque de test Compose, et l'ajouter pour cette boucle serait disproportionné ; l'écran est mesuré sur l'émulateur (100 %, 200 %, paysage, ordre de l'arbre d'accessibilité), remesuré après ce changement |

Le rapport ne voit pas d'autre défaut à l'exécution.

## Le rapport, tel que rendu

### 1. Les tests ne couvrent pas l’intégration réelle de `StopLayout` — certitude élevée

`StopLayoutTest` teste uniquement `stopPlacement`, jamais le composable `StopLayout` ni le placement effectif des `Placeable`.

Par exemple, cette implémentation erronée resterait verte :

```kotlin
placement.order.forEachIndexed { at, index ->
    // Faux : ys est indexé dans l'ordre d'affichage, pas dans l'ordre des enfants.
    placeables[index].place(0, placement.ys[index])
}
```

En mode compact, `placement.order` vaut par exemple :

```text
MESSAGE, MESSAGE, MESSAGE, BUTTON, BUTTON, EXPLANATION, EXPLANATION, EXPLANATION, CONTINUE
```

alors que `placement.ys` est dans cet ordre compact. Un tel code pourrait donc placer les explications et les boutons au mauvais endroit, voire les faire se chevaucher, tandis que tous les tests purs resteraient verts.

De même, supprimer `verticalScroll` ou mesurer le `Layout` avec une mauvaise hauteur disponible ne ferait échouer aucun de ces tests. Il manque au moins un test Compose avec une petite hauteur et une grande police vérifiant :

- l’ordre et les bornes des positions effectivement placées ;
- l’absence de chevauchement ;
- la présence du défilement ;
- l’accessibilité et l’activation du bouton sûr.

Je ne vois pas, dans le code fourni, d’autre défaut runtime certain.
