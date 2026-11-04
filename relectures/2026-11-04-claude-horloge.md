# Relecture — deux horloges dans chaque signal (P-13, D-026) — 2026-11-04

> Agent `android-code-quality-deep-dive`, axes correction puis sécurité, lecture statique ; 0 $ (sous-agent). Objet :
> `Moment` (heure civile + temps écoulé depuis le démarrage, avec le numéro du démarrage), `PhoneClock`, le redémarrage
> (`DetectionEngine.onPhoneRestarted`, `BootCountRestarts`), la base en version 2 (migration automatique), les capteurs,
> avant commit. Lui étaient donnés comme acquis : 321 tests JVM et 25 sur émulateur verts, le compteur de démarrages lu
> dans les sources d'Android 16, `elapsedRealtime` qui continue après un redémarrage d'Android seul (mesuré).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| M1 | Moyen : le redémarrage placé au démarrage du processus ; un vrai redémarrage dont l'appli ne se réveille que deux heures plus tard rouvrait la fenêtre de l'appel coupé — un faux STOP de 15 min sur la demande de l'utilisateur lui-même, que l'ancien code ne faisait pas | **réel**, introduit par ce changement | **corrigé** sans rien garder de plus : l'heure d'allumage du téléphone se déduit de chaque moment (heure civile moins temps écoulé). Allumé après le début de l'appel : vrai redémarrage, l'appel coupé au plus tard là, comme avant. Sinon (Android seul, ou heure reculée) : le démarrage du processus. Test et contrôle négatif ; mesuré sur l'émulateur Android 16 (coupure nette, démarrage à froid) |
| M2 | Moyen : un appel au-delà du plafond de 8 h restait « en cours » ; le raccrochage suivant (un appel manqué, des heures après) le fermait là et rouvrait 15 min de fenêtre | **réel**, antérieur à ce changement (aussi dans un même démarrage) | **corrigé** : une fin qui vient après le plafond ferme l'appel au plafond, sans fenêtre ; au redémarrage, un appel plafonné est fermé au plafond. Test et contrôle négatif |
| F1 | Faible : un échec de lecture du compteur gardé pour toute la vie du processus | réel | **corrigé** : relu au signal suivant |
| F2 | Faible : l'ordre n'est pas transitif quand l'un des moments n'a pas de temps écoulé | réel, sans effet | **laissé** : le moteur ne compare que deux moments à la fois (deux canaux au plus) ; la KDoc le dit, ne jamais trier une liste mélangée |
| F3 | Faible : le plafond et l'écart fin → redémarrage restent mesurés à l'heure civile, réglable | réel, le compromis | **écrit** dans D-026 : il faut un redémarrage, et l'escroc doit rappeler, ce nouvel appel est protégé |
| F4 | Faible : le compteur qui augmente après un redémarrage d'Android seul, non mesuré | **déjà mesuré** le même jour (émulateur Android 16 : 29 → 30) | rien ; les téléphones de Patrice ne se redémarrent pas ainsi sans root |
| F5 | Faible : le réveil du rappel d'appel long devra se programmer sur le temps écoulé, pas l'heure civile | réel, pour plus tard | **écrit** dans P-8 |
| F6 | Faible : deux KDoc imprécises (temps écoulé « négatif », `RestartSource`) | réel | **corrigé** |

**Points vérifiés sans défaut** (par l'agent) : aucune exception possible sur l'invariant « fin pas avant le début », même
avec des moments mélangés ; un redémarrage rapporté deux fois ne change rien ; une mise à jour en plein appel garde
l'appel (rangées sans temps écoulé, heure civile) ; migration 1 → 2 qui n'ajoute que six colonnes vides ; aucune entrée
qu'une autre appli contrôle ne peut fausser l'horloge ou le compteur (`BOOT_COUNT` est un réglage système).

**Bilan** : aucun constat critique ni élevé, aucun de sécurité ; 2 moyens réels corrigés (dont une régression de ce
changement), 3 faibles corrigés ou écrits, 1 déjà mesuré, 1 laissé avec sa raison.
