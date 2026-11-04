# Relecture — la durée de conservation du journal (P-11, D-029) — 2026-11-04

> Agent `android-code-quality-deep-dive`, axe correction, lecture statique ; 0 $ (sous-agent). Objet : le journal
> gardé 14 mois et 10 000 lignes au plus, élagué dans la transaction qui écrit (`JournalRetention`, `DetectorDao`,
> `RoomDetectorStore`, l'horloge injectée), les tests et les textes (politique de confidentialité, D-029), avant commit.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Le SQL du plafond garde-t-il exactement 10 000 lignes, et rien quand il y en a moins ? | **juste** | sous-requête de rang `keep + 1` ; vide, elle vaut NULL et rien ne part ; `AUTOINCREMENT` : l'ordre d'écriture, jamais réutilisé |
| 2 | Un élagage qui échoue coûte-t-il un STOP ? | **non** | la décision est gardée avant l'écriture (`decided`), le chemin d'échec habituel (P-10) |
| 3 | Une autre liaison Hilt de `Clock` ? | **aucune** | la seule est dans `StorageModule` |
| 4 | Les tests testent-ils ce qu'ils disent ? Les valeurs attendues (heure d'été, fin de mois courte) ? | **justes**, recalculées | contrôle négatif fait avant la relecture : l'élagage retiré, les deux tests d'élagage rougissent |
| F1 | Faible : le schéma sait garder le numéro de l'appelant, la politique dit « jamais le numéro » | **exact aujourd'hui** (aucun capteur ne construit `Counterpart.Number`) ; antérieur au changement | à revoir le jour où un capteur le remplirait |
| F2 | Faible : un `DELETE` laisse le contenu dans les pages libres du fichier tant qu'il n'est pas réécrit | réel, sans chemin d'accès (espace privé de l'appli) | rien : le texte dit « effacée », pas « irrécupérable » |
| F3 | Faible : « un siècle d'usage normal, environ un mégaoctet » n'est pas mesuré | réel | **corrigé** : dit « estimé » (D-029, KDoc) |
| F4 | Faible : une heure avancée de plus de 14 mois efface trop tôt | déjà écrit (limites de D-029) | rien : le compromis de l'heure civile, la seule que garde le journal |

**Bilan** : aucun constat élevé ni moyen ; un chiffre qualifié ; deux limites déjà écrites.
