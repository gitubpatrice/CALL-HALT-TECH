# Relecture de sécurité — l'appli SMS par défaut rendue visible — 2026-11-03

> Agent `android-code-quality-deep-dive`, axe sécurité, lecture statique ; 0 $ (sous-agent). Objet : le second bloc
> `<queries>` du manifeste (`SENDTO`, `smsto:`), le KDoc de `NotificationListener.defaultSmsApp()` et le test
> `DefaultSmsAppOnDeviceTest`, avant commit. Le défaut corrigé a été trouvé en mesurant sur le S24 FE (journal de l'appli :
> « Default SMS app: null » pendant 5 min après l'installation).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| — | Surface d'attaque : aucune. `<queries>` n'accorde que la visibilité ; `sbn.packageName` est attesté par le système ; `getDefaultSmsPackage` rend le détenteur du rôle SMS, choisi par l'utilisateur, jamais un tiers qui déclare `SENDTO smsto` | accord | rien à faire |
| 1 | Faible (documentation) : « diffusion protégée, qu'Android écarte de la visibilité » écrit comme un fait, sans preuve | **réel** | **vérifié puis précisé** : `SMS_DELIVER` figure dans les diffusions protégées (`dumpsys package`, émulateur Android 16), et `AppsFilterUtils` (sources d'AOSP, branche `main`) passe ces diffusions à `IntentFilter.match` pour les seuls récepteurs, comme actions à ignorer. Manifeste, test et `docs/ARCHITECTURE.md` (D-021) le disent avec leur source |
| 2 | Faible (test) : `isRoleAvailable` ne garantit pas qu'une appli SMS existe ; sans elle, le test serait rouge à tort | réel, sans effet ici | **écrit dans le KDoc du test** : `getRoleHolders` est réservé au système, et la seule autre façon de savoir dépend de la visibilité même que le test vérifie (le contrôle négatif serait alors ignoré au lieu d'être rouge). Aucun téléphone ni émulateur d'ici n'a le rôle sans appli |
| — | `PRIVACY.md` : rien d'obligatoire (aucune donnée gardée ni envoyée) ; garde CI des permissions non concernée (`<queries>` n'est pas une permission ; `QUERY_ALL_PACKAGES` reste refusée) | accord | rien à faire |

**Bilan** : aucun constat de sécurité ; 2 constats faibles, l'un vérifié dans les sources d'AOSP puis écrit, l'autre écrit.
