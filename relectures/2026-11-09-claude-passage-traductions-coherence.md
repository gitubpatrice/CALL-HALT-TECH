# Passages avant la relecture finale : traductions et cohérence des motifs — 2026-11-09

> Deux agents en lecture seule sur `main` = `553c4f8`, après les décisions de l'audit de publication n° 1 (FN-4, FN-3,
> N-3, l'historique) : `android-i18n-strings-cleaner` (parité des 5 langues, sens, registre, textes en dur) et
> `android-architecture-coherence-checker` (les motifs qui ont divergé entre les corrections récentes et le code plus
> ancien). 0 $ (sous-agents). Prévus par REPRISE.md.

## Traductions — tri

Aucun sens faux, aucun placeholder ni clé en défaut ; `tools/check-translations.py` : parité des 153 clés.

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 5 | L'historique reprenait le titre de la notification, « X vient d'être installée » : faux relu des mois plus tard, ou par la banque | **réel** | **corrigé** : une clé à lui, `history_remote_installed` (« X a été installée »), 5 langues ; test sur l'appareil |
| 3 | « l'accès accessibilité » sans guillemets dans le SMS au proche, avec guillemets à l'écran STOP et dans les autres langues | **réel** (typographie) | **aligné** : « l'accès « accessibilité » » |
| 1, 2 | Le tutoiement dans `access_alerts_how` (IT, ES) | **non retenu** : la citation reprend la boîte de dialogue d'Android, qui tutoie dans ces langues | rien |
| 4, 6 | DE/IT/ES : les libellés d'Android paraphrasés sans guillemets ; « Warnung » / « Avviso » pour l'alerte et l'avertissement | **à faire relire par un natif** | noté |
| 7 | L'anglais de `guardian_number_invalid` sans exemple de numéro | **non retenu** : l'anglais ne vise pas un pays ; un numéro français y serait faux | rien |
| 8, 9 | Espaces insécables avant « : » inégales ; le préfixe d'une clé | typographie du propriétaire ; nom de clé | rien |

## Cohérence — tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Les réponses qui ouvrent une page écrites deux fois (`OPENS`, `CALL_FIRST`), et un `else -> Unit` qui avalait le reste : une réponse ajoutée à un seul endroit ouvrait une page par-dessus l'appel, ou fermait l'écran sans rien ouvrir | **réel** (le seul qui pouvait cacher un bug) | **corrigé** : une seule liste, `OPENING_CHOICES` ; le `when` exhaustif ; un journal de débogage quand « Désinstaller » n'a pas d'appli |
| 2 | À la première connexion de l'écouteur dans un processus, le compte « d'avant » valait 0 : un téléphone déjà au-delà de 30 notifications, dont 5 récentes, faisait une « rafale » à chaque démarrage du processus | **réel** | **corrigé** : la première fois, celles plus anciennes que la fenêtre comptent comme déjà là, comme la règle les aurait vues en direct ; test. Le recompte au changement d'appli SMS ne prévient pas : écrit pourquoi |
| 3 | `platform` dépendait de `ui` (`UNINSTALL_TRIGGERS`) | **réel** | **corrigé** : la règle à côté de `gravest`, dans `platform/stop` |
| 7, 8 | Le KDoc de l'écouteur incomplet ; une variable locale qui masquait un champ d'un autre type | **réel** | **corrigé** |
| 10 | Le composeur n'attrapait pas `SecurityException`, les réglages oui | **réel** | **aligné** |
| 4 | `SmsPreview` en SharedPreferences, le reste en DataStore ; l'appli SMS par défaut lue en deux endroits | **réel**, sans défaut | laissé (chantier moyen) |
| 5 | Les compteurs de `SmsAppNotices` gardés à la déconnexion | **voulu** : les vider ferait perdre la rafale venue pendant l'absence (relecture GPT de N-3, 1) ; l'accueil cache l'avertissement quand l'écouteur est délié | rien |
| 6, 9 | Trois conventions d'horloge (documentées) ; une constante répétée dans trois ViewModels | mineurs | laissés |

## Vérifications après correction

Gate de la CI vert en local : **670 tests** ; traductions alignées sur **154** clés ; `HistoryTextOnDeviceTest` 4 sur 4
sur l'émulateur Android 16.
