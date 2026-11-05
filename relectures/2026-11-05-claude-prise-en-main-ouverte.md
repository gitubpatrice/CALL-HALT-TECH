# Relecture — une appli de prise en main en marche pendant un appel (D-032) — 2026-11-05

> Agent `android-code-quality-deep-dive`, axes correction et sécurité, lecture statique ; 0 $ (sous-agent). Objet, avant
> commit : `platform/notification/RemoteControlNotices` (chaque nouvelle notification d'une appli de la liste de
> D-031), son branchement dans `NotificationListener` (et le chemin banque et SMS, réécrit en `bankRequestOrCode`), le
> texte « est en marche », les outils de mesure de la version de test (`MeasureTapService`, P-15).

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Moyen : à la reliaison de l'écouteur, tout ce qui est affiché comptait pour « déjà là » ; un service démarré pendant la coupure (processus tué en plein appel) n'aurait jamais été nouveau | **réel** | **corrigé** : ce qui a été posté dans les 2 min avant la reliaison compte comme l'appli en marche (`RECONNECTED_WITHIN`), testé ; **mesuré** sur émulateur (D-032) |
| 2 | Faible : un échec de lecture des notifications affichées valait une liste vide, et la mise à jour suivante d'un service permanent (TeamViewer Host) aurait été « nouvelle » : un faux STOP pendant un appel | réel, rare | corrigé d'abord (ce qui était connu, gardé), puis **défait sur la relecture GPT** (2) : garder fait manquer une notification reposée ; oublier donne au pire un STOP de trop, le côté protecteur |
| 3 | Faible : une appli à service permanent relancée pendant un appel (plantage, mise à jour) reposte sa notification : un STOP | réel, voulu | **écrit** dans D-032 |
| 4 | Faible : le branchement de l'écouteur n'a pas de test JVM | réel | mesuré sur émulateur ; rien |
| 5 | Faible : une appli malveillante installée sous un identifiant de la liste pourrait provoquer des STOP | réel, sans gain pour elle (le moteur absorbe les répétitions) | rien |

Sans défaut : la réécriture du chemin banque et SMS est équivalente (le contrôle de répétition d'un code appelé dans
les mêmes conditions) ; l'identité d'une notification est fixée par le système (une autre appli ne peut pas en poster
au nom d'une appli de la liste) ; seuls l'appli, la clé, l'heure de publication et le drapeau de résumé sont lus ;
rien de la version de test n'atteint la version publiable (vérifié aussi sur le manifeste fusionné : la garde CI passe,
aucun service d'accessibilité).
