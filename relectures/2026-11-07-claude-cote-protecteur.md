# Relecture Claude — échouer du côté protecteur (audit C1, motifs 4 et 12, trois axes Q3), 2026-11-07, avant commit

Un agent `android-code-quality-deep-dive` (robustesse), sur l'arbre de travail de `fix/fail-protective`. Coût : 0 $
(sous-agent). Aucun défaut bloquant.

| # | Constat (gravité, certitude) | Tri | Suite |
|---|---|---|---|
| L1 | Une notification illisible n'a pas d'heure : jamais prise pour une republication, reposée en boucle elle refait un STOP une fois le précédent répondu (faible) | Réel, côté protecteur (un écran à fermer, jamais un silence) | **Écrit** dans D-021 |
| L2 | Une erreur de programmation sur les réglages n'est plus que journalisée, et rien n'est journalisé en version publiée (faible) | Réel ; c'est le contrat voulu (« ne lève jamais ») | Accepté |
| L3 | `farewell` et `setUp` hors du bloc protégé, si l'envoi lève (faible) | **Faux** : l'envoi rattrape toute erreur et rend `null` (`TextSender.send`) | Rien |
| T1 | Le branchement dans l'écouteur (`NotificationListener.latestSms`) n'est pas testé : il pourrait rendre `null` de nouveau (moyen) | Réel ; une ligne qui appelle la fonction testée, dans un service qui ne se teste que sur un appareil | **Écrit** comme limite ici ; la fonction elle-même testée, contrôle négatif fait |
| T2 | Le test des valeurs par défaut les comparait au constructeur : il suivrait un changement des défauts (faible) | **Réel, corrigé** | Test « the defaults protect » : la protection en marche, la fenêtre de 15 minutes, écrites |
| D | D-021 omettait le SMS au proche et disait « reposté ne compte qu'une fois » sans réserve | **Réel, corrigé** | Précisé |

Vérifié, sans défaut : une notification lisible sans message reste « rien » (seule une lecture qui lève devient un
message masqué) ; `Flow.catch` laisse passer l'annulation ; les valeurs par défaut sont les protectrices (la protection
remise en marche si le fichier est illisible) ; dans l'écran du proche, l'annulation passe avant l'erreur ; le lanceur
rattrape tout ce que `startActivity` lève (sauf les `Error`), et son `false` mène à la notification, puis au SMS au proche.
