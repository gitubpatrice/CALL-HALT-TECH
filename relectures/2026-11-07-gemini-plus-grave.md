# Relecture Gemini — l'écran STOP parle du plus grave de ses déclencheurs (N-1) — 2026-11-07

> `gemini-3.1-pro-preview`, effort `high` (première relecture du projet par Gemini, sur proposition de Patrice) ;
> consigne : `consignes/2026-11-07-gemini-plus-grave.md`. Envoyé : le diff du code (`app/src`, fichiers neufs compris),
> puis `Gravest.kt`, `StopSession.kt`, `StopNotifier.kt`, `StopRequest.kt`, `StopLauncher.kt`, `StopActivity.kt`,
> `StopText.kt` entiers ; 68 995 caractères. 18 531 jetons d'entrée, 20 763 de sortie : **0,286 $**. Après la relecture
> Claude.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | `FLAG_IMMUTABLE` empêcherait de mettre à jour la notification : l'intent resterait celui du premier STOP | **faux** : le créateur d'un `PendingIntent` immuable le met toujours à jour par `FLAG_UPDATE_CURRENT` (sources d'Android 36, `PendingIntent.FLAG_IMMUTABLE`) ; **mesuré** dans l'ordre qui le départage (code, puis accessibilité) : l'écran ouvert parle de l'accessibilité | aucune |
| 2 | Un STOP arrivé pendant la demande du code de verrouillage, après « Raccrocher et retirer l'accès », empêche la page de s'ouvrir | réel, **déjà écrit** comme limite (D-017, D-033 ; audit, variante verrouillée de N-1, faible) ; hors de ce changement | rien maintenant |
| 3 | Une notification balayée sans être touchée perd ses déclencheurs : le STOP suivant ne parle que du sien | réel ; c'est un STOP laissé sans réponse | **à traiter avec FN-4** (Patrice : un STOP quitté sans réponse revient une fois et prévient le proche) |
| 4 | Un « Raccrocher » qui a échoué cachait encore les boutons d'appel sur le STOP suivant, celui de l'escroc qui rappelle | **réel, antérieur** | **corrigé** : un nouveau STOP repart de l'état d'appel que le moteur vient de décider ; test (l'ancien test, qui figeait l'inverse, réécrit) |
| 5 | Une notification touchée, puis un nouveau STOP posté avant que l'écran soit créé : l'écran effaçait la notification du nouveau | **réel** (fenêtre étroite) | **corrigé** : l'écran n'efface que la notification d'une requête qu'il couvre (la sienne ou une plus ancienne ; les identifiants ne font que croître) |

## Le rapport, tel que rendu

Voici les défauts réels identifiés dans cette implémentation, avec pour chacun le scénario d'échec concret et mon degré de certitude.

### 1. `FLAG_IMMUTABLE` empêche la mise à jour des déclencheurs de la notification
**Cause :** Dans `StopNotifier.post`, le `PendingIntent` est créé avec `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT` et un `requestCode` fixe (0). Depuis Android 12, si le `PendingIntent` existe déjà, le système ignore la tentative de mise à jour des extras (l'Intent) car il est immuable. 
**Scénario d'échec :** Le téléphone bloque l'écran, une notification de repli pour un Code SMS est affichée (le PendingIntent contient l'Intent "Code"). Juste après, l'escroc obtient l'Accessibilité. `notifier.post()` met à jour le texte de la notification pour parler d'Accessibilité, mais l'Intent sous-jacent *reste bloqué sur le Code*. Quand l'utilisateur touche la notification, l'écran s'ouvre sur le Code et n'offre jamais le bouton critique "Retirer l'accès".
**Certitude : 100 %** (Comportement strict du système Android).
*Correction :* Utiliser `FLAG_CANCEL_CURRENT` (avec `FLAG_IMMUTABLE`) pour forcer le remplacement complet, ou utiliser `FLAG_MUTABLE` si cela respecte vos règles de sécurité.

### 2. Une demande de STOP pendant le déverrouillage annule la parade de sécurité
**Cause :** Dans `StopActivity.onNewIntent`, si la session attend le déverrouillage (`answered` = true), elle est écrasée par `StopSession.of(next)`. Cette nouvelle session remet `opening` à `null`. Au retour du déverrouillage dans `onResume()`, la vérification `current?.opening == pending` échoue car `opening` a été effacé.
**Scénario d'échec :** L'écran affiche un STOP d'accessibilité. L'utilisateur appuie sur "Raccrocher et retirer l'accès". Le téléphone est verrouillé et demande le code PIN ou l'empreinte. Pendant que l'utilisateur tape son code, l'escroc envoie exprès un SMS de code. `onNewIntent` s'exécute en arrière-plan et écrase la session. Le déverrouillage réussit, `onResume` s'exécute, mais oublie la cible (les paramètres d'accessibilité). L'utilisateur se retrouve face à un STOP pour le SMS, et le service de l'escroc reste actif.
**Certitude : 100 %**

### 3. Le balayage (swipe) de la notification détruit l'historique des déclencheurs
**Cause :** `pending()` ne renvoie `absorbed` que si `stillPosted()` est vrai (la notification est dans les `activeNotifications`). Si l'utilisateur balaie la notification, `stillPosted()` devient faux. Le prochain appel à `post()` trouvera un historique vide et écrasera `absorbed`.
**Scénario d'échec :** L'utilisateur balaie la notification "Accessibilité" sans la toucher (par erreur ou via "Tout effacer"). Quelques secondes plus tard, un SMS de code arrive. `StopLauncher` crée un nouveau STOP avec un `earlier` vide. La nouvelle notification/le nouvel écran s'affiche et ne parle que du SMS. L'alerte gravissime d'accessibilité est totalement effacée de la mémoire de la session et ne sera jamais traitée.
**Certitude : 100 %**

### 4. L'impossibilité de raccrocher infecte les appels suivants (fuite d'état dans `replacedBy`)
**Cause :** Dans `StopSession.replacedBy`, la fonction utilise `copy(current = next, triggers = ...)` pour intégrer le nouveau STOP. Elle conserve l'état des autres champs, y compris `callStillOn`. Si un raccrochage précédent a échoué, il n'est jamais réinitialisé à `false`.
**Scénario d'échec :** L'utilisateur appuie sur "Raccrocher" mais l'appli échoue à couper la ligne. L'écran met `callStillOn = true` et cache le bouton d'appel. L'escroc raccroche de lui-même puis rappelle. L'arrivée du nouvel appel génère un nouveau STOP. `replacedBy` met à jour la requête (`onCall = true`) mais garde `callStillOn = true`. Résultat : le nouvel écran n'offre *pas* le bouton pour raccrocher le nouvel appel, laissant l'utilisateur coincé en ligne avec l'escroc.
**Certitude : 100 %**

### 5. Course critique au toucher de la notification entraînant la perte d'un STOP
**Cause :** Lorsqu'on touche la notification (qui a `setAutoCancel(true)`), Android prépare le lancement de l'activité. Si un STOP survient avant l'exécution de `StopActivity.onCreate`, il peut être totalement effacé.
**Scénario d'échec :**
1. L'utilisateur touche la notification du STOP A. L'Intent part, la notification disparaît des `activeNotifications`.
2. Exactement au même moment, un STOP B survient. `StopLauncher.show(B)` constate que la notification n'est plus là, initie un nouveau STOP B avec un historique vierge, et poste une nouvelle notification pour le STOP B.
3. Quelques millisecondes plus tard, `StopActivity.onCreate` s'exécute (avec l'Intent originel du STOP A). Il appelle inconditionnellement `notifier.cancel()`.
4. La toute nouvelle notification du STOP B est détruite. L'utilisateur voit l'écran du STOP A. Le STOP B a disparu de l'écran, de la notification et de l'historique.
**Certitude : Élevée** (Course d'état (Race condition) classique en Android due à la latence IPC du démarrage de l'Activity).
