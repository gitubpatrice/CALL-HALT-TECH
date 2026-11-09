# Relecture Claude : les faux positifs de la règle « rafale de SMS » (audit N-3, D-040) — 2026-11-09

> Agent `regles-faux-positifs-auditor`, lecture seule, sur le commit `9269a08` (la première version : 10 conversations
> différentes en 2 minutes) ; consigne : les entrées légitimes qui déclenchent le STOP et le SMS au proche, puis
> l'attaque que la règle manquerait. L'agent a lu les sources de SMS Tech et simulé la règle (scripts jetables, sans
> build ni adb). 0 $ (sous-agent).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | **SMS Tech fait une notification par message**, pas par conversation : la règle comptait 10 messages, pas 10 numéros ; un petit-fils qui écrit dix SMS courts après un appel faisait un STOP « de numéros différents » et un SMS au proche | **réel, grave** — vérifié dans les sources de SMS Tech : `IncomingMessageNotifier.kt:160` (identifiant tiré du message), `:245-249` (`notify(tag de conversation, id par message)`) | **règle refaite** : compter les notifications de l'appli SMS affichées (ce qu'Android plafonne), un message lu retire la sienne ; le STOP au franchissement de 30, puis 45, avec au moins 5 arrivées en 10 minutes ; « de numéros différents » retiré du texte (Patrice) |
| 2 | Les vœux de minuit, de dix numéros ou plus, pendant l'appel de minuit | **réel** (probable, une fois l'an, le même soir pour beaucoup) | **corrigé** par la nouvelle règle : il faut 30 notifications affichées |
| 3 | Les réponses à une naissance, un décès, un changement de numéro annoncés à beaucoup de contacts | **réel** (rare, mal placé) | **corrigé** de même |
| 4 | Le réseau qui revient, les SMS en attente arrivés ensemble | **réel** (rare) | **corrigé** de même (30, pas 10) |
| 5 | Le compteur vidé après une rafale : les mêmes conversations qui écrivent encore en refont une | **réel** | **corrigé** : plus de compteur à vider, le nombre affiché franchit un seuil en montant, une fois |
| 6 | Les notifications qui ne sont pas des messages (échec d'envoi) comptées | **réel** (rare) | **voulu désormais** : Android les compte dans son plafond, la règle aussi |
| — | **L'inverse** : un flot lent et régulier (un SMS toutes les 15 s) remplit le plafond en 12 minutes sans jamais faire 10 en 2 minutes ; un flot de MMS sans texte n'était pas compté | **réel** | **corrigé** : compter ce qui est affiché, quoi qu'il dise ; un flot plus lent qu'un SMS toutes les deux minutes reste écrit comme limite (D-040), l'accueil le dit |
| — | Une appli SMS déjà à son plafond avant l'appel | limite déjà écrite | **l'accueil le dit désormais** dès 45 notifications (Patrice, « oui, sur l'accueil ») |

L'agent n'a rien trouvé à la connexion de l'écouteur (le compteur n'y était pas appelé), dans les résumés de groupe,
les notifications livrées deux fois, les MMS de groupe de Google Messages, les alertes d'urgence, les SMS en plusieurs
parties, le double SIM.

## Vérifications après correction

Gate de la CI vert en local : **657 tests** (126 domaine, 531 appli ; `SmsAppNoticesTest` : la rafale à 30 puis 45,
la même notification republiée, le téléphone qui garde ses messages non lus, la connexion qui recompte, deux applis à
part, l'accueil à 45). **Mesuré** (émulateur Android 16, Google Messages) : le STOP au franchissement de 30 pendant
l'appel, de 45 juste après ; l'avertissement de l'accueil à 48 notifications, parti une fois les notifications
balayées.
