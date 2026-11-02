# Relectures Claude — le capteur de l'état de l'appel (2026-11-02)

> Deux sous-agents Claude, lecture seule, 0 $, sur la branche `feat/call-state-sensor` avant le premier commit :
> une relecture générale (`general-purpose` : justesse face au moteur, cycle de vie, sécurité, tests, textes)
> et une relecture de sécurité (`android-code-quality-deep-dive`, axe sécurité, exigée par le rappel de fin de
> tour). Ils n'avaient que le code et la doc, pas mon raisonnement. Rapports condensés fidèlement ; « Suite »
> dit ce qui en a été fait.

## Relecture générale — aucun constat élevé

| N° | Gravité | Constat | Suite |
|---|---|---|---|
| M1 | MOYENNE | L'horloge recule entre deux appels : le début du second, estampillé avant la fin du premier, est pris pour une répétition (règle GPT 6) ; STOP manqué au-delà des 15 premières minutes du second appel | **Écrit comme limite connue** (`CallStateSensorTest`, « the known limit ») ; correctif = P-13 (horloge monotone), avec la règle GPT 6 à revoir, **avant toute publication** |
| M2 | MOYENNE | Double SIM : sur certains téléphones, un IDLE par SIM fermerait l'appel en cours sur l'autre | **À mesurer** sur le S24 FE (la trace note la SIM de chaque diffusion) ; la parade (état en direct à l'IDLE) a son propre risque, l'appel qui ne se ferme jamais : posée seulement après mesure |
| M3 | MOYENNE | Une `CancellationException` levée par un traitement arrête l'unique consommateur de la file, en silence ; tout le reste attend jusqu'à l'ANR | **Corrigé** : seule l'annulation de la file l'arrête ; tâches en attente libérées si elle s'arrête ; tests |
| L1 | FAIBLE | La mémoire n'attrape que `IOException` : une autre erreur de stockage perd le début d'appel | **Corrigé** : toute erreur donne « sens inconnu », l'appel est signalé ; test |
| L2 | FAIBLE | Diffusion retenue sans limite de temps | **Corrigé** : libérée au plus tard à 8 s, le traitement continue ; tests |
| L3 | FAIBLE | Le sens de l'appel est déduit : une sonnerie perdue fait lire un appel entrant comme sortant ; dangereux pour P-16 | Écrit dans P-16 |
| L4 | FAIBLE | Arrêts silencieux d'Android : appli jamais ouverte, permissions retirées après des mois, hibernation | Écrit dans P-10 (écran d'état) ; PRIVACY dit « ouverte une fois » |
| L5 | FAIBLE | La trace de débogage imprime le signal : le numéro y apparaîtrait le jour où il est lu | **Corrigé** (aussi relevé par la relecture de sécurité) : seul le type du signal |
| L6 | FAIBLE | Non exporté, le récepteur n'accepte que le système : un téléphone qui enverrait PHONE_STATE depuis son processus téléphonique ne serait pas entendu | **Mesuré puis corrigé** : PHONE_STATE est protégée sur le S9 (Samsung, Android 10), même le shell d'adb est refusé ; récepteur **exporté**, sans risque de falsification |
| T | — | Aucun test de bout en bout ; pas de test d'annulation dans la file ; test sur appareil sujet aux courses (`cancel` sans attente) | **Corrigé** : `CallStateSensorTest` (séquences réelles à travers le vrai moteur), tests d'annulation et de libération, `cancelAndJoin` |
| D | — | Doc : flux « à venir », « tout capteur appelle le cas d'usage », P-7 donné pour fermé ; PRIVACY « pour chaque appel » (seul le dernier est gardé) | **Corrigé** |

## Relecture de sécurité — aucun constat critique ni élevé (score estimé 92/100)

| N° | Gravité | Constat | Suite |
|---|---|---|---|
| M1 | MOYENNE | Pas de preuve que le récepteur non exporté reçoit PHONE_STATE | **Mesuré** sur émulateur Android 11, 16 et 17 (vrai service téléphonique), puis exporté (L6 ci-dessus) |
| M2 | MOYENNE | « Jamais le numéro » ne sera plus vrai à l'étape `READ_CALL_LOG` : PRIVACY et la colonne `counterpart_number` changeront ensemble | Règle D-007 ; rien à corriger à cette étape |
| L1-L3 | FAIBLE | Fichier `phone_state` bien exclu de la sauvegarde ; file sans plafond (accès réservé au système) ; la garde CI ne contrôle pas les composants exportés | Rien à corriger ; idée notée |

**Jugé juste par les deux** : rien ne sort du téléphone, aucune donnée personnelle dans les journaux d'une
release, sauvegarde coupée, contrat de l'exécuteur tenu, schéma d'injection d'Agenda Tech (leçon F2) repris.
