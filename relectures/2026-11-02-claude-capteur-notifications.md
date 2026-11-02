# Relecture Claude — capteur des notifications — 2026-11-02

> Deux sous-agents Claude, en lecture seule, sur le commit `2b5bd0d`, sans connaître le raisonnement de l'auteur :
> `general-purpose` (justesse, comportement d'Android, promesse de confidentialité, cohérence avec la doc, tests)
> et `android-code-quality-deep-dive`, axe sécurité. 0 $ (sous-agents). Menées en même temps que la relecture GPT
> (`2026-11-02-gpt-capteur-notifications.md`), sans qu'aucune voie les autres. Rapports condensés fidèlement.

## Tri

| Relecture, n° | Gravité | Constat | Verdict | Suite |
|---|---|---|---|---|
| générale 1 | moyen | La KDoc de l'écouteur promettait trop : une notification republiée n'est absorbée que pendant 60 s, et seulement tant que le STOP est sans réponse ; après une réponse (« Raccrocher » compris), une mise à jour, une confirmation sur le même canal ou un résumé de groupe republié montre un nouveau STOP (et préviendrait le proche à nouveau, une fois D-015 construit) | **réel** pour la KDoc ; le comportement est le choix protecteur, mais il était mal dit et ses tests ne le montraient pas | KDoc corrigée ; deux tests ajoutés (« once the STOP is answered, a repeat shows a new one », « an unanswered STOP absorbs repeats for a minute, not longer ») ; P-2 : distinguer par la clé de la notification avant le SMS au proche, selon la mesure du S24 |
| générale 2 | faible | Trou de la garde CI : une permission `BIND_*` posée sur `<application>` est héritée par chaque composant qui n'en a pas ; un service d'accessibilité passait (démontré en lançant la garde sur un manifeste construit pour) ; une permission par référence (`@string/…`) aussi | **réel**, vérifié : l'ancienne garde rend 0 sur ce cas, la nouvelle 1 | **corrigé** : la garde lit la permission qu'Android applique (composant, sinon `<application>`, valeur vide = aucune, alias = sa cible) et refuse une référence ; 4 cas ajoutés au contrôle négatif (19) ; `BIND_JOB_SERVICE` dit dans la docstring |
| générale 3 | faible | Doc périmée : `ARCHITECTURE.md` § 1 (« Rien ne protège encore », « la version publiée ne montre encore aucun STOP ») et `REPRISE.md` (« l'accès aux notifications ne s'accorde que par adb » : faux, l'écouteur apparaît dans les réglages) | **réel** | **corrigé**. Le texte d'accueil « ne vous protège pas encore » est gardé : aucun écran d'activation, et il pèche du côté prudent |
| générale 4 | faible | Les tests prouvent moins que leurs noms (règle générique des 60 s ; rien après 60 s ni après une réponse) ; l'écouteur Android n'a pas de test automatique | **réel** pour les noms | test renommé, deux tests ajoutés (voir n° 1) ; l'écouteur reste mesuré à la main (Android 10, 16, 17) |
| sécurité 1 | faible | Croissance du journal : une appli qui imite le canal pendant un appel ajoute deux lignes par STOP répondu, sans élagage | **réel** | P-11 complété (élaguer avant publication ; la politique dira la durée de conservation) |
| sécurité 2 | faible | Une appli qui imite le canal, juste avant la vraie demande, la fait absorber par la règle des 60 s | **réel, sans perte** : le STOP est devant la victime, l'écran quitté libère le déclencheur ; seul un écran tué sans réponse laisse passer la vraie demande pendant 60 s | écrit dans D-019 |
| sécurité 3 | faible (doc) | « L'appli ne lit que… » est vrai du code, mais Android remet toute la notification à l'écouteur ; rien n'empêche une lecture future | vrai, pas un défaut | rien ; la relecture de chaque changement le garde |

**Vérifié sans défaut par les deux** : rien de la version de test dans le manifeste fusionné publié ; service
protégé par `BIND_NOTIFICATION_LISTENER_SERVICE` (seul le système le lie) ; aucun journal de notification en
version publiée (`CallHaltApplication.kt:14`) ; rien n'est gardé hors appel ; canal nul géré ; profils de travail
du côté protecteur ; injection Hilt avant la liaison ; garde et contrôle négatif lancés par la relecture générale.

---

## Relecture générale (`general-purpose`), condensée

Aucun défaut bloquant ; l'écouteur suit `ARCHITECTURE.md` § 8 ; la promesse de confidentialité tient en version
publiée. Constats : (1) moyen — la republication après la fenêtre de 60 s ou après une réponse fait un nouveau
STOP, la KDoc dit le contraire ; scénarios : « Raccrocher » puis mise à jour, confirmation « refusée » ou résumé
de groupe sur le même canal ; écran sans réponse puis mise à jour après 90 s ; (2) faible — permission héritée de
`<application>` et permission par référence non vues par la garde, la première démontrée en lançant la garde ;
`BIND_JOB_SERVICE` contredirait « au moins aussi large qu'une permission » ; (3) faible — trois phrases de doc
périmées ; (4) faible — tests plus étroits que leurs noms, aucun cas du contrôle négatif pour (2). Verdict :
capteur sain ; corriger la garde, la KDoc et la doc ; mesurer les republications de Sécur'Pass après une réponse.

## Relecture de sécurité (`android-code-quality-deep-dive`), condensée

Aucun constat critique ni élevé. Vérifié : composants de test hors de la version publiée, service inatteignable
par une autre appli, journaux absents en version publiée, aucun crash sur une notification forgée, promesse de
confidentialité tenue, profils filtrés par le système, garde (alias, noms relatifs, `tools:node` déjà résolu dans
le manifeste fusionné). Constats : (1) faible — croissance du journal par une appli qui imite le canal ; (2)
faible — absorption de la vraie demande dans la règle des 60 s, atteignable mais presque sans coût pour la
victime ; (3) faible, doc — « ne lit que » est un comportement du code, pas une garantie d'Android. Verdict :
aucune vulnérabilité exploitable ni fuite.
