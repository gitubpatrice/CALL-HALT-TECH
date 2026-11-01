# Relecture de la couche de données et du moteur — 2026-11-01

> Agent `android-code-quality-deep-dive` (Claude), axe « données / Room », lecture seule, déclenché par le
> contrôle de fin de travail. Il a lu en entier `domain/**`, `app/.../data/**`, `app/.../di/**` et tous les
> tests, sans connaître les conclusions de l'auteur. Aucun coût d'API (sous-agent de Claude Code).
> Constats reproduits fidèlement, condensés ; la dernière colonne dit ce qui en a été fait.

## Points vérifiés sans défaut

- Un seul processus (aucun `android:process`) : le `Mutex` de `RoomDetectorStore` suffit pour deux signaux
  simultanés.
- `CancellationException` relancée avant le `catch (Exception)`.
- État et journal écrits dans la même `withTransaction`.
- Ligne corrompue (enum inconnu, fin avant début, numéro vide) écartée sans plantage.
- Réglage hors bornes → défaut protecteur ; fichier DataStore corrompu → préférences vides → protection active.
- Colonnes `trigger`, `at`, `type` (mots SQL) : Room les met entre guillemets, aucun risque.

## Constats

| N° | Gravité | Constat (scénario d'échec) | Statut | Suite |
|---|---|---|---|---|
| D1 | ÉLEVÉE | « STOP affiché » est enregistré avant que l'écran existe. Si l'exécuteur échoue (Activity refusée, coroutine annulée, processus tué), un 2ᵉ SMS de code 30 s plus tard est absorbé comme « même rafale » : aucun écran, et le journal affiche un STOP qui n'a pas eu lieu | confirmé | **Corrigé** : signal `StopNotShown` envoyé par le cas d'usage quand l'exécuteur échoue, déclencheur réarmé, journal « non affiché » ; contrat de l'exécuteur précisé (il ne lève que si aucun STOP n'a pu s'afficher). Tests moteur et cas d'usage, contrôle négatif fait. L'annulation reste bornée par la retenue de 60 s (écrit). |
| D2 | ÉLEVÉE | Processus redémarré **après** une panne de stockage : aucun état en mémoire, l'appel enregistré est invisible, le STOP est manqué. Le commentaire de classe promettait le contraire, et le test ne couvrait que la panne survenue en cours de processus | confirmé | **Commentaire rectifié**, test `…theKnownLimit` qui écrit la limite ; correction réelle avec le capteur téléphonique (état de l'appel interrogé en direct) — `docs/ARCHITECTURE.md` P-7 |
| D3 | MOYENNE | Panne durable du stockage silencieuse : exception avalée, aucun journal système en release, journal du proche vide sans explication. Le commentaire de `StorageModule` disait « échouer bruyamment » | confirmé | Commentaire rectifié ; affichage sur l'écran « état de la protection » — P-10 |
| D4 | MOYENNE | Une valeur d'un autre type sous un nom de réglage connu lève `ClassCastException` dans `current()` : aucun signal n'est plus traité, alors que le contrat dit « ne lève jamais » | probable | **Corrigé** : lecture par la table des préférences (`as?`), mauvais type = absent = défaut protecteur ; test ajouté |
| D5 | MOYENNE | Fin d'appel manquée (A, contact), puis appel B d'un inconnu : B est pris pour un double appel, hérite du début et de l'interlocuteur de A, le rappel d'appel long ne part jamais. Le STOP n'est pas touché | confirmé | À trancher avec le capteur (avec `PHONE_STATE`, un second appel pendant OFFHOOK n'est même pas observable) — P-7 |
| D6 | FAIBLE | Protection rallumée en plein appel : le STOP fonctionne, mais aucun réveil n'est planifié pour le rappel d'appel long ; idem pour un seuil abaissé | confirmé | Documentation du moteur précisée ; tout changement de réglage enverra un `Tick` — P-8 |
| D7 | FAIBLE | Si un capteur horodate avec l'heure de l'opérateur (SMS), un code estampillé avant le début de l'appel tombe hors fenêtre | probable | **Contrat écrit** dans `Signal` : l'heure est celle du téléphone à la réception |
| D8 | FAIBLE | Journal jamais élagué ; `pendingStops` garde d'anciennes entrées ; avertissement « prise en main installée » non dédoublonné hors appel | confirmé | P-11 |

Après correctifs : 79 tests JVM, 4 instrumentés (S9 Android 10 et S24 FE Android 16), lint 0, detekt 0.
