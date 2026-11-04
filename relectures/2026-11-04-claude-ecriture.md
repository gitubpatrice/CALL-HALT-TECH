# Relecture — chaque validation sur le disque (P-22, D-027) — 2026-11-04

> Agent `android-code-quality-deep-dive`, axes correction puis sécurité, lecture statique ; 0 $ (sous-agent). Objet :
> `CallHaltDatabase.create` (journal d'annulation, `SyncEachCommit` : `journal_mode = TRUNCATE` et `synchronous = FULL` à
> l'ouverture), `StorageModule`, les tests sur téléphone, avant commit. Lui étaient donnés comme acquis : la perte mesurée
> en `WAL` (coupure 10 s après une validation), le passage à `TRUNCATE`/`FULL` et l'appel gardé après la même coupure,
> la mise à jour depuis un fichier `-wal` non reporté, les valeurs `persist`/`NORMAL` du Galaxy S9, 27 tests verts.
> L'agent n'avait pas accès aux sources d'AOSP, de Room ni de SQLite : il a marqué « de mémoire » ce qui en dépend.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| H1 | Élevé : en `TRUNCATE`, SQLite ne forcerait pas sur le disque le journal vidé ; une coupure juste après la validation laisserait un journal « chaud », et l'appel serait défait au démarrage. La mesure à 10 s ne l'exclut pas (ext4 écrit ses métadonnées toutes les 5 s) | **faux**, réfuté de deux façons | **source** : `pager.c` (SQLite, lignes 2131-2143), en `TRUNCATE` le journal vidé est suivi de `sqlite3OsSync` dès que `fullSync`, soit `synchronous` ≥ `FULL` (ligne 3684), depuis le commit du 2014-10-21 « Call fsync() right after ftruncate() when in journal_mode=TRUNCATE and PRAGMA synchronous=FULL » (bogue Mozilla 1072773) ; Android 10 embarque SQLite 3.22 (2018). **Mesure** : coupure nette environ 1 s après la validation, émulateur Android 16 : l'appel gardé, le journal vide sur le disque. Écrit dans la KDoc et D-027 |
| M1 | Moyen : le réglage tient à l'ouverture, mais une connexion rouverte (erreur grave, changement de `WAL`) reprendrait les valeurs d'Android sans repasser par le rappel | réel en principe, sans déclencheur | **écrit** dans D-027 (limites) : l'agent n'a trouvé dans l'appli aucun appel qui la rouvre ; le test vérifie l'état après l'ouverture |
| M2 | Moyen : un mode que SQLite refuserait de changer n'est que noté au journal de débogage, l'accueil ne le dit pas | réel, improbable (une seule connexion, aucune transaction ouverte) | **écrit** dans D-027 : `FULL` reste posé, et une erreur levée fait échouer l'ouverture, que P-10 montre déjà |
| M3 | Moyen : si la base était défaite et pas le fichier DataStore du dernier démarrage, le redémarrage ne serait plus rapporté | **sans objet** | le démarrage est retenu après le retour de `store.apply` (`ProcessSignalUseCase`), donc après une validation désormais sur le disque ; perdre le compteur seul ne fait que rapporter le redémarrage deux fois, sans effet (D-020) |
| F1 | Faible : hors `WAL`, une lecture attend l'écriture en cours | réel, sans effet | aucune lecture sur le fil principal (Room le refuse), aucune lecture pendant `step` ; écrit dans D-027 (coût) |
| F2 | Faible : « la paire que Room pose lui-même » non vérifiable de mémoire | **vérifié** | Room 2.8.5 porte les quatre chaînes (`BaseRoomConnectionManager`) ; la source d'AndroidX (`RoomConnectionManager`, Room 3) apparie `TRUNCATE` avec `FULL` et `WAL` avec `NORMAL`. KDoc précisée : « par un pilote » |
| F3 | Faible : le second test appelle le rappel à la main, il ne prouve pas que Room l'appelle | réel, couvert | le premier test le prouve sur le S9, où les valeurs d'Android sont autres (rouge sans le rappel, mesuré) |

**Sécurité** (par l'agent) : aucune nouvelle surface ; les deux réglages sont des constantes, rien de personnel au
journal de débogage. En `persist`, le journal garderait d'anciennes pages : c'est `TRUNCATE` qui est posé.

**Bilan** : aucun constat de sécurité ; le constat élevé, faux (source et mesure) ; deux moyens réels sans déclencheur,
écrits ; un moyen sans objet ; trois faibles vérifiés ou couverts.
