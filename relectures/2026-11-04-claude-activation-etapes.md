# Relecture — deux défauts de l'activation trouvés en mesurant (P-3, Android 11 et 15) — 2026-11-04

> Agent `android-code-quality-deep-dive`, axe correction, lecture statique ; 0 $ (sous-agent). Objet, avant commit : le
> compteur « Étape x sur y » (`activationStep`), et l'étape qui relit ses permissions (`AccessStep`, `remember(reads)`),
> trouvés en mesurant P-3 sur les émulateurs Android 11 et 15.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| M1 | Moyen : quand seul le plein écran manque, l'étape mène à la page des notifications de l'appli ; sous Android 14, l'interrupteur y est-il ? (mesuré sous 15 seulement) | **réel, à mesurer** | **mesuré et corrigé** : sous 14 aussi, le plein écran est refusé d'office à une appli installée depuis un fichier ; l'étape ouvre désormais d'abord sa page dédiée (`ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`, Android 14+, déclarée dans les `<queries>`), puis la page des notifications ; mesuré sous 14 et 15 jusqu'à « Protection activée » |
| F1 | Faible : sous `key(access)`, `LifecycleEventEffect(ON_RESUME)` rejoue ON_RESUME, une relecture de plus | réel, sans coût (un `checkSelfPermission`) | rien |
| F2 | Faible : un accès retiré pendant l'activation, déjà montré, était compté deux fois dans le total | **réel**, rare | **corrigé** (`it !in shown`), testé |
| F3 | Faible : tests sans le cas Xiaomi ni l'accès retiré | réel | **ajoutés** (1 sur 6 à 6 sur 6 ; accès retiré compté une fois) |
| F4 | Faible : REPRISE disait « 4 sur 5 » pour le Redmi ; c'était « 5 sur 6 » (Xiaomi : le démarrage automatique) | **réel** | **corrigé** |
| F5 | Faible : « Android 15 ne donne pas le plein écran » sans la réserve « installée depuis un fichier » ; Android 14 non dit | réel | **corrigé** (REPRISE, D-024, P-3 : 14 et 15 mesurés depuis un fichier, F-Droid non mesuré) |
| a | Le numéro au premier affichage, une étape passée, la sauvegarde de `shown` (rotation, mort du processus) | **justes** | — |
| b | La relecture des permissions : recomposition, double ON_RESUME, texte et bouton cohérents après relecture | **justes** | — |

**Bilan** : aucun constat élevé ; un moyen mesuré et corrigé (Android 14) ; trois défauts faibles corrigés, deux tests
ajoutés.

## Relecture de sécurité du même changement (après fusion, `801e2f2`)

> Agent `android-code-quality-deep-dive`, axe sécurité, lecture statique ; 0 $ (sous-agent). Demandée par le contrôle de
> fin de tour (le manifeste a changé).

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | L'entrée `<queries>` (la page « plein écran ») : une permission, une visibilité trop large, un effet sur la garde CI ? | **aucun** : des noms d'actions seulement, pas de `QUERY_ALL_PACKAGES` ; la garde ne lit que les permissions et les composants `BIND_*`, inchangés ; « zéro Internet » intact | — |
| 2 | Une appli tierce à la place de la page des Réglages ? | **non** : `MATCH_SYSTEM_ONLY` puis `setPackage` du paquet système, comme les autres pages | — |
| 2b | Bas, antérieur : le dernier repli d'`AccessSettings.open` lance la première page telle quelle, sans paquet, si AUCUNE page n'est servie par le système | réel en logique, **non exploitable en pratique** : la liste finit par les Réglages du téléphone (`ACTION_SETTINGS`, dans les `<queries>`) ; il faudrait un téléphone sans Réglages système ET une appli malveillante déclarant l'action, pour un écran d'instructions imité, sans donnée envoyée | **laissé** : le choix de la relecture du 2026-11-02 (un bouton qui ne fait rien serait pire) tient |
| 3, 4 | `packageUri`, intents, données, l'étape qui relit ses permissions | **rien** | — |
