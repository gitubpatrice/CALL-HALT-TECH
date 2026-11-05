# Relecture — le capteur des applis de prise en main installées (D-031) — 2026-11-05

> Trois agents Claude, lecture seule ; 0 $ (sous-agents). Objet, avant commit : `platform/install/` (la liste de 49
> identifiants, `installSignal`, `InstallSensor`), les `<queries>` du manifeste, `platform/warning/WarningNotifier`,
> l'exécuteur qui poste les avertissements, l'appli portée par `ShowWarning` et `JournalEntry.Warning`, les textes.

## 1. La liste elle-même (agent `general-purpose`, recherche web)

Chaque identifiant cherché sur sa page Google Play (vitrine française), F-Droid ou le site de l'éditeur : 37 applis
« côté contrôlé », leurs modules, et les applis écartées (contrôleur seulement, caméra seulement, homonymes : une
« Quick Assist » qui n'est pas de Microsoft, un guide qui n'est pas UltraViewer). **Revérifié par Claude** : les 48
identifiants retenus de Google Play, un à un (HTTP 200, titre de la page relevé), et deux identifiants que l'agent
donnait pour faux (`com.anydesk.adcontrol.ad2`, `com.teamviewer.quicksupport.addon.samsung`) : 404, comme il le disait.
RustDesk : identifiant lu dans l'APK de sa version 1.5.0 (empreinte conforme à celle de GitHub).

## 2. Correction et sécurité (agent `android-code-quality-deep-dive`)

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Moyen : le récepteur n'existe que dans le processus vivant ; aucun rattrapage d'une installation faite processus mort | réel, **une limite** : l'écouteur de notifications tient le processus (D-023, relancé en 1 s, P-5) ; sans cet accès, l'accueil dit déjà « Protection désactivée » | **écrite** dans D-031 ; rattrapage (`getChangedPackages`) non fait |
| 2 | Faible : une exception levée en postant l'avertissement journaliserait « non affiché » un STOP décidé avec lui | réel, latent (le moteur ne décide jamais les deux ensemble aujourd'hui) | **corrigé** (`AndroidDecisionExecutor.posted`), testé, contrôle négatif fait |
| 3 | Faible : un canal « avertissements » coupé par l'utilisateur passait pour posté | réel | **corrigé** (`canPost` lit l'importance du canal) |
| 4 | Faible : une seconde appli de la liste dans la minute d'un STOP sans réponse est absorbée, sans ligne de journal | réel, voulu | **écrit** dans D-031 |
| 5 | Faible : une appli ajoutée pour un second utilisateur, ou restaurée, compte comme installée | probable, côté protecteur | **écrit** dans D-031 |
| 6 | Faible : `onReceive` sans test ; le test lit le manifeste source, pas le fusionné | réel ; le fusionné ne peut qu'ajouter des `<queries>` (aucune surcouche n'en retire) | mesuré sur émulateur ; rien |
| 7 | Faible : la notification nomme l'appli sur l'écran verrouillé | réel, voulu (urgence) | rien |
| 8 | Deux applis installées : deux notifications et deux pages distinctes | **juste** | — |

Sans défaut : la diffusion ne peut pas être forgée (protégée, récepteur non exporté) ; Android 10, sans filtre de
visibilité, reçoit tout, filtré par `installSignal` ; un profil de travail n'entend rien ; une appli qui prendrait un
identifiant de la liste reçoit notre nom et la page des Réglages du système.

## 3. Les faux positifs de la règle (agent `regles-faux-positifs-auditor`)

**Aucun identifiant faux, aucun à retirer** (les 49 pages relues, un éditeur par famille). Les défauts étaient dans les
mots.

| N° | Situation construite | Verdict | Suite |
|---|---|---|---|
| 1 | Hors appel, AirDroid installé pour envoyer ses photos sur son PC : « Désinstallez-la. », un ordre sans condition, faux pour lui ; et la phrase ne couvrait pas l'escroc qui se dit de Microsoft ou de l'opérateur | **réel** | **corrigé** (Patrice) : « Si vous ne l'avez pas installée de vous-même, désinstallez-la. » |
| 2 | Pendant un appel, une banque allemande fait installer QuickSupport au client qui l'a appelée (page de la Volksbank Berg, **relue par Claude**) : le STOP disait « ne vous demanderont JAMAIS d'installer » — faux pour lui, et une règle qu'on sait fausse rend l'écran douteux (D-017) | **réel** | **corrigé** (Patrice) : « ne vous appelleront JAMAIS pour vous faire installer ou ouvrir une telle appli », comme la règle des codes |
| 3 | Un proche ou l'informatique de l'entreprise aide par téléphone : STOP, « si on vous dit d'ignorer cet écran, c'est une arnaque » | réel, le prix accepté pour les codes (D-017) | **écrit** dans D-031 ; rien à changer (la phrase désarme l'escroc encore en ligne) |
| 4 | Le module réclamé ensuite (« AnyDesk plugin ad1 ») : un second STOP | voulu | **écrit** : une seconde chance |
| 5 | BeyondTrust, SimpleHelp, AweSun par code ne font que montrer l'écran ; le STOP disait « contrôler » | **réel** | **corrigé** (Patrice) : « voir ou contrôler », « quelqu'un le voit ou le contrôle peut-être », dans les 5 langues |
| 6 | Une installation sans rapport pendant un appel avec sa sœur | réel, rare | rien : filtrer selon l'interlocuteur reviendrait à croire le numéro |
| 7 | Une restauration sur un nouveau téléphone réinstalle QuickSupport | probable, non mesuré | **écrit** dans D-031, piste à mesurer d'abord |
| 8 | L'agent imposé par l'employeur (AirDroid Business, Jump Client…) | réel, rare | rien : le retirer laisserait passer l'essai gratuit qu'ouvre l'escroc |

Les traductions des nouveaux mots (DE, IT, ES) suivent la tournure de la règle des codes, déjà traduite ; la relecture
par un natif reste à faire, comme pour les autres.
