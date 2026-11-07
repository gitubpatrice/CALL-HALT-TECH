# Relecture GPT — « Retirer l'accès » sur le STOP d'accessibilité (D-033) — 2026-11-07

> `gpt-5.6-luna`, effort `high` ; consigne : `consignes/2026-11-07-gpt-retirer-acces.md`. Envoyé : le diff du code
> (`app/src`, `domain/src` ; 50 Ko) et trois fichiers entiers (`StopActivity`, `StopSession`, `AccessSettings`), après
> les deux relectures Claude et leurs corrections ; 19 063 jetons d'entrée, 12 645 de sortie : **0,019 $**.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | `MATCH_SYSTEM_ONLY` ne garantit pas l'appli Réglages : une appli système préinstallée qui déclarerait la même page passerait avant | **hors du modèle de menace, limite écrite** : l'appli d'arnaque, installée ensuite, ne peut pas être système ; une appli du fabricant n'est pas l'escroc. La fonction est celle de toutes les pages de l'activation (relecture de sécurité du 2026-11-02, S2) | écrit (D-033) |
| 2 | Un STOP perdu quand une instance recréée reçoit aussi un nouvel intent : `onCreate` restaure l'ancienne session et peut fermer l'écran | **réel en partie** : un nouvel intent arrive par `onNewIntent` avant `onResume` (vérifié dans `ActivityThread.performResumeActivity`), donc la session en attente du code le laisse passer ; mais la garde « répondu, rien à ouvrir → fermer », placée dans `onCreate`, l'aurait perdu (état presque impossible : une session sauvée entre la réponse et la fermeture) | la garde déplacée dans `onResume`, après la livraison ; **remesuré** (verrouillé ; recréé pendant la demande du code) |
| 3 | Les tests restent verts si le câblage Android est faux (aucun test de l'activité) | **réel, connu** (noté aussi par la relecture Claude) : ces chemins reposent sur les mesures aux émulateurs, refaites après chaque correction | écrit |

## Rapport brut

J’identifie **deux défauts réels** et un **défaut de couverture de test**.

### 1. `MATCH_SYSTEM_ONLY` ne garantit pas l’application « Réglages » du téléphone — certitude élevée

`MATCH_SYSTEM_ONLY` filtre les applications système, mais ne garantit pas que l’activité appartient au paquet des réglages (`com.android.settings` ou équivalent OEM).

Scénario concret :

1. Un constructeur préinstalle une application système — aide, sécurité, support — qui déclare aussi `ACTION_ACCESSIBILITY_SETTINGS`.
2. `queryIntentActivities(..., MATCH_SYSTEM_ONLY)` renvoie cette application avant l’application réelle des réglages.
3. `systemSettings()` fait alors `setPackage()` sur ce paquet.
4. `openOwn()` ouvre cette activité, qui peut afficher une page différente, voire ses propres instructions.

Le fait d’être préinstallée et marquée système exclut bien l’application d’arnaque installée par l’utilisateur, mais ne prouve pas que la page soit celle des réglages du téléphone. Le code devrait au minimum sélectionner le paquet d’un composant de réglages connu/validé, ou valider davantage le résolveur.

### 2. Un STOP peut être perdu lors d’une recréation avec un nouvel `Intent` — certitude moyenne à élevée

Dans `onCreate`, le code donne toujours priorité à l’état restauré :

```kotlin
val created = restored ?: StopRequest.readFrom(intent)?.let(StopSession::of)
```

Puis il peut terminer immédiatement l’activité :

```kotlin
if (created.answered && created.opening == null) {
    finish()
    return
}
```

Scénario :

1. Le STOP A est répondu, ou attend l’ouverture des réglages après le déverrouillage.
2. L’activité est détruite par le système, avec son état sauvegardé.
3. Entre-temps, un STOP B arrive et provoque la recréation/lancement de l’activité avec un nouvel `Intent`.
4. `onCreate` restaure A et ignore B.
   - si A est déjà répondu sans ouverture en attente, l’activité se termine ;
   - si A attend une ouverture, elle redemande le déverrouillage pour A.
5. Le STOP B n’est jamais affiché.

Le chemin `onNewIntent()` traite correctement le cas où l’instance reste vivante, mais il ne couvre pas le cas où le nouvel intent est livré à une nouvelle instance porteuse d’un `savedInstanceState`.

### 3. Les tests restent verts si le câblage Android est faux — certitude élevée

Les tests ajoutés vérifient bien :

- les boutons et leur ordre ;
- la décision de couper l’appel ;
- la transition pure de `StopSession`.

En revanche, aucun test ne vérifie réellement que :

- `REMOVE_ACCESS` appelle `unlockThen()` ;
- l’action est bien `ACTION_ACCESSIBILITY_SETTINGS` ;
- l’activité résolue est bien limitée à l’application système choisie ;
- `NEW_TASK | CLEAR_TASK` est présent ;
- le callback du verrou ouvre la page une seule fois ;
- un STOP reçu pendant cette séquence ne fait pas disparaître le nouvel écran.

Par exemple, une implémentation qui remplacerait accidentellement `openAccessibilitySettings()` par un simple `startActivity(Intent(Settings.ACTION_SETTINGS))`, ou qui supprimerait `unlockThen()`, laisserait tous les tests fournis au vert alors que le comportement réel serait incorrect.
