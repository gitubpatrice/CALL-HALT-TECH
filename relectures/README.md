# Relectures

Chaque relecture externe ou indépendante du projet : qui, quoi, quand, à quel prix, et ce qui en a été
fait. Les rapports sont conservés **tels que rendus** (ou condensés fidèlement quand ils sont longs) ; le
tri vit dans la colonne « Suite » et dans les documents qu'elle cite. Les consignes envoyées sont dans
`consignes/`, pour pouvoir refaire une relecture à l'identique.

Règles (mémoire du portefeuille) : un constat externe se **vérifie** avant d'être appliqué ; on envoie le
strict nécessaire ; GPT : `gpt-5.6-sol` effort `high`, plafond 2 $ par relecture, annoncé avant.

| Date | Relecteur | Objet | Coût | Rapport | Suite |
|---|---|---|---|---|---|
| 2026-11-01 | GPT `gpt-5.6-sol` (high) | `PROJET.md` seul, sans les idées de Claude | 0,575 $ | [gpt-fiche](2026-11-01-gpt-fiche.md) | 12 corrections intégrées (`PROJET.md` § 9.1, `docs/ARCHITECTURE.md`) ; 12 idées : 7 retenues (§ 9.2 n° 1, 3, 4, 5, 9, 15), 1 déjà couverte par la méthode, 4 écartées (§ 9.3) ; 1 API fausse corrigée |
| 2026-11-01 | Claude, agent `android-code-quality-deep-dive` | moteur + couche de données (axe Room) | 0 $ (sous-agent) | [claude-couche-donnees](2026-11-01-claude-couche-donnees.md) | 8 constats : 3 corrigés (D1, D4, D7), les autres écrits en points ouverts |
| 2026-11-01 | GPT `gpt-5.6-sol` (high) | moteur + couche de données, code complet (~45 Ko) | 0,500 $ | [gpt-code](2026-11-01-gpt-code.md) | 9 constats, tous réels : 6 corrigés avec test et contrôle négatif (1, 2, 3, 6, 7, 9), 3 en points ouverts (P-12, P-13, P-14). Ses n° 1 et 2 avaient échappé à la relecture Claude, qui avait vu la limite voisine (D2) : deux relecteurs, deux angles |

| 2026-11-01 | Claude, agent `general-purpose` | textes de l'écran STOP (5 langues) + `StopText` / `StopScreen` | 0 $ (sous-agent) | [claude-textes-stop](2026-11-01-claude-textes-stop.md) | 11 constats : 9 corrigés (dont H1 prise en main, H2 règle fausse pour qui appelle sa banque), 1 laissé, 1 mesuré ; 2 points ouverts (P-15, P-16). Relecture GPT des phrases DE/IT/ES : pas faite, la relecture par un natif reste à faire |

**Cumul des relectures payantes : 1,075 $.**
