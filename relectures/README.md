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
| 2026-11-02 | Claude, agents `general-purpose` et `android-code-quality-deep-dive` (sécurité) | capteur de l'état de l'appel, avant commit | 0 $ (sous-agents) | [claude-capteur-appel](2026-11-02-claude-capteur-appel.md) | aucun constat élevé ; 6 corrigés (dont l'arrêt silencieux de la file), 1 mesuré puis corrigé (récepteur exporté), 2 à mesurer ou à faire avant publication (double SIM, P-13), le reste écrit |
| 2026-11-02 | Claude, agents `android-code-quality-deep-dive` (sécurité) et `general-purpose` | écran STOP réel, avant commit | 0 $ (sous-agents) | [claude-ecran-stop](2026-11-02-claude-ecran-stop.md) | sécurité : aucun élevé, 3 corrigés ; générale : 2 élevés (écran quitté, STOP remplacé) et 3 moyens corrigés, 2 mesurés sur l'émulateur Android 16 ; 3 points ouverts (P-17 à P-19) |
| 2026-11-02 | Claude, agent `general-purpose` (recherche web) | ce que font déjà les banques, Google, Samsung et l'État contre le faux conseiller | 0 $ (sous-agent) | [recherche-protections-banques](2026-11-02-recherche-protections-banques.md) | 3 sources décisives vérifiées par Claude (CNIL, deux billets de Google) ; `PROJET.md` § 9.6 : des applis bancaires détectent déjà l'appel, chacune pour elle ; rien de transversal en France ; la méthode ne change pas |
| 2026-11-02 | Claude, agents `general-purpose` et `android-code-quality-deep-dive` (sécurité) | capteur des notifications, commit `2b5bd0d` | 0 $ (sous-agents) | [claude-capteur-notifications](2026-11-02-claude-capteur-notifications.md) | aucun critique ni élevé ; trou de la garde CI (permission héritée de `<application>`) **corrigé**, vérifié rouge sur l'ancienne garde ; KDoc et 3 phrases de doc corrigées, 2 tests ajoutés ; 3 limites écrites (P-2, P-11, D-019) |
| 2026-11-02 | GPT `gpt-5.6-luna` (high) — première relecture par Luna | capteur des notifications, même commit, 14 fichiers (~60 Ko) | 0,017 $ | [gpt-capteur-notifications](2026-11-02-gpt-capteur-notifications.md) | 5 constats : 1 réel (demande perdue à la mort du processus, quelques millisecondes) écrit en P-2, 4 écartés après vérification ; a manqué le trou de la garde trouvé par Claude |
| 2026-11-02 | Claude, agent `general-purpose` | un appel ne survit pas à un redémarrage, commit `c087b9f` | 0 $ (sous-agent) | [claude-redemarrage](2026-11-02-claude-redemarrage.md) | a corrigé la prémisse : dans le doute, le redémarrage se rapporte (se taire ouvrait un STOP manqué) ; 3 corrigés avec tests (doute, ordre, horloge en avant), 2 écrits (horloge reculée, redémarrage léger) |
| 2026-11-02 | GPT `gpt-5.6-luna` (high) | même commit, diff + 9 fichiers (~86 Ko) | 0,017 $ | [gpt-redemarrage](2026-11-02-gpt-redemarrage.md) | 5 constats : 2 réels corrigés (ordre, lecture hors du `try`), 1 écrit (horloge reculée), 2 faux dont la correction proposée (se taire dans le doute) aurait ouvert un STOP manqué |

**Cumul des relectures payantes : 1,109 $.**
