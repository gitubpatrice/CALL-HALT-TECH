# Relecture GPT — un service d'accessibilité activé pendant un appel (D-033) — 2026-11-05

> `gpt-5.6-luna`, effort `high` ; consigne : `consignes/2026-11-05-gpt-accessibilite.md`. Envoyé : le diff du code
> (`app/src/main/java`, `domain/src/main`, le manifeste, le texte français ; fichiers neufs compris) ; 4 984 jetons
> d'entrée, 10 795 de sortie : **0,014 $**.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Un changement entre la première lecture et l'abonnement est perdu | réel en principe, **sans effet** : quelques microsecondes au démarrage du processus ; dans l'autre ordre, il entre dans la base — comme tout ce qui précède le démarrage, écrit dans D-033 | ordre inversé (s'abonner, puis lire), commentaire exact |
| 2 | Un service activé puis désactivé avant le rappel est manqué | réel, **sans enjeu** : un accès retiré aussitôt ne sert pas à l'escroc ; s'il revient, c'est un nouveau changement | rien |
| 3 | Une lecture ratée gardait une base périmée : plus tard, un faux STOP pour une activation ancienne | **réel** | **corrigé** : l'état redevient inconnu, la lecture suivante n'est qu'une base |
| 4 | Un profil de travail n'est pas observé | réel, une limite | **écrit** (comme D-031) |

## Le rapport, tel que rendu

### 1. Fenêtre entre la lecture initiale et l’enregistrement de l’observateur — **certitude élevée**

`register()` lit d’abord la valeur, puis enregistre le `ContentObserver` :

```kotlin
known = read(resolver)
resolver.registerContentObserver(...)
```

Scénario :

1. `known` vaut l’ensemble initial, vide.
2. Juste après `read()`, l’utilisateur active le service `com.example/.ControlService`.
3. Le provider écrit le réglage et envoie sa notification.
4. `registerContentObserver()` n’est pas encore exécuté : la notification est perdue.
5. Le service reste activé, mais `known` reste vide et aucun nouveau changement ne survient.

L’activation pendant l’appel n’est donc jamais signalée. C’est une course classique entre le snapshot initial et l’abonnement.

---

### 2. Le callback est un avis de modification, mais le code ne lit que l’état final — **certitude élevée**

`onChange()` ne reçoit pas la valeur qui a provoqué la notification ; il relit la valeur courante. Plusieurs écritures peuvent donc être regroupées ou observées après coup.

Scénario concret :

1. Le réglage est vide.
2. Pendant l’appel, les Réglages activent le service `A`.
3. Avant que le `Handler` principal exécute `onChange()`, une seconde écriture désactive `A` — par exemple l’utilisateur annule immédiatement, ou une politique système restaure le réglage.
4. Le callback lit à nouveau un réglage vide.
5. `now - before` est vide : aucun STOP.

Même avec deux notifications en attente, les deux callbacks peuvent lire le même état final. L’activation transitoire est donc manquée.

---

### 3. Une lecture illisible conserve une base devenue obsolète — faux STOP possible — **certitude élevée**

Après une lecture réussie, `known` reste inchangé si `read()` échoue :

```kotlin
val now = read(resolver) ?: return
```

Scénario :

1. `known` vaut `{}`.
2. Le service `A` est activé pendant l’appel.
3. Le callback est déclenché, mais la lecture lève `SecurityException`.
4. La méthode retourne sans invalider `known`, qui vaut toujours `{}`.
5. Plus tard, une autre modification du réglage rend la lecture possible ; `A` est toujours présent.
6. Le code compare `{}` avec `{A}` et produit un `ACCESSIBILITY_ENABLED`.

Le STOP est alors attribué à une activation qui n’est pas la modification observée à ce moment-là. Il peut notamment apparaître après la fenêtre de 15 minutes, ou alors que `A` était déjà activé depuis longtemps. Cela contredit aussi le contrat annoncé : après une période illisible, la première lecture redevenue possible devrait seulement rétablir une base.

---

### 4. Périmètre utilisateur non géré — **certitude moyenne, dépend du périmètre produit**

`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` est propre à l’utilisateur Android. L’observateur est enregistré pour l’utilisateur courant du processus ; il ne suit pas automatiquement les autres utilisateurs ou profils.

Scénario :

1. `CallHaltApplication` fonctionne dans l’utilisateur principal.
2. Un service d’accessibilité est activé dans un profil professionnel ou un autre utilisateur Android.
3. Seul le réglage de cet autre utilisateur est modifié.
4. L’observateur du processus principal ne reçoit pas cette modification et `read()` ne la voit pas.

Si l’application doit protéger uniquement l’utilisateur dans lequel elle est installée et active, ce n’est pas un défaut fonctionnel. Si elle doit couvrir les profils/utilisateurs utilisés pour l’appel, l’événement est manqué.

---

### Points que je ne retiendrais pas comme défauts

- La normalisation `pkg/.Classe` / `pkg/pkg.Classe` évite bien le faux changement dû à la réécriture de la forme du composant.
- La liste blanche ne semble pas fournir d’exemption facilement obtenable par un escroc : un APK téléchargé ne peut pas normalement usurper à la fois le nom de paquet et `FLAG_SYSTEM`.
- L’accès à `ContentObserver` sur le thread principal n’est pas, en soi, un défaut de concurrence ici : `known` est lu et modifié séquentiellement sur ce thread, puis le traitement métier est remis à `SensorQueue`.