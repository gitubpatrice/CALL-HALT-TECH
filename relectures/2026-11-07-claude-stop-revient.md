# Relecture Claude : un STOP quitté revient une fois, puis le proche est prévenu (audit FN-4, D-038) — 2026-11-07

> Agent `android-code-quality-deep-dive`, lecture seule, sur le commit `b55e96e` ; consigne : justesse des règles,
> plateforme (réveils, intent de suppression, récepteur), persistance, tests, ce que les mesures prouvent. 0 $
> (sous-agent).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | La notification d'un STOP qui revient refusée : `StopNotShown` produit le SMS au proche, mais le cas d'usage jetait les décisions de ce retour — le proche n'était jamais prévenu | **réel, grave** | **corrigé** : `ProcessSignalUseCase` exécute ce que le « non affiché » demande (hors STOP), puis relaie l'erreur ; test du cas d'usage avec un exécuteur qui refuse le retour |
| 2 | Aucune borne de retard : le processus tué, la ligne `stop_return` restait ; le lendemain, un autre réveil la faisait ressortir pendant un autre appel (STOP, ou SMS des heures après) | **réel** | **corrigé** : un retour ne vaut que pour le même appel (commencé avant le départ du STOP) ; plus de 15 min de retard, abandonné sans STOP ni SMS ; un réveil au démarrage du processus relit les retours ; tests |
| 3 | L'attente d'une coroutine suit l'horloge monotone, qui s'arrête quand le téléphone dort profondément : le commentaire disait « temps écoulé depuis le démarrage » | **réel** (le commentaire) ; l'effet est à mesurer | **corrigé** : commentaire et limite écrits (D-038) ; pendant un appel la téléphonie garde le téléphone éveillé ; à mesurer sur le S24 après un appel, écran éteint |
| 4 | La tolérance de 5 s faisait prendre un appel fini juste avant le départ du STOP pour un raccrochage : pas de retour | **réel** | **corrigé** : le raccrochage se lit strictement après le départ ; test à la frontière |
| 5 | L'intent de suppression au toucher (`autoCancel`) : documenté comme absent, non mesuré | réserve | **mesuré le 2026-11-09** (émulateur Android 16) : touchée, la notification revenue ouvre l'écran STOP, aucun SMS (`2026-11-09-gpt-stop-revient.md`) |
| 6 | Tests : frontière du raccrochage, réveil tardif, rafale pendant le retour, protection coupée, chemin « non affiché » de bout en bout | **réel** | **ajoutés** (moteur et cas d'usage) |
| 7 | Ce que les mesures prouvent : le chemin heureux et le raccrochage franc ; pas la réception du SMS par un autre téléphone (D-034 le dit), ni le processus tué, l'écran éteint, le toucher | juste | écrit |
| 8 | Fenêtres, remplacement par un STOP plus récent ou une réponse, redémarrage, aller-retour des deux horloges | sans défaut | rien |

## Vérifications après correction

Gate local vert (628 tests). Restent, à la reprise : la relecture externe, la mesure du toucher, les tests de stockage
sur l'émulateur après ces corrections.
