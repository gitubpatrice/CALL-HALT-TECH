# Relecture — un service d'accessibilité activé pendant un appel (D-033) — 2026-11-05

> Deux agents Claude, lecture seule ; 0 $ (sous-agents). Objet, avant commit : `platform/accessibility/`
> (`AccessibilityGrants`, `AccessibilitySensor`), le nouveau déclencheur `ACCESSIBILITY_ENABLED`, ses textes.

## 1. Correction et sécurité (agent `android-code-quality-deep-dive`)

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Moyen : les entrées comparées telles quelles ; un même service écrit en forme courte (`pkg/.Cls`) ou longue passerait pour nouveau à une réécriture : un faux STOP | réel (probable) | **corrigé** : tout est lu en forme longue, testé ; Android 15 écrit la longue (mesuré) |
| 2 | Faible : un service activé pendant que le processus était mort n'est pas vu | réel, rare | **écrit** dans D-033 |
| 3 | Faible : deux applis dans la minute d'un STOP sans réponse : un STOP, la première au journal | réel, sans conséquence | **écrit** dans D-033 |

Sans défaut : le fil principal, le profil de travail, le réglage lisible sans restriction de l'API 29 à 36, son
écriture réservée à `WRITE_SECURE_SETTINGS`, le moteur et la base (le nouveau déclencheur gardé par son nom).

## 2. Les faux positifs de la règle (agent `regles-faux-positifs-auditor`)

**La règle est à garder** : sous Android 15 et avant, la seule barrière pendant l'appel ; la seule qui voie une appli
hors de la liste de D-031, ou dont les notifications sont refusées.

| N° | Situation construite | Verdict | Suite |
|---|---|---|---|
| 1 | Une fille fait activer TalkBack (ou le menu d'accessibilité) à sa mère pendant l'appel ; le STOP lui fait dire « appuie sur Continuer » | **réel, grave** | **corrigé** : les fonctions du téléphone exemptées, par leur nom ET la marque d'appli préinstallée ; **mesuré** (le menu d'accessibilité → rien ; RustDesk → STOP) |
| 2 | TalkBack activé par le raccourci des touches de volume pendant l'appel : le STOP lu à voix haute à qui ne sait pas le manier | réel, grave | **corrigé** par le n° 1 |
| 3 | Bixby (S9) : appli système qui a un service d'accessibilité et peut écrire le réglage elle-même | capacité lue sur le S9, comportement non mesuré | **couvert** : Bixby et l'appareil photo de Samsung dans la liste |
| 4 | Un antivirus activé juste après un appel inquiétant | réel, moyen | **laissé** : un faux antivirus est un vecteur classique ; écrit |
| 5 | Le détecteur d'hameçonnage de Pass Tech, du même portefeuille | réel, rare | laissé (optionnel : par son certificat) |
| 6 à 9 | Enregistreur d'appels, migration du menu d'accessibilité par Android, restauration d'un téléphone, applis d'utilisateurs avancés | réels, rares | 7 couvert par le n° 1 ; les autres écrits ou laissés |
| 10 | Le réglage illisible au démarrage valait « aucun service » : au premier changement, tous paraissaient nouveaux | réel (logique), rare | **corrigé** : inconnu, le premier changement est seulement lu |
| 11 | « Éteignez votre téléphone » ne retire pas l'accès, qui revient au démarrage | réel | **à proposer à Patrice** : un bouton vers la page Accessibilité des Réglages |

Vu sur le S24 FE (Android 16, lecture seule) : la suite (`com.samsung.accessibility`) et le TalkBack de Samsung
(`com.samsung.android.accessibility.talkback`), SYSTEM ; et « Lien avec Windows » (`com.microsoft.appmanager`), qui
recopie l'écran sur un PC : non exempté.
