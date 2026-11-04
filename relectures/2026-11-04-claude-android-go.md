# Relecture — la protection sur un téléphone Android Go (P-21) — 2026-11-04

> Agent `android-code-quality-deep-dive`, axe correction, lecture statique avec les sources d'AOSP (Settings et
> frameworks/base, Android 10, 13, 14 et ses QPR, 15, 16) ; 0 $ (sous-agent). Objet : `phoneCanProtect(lowRam, sdk)`
> (un téléphone Go refusé avant Android 14 seulement, au lieu de toujours), avant commit. Lui étaient donnés : la lecture
> d'AOSP faite par Claude (écouteurs refusés sous Android 10 seulement ; liste « superposition » « non disponible »
> avant 14 ; envoyée vers la nouvelle page à partir de 14) et la mesure sur l'émulateur Android 16 en mémoire réduite.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| Q3 | La frontière (Android 14) : la redirection de la liste vers la nouvelle page existe-t-elle déjà sous 14 ? | **vérifié** | `ManageApplications.onAttach` (android14-release, 304-311 ; aussi QPR1, QPR3, 15, 16), drapeau vrai par défaut (`FeatureFlagUtils` 14, 233) ; absente sous 13. Rien à changer |
| M1 | Moyen : la preuve vient d'une seule image Android 16 ; 14 et 15 sont lus, pas mesurés ; un fabricant peut régler le drapeau autrement (il est persistant) | réel | **écrit** : la KDoc et P-21 disent « 14 et 15 lus, 16 mesuré », et le risque d'une page sans interrupteur reste à mesurer sur un vrai téléphone Go |
| M2 | Moyen (produit) : de 11 à 13, un téléphone Go reste refusé, alors que l'écouteur y est permis (la notification de repli pourrait protéger) | réel, **décision pour Patrice** | **écrit** dans P-21 et posé à Patrice |
| M3 | Moyen : sur un vrai téléphone de 1 à 2 Go, le système tue les processus plus tôt (`lmkd`) ; `debug.force_low_ram` ne le reproduit pas | réel, non mesurable ici | **écrit** dans P-21 (« Restent ») |
| Q2 | Un autre blocage de la mémoire réduite sous 14 à 16 ? | **aucun trouvé** | cherché dans une douzaine de fichiers du système (démarrage d'activité en arrière-plan, exemption de l'affichage par-dessus, plein écran, écouteurs, AppOps, permissions) : les seuls contrôles sont dans les Réglages |
| Q1 | `debug.force_low_ram` modèle-t-il un vrai téléphone Go ? | **fidèle pour ce chemin** (vérifié : `ActivityManager.isLowRamDeviceStatic` le lit sur une image déboguable, une fois par processus, d'où le redémarrage d'Android seul) | pas pour ce qui lit `ro.config.low_ram` en natif (mémoire, `lmkd`) : c'est M3 |
| F1 | Faible : la nouvelle KDoc s'était glissée entre la KDoc de `mayBeRestricted` et sa fonction | réel | **corrigé** : chaque fonction sous sa KDoc |
| F2 | Faible : le texte `home_unsupported` (5 langues) reste-t-il juste ? | **juste** | il n'est plus montré que de 10 à 13, où Android ne laisse rien s'afficher devant |

**Bilan** : la règle et sa frontière sont justes ; un défaut de documentation corrigé ; trois limites écrites, dont
une décision pour Patrice (Android Go de 11 à 13).
