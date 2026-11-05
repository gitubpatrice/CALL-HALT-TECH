# Relecture GPT — une appli de prise en main en marche (D-032) — 2026-11-05

> `gpt-5.6-luna`, effort `high` ; consigne : `consignes/2026-11-05-gpt-prise-en-main-ouverte.md`. Envoyé : le diff du
> code (`app/src/main/java` et le texte français, fichier neuf compris), puis `NotificationListener.kt`, `LatestSms.kt`
> et `NotificationSignal.kt` entiers ; 30 872 caractères. 7 403 jetons d'entrée, 13 735 de sortie : **0,018 $**.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | À la reliaison, une notification déjà vue et dite, de moins de 2 min, est redite : un second STOP | **réel** | **corrigé** : l'heure de publication de chaque clé est retenue ; vue telle quelle, rien ; repostée sous la même clé, une nouvelle. **Mesuré** : le premier essai donnait encore un second STOP — Android détruit l'écouteur qu'il délie, et la mémoire partait avec lui ; elle vit désormais dans le processus (`@Singleton`), remesuré : rien |
| 2 | Lecture des notifications affichées impossible à la reliaison : garder ce qui était connu fait manquer une notification retirée puis reposée sous la même clé | **réel** ; contredit le choix fait sur la relecture Claude (2) | **corrigé** : tout est oublié, côté protecteur (au pire un STOP de trop) |
| 3 | Une notification apparue puis retirée pendant l'absence de l'écouteur est perdue | réel, inévitable (aucun rappel pendant l'absence) | **écrit** dans D-032 |
| 4 | Le contrôle de répétition des codes SMS ne garde que le dernier code, et aucun sans heure | réel, **antérieur** à ce changement, côté protecteur (un STOP de trop, absorbé s'il est sans réponse) | rien maintenant |
| 5 | Une notification d'une appli de la liste, non nouvelle, retombait dans le chemin banque et SMS | réel, sans effet connu | **corrigé** : ses notifications ne sont rien d'autre |

## Le rapport, tel que rendu

Voici les défauts réels que je retiens.

1. **Une reliaison peut produire un STOP pour une notification déjà connue** — **certitude : élevée**

`alreadyShown()` efface `shown`, puis renvoie toutes les notifications encore affichées dont `postTime` est dans les deux dernières minutes. Elle ne distingue pas :

- une notification postée pendant la coupure ;
- une notification déjà vue avant la coupure, mais postée récemment.

Scénario :

1. RustDesk poste `K` ;
2. `onNotificationPosted(K)` l’ajoute à `shown` et déclenche déjà un STOP ;
3. l’écouteur est déconnecté dix secondes plus tard ;
4. il est reconnecté presque immédiatement ;
5. `K` est toujours affichée et a moins de deux minutes ;
6. `alreadyShown()` la renvoie à nouveau.

Pendant un appel, le même démarrage peut donc générer un STOP répété. Le cas existe aussi après redémarrage du processus si la notification avait été vue par l’ancienne instance.

Le seuil de deux minutes permet de couvrir une coupure, mais il ne permet pas de savoir si la notification était déjà connue. Il faudrait au minimum conserver l’instant de déconnexion et exclure les notifications déjà connues avant celle-ci.

2. **Une erreur de lecture de `activeNotifications` laisse un état de clés périmé** — **certitude : élevée sur le défaut, moyenne sur la fréquence**

`shownNow()` transforme toute `RuntimeException` en `null`. Dans ce cas, `onListenerConnected()` fait `return` sans réinitialiser `remoteControl`.

Scénario :

1. la clé `K` est dans `shown` ;
2. l’écouteur est déconnecté ;
3. `K` est retirée ou remplacée pendant la coupure ;
4. à la reliaison, `activeNotifications` échoue et `shownNow()` renvoie `null` ;
5. la nouvelle notification est repostée avec la même clé `K` ;
6. `posted()` fait `shown.add(K)`, qui renvoie `false` ;
7. le démarrage est manqué.

Le `catch` empêche le service de tomber, mais il ne déclenche aucune resynchronisation ultérieure. La même problématique existe si `activeNotifications` renvoie `null`.

3. **Une notification apparue puis retirée pendant la coupure est forcément manquée** — **certitude : élevée**

À la reliaison, le code ne dispose que de `activeNotifications`. Les callbacks `onNotificationPosted` et `onNotificationRemoved` n’ont pas été reçus pendant l’absence du listener.

Scénario :

1. l’écouteur est coupé pendant un appel ;
2. RustDesk poste une notification de demande de connexion ;
3. l’utilisateur ou l’application la retire avant la reliaison ;
4. aucune notification RustDesk correspondante n’est encore active ;
5. `alreadyShown()` ne voit rien et aucun STOP n’est émis.

Cela contredit le contrat formulé comme « chaque nouvelle notification ». Si le contrat est volontairement limité aux notifications encore affichées à la reliaison, ce n’est pas un bug supplémentaire, mais avec le contrat actuel c’est une perte de détection inévitable.

4. **La déduplication des codes SMS ne déduplique que le dernier code, et jamais ceux sans horodatage** — **certitude : élevée**

`lastCode` ne contient qu’une seule identité, et `isRepost()` ne reconnaît un doublon que si le doublon arrive immédiatement après le premier.

Scénario d’interleaving :

1. le code A est reçu : `lastCode = (A, tA)` ;
2. le code B est reçu : `lastCode = (B, tB)` ;
3. Android redélivre ou republie A ;
4. `(A, tA) != (B, tB)` ;
5. A est traité comme un nouveau code et peut provoquer un second STOP.

Autre scénario, explicitement prévu par `LatestSms` :

1. la notification contient un code mais pas de date, donc `sentAtMillis == 0` ;
2. elle est livrée deux fois avec la même clé ;
3. la condition `sentAtMillis > 0` est toujours fausse ;
4. les deux livraisons sont traitées.

Le contrôle devrait conserver plusieurs identités récentes, ou au moins une identité par notification/conversation, avec une politique d’expiration adaptée.

5. **Le chemin remote peut retomber dans le chemin banque/SMS pour une notification remote déjà connue** — **certitude : élevée sur le comportement, dépendante d’une collision de canal**

Cette expression :

```kotlin
remoteControl.posted(sbn.notice(), at)
    ?: bankRequestOrCode(sbn, at)
```

appelle `bankRequestOrCode()` lorsque `posted()` renvoie `null`. C’est notamment le cas d’une notification remote déjà présente dans `shown`, ou d’un résumé de groupe.

Les identifiants de canaux sont locaux à chaque application. Une application de contrôle distant peut donc utiliser un canal nommé `securpass_id`, ou un autre identifiant reconnu par `BANK_REQUEST_CHANNELS`.

Scénario :

1. une première notification RustDesk est correctement classée remote ;
2. RustDesk met à jour cette même notification ;
3. la clé est déjà dans `shown`, donc `posted()` renvoie `null` ;
4. `bankRequestOrCode()` lit le canal ;
5. le canal vaut `securpass_id` ;
6. la mise à jour est classée `BANK_VALIDATION_REQUEST` et peut produire un STOP bancaire supplémentaire.

La lecture de `channelId` est déjà nécessaire au chemin bancaire, mais le fallback rend possible cette double classification. Si les collisions de canaux sont volontairement acceptées comme faux positifs protecteurs, ce point est une conséquence assumée plutôt qu’un défaut produit.

Je ne vois pas de lecture de contenu dans le capteur remote lui-même : `notice()` ne lit que le paquet, la clé, `postTime` et le drapeau de résumé. Les textes ne sont lus que par le chemin SMS existant.