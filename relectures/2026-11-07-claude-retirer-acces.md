# Relectures Claude — « Retirer l'accès » sur le STOP d'accessibilité (D-033), 2026-11-07, avant commit

Deux agents, en parallèle, sur l'arbre de travail de `feat/remove-accessibility-access` : `general-purpose` (justesse :
les boutons, le cycle de vie de l'écran, l'appel coupé d'abord) et `android-code-quality-deep-dive` (axe sécurité : la
page ouverte, le déverrouillage, le manifeste). Coût : 0 $ (sous-agents).

## 1. Justesse

Vérifié, sans défaut : la page servie par une appli du système seulement (`Intent(page)` recopie les drapeaux,
`setPackage` vise un paquet trouvé par `MATCH_SYSTEM_ONLY`) ; l'appel coupé avant tout ce qui s'ouvre, verrou compris, et
l'écran qui reste sans boutons si la coupure échoue ; une seule réponse au journal (la garde `answered`, pas de `LEFT`
après une réponse) ; le SMS au proche sur `CONTINUE` seulement ; les choix enregistrés par leur nom (insérer
`REMOVE_ACCESS` ne décale rien) ; les nouveaux tests attrapent une condition d'appel inversée et l'ordre des boutons.

| # | Constat (gravité, certitude) | Tri | Suite |
|---|---|---|---|
| 1 | Un second STOP arrivé pendant la demande du code est absorbé par l'écran déjà répondu : boutons inertes, puis fermé par le rappel du verrou, sans réponse ; compté « montré », il retient ses répétitions 60 s (moyen ; chemin confirmé, scénario rare : le SMS de code de l'escroc pendant que la victime tape son code) | **Réel, corrigé** | `StopSession.receiving` : sur un écran répondu, un nouveau STOP est un écran à lui ; le rappel n'ouvre rien et ne ferme rien s'il a pris l'écran (`opening` comparé). Tests `StopSessionTest` ; **contrôle négatif** fait. **Mesuré** (Android 16, verrouillé) : un SMS de code pendant la demande du code → après le code, le STOP du code reste, « Appeler ma banque » ouvre le composeur ; journal STOP, `REMOVE_ACCESS`, STOP du code, `CALL_MY_BANK` |
| 2 | Recréé pendant la demande du code (rotation, pliable, mode sombre), l'écran perd le rappel — Android ne l'appelle pas une fois l'activité détruite (`KeyguardManager`, vérifié dans les sources) — et la garde ajoutée fermait la nouvelle instance : ni page ni composeur, la réponse pourtant au journal (faible à moyen, confirmé) | **Réel, corrigé** | La réponse qui attend le déverrouillage (`StopSession.opening`) est gardée dans la sauvegarde de l'écran ; l'instance recréée redemande le déverrouillage une fois au premier plan (`onResume`), si aucun STOP n'a pris l'écran entre-temps ; la page s'ouvre une seule fois (`opened`). **Mesuré** : passage en mode sombre pendant la demande du code → l'écran recréé (« relaunch »), seconde demande, code tapé → la page Accessibilité |

Manque noté : aucun test ne lance l'activité elle-même ; ces chemins reposent sur les mesures aux émulateurs.

## 2. Sécurité (`android-code-quality-deep-dive`)

**Aucune faille réelle.** Vérifié : aucune autre appli ne peut recevoir ni imiter la page (intent rendu explicite, pas de
repli vers une page non système, l'échec n'ouvre rien) ; `requestDismissKeyguard` ne contourne pas le verrou (code refusé,
la page reste derrière lui) ; `NEW_TASK | CLEAR_TASK` vise la tâche de l'affinité des réglages, fixée par le système ;
l'ajout aux `<queries>` ne rend visibles que les activités de cette action ; aucun journal en version publiée
(`Timber.plant` en debug seulement), et aucune donnée sensible dans ceux du debug. Noté, hors de ce changement : le repli
ancien de `AccessSettings.open` (page telle quelle si aucune page système), non atteint ici.
