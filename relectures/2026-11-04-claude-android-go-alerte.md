# Relecture — Android Go de 11 à 13, protégé par la notification du STOP seule (D-030) — 2026-11-04

> Agent `android-code-quality-deep-dive`, axes correction et sécurité, lecture statique ; 0 $ (sous-agent). Objet :
> `phoneSupport` (FULL, ALERT_ONLY, NONE), l'affichage par-dessus retiré de l'activation, l'état « alerte seule » de
> l'accueil et ses textes (5 langues), la notification envoyée tout de suite sans affichage par-dessus
> (`ActivityStopLauncher`), avant commit. Lui étaient donnés : les mesures sur les émulateurs Android 11 et 13 en mémoire
> réduite.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| M1 | Moyen : sans affichage par-dessus, si la notification ne peut pas partir (notifications coupées) alors que l'écran est passé (appli au premier plan), le lanceur répondait « non montré » : un STOP vu, journalisé « non affiché » | **réel** | **corrigé** : la notification refusée, l'écran est encore attendu 5 s, et passé, il compte pour montré. **Mesuré** (émulateur Android 13 en mémoire réduite, notifications coupées, pendant un appel) : appli ouverte, le STOP s'affiche, journal « STOP » ; contrôle négatif, appli en arrière-plan : rien ne passe, journal « non affiché » après 5 s |
| F1 | Faible : appli au premier plan, la bannière peut paraître quelques millisecondes avant que l'écran ne la retire | probable, non mesuré | rien : l'écran la retire à sa création (D-024) |
| F2 | Faible : deux KDoc disaient le STOP « ouvert de lui-même » sur un téléphone éteint ; la mesure dit « créé derrière, là au rallumage » | **réel** | **corrigé** (`Access.PhoneSupport.ALERT_ONLY`, `ProtectionStatus.ALERT_ONLY`) |
| F3 | Faible : « ne peut pas s'afficher de lui-même » un peu fort (verrouillé, le STOP se crée derrière) | prudent et vrai pour l'utilisateur | rien (texte accepté par Patrice) |
| F4 | Faible : l'italien de `home_unsupported`, lourd | réel | **corrigé** (« Android non permette a nessuna app di… ») ; la relecture des traductions par des natifs reste avant publication |
| 2 | L'ordre des états de l'accueil ; un téléphone ordinaire change-t-il ? | **juste** ; **non** (le même calcul qu'avant) | — |
| 3 | Un lecteur de l'ancienne règle, ou du « SCREEN toujours là » ? | **aucun** (activation par `missing` et `applicable`, tests sur téléphone non touchés) | — |
| 5 | Une faille par le nouveau chemin ? | **pas pire qu'avant** : retirer l'affichage par-dessus demande l'utilisateur dans les Réglages, et l'accueil le dit ; la notification part même plus tôt | — |

**Bilan** : aucun constat élevé ; un moyen corrigé et mesuré, avec contrôle négatif ; deux défauts de texte corrigés.
