# Relecture — la panne du stockage sur l'accueil (P-10) — 2026-11-03

> Agent `android-code-quality-deep-dive`, axes correction et sécurité, lecture statique ; 0 $ (sous-agent). Objet :
> `RoomDetectorStore.failing` et `check()`, l'état « Protection affaiblie » de l'accueil, la page de stockage du
> téléphone (`AccessSettings.storage`, `<queries>`), les textes des 5 langues et les tests, avant commit. Lui étaient
> donnés comme acquis : le gate local, 21 tests sur l'émulateur Android 16, le contrôle négatif du test du magasin, et
> la mesure sur le S24 FE (base remplacée par un dossier : « affaiblie », la « Gestion du stockage » de Samsung, le
> message parti une fois la base rendue).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| — | Sécurité : la page de stockage est servie par les Réglages du système (`MATCH_SYSTEM_ONLY`, `setPackage`), aucune appli tierce ne s'y glisse ; rien de personnel n'est journalisé ; aucune permission nouvelle | accord | rien à faire |
| 1 | Moyen : un processus où la base n'a jamais pu s'ouvrir part d'un état vide ; la base réparée, `check()` y réécrit cet état et efface les appels qu'elle gardait | **réel, pas nouveau** : `apply()` le faisait déjà au signal suivant, `check()` le fait plus tôt | **consigné dans P-7**, qui le fermera (l'état de l'appel interrogé en direct) |
| 2 | Concurrence : deux écrans, un seul magasin, un seul verrou | accord | rien à faire |
| 3 | Annulation pendant `check()` : la transaction est annulée, le verrou rendu ; au pire le message reste jusqu'au retour suivant | accord, bénin | rien à faire |
| 4a | **Moyen** : `check()` ne faisait que LIRE quand rien n'avait échoué ; un stockage passé en lecture seule (mémoire flash usée) passait pour sain | **réel** | **corrigé** : `check()` réécrit l'état qu'`apply()` lirait (une seule fonction `base`, partagée) ; nouveau test `theCheckWritesNotOnlyReads`, rouge avec la lecture seule (contrôle négatif fait) |
| 4b | Faible : `failing` est faux avant le premier essai | réel | dit dans le KDoc de `failing` |
| 4c | Moyen : un processus redémarré après une panne qui a perdu l'appel lit une base saine ; `failing` est faux | réel, déjà écrit (LIMIT de la classe) | répété dans le KDoc de `failing` ; P-7 |
| — | Un téléphone plein n'est pas toujours vu par `check()` : réécrire les mêmes lignes peut ne demander aucune place | limite (relevée en corrigeant 4a) | écrite dans le KDoc et dans P-10 |
| 5 | Faible, hérité : sans aucune page système, `open` lance la première page telle quelle | réel, théorique | **laissé** (D-022) : il faudrait un téléphone sans `INTERNAL_STORAGE_SETTINGS` ni `SETTINGS` système |
| 6a | Faible, texte : « le téléphone est peut-être plein » est la seule piste, alors qu'une migration manquante n'y gagne rien | réel | **gardé** : « peut-être », puis « S'il en a, redémarrez-le » ; texte validé par Patrice (« le reste ça va ») |
| 6b | Faible, texte : « redémarrez-le » ambigu | non : « S'il en a » porte sur le téléphone, « le » aussi | rien à faire |

**Bilan** : aucun constat de sécurité ; 1 moyen corrigé (avec son test et son contrôle négatif), 1 moyen ancien consigné
dans P-7, 2 faibles écrits dans le KDoc, 2 faibles laissés avec leur raison.
