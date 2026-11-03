# Relecture — l'étape « Active même quand vous ne l'ouvrez pas » (D-023) — 2026-11-03

> Agent `android-code-quality-deep-dive`, axes sécurité et correction, lecture statique ; 0 $ (sous-agent). Objet :
> `Access.BACKGROUND`, le dialogue `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` et sa permission, l'écran d'activation,
> les textes des 5 langues, la politique de confidentialité et la garde CI, avant commit. Les mesures du S24 FE (veille
> profonde, « Non restreinte ») lui étaient données comme acquises.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| — | Sécurité : aucun détournement. Le dialogue est résolu vers les Réglages du système (`MATCH_SYSTEM_ONLY`, `setPackage`) ; seul le système écrit la liste blanche ; une page imitée ne peut rien accorder | accord | rien à faire |
| 1 | **Moyen** : refusé (par l'utilisateur, ou un téléphone qui n'a pas le dialogue), l'étape bloquait l'activation : « Activer » y ramenait, l'accès aux notifications n'était plus atteignable depuis l'appli | **réel** | **corrigé** : « Passer cette étape », après un refus seulement, à la place de « Plus tard » ; l'accueil dit toujours l'étape manquante, et la prochaine activation la redemande |
| 2 | Faible, texte : « ne mette jamais en veille » promet trop ; « elle ne se réveille que quand un appel commence ou qu'une notification arrive » est inexact (toute notification, la fin d'un appel) | **réel** | **corrigé** dans les 5 langues : « ne mette pas en veille », « elle n'agit que quand un appel ou une notification arrive ». « Presque pas de batterie » est gardé : le S24 indiquait « 0 % utilisé(s) depuis le dernier chargement complet », écouteur relié depuis des heures |
| 3 | Faible, texte : le dialogue cité mot pour mot est celui d'Android d'origine ; Samsung peut dire autre chose | **réel, confirmé par la mesure** | **corrigé** : Samsung demande « Arrêter optimisation utilisation batterie ? » (« OK »). Le texte ne cite plus la question : « si l'appli peut toujours fonctionner en arrière-plan, ou d'arrêter d'optimiser sa batterie : acceptez, avec « Autoriser » ou « OK » selon le téléphone » |
| 4 | Faible : si aucune page système n'est trouvée, `open` lance la première page telle quelle, qu'une autre appli pourrait servir | réel, théorique | **laissé** : décision de D-022 (un bouton qui ne fait rien laisserait l'utilisateur bloqué) ; il faudrait un téléphone sans Réglages système ni infos d'appli |
| — | `PRIVACY.md`, `PRIVACY.fr.md`, garde CI et manifeste : cohérents ; aucune permission réseau | accord | rien à faire |

**Bilan** : aucun constat de sécurité ; 1 moyen et 2 faibles corrigés, 1 faible laissé avec sa raison.
