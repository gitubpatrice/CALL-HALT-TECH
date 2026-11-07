# Relecture Claude : l'écran STOP lisible à toute taille de police, et senti à l'oreille (U1 à U4), avant PR — 2026-11-07

> Agent `android-code-quality-deep-dive`, lecture seule ; consigne : la justesse de `StopLayout` et de
> `stopPlacement`, la vibration (quand, pendant un appel, exceptions, la permission et les promesses publiques),
> l'accueil et l'accessibilité. 0 $ (sous-agent). Envoyé : le commit `6cb4c7e`, sans mes conclusions.

## Verdict

**Aucun défaut de justesse** dans `StopLayout` ni `stopPlacement` : écarts, bas d'écran, listes vides, rotation
(défilement sauvegardé, pas de vibration), nouveau STOP (défilement et compte à rebours repartent), « Continuer » hors
champ désactivé 10 s. La vibration ne part ni à la reconstruction ni sur l'écran de démonstration ; l'usage d'alarme
passe le mode silencieux. `VIBRATE` ne change aucune promesse publique (README et SECURITY promettent l'absence
d'`INTERNET`, PRIVACY la liste vérifiée, mise à jour).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | La vibration n'est pas protégée, et part avant tout le reste d'`onCreate` : une exception du service de vibration (un fabricant, la permission retirée par un profil d'entreprise) ferait tomber l'écran STOP lui-même | **réel** (moyen-bas, rare) | **corrigé** : tout ce qu'elle lève est attrapé et journalisé ; appelée en dernier, l'écran déjà en place |
| 2 | En mode compact, TalkBack lirait-il l'explication avant les boutons (l'ordre où les pièces sont composées) ? | **à mesurer** | **mesuré, sans défaut** : à 200 %, l'arbre d'accessibilité donne « c'est une arnaque », « Raccrocher », « Raccrocher et appeler ma banque », puis « Ni votre banque… » — l'ordre affiché |
| 3 | Bascule à quelques pixels près (l'ordre compact a un écart de 16 au lieu de 12) | sans conséquence | rien |
| 4 | Tests légers ; une largeur non bornée planterait | **réel** (bas) | **corrigé** : tests de la démonstration, d'un seul bouton, et de l'invariant « aucun chevauchement, chaque pièce une fois, tout dans la hauteur » sur 500 tirages ; largeur non bornée = la plus large des pièces |

## Vérifications après correction

`StopLayoutTest` 8 tests ; gate local vert (600 tests) ; contrôle des permissions du manifeste de release et son
contrôle négatif (19/19) verts.
