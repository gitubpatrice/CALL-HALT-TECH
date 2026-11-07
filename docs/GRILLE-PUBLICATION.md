# Grille de publication — le score de 95 sur 100 (D-016)

> Fixée le 2026-11-07, **avant** le premier audit de publication (D-016 l'exige : une grille écrite après coup se règle
> sur les résultats). Commitée avant que le premier agent d'audit ne soit lancé. Elle ne change ensuite que par une
> décision écrite ici, datée, avec sa raison — jamais pour faire passer un audit.

## 1. Les conditions bloquantes

Une seule manque : **pas de publication**, quel que soit le score.

- **B1** — Aucun constat **critique ou élevé** ouvert, dans aucun des audits du § 3. Un tel constat ne devient une limite
  acceptée que par une décision de Patrice, écrite dans `docs/ARCHITECTURE.md`.
- **B2** — P-1, P-2 et P-3 mesurés sur de vrais téléphones (`docs/ARCHITECTURE.md`, § 7).
- **B3** — Une relecture externe (GPT `gpt-5.6-sol`, le code qui décide) **du commit audité**, faite après les
  corrections des audits Claude.
- **B4** — Sur ce commit : CI verte (tests, lint et detekt à zéro, garde « aucune permission Internet » sur le manifeste
  fusionné de la version publiée), CodeQL sans alerte.

## 2. Le score : cinq axes de 20 points

Chaque axe part de 20 et ne descend pas sous 0. **Un axe dont l'audit n'a pas été fait vaut 0.**

| Axe | Ce qu'il mesure | D'où viennent les constats |
|---|---|---|
| **A. Sécurité** | ce qu'un escroc, une appli malveillante ou un SMS piégé peut obtenir | skill `audit-securite-mobile`, palier profond (panel adverse, 2 vérificateurs sur 3) |
| **B. Justesse de la détection** | un STOP qui manque (l'arnaque passe) ou qui arrive à tort (l'appli est désactivée) | `regles-faux-positifs-auditor` sur toutes les règles du commit audité ; `android-motif-defauts-auditor` (garde mal visé, repli du mauvais côté) ; relecture externe |
| **C. Solidité du code** | plantage, état perdu, course, fuite de ressource, test vert sur un chemin mort | `android-3-axes-auditor` sur l'appli entière ; `android-architecture-coherence-checker` ; `android-motif-defauts-auditor` |
| **D. Fiabilité mesurée** | ce qui marche sur de vrais téléphones, pas seulement sur émulateur | la liste du § 4 |
| **E. Textes, promesses, langues** | ce que disent l'appli, la politique de confidentialité, le README et la fiche, contre ce que fait le code ; les 5 langues | `android-motif-defauts-auditor` (motif 5) ; `android-i18n-strings-cleaner` ; relecture par des natifs |

**Retraits**, par constat **resté ouvert** sur le commit audité (gravité après le panel, qui ne peut que la baisser) :

| Constat | Retrait |
|---|---|
| critique, élevé | bloquant (B1) |
| moyen | − 3 |
| faible | − 1, au plus − 5 par axe |
| écrit comme limite | 0, **seulement** si la correction est impossible sous Android, ou ferait perdre plus de protection qu'elle n'en donne, et que la raison est écrite dans `docs/ARCHITECTURE.md` ; sinon, il compte |

Un constat trouvé par deux audits compte une fois, dans l'axe de sa conséquence. Un constat corrigé ne compte plus si sa
correction a eu ses relectures (Claude, puis GPT) et sa mesure quand elle touche le téléphone.

## 3. Les audits, et leur ordre

1. Claude, en parallèle : sécurité (A), faux positifs (B), motifs de défaut (B, C, E).
2. Claude : 3 axes et cohérence (C), traductions (E).
3. Corrections, avec leurs relectures, comme pour toute modification.
4. Relecture externe du commit corrigé (B3).
5. Le score, calculé sur ce commit. Chaque rapport va dans `relectures/`, avec son tri constat par constat.

Le score vaut pour **un** commit. Ce qui change ensuite passe par les relectures habituelles ; un changement du code qui
décide (`domain/`, les règles des capteurs) demande une relecture externe de ce changement.

## 4. Axe D : les mesures attendues (état au 2026-11-07)

| Mesure | Retrait tant qu'elle manque |
|---|---|
| Réception d'un SMS au proche, une vraie SIM de chaque côté (D-034) — une fonction jamais mesurée de bout en bout | − 4 |
| Installation par le client F-Droid (P-3) — le canal de publication lui-même ; mesurable avant publication par un dépôt F-Droid local | − 4 |
| Android 10 sur un téléphone avec SIM (P-1) | − 2 |
| Samsung sur plusieurs jours, la mise en veille (P-21, le S24 en observation) | − 2 |
| HyperOS (D-025, mesuré sur MIUI 12 seulement) | − 2 |
| Deux SIM | − 2 |
| AnyDesk (D-031 et D-032 mesurés avec RustDesk seulement) | − 2 |

Un défaut **observé** et non résolu compte comme un constat de l'axe où il tombe : l'écran d'appel qui repasse devant le
STOP après un redémarrage d'Android seul (D-026) compte − 3 (moyen) dans l'axe D.

**Axe E, la relecture par des natifs** des phrases de sécurité (l'écran STOP, les SMS au proche, « Retirer l'accès ») :
− 2 par langue non relue (allemand, italien, espagnol). Le français et l'anglais sont les nôtres.
