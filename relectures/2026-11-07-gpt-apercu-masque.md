# Relecture GPT — l'aperçu masqué de l'appli SMS, dit sur l'accueil (audit FN-2, D-037) — 2026-11-07

> `gpt-5.6-luna`, effort `high` ; consigne : `consignes/2026-11-07-gpt-apercu-masque.md`. Envoyé : le diff de la branche
> (`origin/main..093c57a`), puis `LatestSms.kt` et `NotificationListener.kt` ; 62 520 caractères. 15 990 jetons
> d'entrée, 8 173 de sortie : **0,013 $**. Après la relecture Claude (`2026-11-07-claude-apercu-masque.md`).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Un vrai SMS dont le texte est exactement « Nouveau message » ferait dire « masqué » | **réel, accepté** : il faut ce texte seul, venu d'un tiers, sous SMS Tech ; le message suivant l'efface ; une preuve indépendante du texte demanderait de comparer plusieurs messages, donc d'en garder | écrit (D-037) |
| 2 | L'appli SMS par défaut est relue au plus une fois par minute par l'écouteur : juste après un changement, un message de la nouvelle appli n'est pas lu | **réel, transitoire, déjà écrit** (la même minute vaut pour les codes, KDoc de `defaultSmsApp`) | écrit (D-037) |
| 3 | Une notification déjà affichée quand l'écouteur se (re)connecte n'est pas lue | **réel** | **corrigé** : à la connexion, les messages de l'appli SMS déjà affichés sont lus pour l'aperçu seulement, jamais comme un nouveau code ; **mesuré** (écouteur coupé, un SMS « Nouveau message », rien de gardé ; écouteur rétabli → `hidden_in = com.filestech.sms`) |
| 4 | Aucun test n'instancie `SmsPreview` (une `seen` vide passerait) | **réel** | **corrigé** : `SmsPreviewOnDeviceTest` (gardé, retrouvé par une autre instance, effacé par un message montré) ; vert sur l'émulateur Android 16 |

Le rapport ne relève aucune fuite du texte d'un message.

## Le rapport, tel que rendu

### 1. Faux avertissement sur un SMS dont le contenu est exactement le texte sentinelle — **certitude élevée**

`hiddenInAfter()` ne sait pas si `"Nouveau message"` vient réellement de SMS Tech ou si c’est le contenu du SMS reçu.

Scénario :

1. SMS Tech affiche normalement les aperçus.
2. Un correspondant envoie réellement le texte `Nouveau message`.
3. La notification contient ce texte et un expéditeur.
4. `receivedInConversation` vaut `true`, puis `hiddenInAfter()` l’interprète comme le texte de remplacement de SMS Tech.
5. Le nom de paquet est conservé et l’accueil affiche l’avertissement.

Le test ne couvre que « autre texte », pas un vrai message ayant exactement l’un des textes sentinelles. Il faudrait accepter ce risque explicitement, ou ajouter un élément de preuve indépendant du contenu — par exemple comparer plusieurs notifications/messages, si cela reste compatible avec la confidentialité.

---

### 2. Avertissement absent après un changement récent d’application SMS par défaut — **certitude élevée**

`NotificationListener.defaultSmsApp()` met en cache le paquet pendant 60 secondes, alors que `ProtectionViewModel` relit directement l’application par défaut.

Scénario :

1. Google Messages est l’application SMS par défaut ; le listener a mémorisé son paquet.
2. L’utilisateur choisit SMS Tech comme nouvelle application par défaut.
3. Il reçoit immédiatement un SMS avec les aperçus masqués.
4. Pendant la fenêtre de cache, `defaultSmsApp()` retourne encore Google Messages.
5. La condition `sbn.packageName == defaultSmsApp()` échoue : `SmsPreview.seen()` n’est jamais appelé.
6. L’accueil relit, lui, SMS Tech comme application actuelle, mais `hiddenIn` vaut toujours `null` : aucun avertissement.

Le défaut est transitoire, mais réel. Le cache devrait être invalidé au changement de rôle SMS, ou la notification devrait vérifier le paquet courant sans ce cache dans ce chemin.

---

### 3. Avertissement absent pour une notification reçue pendant que le listener était déconnecté — **certitude élevée**

À la reconnexion, `onListenerConnected()` récupère `activeNotifications`, mais ne passe que les notifications de contrôle à `remoteControl`. Il ne traite pas les notifications SMS déjà présentes.

Scénario :

1. SMS Tech masque les aperçus.
2. Une notification SMS arrive pendant une interruption du `NotificationListenerService`, ou avant que l’accès aux notifications soit accordé.
3. Le service se reconnecte alors que la notification est encore affichée.
4. `onListenerConnected()` ne l’envoie pas à `bankRequestOrCode()`.
5. `SmsPreview.seen()` n’est jamais appelé et l’accueil reste silencieux.

Le cas est encore plus net si l’utilisateur répond ensuite : la notification peut alors ne contenir comme dernier message que sa propre réponse, que le code ignore volontairement. Le traitement des notifications actives à la reconnexion devrait au moins initialiser `SmsPreview`, même s’il ne faut pas retraiter ces notifications comme de nouveaux codes.

---

### 4. Les tests peuvent rester verts avec une implémentation de `SmsPreview` non fonctionnelle — **certitude certaine**

`SmsPreviewTest` teste uniquement les fonctions pures `hiddenInAfter()` et `previewHiddenNow()`. Aucun test n’instancie `SmsPreview` et ne vérifie :

- l’appel réel à `seen()` depuis `NotificationListener` ;
- l’écriture dans `SharedPreferences` ;
- la restauration après recréation du singleton/processus ;
- la propagation jusqu’au `ProtectionViewModel` et à l’accueil.

Scénario de régression :

```kotlin
fun seen(packageName: String, latest: LatestSms) {
    // implémentation accidentellement vide
}
```

Tous les tests actuels de `SmsPreviewTest` restent verts, alors que la fonctionnalité ne détecte plus rien et ne persiste plus aucun état. Un test d’intégration avec un faux `Context`/`SharedPreferences`, ou un test du composant réel avec notification puis recréation, est nécessaire.

Je n’ai pas relevé de fuite du texte du message dans le diff : il reste en mémoire pour l’analyse, mais le stockage et les logs ajoutés ne conservent que le paquet ou la classe d’exception.
