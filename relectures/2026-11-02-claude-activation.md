# Relectures Claude — écran d'activation et état de la protection — 2026-11-02

> Commit `4095991` (branche `feat/activation-screen`), lu par deux agents qui ne voyaient ni les constats l'un de
> l'autre, ni ceux de GPT (`2026-11-02-gpt-activation.md`) : `general-purpose` (justesse, versions d'Android, textes
> contre code, accessibilité) et `android-code-quality-deep-dive`, axe sécurité. L'agent de sécurité a relu ensuite les
> corrections de ses deux constats. 0 $ (sous-agents). Rapports condensés fidèlement ; le tri dit ce qui en a été fait.

## Relecture générale — 8 constats

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | L'accueil « en marche » promettait toutes les banques (« quand l'appli de votre banque vous demande de valider ») ; seul le canal Sécur'Pass est reconnu (D-019). Et « pendant un appel » : les appels par Internet ne sont pas vus (P-4) | **réel, confirmé** | **corrigé** : « une demande de validation Sécur'Pass (Banque Populaire, Caisse d'Épargne, Crédit Coopératif…) », « appel téléphonique », et « elle ne voit pas tout, ni les appels par Internet » |
| 2 | En allemand, italien et espagnol, « un code par SMS » : la règle ne lit que le français et l'anglais (D-021) — « Ihr Bestätigungscode lautet … » passe | **réel, confirmé** | **corrigé** : ces trois langues disent « (en français ou en anglais) » |
| 3 | « Ouvrir les infos de l'appli », seule issue des paramètres restreints, était en bas de l'aide, hors de l'écran à grande police : l'utilisateur retouche « Ouvrir les réglages » en boucle | **réel, probable** | **corrigé** : le bouton est épinglé en bas avec les autres (mesuré sur Android 16) |
| 4 | Un dialogue fermé par Retour (ou par un appel entrant) répond « refusé » sans que l'explication de refus change : l'appli croyait que le téléphone ne demandait plus | **réel, probable** | **corrigé** : après tout refus, « Autoriser » reste et « Ouvrir les réglages » s'ajoute ; mesuré : après Retour, « Autoriser » rouvre le dialogue |
| 5 | « En marche » sur la foi des accès : Xiaomi (fenêtres pop-up en arrière-plan), écouteur accordé mais pas relié | à mesurer | **écrit** : P-21 |
| 6 | Android Go : l'affichage par-dessus n'y est jamais accordé ; l'utilisateur tournait en rond | **réel** (documentation ; non mesuré) | **corrigé** : l'accueil dit que ce téléphone ne permet pas la protection, sans bouton d'activation (`phoneCanProtect`) ; P-21 |
| 7 | `AccessSettings.open` n'attrapait que `ActivityNotFoundException` : une page qu'un fabricant n'exporte pas ferait planter l'appli | **réel, à vérifier par fabricant** | **corrigé** : `SecurityException` passe à la page suivante |
| 8 | Le texte de l'activation taisait le canal lu de chaque notification (PRIVACY le dit) | **réel, faible** | **corrigé** : « l'appli qui l'envoie et son type » |

Vérifié sans défaut par l'agent : les gardes de version (29 à 36), `mayBeRestricted` contre la mesure, la navigation,
le cycle de vie (relecture au retour, rotation, mort du processus), TalkBack. Latent, écrit en P-21 : l'accueil ignore
le réglage « protection activée », que rien n'écrit encore.

## Relecture de sécurité — aucun constat critique ni élevé, 4 constats

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| S1 | L'aide aux paramètres restreints était une recette générale, son avertissement venait après les étapes ; « le téléphone peut demander le code qui le déverrouille » pouvait devenir « lisez-moi le code » | **réel, moyen** | **corrigé** : l'avertissement en tête et en gras, « depuis cet écran », « si on vous le demande au téléphone, raccrochez » ; chaque étape nomme Call Halt Tech ; « tapez-le vous-même, sans le dire à personne ». Revu par l'agent : adéquat |
| S2 | Les pages de réglages sont des intentions implicites : une appli qui déclare la même action serait proposée à côté des Réglages, et pourrait les imiter | **réel, faible** | **corrigé** : `MATCH_SYSTEM_ONLY` puis `setPackage`, les cinq actions dans `<queries>` ; repli implicite seulement si aucune page système n'existe. Revu par l'agent : fermé, `<queries>` minimal |
| S3 | « En marche » alors que l'écouteur n'est pas relié | à mesurer | **écrit** : P-21 (même constat que la relecture générale, n° 5) |
| S4 | La source d'installation est déclarée par l'installateur : elle ne sert qu'à montrer l'aide, jamais de preuve | information | déjà le cas ; dit dans D-022 |

**Bilan** : 10 défauts réels corrigés à eux deux, 1 point à mesurer écrit (P-21). GPT a trouvé ce qu'aucun des deux
n'a vu : l'aide envoyait au menu avant que le refus le fasse apparaître.
