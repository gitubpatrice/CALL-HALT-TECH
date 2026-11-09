# Relecture GPT : « Désinstaller [appli] » sur le STOP d'une appli de prise en main installée (audit FN-3, D-039) — 2026-11-09

> `gpt-5.6-luna`, effort `high` ; consigne : `consignes/2026-11-09-gpt-desinstaller.md`. Envoyé : le diff de la branche
> (`origin/main..88c96d8`, `app/src`, `domain/src`, PRIVACY), puis `StopActivity.kt`, `StopSession.kt`, `StopText.kt`,
> `WarningNotifier.kt` et `Gravest.kt` entiers ; 112 212 caractères. 27 158 jetons d'entrée, 14 914 de sortie : **0,023 $**.
> Après la relecture Claude (`2026-11-09-claude-desinstaller.md`).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Une désinstallation juste après l'installation : le retrait part tout de suite, l'installation encore dans la file poste l'avertissement ensuite, qui reste | **réel** (fenêtre courte) | **corrigé** : le retrait passe par la même file unique, après l'installation ; mesuré (désinstallée → avertissement retiré) |
| 2 | `setSilent(true)` ne rendrait pas silencieux un avertissement sur un canal d'importance haute : il faudrait un second canal | **non retenu, mesuré** : `setSilent` d'AndroidX règle la notification elle-même, quel que soit le canal (sa documentation) ; sur l'émulateur Android 16, pendant l'appel, aucune bannière par-dessus le STOP, aucun son (`mSoundNotificationKey=null`), quand la même installation hors appel montre la bannière (témoin, capture) | écrit (D-039) |
| 3 | Aucun test ne vérifie que l'avertissement posté avec un STOP sort silencieux | **réel** | **corrigé** : `WarningNotifierOnDeviceTest` (silencieux avec un STOP, pas sans ; retiré) ; contrôle négatif fait (sans `setSilent`, il échoue) ; vert sur l'émulateur Android 16 |

## Vérifications après correction

Gate de la CI vert en local (644 tests) ; tests de la plateforme sur l'appareil : 23 sur 23 (Android 16).

## Le rapport, tel que rendu

### 1. Course entre `ACTION_PACKAGE_REMOVED` et le traitement asynchrone de l’installation  
**Certitude : élevée — défaut d’exécution**

Scénario concret :

1. RustDesk est installé.
2. `ACTION_PACKAGE_ADDED` est reçu ; `InstallSensor` place le signal dans `queueAsync`.
3. La file est momentanément occupée, donc aucune notification n’est encore postée.
4. RustDesk est désinstallé immédiatement.
5. `ACTION_PACKAGE_REMOVED` appelle directement `warnings.withdraw(packageName)`, mais il n’y a encore rien à retirer.
6. Le signal d’installation finit ensuite d’être traité et `WarningNotifier.show()` poste quand même la notification.

Résultat : une notification « RustDesk vient d’être installée » reste alors que RustDesk n’existe plus, et son action ouvre au mieux les réglages généraux.

Le retrait devrait être sérialisé avec le traitement de l’installation, ou bien le traitement de l’installation devrait vérifier l’état courant du paquet juste avant `notify()`. Une génération/version par paquet permettrait aussi d’invalider un signal d’installation devenu obsolète.

---

### 2. `setSilent(true)` ne garantit pas un avertissement silencieux sur le canal utilisé  
**Certitude : moyenne à élevée — dépend du comportement exact de l’API/OEM**

Le canal `warnings` est créé avec `IMPORTANCE_HIGH` :

```kotlin
NotificationChannelCompat.Builder(CHANNEL, IMPORTANCE_HIGH)
```

Le même canal sert aux avertissements normaux et aux avertissements associés à un STOP. Or, à partir d’Android 8, l’importance, le son et le comportement interruptif sont principalement ceux du canal ; `setSilent(true)` et `setPriority(...)` ne permettent pas de transformer ce canal déjà configuré en canal non interruptif.

Scénario concret :

- l’utilisateur a autorisé le canal « Applis de prise en main à distance » ;
- RustDesk est installé pendant un appel ;
- le STOP est affiché ;
- `notify()` publie sur le canal HIGH avec `setSilent(true)`.

Selon la version et le constructeur, la notification peut encore produire un son, une vibration ou un heads-up par-dessus le STOP. Le fait qu’elle apparaisse comme `SILENT` dans une inspection donnée ne garantit pas ce comportement dans tous les états du canal.

Il faut normalement deux canaux : un canal HIGH pour les avertissements ordinaires et un canal LOW/MIN pour les avertissements `withStop=true`. Le canal choisi doit être celui du `notify()`.

---

### 3. Les tests ajoutés peuvent rester verts si le silence réel est cassé  
**Certitude : élevée — lacune de test**

Les tests vérifient bien que le moteur produit :

```kotlin
Decision.ShowWarning(..., withStop = true)
```

Mais les tests présentés ne vérifient pas que `WarningNotifier` traduit effectivement ce champ en notification silencieuse. Le fake collecte seulement la décision.

Une implémentation fausse comme celle-ci resterait donc verte :

```kotlin
WarningReason.REMOTE_CONTROL_INSTALLED ->
    remoteControlInstalled(warning.source, silent = false)
```

ou une suppression de `.setSilent(silent)`.

Scénario : pendant un appel, tous les tests du moteur passent, mais la vraie notification est publiée avec le son du canal HIGH et interrompt l’utilisateur par-dessus le STOP.

Il manque au minimum un test de l’adaptateur de notification vérifiant le canal utilisé et/ou les propriétés effectives de la notification pour `withStop=true`.