# Relecture Claude : l'aperçu masqué de l'appli SMS, dit sur l'accueil (audit FN-2, D-037), avant commit — 2026-11-07

> Agent `android-code-quality-deep-dive`, lecture seule ; il a lu aussi les sources de SMS Tech
> (`IncomingMessageNotifier`, `OutgoingFailureNotifier`, `MmsFailureNotifier`). Consigne : justesse (faux « masqué »,
> faux « montré », changement d'appli SMS), concurrence, vie privée, tests. 0 $ (sous-agent).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Toute notification de SMS Tech effaçait le oui, pas seulement un message reçu : un échec d'envoi, une alerte de sécurité portent un vrai texte — l'avertissement partait, l'aperçu toujours masqué | **réel** (confirmé dans les deux sources) | **corrigé** : seul un message reçu dans une conversation compte (`receivedInConversation`) ; test JVM et test sur appareil |
| 2 | Le dernier message d'une conversation peut être la réponse de l'utilisateur ; en « uniquement après déverrouillage », un message reçu déverrouillé montrerait son texte | **réel** pour la réponse ; **faux** pour le déverrouillage : SMS Tech écrit son texte fixe dans les deux cas (ses sources, et la mesure, faite déverrouillée) | **corrigé** : la réponse de l'utilisateur (sans expéditeur) ne compte pas ; test sur appareil |
| 3 | Après un changement d'appli SMS par défaut, l'avertissement restait jusqu'au premier SMS de la nouvelle | **réel** | **corrigé** : le nom de paquet de l'appli qui masque est gardé, l'accueil ne parle que tant qu'elle est l'appli par défaut (relue à chaque retour) ; mesuré ; PRIVACY dit le nom de paquet |
| 4 | Ce que la mesure ne prouvait pas (1, 3, le déverrouillage) | juste | 3 et le déverrouillage mesurés ; 1 couvert par les tests (un échec d'envoi n'est pas provoqué sur l'émulateur) |
| 5 | Tests : seule la fonction pure était testée | **réel** (bas) | les règles nouvelles testées (JVM et appareil) ; la persistance et le branchement restent mesurés sur l'émulateur, pas testés |
| 6 | Concurrence, `combine`, lecture des préférences à l'injection | sans défaut | rien |

Vie privée : rien du texte n'est gardé ni journalisé ; PRIVACY dit vrai (mis à jour pour le nom de paquet).

## Vérifications après correction

Gate local vert (607 tests) ; `LatestSmsOnDeviceTest` (7 tests) vert sur l'émulateur Android 16 ; mesure de bout en
bout refaite (aperçu « jamais » → avertissement ; Google Messages par défaut → parti, sans SMS).
