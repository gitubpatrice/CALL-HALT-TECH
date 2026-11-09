# Relecture Claude : « Désinstaller [appli] » sur le STOP d'une appli de prise en main installée (audit FN-3, D-039) — 2026-11-09

> Agent `android-code-quality-deep-dive`, lecture seule, sur le commit `0892bda` ; consigne : justesse (la bonne appli,
> le bon déclencheur, D-011), l'avertissement (silencieux, retiré), cohérence avec le jumeau `REMOVE_ACCESS` (D-033),
> tests, textes et documents. 0 $ (sous-agent).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Le bouton disparaît quand l'appli installée se met en marche pendant que son STOP est affiché : le STOP « en marche » (D-032) a le même poids, le plus récent prend l'écran, et `uninstallable` exigeait le déclencheur d'installation ; dans l'ordre inverse le bouton reste | **réel** | **corrigé** : le bouton reste tant que l'écran parle de cette appli, installée ou en marche (`UNINSTALL_TRIGGERS`) ; l'accessibilité, plus grave, passe avant ; tests dans les deux ordres |
| 1b | Un STOP « en marche » tout neuf (l'appli installée avant l'appel, ou son STOP déjà quitté) n'offre pas de désinstaller | **choix de produit** : on n'offre que ce qui a été installé pendant cet appel | **limite écrite** (D-039) ; à soumettre à Patrice |
| 2 | Des textes disent encore l'avertissement « hors appel » : la description du canal (5 langues), PRIVACY (2 langues) | **réel** | **corrigé** ; la ligne « Hors appel » d'ARCHITECTURE § 3 reste juste (hors appel, seule l'installation avertit) |
| 3 | Une seconde appli de la liste installée pendant que le STOP de la première est affiché est absorbée : avec FN-3, elle n'avait plus ni STOP, ni avertissement, ni ligne de journal | **réel** (rare) | **corrigé** : absorbée par l'écran, elle garde son avertissement (silencieux) et sa ligne de journal ; test ; D-031 complétée |
| 4 | Tests : le retrait à la désinstallation n'était gardé par aucun test (inverser `!replacing` passait) ; le cas 1 ; `setSilent` | **réel** (a, b) ; `setSilent` mesuré, pas testable sur la JVM | **ajoutés** : `withdrawsWarning`, fonction pure testée ; les deux ordres du cas 1 |
| — | L'accessibilité activée après l'installation prend l'écran et le bouton | voulu, écrit (D-039) | rien |

## Vérifications après correction

Gate de la CI vert en local (`:app:assembleDebug :domain:test :app:testDebugUnitTest :app:lintDebug detekt`) : **644
tests** (124 domaine, 520 appli). Le relecteur n'a trouvé ni bouton visant la mauvaise appli, ni page des réglages
ouverte par-dessus un appel non coupé, ni asymétrie avec `REMOVE_ACCESS`.
