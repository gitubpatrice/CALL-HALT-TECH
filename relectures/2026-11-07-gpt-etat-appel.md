# Relecture GPT — l'état réel de l'appel, lu en direct (D-035, FN-1) — 2026-11-07

> `gpt-5.6-sol`, effort `high` (code qui décide : D-016) ; consigne : `consignes/2026-11-07-gpt-etat-appel.md`. Envoyé :
> le diff du code (`app/src`, `domain/src`, fichiers neufs compris), puis `TelephonyLiveCall.kt`, `CallStateAtStart.kt`,
> `PhoneState.kt`, `ProcessSignalUseCase.kt`, `DetectionEngine.kt`, `CallWindow.kt` entiers ; 82 512 caractères.
> 19 922 jetons d'entrée, 11 244 de sortie : **0,437 $**. Après les relectures Claude.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | `isInManagedCall` n'existerait qu'à partir de l'API 31 : sur Android 10-11, une `NoSuchMethodError` (une `Error`, qu'aucun `catch` n'attrape) ferait perdre toute action | **faux** : la méthode existe depuis l'API 26 (`api-versions.xml` du SDK : `since="26"`), l'appli démarre à l'API 29 ; lint l'aurait refusée | aucune ; le test sur l'appareil, lancé par la CI sous Android 10, la fait tourner |
| 2 | Trois lectures successives, pas simultanées : un « pas d'appel » de la première suffisait à dire « aucun appel » même si les deux autres disaient un appel décroché, et terminait un appel qui continue | **réel** (rare : un appel qui commence ou finit entre deux lectures) | **corrigé** : les trois doivent concorder, sinon rien ; test « reads that disagree tell nothing, never a call's end » |
| 3 | Seule la lecture était protégée : une erreur en traitant ce qu'elle a lu (la base) sortait encore avant l'action | **réel** (le magasin ne doit pas lever, mais l'action ne doit pas en dépendre) | **corrigé** : toute l'étape de lecture est protégée ; test « a call state read that cannot be committed never loses the action » |
| 4 | Les tests restent verts sur un adaptateur qui ne lirait jamais le téléphone | **réel** | **corrigé** : `TelephonyLiveCallOnDeviceTest`, sur Android (sans appel, le téléphone dit qu'il n'y en a aucun) ; passé sur l'émulateur Android 16, lancé par la CI sous Android 10 |

## Le rapport, tel que rendu

Je retiens **4 constats**.

1. **[Critique, sous condition] `isInManagedCall` peut faire perdre toute action sur Android < 12 / API 31**  
   **Scénario :** sur un appareil API 30, une validation bancaire arrive pendant un appel. `TelephonyLiveCall.current()` invoque `TelecomManager.isInManagedCall`, ajouté en API 31, sans garde `SDK_INT`. Cela peut lever `NoSuchMethodError`. Ce type hérite de `Error`, pas de `RuntimeException` ni d’`Exception` : aucun des deux `catch` ne l’intercepte. `process(signal)` n’est donc jamais appelé et aucun STOP n’apparaît. La lecture au démarrage est également affectée.  
   **Certitude : élevée sur le mécanisme ; exposition à confirmer**, car le `minSdk` n’est pas fourni. Si `minSdk >= 31`, ce constat tombe.

2. **[Élevé] Une lecture incohérente est transformée à tort en fin d’appel certaine**  
   `current()` réalise trois appels Binder non atomiques. Pour le triplet contradictoire :
   ```text
   isInCall = false
   isInManagedCall = true
   callState = OFFHOOK
   ```
   `liveState` choisit `IDLE` uniquement à cause du premier résultat, alors que les deux suivants indiquent un appel géré décroché. Une contradiction devrait produire `null`, pas une fin certaine.  
   **Scénario :** le moteur connaît un appel actif ; pendant une action, les trois lectures donnent ce triplet à cause de snapshots décalés. Le moteur termine artificiellement l’appel à cet instant. L’appel réel continue 20 minutes puis se termine ; sa diffusion `IDLE` est ignorée puisque le moteur le croit déjà terminé. Une action 5 minutes après la vraie fin ne déclenche alors aucun STOP, car la fenêtre synthétique ouverte 20 minutes plus tôt est expirée.  
   Ce n’est pas le cas exclu de la panne franche de Telecom, mais une incohérence entre lectures successives.  
   **Certitude : élevée sur la mauvaise réduction ; moyenne sur la fréquence réelle du triplet.**

3. **[Élevé] Seule l’exception de lecture est neutralisée ; une exception en traitant le signal lu fait perdre l’action**  
   Le `try/catch` couvre seulement :
   ```kotlin
   liveCall.stateAt(action.at)
   ```
   mais pas :
   ```kotlin
   liveState(signal)?.let { process(it) }
   ```
   **Scénario :** pendant un appel connu, une validation bancaire arrive. La lecture rend `CallStarted`; le premier `store.apply` utilisé pour traiter ce signal rencontre une erreur SQLite transitoire. L’exception sort de `invoke` avant `process(action)`. L’événement bancaire, non rejoué, est perdu et aucun STOP n’est montré, alors qu’un traitement direct de l’action aurait pu réussir à la tentative suivante. Un arrêt du processus entre les deux commits produit le même résultat.  
   **Certitude : élevée sur le chemin de contrôle ; moyenne sur l’occurrence opérationnelle.**

4. **[Tests, élevé] La suite montrée reste verte si l’adaptateur de production ne lit jamais le téléphone**  
   Cette implémentation fautive :
   ```kotlin
   override suspend fun stateAt(at: Moment): Signal? = null
   ```
   dans `TelephonyLiveCall` laisse les tests ajoutés au vert :
   - `CallStateAtStartTest` injecte un faux `LiveCallSource` ;
   - `ProcessSignalUseCaseTest` injecte également des faux ;
   - `PhoneStateTest` teste seulement `liveState` et `phoneStateOf`, pas le câblage de `stateAt()` vers les API Android.

   **Scénario non détecté :** après redémarrage, l’appel décroché avant déverrouillage n’est jamais remonté par l’adaptateur réel ; la validation bancaire ne produit de nouveau aucun STOP.  
   **Certitude : élevée pour la suite fournie.**
