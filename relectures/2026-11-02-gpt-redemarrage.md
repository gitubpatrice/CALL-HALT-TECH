# Relecture du code par GPT — un appel ne survit pas à un redémarrage — 2026-11-02

> Modèle **gpt-5.6-luna**, effort high, **0,017 $** (20 942 tokens d'entrée, 10 537 de sortie dont 9 322 de
> raisonnement) ; le diff `a97fda4..c087b9f` et 9 fichiers complets (~86 Ko). Consigne :
> `consignes/2026-11-02-gpt-redemarrage.md`. Menée en même temps que la relecture Claude
> (`2026-11-02-claude-redemarrage.md`), sans que l'une voie l'autre. Rapport brut ci-dessous, après le tri.

## Tri (chaque constat vérifié dans le code)

| N° | Verdict | Suite |
|---|---|---|
| 1 | **faux tel qu'écrit, et sa direction aussi** : un compteur jamais vu fait rapporter le démarrage EN COURS, qui ne termine que les appels commencés avant lui ; un appel réellement en cours a commencé après. Le danger est à l'inverse : se taire laisse le fantôme « en cours » et y absorbe le vrai appel suivant (relecture Claude, n° 1) | **rien de ce qu'il proposait** ; le contraire est appliqué : dans le doute, le redémarrage est rapporté |
| 2 | même famille (mémoire en panne, puis revenue) | idem |
| 3 | **réel**, fenêtre de quelques millisecondes : le compteur était retenu AVANT que le moteur ait traité le redémarrage (trouvé aussi par la relecture Claude, n° 2) | **corrigé** : retenu une fois traité (`Restart.record`) ; traité deux fois, il ne change rien (test du moteur) ; ordre vérifié par un test du cas d'usage |
| 4 | **réel** : la lecture du compteur était hors du `try`, contre le contrat du port (relecture Claude, n° 5) | **corrigé** (test : un lecteur qui lève une exception ne fait rien rapporter) |
| 5 | **réel**, rare : une horloge reculée pendant le redémarrage met l'heure calculée avant le début du fantôme, qui reste ouvert | **écrit** (D-020, P-13), avec la parade propre : le numéro de démarrage porté par chaque appel |

**Bilan** : 2 défauts réels corrigés, 1 limite écrite, 2 constats faux dont la correction proposée aurait ouvert
un STOP manqué. Une relecture ne s'applique pas sans être vérifiée — ici, contre une autre.

---

J’ai relevé **5 défauts réels**.

### 1. L’absence de point de référence est confondue avec un redémarrage

**Scénario :**

1. Une version antérieure de l’application a enregistré un appel en cours dans `DetectorStore`.
2. `LAST_BOOT_COUNT` est absent — cas d’une mise à jour vers cette version, d’une première lecture après une panne d’écriture, ou d’une restauration partielle.
3. Le téléphone n’a pas redémarré et `BOOT_COUNT == 42`.
4. Le premier signal traité lit `previous == null`, écrit `42`, puis renvoie `PhoneRestarted`.
5. `onPhoneRestarted` termine l’appel encore réellement en cours.

Résultat : un appel réel est classé comme terminé et les signaux suivants sont traités comme « après appel ». La branche est explicitement voulue pour « first install, an update », mais elle produit bien un redémarrage qui n’a pas eu lieu lors d’une mise à jour avec un état de détection conservé.

**Certitude : élevée.**

---

### 2. Une panne temporaire de mémoire peut ensuite fabriquer un faux redémarrage

C’est une variante importante du problème précédent, même si le code documente que la panne mémoire doit retourner `null`.

**Scénario :**

1. Le téléphone est dans le boot `42`, sans redémarrage.
2. Un appel est suivi normalement.
3. `store.edit` échoue lors d’un appel à `unreported()`. La méthode retourne `null`, sans mémoriser `42`.
4. Plus tard, le DataStore redevient disponible, toujours dans le boot `42`.
5. `previous == null`, donc `unreported()` rapporte `Boot(42, ...)` comme un redémarrage.
6. L’appel réel est terminé dans le domaine.

Le `null` « mémoire indisponible » n’est donc pas neutre : il rend le prochain succès indistinguable du premier démarrage de l’application.

**Certitude : élevée.**

---

### 3. Le redémarrage est consommé avant que `PhoneRestarted` soit traité

`BootCountRestarts` écrit `LAST_BOOT_COUNT` avant que `ProcessSignalUseCase` ne puisse committer `PhoneRestarted`.

**Scénario :**

1. Un boot nouveau est détecté ; `unreported()` écrit le nouveau compteur.
2. `store.apply { ... PhoneRestarted ... }` échoue, ou la coroutine est annulée entre les deux opérations.
3. `ProcessSignalUseCase` sort immédiatement : le signal original n’est pas traité.
4. Au signal suivant, `unreported()` retourne `null`, car le boot est déjà mémorisé.
5. L’état du détecteur contient toujours l’ancien appel.

Conséquences possibles :

- le premier signal sensible post-redémarrage est perdu et aucun STOP n’est affiché ;
- ou une demande légitime est faussement traitée comme intervenant pendant un appel qui aurait dû être terminé.

La propagation de `CancellationException` rend ce cas concret lors de l’arrêt du processus. Le contrat de `DetectorStore` prévoit un repli pour certaines erreurs, mais il ne rend pas atomique l’écriture du compteur et le traitement de `PhoneRestarted`.

**Certitude : élevée.**

---

### 4. `AndroidBootReader.current()` peut faire échouer tout le traitement d’un signal

Le `try/catch` de `BootCountRestarts.unreported()` commence **après** l’appel à `boots.current()` :

```kotlin
val boot = boots.current() ?: return null
return try { ... }
```

Or `Settings.Global.getInt(...)` peut lever une exception dans un état système/OEM anormal — par exemple valeur `BOOT_COUNT` malformée (`NumberFormatException`) ou erreur du fournisseur Settings.

**Scénario :**

1. Un signal téléphonique ou bancaire arrive.
2. La lecture de `BOOT_COUNT` lève une exception.
3. `RestartSource.unreported()` viole son contrat « never throws ».
4. Le signal n’atteint pas le moteur ; la file le journalise comme échec et passe au suivant.
5. Le redémarrage peut donc être manqué, et le signal sensible perdu.

La certitude est élevée sur le défaut de robustesse du code ; la fréquence sur appareil Android stock est plus incertaine.

**Certitude : élevée sur le défaut, moyenne sur la fréquence.**

---

### 5. L’horodatage du boot peut rendre `PhoneRestarted` inopérant

Le timestamp est reconstruit avec l’horloge civile courante :

```kotlin
Instant.now().minusMillis(SystemClock.elapsedRealtime())
```

Cette valeur n’est correcte que si l’horloge civile n’a pas changé depuis le démarrage.

**Scénario :**

1. Un appel est enregistré à `10:00`.
2. Le téléphone redémarre réellement à `10:30`.
3. Pendant le redémarrage, l’horloge est reculée à `09:00`.
4. Le premier signal post-redémarrage arrive à `09:05`.
5. `Boot.at` vaut environ `09:00`, donc `CallWindow.inProgress(call, signal.at)` est faux puisque `09:00 < 10:00`.
6. `PhoneRestarted` ne termine pas l’ancien appel.
7. Lorsque l’horloge revient à `10:40`, l’état fantôme peut à nouveau déclencher un STOP ou rester actif jusqu’au plafond.

Le problème n’est pas simplement l’usage général de l’horloge civile : ici, il empêche précisément le signal correctif de terminer l’appel qu’il devait réparer.

**Certitude : élevée.**