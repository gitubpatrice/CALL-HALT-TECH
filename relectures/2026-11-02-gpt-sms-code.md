# Relecture du code par GPT — capteur des SMS de code — 2026-11-02

> Modèle **gpt-5.6-luna**, effort high, **0,017 $** (18 423 tokens d'entrée, 10 864 de sortie dont 9 840 de
> raisonnement) ; le diff `d0c1543..5eb542a` et 6 fichiers complets. Consigne : `consignes/2026-11-02-gpt-sms-code.md`.
> Menée en même temps que les relectures Claude (`2026-11-02-claude-sms-code.md`). Rapport brut ci-dessous.

## Tri (chaque constat vérifié dans le code)

| N° | Verdict | Suite |
|---|---|---|
| 1 | **réel** : la bibliothèque construisait toute la conversation pour en donner le dernier message ; la promesse dit « le dernier message » | **corrigé** : la dernière entrée lue directement (aussi relecture Claude, n° 5) |
| 2 | **réel** : le grand texte (`BigTextStyle`) n'était pas lu | **corrigé** ; la « boîte de réception » (`InboxStyle`, plusieurs messages) reste non lue, écrit |
| 3 | déjà écrit : une notification arrivée pendant que l'écouteur n'est pas lié n'est pas vue | P-2, étendu aux SMS de code |
| 4 | **réel** : le texte de masquage gardé une fois pour toutes (langue changée) | **corrigé** : lu dans la langue du téléphone, relu quand elle change (aussi relecture Claude, n° 3) |
| 5 | coût du fil principal | **corrigé** avec le n° 1 et la relecture Claude n° 1 |

**Bilan** : 3 défauts réels corrigés, 1 limite déjà écrite. Luna n'a pas vu les faux positifs de la règle
(hors de sa consigne) ni le coût de `getDefaultSmsPackage` ; la relecture Claude, pas le grand texte.

---

J’ai retenu cinq défauts réels.

1. **La promesse « seul le dernier message est lu » n’est pas tenue**

   `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification()` reconstruit la conversation à partir de `EXTRA_MESSAGES`. La bibliothèque désérialise les messages de la liste, pas uniquement le dernier. `lastOrNull()` ne limite donc pas la lecture aux données du dernier message.

   **Scénario :** une notification contient les 25 derniers messages d’une conversation, dont plusieurs textes sensibles anciens. Même si le dernier est « Bonjour » et qu’aucun STOP n’est déclenché, les textes précédents ont été extraits en mémoire par l’application.

   Ils ne sont ni journalisés ni persistés, mais ils ont tout de même été lus, contrairement à la promesse.

   **Certitude : élevée.**

2. **Des formats courants de notifications SMS sont ignorés**

   Le code ne lit que :

   - le dernier message d’une `MessagingStyle` ;
   - sinon `Notification.EXTRA_TEXT`.

   Il ignore notamment `Notification.EXTRA_BIG_TEXT` et `Notification.EXTRA_TEXT_LINES`, utilisés par des notifications `BigTextStyle` ou `InboxStyle`.

   **Scénario :** une appli SMS affiche `« Nouveau message »` dans `EXTRA_TEXT`, mais place le contenu détaillé — `« Votre code de validation est 482913 »` — dans `EXTRA_BIG_TEXT`. Ici, aucun code n’est détecté. Même problème pour un résumé de groupe qui ne contient que `« 2 nouveaux messages »` dans `EXTRA_TEXT`, alors que le texte utile est dans les lignes détaillées.

   Cela concerne aussi certaines implémentations RCS/MMS qui n’utilisent pas `MessagingStyle` pour leur aperçu textuel.

   **Certitude : élevée.**

3. **Les notifications déjà actives après une perte de liaison sont définitivement manquées**

   `onListenerConnected()` ne fait qu’écrire un log. Le service ne parcourt pas les notifications déjà actives avec `getActiveNotifications()`.

   **Scénario :** un SMS de code arrive pendant que le processus du listener est mort, que l’accès aux notifications vient d’être accordé, ou pendant la courte période de reconnexion du service. La notification reste visible dans la barre d’état, mais aucun nouveau `onNotificationPosted()` n’est forcément envoyé pour elle après la reconnexion. Le code n’est alors jamais évalué.

   En particulier, un code reçu pendant un appel peut ainsi ne produire aucun STOP.

   **Certitude : élevée.**

4. **La détection du masquage est dépendante d’une chaîne localisée mise en cache**

   `maskedText` est initialisé une seule fois par `lazy`, puis comparé par égalité exacte à `text.toString()`.

   **Scénario concret :** le service traite d’abord une notification alors que le téléphone est en anglais ; `maskedText` vaut alors `“Sensitive notification content hidden”`. L’utilisateur passe ensuite le téléphone en français sans que le processus du service soit recréé. Android remplace le texte d’un nouveau code masqué par `“Le contenu sensible de la notification a été masqué”`, mais l’application le traite comme un texte ordinaire : le code masqué est manqué.

   Il existe aussi une dépendance non contractuelle aux variantes OEM et aux éventuelles ponctuations/variantes du texte de masquage. La mesure Samsung ne couvre pas ce chemin puisqu’aucun masquage n’y est observé.

   **Certitude : élevée pour le changement de langue avec service conservé ; moyenne à élevée pour les variantes OEM.**

5. **Une partie coûteuse de l’analyse s’exécute sur le thread principal du listener**

   `onNotificationPosted()` est exécutée sur le thread principal. Avant de soumettre le travail à `SensorQueue`, il effectue :

   - la recherche du paquet SMS par défaut ;
   - l’extraction complète de la `MessagingStyle` ;
   - la désérialisation potentielle de nombreux messages ;
   - les expressions régulières sur le dernier texte.

   **Scénario :** l’appli SMS publie une notification contenant une longue conversation, un gros texte RCS/MMS ou plusieurs mises à jour rapprochées. L’extraction et l’analyse bloquent le thread principal du service ; l’envoi du signal à `SensorQueue`, donc l’affichage du STOP, est retardé, voire le listener peut être considéré comme lent par le système.

   **Certitude : élevée.**

Je ne retiens pas de défaut supplémentaire concernant le profil professionnel sur les seuls fichiers fournis : les notifications et le paquet SMS par défaut sont généralement traités dans le contexte utilisateur du listener. Un problème de profil de travail nécessiterait de confirmer que ce listener reçoit effectivement des notifications d’un autre profil.