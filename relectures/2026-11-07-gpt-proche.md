# Relecture GPT — le SMS au proche (D-034) — 2026-11-07

> `gpt-5.6-luna`, effort `high` ; consigne : `consignes/2026-11-07-gpt-proche.md`. Envoyé : le diff du code
> (`app/src/main`, `domain/src/main`, `tools`, les textes anglais et français ; fichiers neufs compris), après les deux
> relectures Claude ; 24 733 jetons d'entrée, 14 619 de sortie : **0,022 $**.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Le limiteur n'est pas atomique entre `allows` et `record` : deux alertes simultanées passent toutes deux | **faux ici** : les décisions sont exécutées une à une, par la file unique des capteurs (P-12) ; seul `release`, depuis une autre coroutine, est concurrent, et chaque méthode est synchronisée — vu aussi par la relecture Claude | rien |
| 2 | Un échec que le téléphone annonce après les 2 minutes d'attente ne rend jamais la place | **réel** : sans service plus de 2 minutes, le même STOP ensuite est retenu pour un SMS jamais parti | l'attente dure autant que la retenue (15 min) : après, il n'y a plus de place à rendre |
| 3 | Un double toucher envoie deux premiers SMS, deux adieux | **réel** : l'écran ne passait « occupé » qu'après la lecture et l'écriture du proche | « occupé » dès le toucher, un second choix ignoré ; le retrait n'agit qu'une fois. Test, **contrôle négatif** fait |
| 4 | Le proche enregistré sans son premier SMS (ou l'ancien sans adieu) si le processus meurt, ou si l'écran est quitté, entre les deux | **réel pour l'écran quitté** (la portée de l'écran est annulée en quittant) ; la mort du processus entre deux instructions, sans enjeu | les enregistrements et les SMS du choix tournent dans la portée de l'application |
| 5 | L'échec de l'adieu n'est ni attendu ni dit | réel, **limite écrite** (D-034) : l'utilisateur n'y peut rien, l'escroc qui fait retirer le proche moins encore ; l'adieu n'est pas renvoyé | écrit |
| 6 | « (0) » retiré n'importe où dans un numéro international | **réel**, peu probable | seulement juste après l'indicatif ; test |

Rien de trouvé sur le récepteur et le `PendingIntent` (« explicite et immuable, récepteur non exporté »).

## Rapport brut

Voici six défauts réels.

1. **Le limiteur n’est pas atomique entre `allows()` et `record()`** — **certitude élevée côté code, moyenne sur la probabilité d’occurrence**

   `allows()` et `record()` sont synchronisés séparément. Deux appels concurrents à `SmsGuardianNotifier.tell()` peuvent donc tous deux obtenir `true` avant que l’un des deux n’enregistre son envoi.

   Scénario concret : deux événements arrivent presque simultanément depuis deux traitements du moteur. Les deux passent `limiter.allows(...)`, puis les deux appellent `sender.send(...)`. Le proche reçoit deux SMS pour le même déclencheur, ou le quota journalier passe de 19 à 21.

   Il faudrait au minimum réserver atomiquement la place avant l’envoi, puis la libérer si `send()` échoue.

2. **Un échec signalé après le délai de deux minutes ne libère jamais la place** — **certitude élevée**

   Dans `SmsGuardianNotifier.send()` :

   ```kotlin
   val outcome = withTimeoutOrNull(RESULT_WAIT) { sent.await() }
   ...
   if (outcome == false) limiter.release(event, at)
   ```

   Si le résultat vaut `null` après deux minutes, aucune surveillance ne reste active. Si le récepteur reçoit ensuite `RESULT_ERROR_NO_SERVICE`, le `Deferred` est complété mais plus personne ne l’attend ; `limiter.release()` n’est jamais appelé.

   Scénario : le téléphone reste sans service pendant plus de deux minutes, puis `SmsSentReceiver` reçoit l’échec à la troisième minute. Un même STOP survenu à la quatrième minute est bloqué par la fenêtre de 15 minutes, alors que le premier SMS n’est effectivement jamais parti.

3. **Un double toucher peut envoyer plusieurs SMS de configuration ou d’adieu** — **certitude élevée**

   `GuardianChoice.save()` et `remove()` lancent une coroutine sans passer immédiatement à un état occupé. Le bouton reste donc actif pendant que `repository.current()` et `repository.set()` sont exécutés.

   Scénario de remplacement :

   - le proche actuel est A ;
   - l’utilisateur saisit B et touche deux fois rapidement « Prévenir ce numéro » ;
   - les deux coroutines lisent A comme ancien proche ;
   - chacune envoie un adieu à A ;
   - chacune envoie le SMS de configuration à B.

   A reçoit donc deux SMS « ce téléphone ne vous préviendra plus » et B deux SMS de configuration. Le même problème existe avec un double toucher de la suppression.

4. **La configuration peut être enregistrée sans que le premier ou le dernier SMS ne soit envoyé** — **certitude élevée sur le chemin de code**

   Pour une configuration, le dépôt est modifié avant l’appel à `setUp()` :

   ```kotlin
   repository.set(guardian)
   ...
   setUp(guardian)
   ```

   Pour une suppression, le dépôt est vidé avant `farewell(previous)`.

   Scénario : le processus est tué, ou le ViewModel est annulé quand l’utilisateur quitte l’écran, juste après l’écriture dans DataStore et avant l’envoi. Au redémarrage, le nouveau proche est considéré comme enregistré, donc aucun nouveau SMS de configuration n’est déclenché. Lors d’un remplacement ou d’une suppression, l’ancien proche peut de même ne jamais recevoir l’adieu. Ce n’est pas le défaut accepté du limiteur en mémoire : ici l’état persistant affirme que l’opération est terminée alors que son SMS associé n’a pas été envoyé.

5. **L’échec asynchrone du SMS d’adieu est ignoré** — **certitude élevée**

   `farewell()` vérifie uniquement si `sender.send()` retourne `null` :

   ```kotlin
   if (sender.send(previous.number, texts.removed()) == null) ...
   ```

   Si le SMS est accepté par `SmsManager`, `send()` retourne un `Deferred`; mais le résultat `false` ultérieur n’est jamais attendu.

   Scénario : le proche est retiré sans SIM ou sans service. `SmsManager` accepte la demande, puis `SmsSentReceiver` reçoit un échec. L’interface affiche pourtant « Plus personne n’est prévenu », sans signaler que l’ancien proche n’a pas reçu l’adieu et sans possibilité de renvoi. Cela est distinct du cas explicitement accepté où l’autorisation `SEND_SMS` a déjà été retirée.

6. **`(0)` est retiré n’importe où dans un numéro international, pas seulement après l’indicatif** — **certitude élevée sur le code, occurrence moyenne**

   Le code fait :

   ```kotlin
   compact.replace(TRUNK_ZERO, "")
   ```

   sans vérifier la position de `(0)`.

   Scénario : l’utilisateur saisit un numéro international dans lequel `(0)` représente réellement un chiffre du numéro, par exemple `+44 20 7123 (0)456`. Le numéro enregistré et envoyé sera celui dont le `0` a été supprimé, donc un autre numéro. L’interface ne conserve ensuite que la version normalisée, ce qui rend l’erreur difficile à repérer.

Je ne vois pas, dans le code fourni, de fuite évidente du code ou du contenu des SMS via `SmsSentReceiver` : le `PendingIntent` est explicite et immuable, et le récepteur est non exporté.
