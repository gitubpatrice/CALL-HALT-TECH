# Relecture Claude — un appel ne survit pas à un redémarrage — 2026-11-02

> Un sous-agent Claude `general-purpose`, en lecture seule, sur le commit `c087b9f`, sans connaître le raisonnement
> de l'auteur. 0 $ (sous-agent). Menée en même temps que la relecture GPT (`2026-11-02-gpt-redemarrage.md`).
> Rapport condensé fidèlement.

## Tri

| N° | Gravité | Constat | Verdict | Suite |
|---|---|---|---|---|
| 1 | **la plus grave** | Un redémarrage rapporté en retard perd le vrai appel : si le premier signal après le démarrage est un début d'appel qui ne reçoit pas le redémarrage (mémoire en panne puis revenue, ce que le code permettait en se taisant), le moteur absorbe ce vrai appel dans le fantôme ; au signal suivant, le redémarrage termine le fantôme, le vrai appel n'a jamais été gardé, sa fin est ignorée : **un STOP manqué**. Et la prémisse « un redémarrage inventé terminerait un appel en cours » est fausse pour le démarrage EN COURS | **réel**, vérifié dans le moteur (`DetectionEngine.kt` : `sameConversation`, puis la fin ignorée) | **corrigé** : dans le doute, le redémarrage est rapporté ; D-020, la KDoc du port et du signal réécrites |
| 2 | moyen | Le compteur est retenu avant que le redémarrage soit validé dans la base : un processus tué entre les deux (première ouverture de Room, au démarrage, sous charge) le perd, et le fantôme de 8 h revient | **réel** | **corrigé** : retenu une fois traité (`Restart.record`), au moins une fois ; idempotent (test) |
| 3 | faible | L'heure du démarrage vient de l'horloge du moment : reculée au démarrage (horloge remise à zéro), le fantôme n'est pas terminé ; corrigée en avant entre l'heure d'un début d'appel et son traitement, la fin du fantôme tombe après ce début et `repeatOfEnded` absorbe le nouvel appel | réel, rare | le second cas **corrigé** (jamais après le signal qui suit : test) ; le premier **écrit** (D-020, P-13) |
| 4 | faible | Un « redémarrage léger » (le système seul) ne remet pas `elapsedRealtime` à zéro | à vérifier | **écrit** (P-7, D-020) |
| 5 | faible | La lecture du compteur hors du `try`, contre le contrat du port : un signal sensible perdu si elle lève | réel (lever : peu probable) | **corrigé** (test) |
| doc | — | KDoc du signal et du port fausses sur le chemin de panne ; D-020 « mémoire en panne : rien » inexact | réel | réécrites avec le n° 1 |

**Vérifié sans défaut** : sur le chemin normal, le redémarrage est rapporté au premier signal, avant lui ; il ne peut
terminer aucun appel réel ; deux clés dans un même DataStore, sûres ; jamais rapporté deux fois en même temps (une
seule file, `StopAnswers` y passe aussi) ; un échec de `PhoneRestarted` ne peut être qu'une annulation aujourd'hui
(le magasin rattrape le reste) ; les tests prouvent ce que disent leurs noms ; `REPRISE.md` exact.

## Rapport, condensé

Le correctif fait ce qu'il dit sur le chemin normal ; ses défauts sont sur les chemins rares. (1) Se taire quand la
mémoire est en panne ne protège rien et ouvre un STOP manqué : l'heure du démarrage en cours ne termine que des
appels qui ne peuvent plus être en cours. (2) Retenir le compteur avant la validation : « au plus une fois » ; il faut
« au moins une fois », puisque `PhoneRestarted` est idempotent. (3) L'horloge du moment : deux cas, l'un ne termine pas
le fantôme, l'autre absorbe un nouvel appel. (4) Redémarrage léger, à vérifier. (5) Lecture hors du `try`. À la
question posée sur l'échec de `PhoneRestarted` : si le signal suivant était alors perdu, ce serait le mauvais côté
(un STOP manqué plutôt qu'un STOP de trop) ; inatteignable aujourd'hui. Verdict : correctif juste pour le cas mesuré,
qui ne termine aucun vrai appel sur le chemin normal ; fermer (1) et (2) avant publication.
