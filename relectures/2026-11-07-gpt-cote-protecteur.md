# Relecture GPT — échouer du côté protecteur — 2026-11-07

> `gpt-5.6-luna`, effort `high` ; consigne : `consignes/2026-11-07-gpt-cote-protecteur.md`. Envoyé : le diff du code
> (`app/src`, fichier neuf compris), puis `LatestSms.kt`, `StopLauncher.kt`, `GuardianChoice.kt` entiers ; 36 245
> caractères. 8 573 jetons d'entrée, 9 557 de sortie : **0,013 $**. Après la relecture Claude.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Le filet des réglages et du proche est avant la conversion : une valeur gardée sous un autre type lèverait dans la conversion, jusqu'au moteur | **faux** : la conversion lit chaque valeur par `as? T` (`SettingsMapping.read`, déjà commenté pour cela) : un mauvais type se lit absent. Le filet déplacé un moment, puis remis : le test l'a montré (il passait dans les deux ordres) | test gardé comme garde-fou de cette lecture : « a value kept under another type reads as absent » |
| 2 | La lecture du proche (`repository.current()`) est hors du bloc protégé de l'écran du proche | réel en principe (le dépôt ne lève plus, mais l'écran ne doit pas en dépendre) | **corrigé** : la lecture dans le bloc, à l'enregistrement comme au retrait |
| 3 | L'envoi du SMS de mise en place ou d'adieu peut lever et laisser l'écran « occupé » | **faux** : `TextSender.send` rattrape toute erreur et rend `null` ; l'attente d'un résultat ne finit pas en erreur | rien |
| 4 | Le branchement dans l'écouteur n'est pas testé | réel (= relecture Claude, T1) | écrit comme limite (relecture Claude) |

## Le rapport, tel que rendu

Je retiens quatre constats.

1. **Certitude élevée — le `catch` des repositories ne protège pas les erreurs du `map`**

   `Flow.catch` ne capture que les exceptions de l'amont. Ici, il est placé avant `.map { ... }`. Une exception produite lors de la conversion des `Preferences` échappe donc au repli.

   Scénario concret : une ancienne version ou une migration écrit une valeur sous le mauvais type pour une clé (`String` au lieu de `Boolean` ou `Long`). L'accès à la préférence lève un `ClassCastException` dans le `map`. `SettingsRepository.current()` lève alors jusqu'au moteur, qui perd le signal et ne déclenche pas le STOP. Le même problème existe pour `GuardianRepository` si la conversion du numéro du proche échoue.

   Le repli doit englober aussi la transformation, pas seulement `dataStore.data`.

2. **Certitude élevée — `GuardianChoice.save` peut rester définitivement en `Sending` avant le `try`**

   Dans `save`, cette ligne est hors du bloc protégé :

   ```kotlin
   val previous = repository.current()
   ```

   Scénario concret : la lecture du proche échoue — notamment à cause du défaut précédent dans le `map` — ou un repository équivalent lève une `IllegalStateException`. La coroutine se termine avant d'atteindre le `catch`, tandis que `step` est déjà `Sending(guardian)`. L'écran reste donc occupé avec ses boutons désactivés.

   `remove` présente le même défaut avec `repository.current()`, même si son état initial est alors `Idle`.

3. **Certitude élevée — les erreurs d'envoi peuvent encore laisser l'écran silencieusement occupé**

   Après une sauvegarde réussie, `setUp()` et `farewell()` ne sont pas protégés :

   ```kotlin
   val sent = sender.send(...)
   ...
   val ok = ... { it.await() } == true
   ```

   Scénarios concrets :

   - le `Deferred` retourné par `send` se termine exceptionnellement ;
   - `SmsManager` lève une `SecurityException` parce que la permission SMS a été retirée entre `canSend()` et `send()` ;
   - lors du remplacement d'un proche, le SMS de départ de l'ancien proche lève une exception dans `farewell`.

   Dans ces cas, la coroutine se termine sans passer par le `compareAndSet`; l'état reste `Sending`. Dans le cas du remplacement, le nouveau proche peut même être déjà enregistré alors que son SMS de mise en place n'est jamais tenté. Il faut convertir les exceptions non liées à l'annulation en `NotSent`/`SaveFailed`, sans intercepter `CancellationException`.

4. **Certitude élevée — les tests de notification peuvent rester verts avec un listener toujours vulnérable**

   Le nouveau test appelle directement :

   ```kotlin
   latestSmsOrMasked(...)
   ```

   Il ne passe pas par `NotificationListener.latestSms`. Une implémentation fausse qui conserverait dans le listener l'ancien comportement :

   ```kotlin
   catch (...) { null }
   ```

   tout en laissant le helper testé tel quel ferait passer tous les nouveaux tests. En production, une notification SMS illisible provoquerait toujours `null`, donc aucun STOP.

   Il manque un test d'intégration ou au moins un test de la méthode du listener vérifiant qu'une exception de `readLatestSms` est bien transformée en `SmsMessage.Masked` au point d'appel réel.
