# Relectures Claude — l'état réel de l'appel, lu en direct (D-035, FN-1), 2026-11-07, avant commit

Deux agents, en parallèle, sur l'arbre de travail de `fix/live-call-state` : `general-purpose` (justesse, avec un harnais
de 8 scénarios sur les classes compilées du moteur, de vrais horodatages) et `android-code-quality-deep-dive`
(robustesse et sécurité). Coût : 0 $ (sous-agents).

## 1. Robustesse et sécurité (`android-code-quality-deep-dive`)

| # | Constat (gravité, certitude) | Tri | Suite |
|---|---|---|---|
| M1 | Une exception de la lecture autre qu'un refus de permission sort avant l'action : le SMS de code ou la demande de validation est perdu, sans STOP — le faux négatif que le correctif supprime (moyen ; chemin confirmé) | **Réel, corrigé** | La lecture rattrape toute `RuntimeException` ; le point d'entrée rattrape tout ce qui vient de la lecture (puis, sur la relecture GPT, 3, de son traitement aussi). Test « a call state that cannot be read never loses the action » |
| B1 | Une action horodatée juste avant un appel décroché pendant qu'elle attendait son tour est vue pendant l'appel (faible) | Réel, côté protecteur | **Écrit** (commentaire du point d'entrée, limites de D-035) |
| B2 | Un « pas d'appel » rendu par la plateforme sur sa propre panne ne se distingue pas d'un vrai (faible) | Réel, sans parade | **Écrit** (voir justesse, F2) |
| B3 | La lecture est un appel au système : lent, il retient la file unique (faible) | Réel ; la file relâche les diffusions à 8 s | Rien : la lecture est brève |

Vérifié, sans défaut : lecture hors du fil principal (la file tourne sur `Dispatchers.IO`) ; démarrage sans accès à la
base sur le fil principal (`Provider` résolu dans la file) ; ni cycle ni portée incohérente dans Hilt ; les deux
`@Suppress` justifiés ; aucune donnée gardée de plus, pas de numéro lu (`PRIVACY.md` reste exact).

## 2. Justesse (`general-purpose`)

| # | Constat (gravité, certitude) | Tri | Suite |
|---|---|---|---|
| F1 | `getCallState` compte un appel auto-géré (WhatsApp) ; si la diffusion ne le compte pas, la lecture crée un appel qu'aucune diffusion ne termine, et l'action d'après, des heures plus tard, fait un faux STOP « votre appel vient de se terminer » et un SMS au proche (élevé s'il est confirmé ; les sources d'Android se contredisent) | **Réel en risque, corrigé sans attendre la mesure** | La lecture est réduite à ce qui ne peut pas contredire la diffusion (`liveState`) : décroché seulement pour un appel géré par le téléphone sans sonnerie ; aucun appel seulement sans aucun appel, d'aucune sorte ; un appel auto-géré seul n'est dit dans aucun sens. La question est versée à P-4, à mesurer |
| F2 | Le commentaire « la diffusion suivante répare » un faux « pas d'appel » de Telecom est faux : la vraie fin est ignorée (faible ; il faut un serveur système mort) | **Réel** | Commentaire et D-035 réécrits ; écrit comme limite |
| F3 | La direction d'un appel sortant qui réveille le processus est perdue : la lecture au démarrage passe avant la diffusion (faible ; aucune règle ne la lit aujourd'hui) | **Réel, corrigé** | Le moteur prend la direction d'un second début quand celle de l'appel en cours est inconnue, sans toucher à son heure de début ; deux tests |
| F4 | Un appel décroché ET terminé avant le premier déverrouillage n'ouvre aucune fenêtre (faible) | Réel | **Écrit** dans D-035 (seul le journal d'appels le dirait, écarté) |
| F5 | Un second appel qui sonne au moment de l'action masque un appel inconnu du moteur (faible) | Réel | **Écrit** dans D-035 |
| F6 | = B1 | | **Écrit** |
| F7 | Au-delà du plafond de 8 h, un téléphone toujours en appel recommence un appel à chaque action (faible ; le téléphone le dit) | Réel | **Écrit** dans D-035 |
| F8 | P-7 et D-035 en disaient plus que le code | Réel | **Réécrits** |
| F9 | Pas de test du contrôle au démarrage | Réel | **Fait** : `CallStateAtStartTest` (horodaté au démarrage, rien sur le fil appelant, un état inconnu ne change rien) |

Vérifié, sans défaut : FN-1 fermé (scénario rejoué avec de vrais horodatages) ; l'ordre dans la file (redémarrage,
lecture, action) ; les tests du point d'entrée discriminants (le test de l'ordre échoue si l'ordre est inversé, sans la
lecture, ou avec une autre heure) ; le contrôle négatif (4 tests).
