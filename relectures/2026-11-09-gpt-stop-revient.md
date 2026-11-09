# Relecture GPT : un STOP quitté revient une fois, puis le proche est prévenu (audit FN-4, D-038) — 2026-11-09

> `gpt-5.6-sol`, effort `high` (code qui décide et base : D-016) ; consigne : `consignes/2026-11-09-gpt-stop-revient.md`.
> Envoyé : le diff du code (`origin/main..4e156ed`, `app/src` et `domain/src`, fichiers neufs compris), puis
> `DetectionEngine.kt`, `ProcessSignalUseCase.kt`, `CallWindow.kt`, `StopLauncher.kt`, `StopActivity.kt`,
> `StopAnswers.kt` et `RoomDetectorStore.kt` entiers ; 146 588 caractères. 35 115 jetons d'entrée, 11 778 de sortie
> (dont 10 080 de raisonnement) : **0,529 $**. Après la relecture Claude (`2026-11-07-claude-stop-revient.md`).

## Tri

Chaque constat est relu dans le code (`StopReturns.kt`, `CallWindow.kt`, `ProcessSignalUseCase.kt`,
`RoomDetectorStore.kt`, `StopAnswers.kt`, `AndroidDecisionExecutor.kt`, `StopNotifier.kt`).

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Le retour n'est pas rattaché à l'appel de départ : un appel A toujours en cours et un appel B commencé depuis, le STOP revient (pour A) ; un appel B parallèle raccroché efface le retour d'un STOP d'après-appel | **non retenu** : le retour choisit l'appel comme un premier STOP (`CallWindow.callAt`, `DetectionEngine.kt:133` : le plus ancien dont la fenêtre court, « l'appel par lequel l'arnaque a commencé ») ; A en cours, c'est le même appel ; raccrocher l'appel en cours au moment où l'on quitte est le geste ordinaire, quel que soit l'appel | la KDoc de `due` disait « ou un autre appel est en cours », ce que le code ne fait pas : **corrigée** ; D-038 dit la règle |
| 2 | Processus tué entre l'enregistrement de `returnedAt` et l'affichage : 2 min plus tard, SMS d'un retour jamais montré | **réel, très rare** (quelques millisecondes) ; le SMS reste vrai pour le premier STOP, quitté sans réponse | **limite écrite** (D-038) |
| 3 | Processus tué entre l'enregistrement de l'échéance et l'envoi : le SMS est perdu | **réel, très rare** ; commun à toute décision (commit puis exécution : le SMS de D-034 aussi), pas propre à FN-4 ; la personne a déjà été interrompue deux fois | **limite écrite** (D-038) |
| 4 | Une réponse donnée juste avant les 2 min peut passer après le réveil dans la file (`endCall` est appelé avant de soumettre la réponse) | **réel, fenêtre étroite** : le temps de couper l'appel, à la seconde même de l'échéance ; enregistrer la réponse avant de couper défairait D-011 (rien d'enregistré si l'appel n'est pas coupé) | **limite écrite** (D-038) |
| 5 | Une écriture Room qui échoue, puis le processus tué : la base garde le retour sans `returnedAt`, il revient une seconde fois | **couvert** par la limite écrite de `RoomDetectorStore` (l'état gardé en mémoire pendant une panne meurt avec le processus) ; le défaut va du côté protecteur (un retour de plus) | rien |
| 6 | Deux retours dans le même réveil, l'un publié et l'autre refusé : les deux sont lus « non montrés » et deux SMS partent | **improbable** : les deux envois passent les mêmes contrôles (`StopNotifier.canPost` : permission, notifications, importance du canal) à quelques millisecondes d'écart, pour la même notification (id 1, qui reprend les déclencheurs de la précédente) ; il faudrait couper les notifications entre les deux | rien |

## Mesures faites en reprenant (2026-11-09, émulateur Android 16)

- **Toucher la notification revenue** (réserve 5 de la relecture Claude) : appel décroché, code injecté → STOP ;
  quitté par Accueil à 08:24:34 → notification STOP revenue ; touchée à 08:26:02 → l'écran STOP s'ouvre, la
  notification part, **aucun SMS** ; « Raccrocher » à 08:26:16 → appel coupé à 08:26:17 ; à 08:27:13, au-delà des
  2 min, toujours aucun SMS « quitté sans réponse » (le seul SMS du journal est celui du STOP de départ, D-034).
- **Tests de stockage sur l'appareil** (`com.filestech.callhalt.data.store`) : 17 sur 17 verts, dont la migration
  2 → 3 (`theTableOfReturnsIsAdded_andAScreenLeftIsKeptToComeBack`).
- Gate local vert après la correction de la KDoc : 628 tests, lint et detekt à zéro.

## Le rapport, tel que rendu

### 1. Le retour n’est pas rattaché à l’appel d’origine

**Certitude : élevée (95 %).**

`StopReturn` ne conserve que `leftAt`/`returnedAt`. À l’échéance, `due()` reprend l’appel le plus ancien couvert par `CallWindow.callAt()` et `hungUpSince()` examine indistinctement tous les appels.

**Scénario — retour pendant un autre appel :**

- 10:00:00 — `CallStarted(canal A)`.
- 10:01:00 — `SensitiveAction(CODE_MESSAGE)` : premier STOP.
- 10:01:05 — `StopAnswered(LEFT)`.
- 10:01:10 — `CallStarted(canal B)` ; l’appel A reste suivi comme actif, par exemple en attente.
- 10:01:20 — `Tick`.

`callAt()` sélectionne A, commencé avant B. Comme A n’est pas terminé, `hungUpSince()` vaut faux : le STOP revient alors que l’utilisateur est désormais dans l’appel B. Il manque un contrôle d’un appel plus récent/en cours, et l’identité de l’appel de départ n’est pas persistée.

**Variante inverse :**

- A est déjà terminé à 10:00:00 ; STOP post-appel quitté à 10:01:05.
- Un appel parallèle B, sans rapport, commencé avant 10:01:05, se termine à 10:01:10.
- À 10:01:20, `hungUpSince()` voit la fin de B et supprime le retour de A à tort.

Le test ajouté ne couvre que le remplacement sur le même canal.

---

### 2. Une mort du processus entre le commit de `returnedAt` et l’affichage produit ensuite un SMS à tort

**Certitude : très élevée (99 %).**

L’état « revenu » est enregistré avant l’exécution de `ShowStop`. Une mort du processus dans cet intervalle ne produit aucun `StopNotShown`, puisque la `CancellationException` est simplement relayée et qu’une mort brutale n’exécute rien.

**Scénario :**

- 10:00:00 — appel en cours et premier STOP.
- 10:00:05 — `LEFT`.
- 10:00:20 — `Tick` : Room commit `returnedAt = 10:00:20` et `pendingStops`, puis le processus est tué avant `executor.execute()`.
- Aucune notification de retour n’a été publiée.
- 10:00:30 — relance du processus, `Tick` immédiat : voyant `returnedAt`, le moteur programme seulement 10:02:20.
- 10:02:20 — `Tick` : `NotifyGuardian(StopLeft)`.

Le proche reçoit donc « quitté sans réponse » alors que le STOP revenu n’a jamais été présenté. Ce cas est distinct de la limite écrite concernant une mort **pendant l’attente**.

---

### 3. Une mort du processus entre le commit du délai expiré et l’envoi perd définitivement le SMS

**Certitude : très élevée (99 %).**

À l’expiration des deux minutes, `due()` supprime d’abord `stopReturns` et `pendingStops`; cet état est commité avant `NotifyGuardian`.

**Scénario :**

- 10:00:20 — le STOP revient réellement et sa notification est publiée.
- L’utilisateur l’ignore.
- 10:02:20 — `Tick` : Room commit l’état sans retour, avec décision `NotifyGuardian(StopLeft)`.
- Le processus est tué juste avant l’exécution de cette décision.
- 10:02:30 — relance : la base ne contient plus rien qui permette de recréer le SMS.

Le STOP revenu a bien été ignoré, mais le SMS manque définitivement. Il n’existe ni état « notification du proche à faire » ni outbox durable.

---

### 4. Une réponse effectuée avant l’échéance peut passer derrière le `Tick` dans la file

**Certitude : moyenne à élevée (85 %).**

`StopAnswers.answer()` horodate la réponse avant d’appeler synchroniquement `calls.endCall()`, mais ne soumet `StopAnswered` qu’après le retour de cet appel. Le `Tick`, lui, peut être soumis entre les deux.

**Scénario :**

- 10:00:20 — STOP revenu ; échéance à 10:02:20.
- 10:02:19.900 — l’utilisateur appuie sur « Raccrocher ». `at` vaut 10:02:19.900.
- `calls.endCall()` bloque jusqu’à 10:02:20.100.
- 10:02:20.000 — la coroutine de réveil soumet le `Tick`, qui passe en premier dans la file.
- Le `Tick` supprime le retour et demande le SMS.
- 10:02:20.100 — `StopAnswered(at=10:02:19.900, HANG_UP)` est enfin soumis, trop tard pour annuler le SMS.

La file unique sérialise le traitement, mais pas l’ordre réel des gestes puisque la soumission de la réponse est retardée. Le moteur ne réconcilie pas ensuite le `Tick` avec l’horodatage antérieur de la réponse.

---

### 5. Une écriture Room transitoirement échouée peut faire revenir le STOP deux fois après une mort du processus

**Certitude : élevée (95 %, sous condition d’une panne d’écriture transitoire).**

`RoomDetectorStore` exécute les décisions même si la transaction échoue, en gardant seulement le nouvel état dans `lastKnown`. Cet état disparaît avec le processus, tandis que la base conserve le retour non encore marqué comme revenu.

**Scénario :**

- 10:00:05 — le retour initial (`returnedAt = null`) est correctement enregistré.
- 10:00:20 — `Tick`; la transaction qui devait enregistrer `returnedAt` échoue et est annulée.
- Malgré cela, `ShowStop(returning=true)` est exécuté : le STOP revient une première fois.
- 10:00:25 — processus tué avant qu’une autre écriture réussisse.
- 10:00:30 — stockage de nouveau disponible, relance du processus et `Tick` immédiat.
- La base contient toujours `returnedAt = null`; le STOP revient une seconde fois.

C’est une conséquence directe de l’exécution volontaire des décisions après échec de persistance, combinée à un état « revenu » non durable.

---

### 6. Un échec partiel parmi plusieurs retours marque aussi les STOP correctement affichés comme « non montrés »

**Certitude : élevée (95 %).**

Si une évaluation contient plusieurs `ShowStop`, `AndroidDecisionExecutor` peut en afficher certains puis lever parce qu’au moins un autre a échoué. Le `catch` de `ProcessSignalUseCase` transforme ensuite **tous** les `ShowStop` de la liste en `StopNotShown`, sans connaître celui qui a échoué.

**Scénario :**

- 10:01:05 — un écran agrégé portant les déclencheurs `CODE_MESSAGE` et `BANK_VALIDATION_REQUEST` est quitté ; les deux retours ont le même `leftAt`.
- 10:01:20 — un `Tick` rend les deux retours exigibles.
- La publication du premier retour réussit.
- La publication du second échoue transitoirement.
- L’exécuteur lève après les tentatives.
- `ProcessSignalUseCase` envoie `StopNotShown` pour les deux déclencheurs.
- Les deux retours sont supprimés et deux `StopLeft` peuvent être envoyés, dont un immédiatement pour le STOP pourtant correctement publié et pas encore ignoré.

Les tests de l’exécuteur et du use case ne couvrent qu’un seul `ShowStop`; ils resteraient donc verts avec ce comportement.