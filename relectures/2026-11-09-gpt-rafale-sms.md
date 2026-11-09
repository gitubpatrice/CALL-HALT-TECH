# Relecture GPT : le STOP d'une rafale de SMS, et l'appli SMS trop pleine dite sur l'accueil (audit N-3, D-040) — 2026-11-09

> `gpt-5.6-luna`, effort `high` ; consigne : `consignes/2026-11-09-gpt-rafale-sms.md`. Envoyé : le diff de la branche
> (`origin/main..f322452`, `app/src`, `domain/src`, PRIVACY), puis `NotificationListener.kt`, `SmsAppNotices.kt` et
> `ProtectionViewModel.kt` entiers ; 66 538 caractères. 16 543 jetons d'entrée, 8 881 de sortie : **0,014 $**. Après
> la relecture des faux positifs (`2026-11-09-claude-rafale-sms.md`), qui a fait refaire la règle.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Une rafale arrivée pendant que l'écouteur est délié est perdue : à la reconnexion, le compte est refait sans signal | **réel** | **corrigé** : la reconnexion compare au compte gardé ; un franchissement avec assez de notifications récentes est une rafale ; tests ; **mesuré** (écouteur délié pendant l'appel, 35 SMS, relié → le STOP, le SMS au proche) |
| 2 | Une notification mise à jour devient « récente » : cinq conversations anciennes qui reçoivent un message, plus une nouvelle, font une rafale à 30 | **réel** | **corrigé** : une notification garde l'heure de sa première arrivée ; test |
| 3 | L'appli SMS par défaut lue au plus une fois par minute : les notifications de la nouvelle, pendant cette minute, ne sont pas comptées, ni recomptées ensuite | **réel** (rare) | **corrigé** : au changement d'appli SMS, le compte est refait de ce qui est affiché |
| 4 | L'heure murale d'une notification convertie à la reconnexion : un changement d'heure entre-temps fausse son âge | **réel** (rare : une déconnexion et un changement d'heure) | **limite écrite** (D-040) ; une heure « future » est prise pour le présent (test) |

## Vérifications après correction

Gate de la CI vert en local : **660 tests**.

## Le rapport, tel que rendu

J’ai relevé quatre défauts réels.

### 1. Une rafale survenue pendant la déconnexion de l’écouteur est perdue  
**Certitude : très élevée**

Scénario :

1. L’écouteur est déconnecté pendant un appel.
2. 35 notifications de l’appli SMS arrivent.
3. L’écouteur se reconnecte alors que ces 35 notifications sont toujours affichées.
4. `onListenerConnected()` appelle `smsNotices.connected(...)`.

`connected()` remplace bien le contenu de la map et publie le compteur, mais son contrat dit explicitement : **« Never a burst by itself »**. Aucun `SMS_BURST` n’est donc soumis au moteur pour le franchissement de 30.

Résultat : aucune page STOP ni notification au proche, alors que la rafale a bien eu lieu pendant l’appel. Le test :

```kotlin
fun `the listener connecting counts again from what is shown, without a burst`()
```

verrouille même ce comportement. Il resterait vert avec cette implémentation fautive.

Il faudrait au minimum permettre à la reconnexion de signaler un franchissement déjà observable, avec une déduplication pour ne pas en produire un second à chaque reconnexion.

---

### 2. Une mise à jour d’une même notification peut fabriquer une fausse rafale  
**Certitude : élevée**

`posted()` remplace l’entrée existante, mais met aussi à jour son `postedAt` :

```kotlin
notices[key] = Notice(packageName, atMillis)
```

Scénario :

1. Il y a 29 notifications anciennes, âgées de plus de dix minutes.
2. Cinq de ces notifications sont mises à jour — par exemple cinq conversations reçoivent chacune un nouveau message — mais elles gardent leur même clé.
3. Une seule nouvelle notification apparaît : le compteur passe de 29 à 30.
4. Les cinq notifications mises à jour sont maintenant considérées comme « récentes ».

Le calcul :

```kotlin
recent >= RECENT_MIN
```

vaut alors 6, et un `SMS_BURST` est produit, bien qu’une seule notification supplémentaire ait été affichée. Cela contredit l’intention documentée du test « the same notification posted again is no more of them » : la notification ne compte pas davantage dans le total, mais elle compte à nouveau dans la récence.

Il faut distinguer au moins :

- l’identité et le nombre de notifications actuellement affichées ;
- le moment de création/arrivée de cette notification ;
- une simple mise à jour d’une notification existante.

Le test actuel ne vérifie que le compteur final, pas l’absence de franchissement erroné après des mises à jour.

---

### 3. Le changement d’application SMS par défaut peut faire rater toute une rafale  
**Certitude : élevée**

Le paquet par défaut est mémorisé pendant une minute :

```kotlin
const val SMS_APP_LOOKUP_MS = 60_000L
```

Scénario :

1. `A` est l’appli SMS par défaut.
2. L’utilisateur choisit `B` comme nouvelle appli SMS.
3. Pendant la minute où `defaultSmsApp()` renvoie encore `A`, 45 notifications sont publiées par `B`.
4. Ces notifications sont reçues par le listener, mais elles ne sont pas comptées :

```kotlin
sbn.packageName == defaultSmsApp()
```

Lorsque le cache expire, les notifications déjà passées ne sont pas rescannées. Le compteur de `B` repart donc de zéro ou d’une valeur partielle. La rafale peut être entièrement perdue, ainsi que l’avertissement de l’accueil.

Le commentaire indique que le changement est « vu dans la minute », mais il ne l’est pas réellement pour les notifications déjà reçues. Il faudrait invalider le paquet dès qu’un changement est détecté et reconstruire immédiatement le compteur à partir de `activeNotifications`.

---

### 4. La conversion `postTime` mural → horloge monotone est fausse après un changement d’heure  
**Certitude : élevée, cas dépendant du changement d’heure**

Sur les notifications déjà affichées lors d’une reconnexion, le code calcule :

```kotlin
val sinceBoot = SystemClock.elapsedRealtime() - System.currentTimeMillis()
...
it.postTime + sinceBoot
```

Cela suppose que l’horloge murale n’a pas changé entre la publication de la notification et la reconnexion.

Scénario :

1. Une notification est publiée.
2. L’écouteur reste déconnecté.
3. L’heure automatique, un changement manuel ou une synchronisation réseau modifie l’horloge.
4. L’écouteur se reconnecte.
5. L’ancien `postTime` est converti avec un décalage qui n’est plus celui du moment de publication.

Une notification récente peut alors être considérée comme âgée de plus de dix minutes, ou au contraire comme récente. Le franchissement de 30 ou 45 peut donc manquer, ou un `SMS_BURST` peut être déclenché à tort.

Il faut soit conserver une référence monotone au moment où les notifications sont observées, soit détecter les sauts d’horloge et ne pas utiliser les âges reconstruits comme preuve de récence.