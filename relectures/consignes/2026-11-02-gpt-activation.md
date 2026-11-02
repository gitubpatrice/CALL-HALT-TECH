# Relecture de code : l'écran d'activation d'une appli Android anti-arnaque

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. L'appli interrompt l'utilisateur (« écran
STOP ») quand une action sensible survient pendant un appel téléphonique ou dans les 15 minutes qui suivent (un SMS
de code, une demande de validation de l'appli bancaire). Ses premiers utilisateurs sont des personnes âgées.

## Ce qui est relu (le diff ci-dessous, et les fichiers complets)

- L'**activation** (`ActivationScreen`) demande trois accès, un par écran, dans cet ordre : le téléphone
  (`READ_PHONE_STATE` + `ANSWER_PHONE_CALLS`, un seul dialogue), l'affichage par-dessus les autres applis
  (`SYSTEM_ALERT_WINDOW`, page des réglages), l'accès aux notifications (page des réglages). Les accès sont relus à
  chaque retour au premier plan ; l'étape accordée laisse place à la suivante.
- L'**accueil** (`HomeScreen`) dit l'état de la protection : « en marche » seulement si les trois sont accordés.
- L'**aide aux « paramètres restreints »** d'Android 13+ s'affiche selon `mayBeRestricted` (version d'Android et
  source d'installation).

## Mesuré sur émulateurs le 2026-11-02 (ne pas contester sans preuve)

APK installé depuis un fichier : Android 14 bloque l'accès aux notifications seul ; Android 16 bloque aussi
l'affichage par-dessus. Le menu « Autoriser les paramètres restreints » des infos de l'appli n'apparaît qu'après un
premier refus ; un seul déblocage ouvre les deux accès. À partir d'Android 11, `ACTION_MANAGE_OVERLAY_PERMISSION`
ignore le paquet et montre une liste. Le parcours complet a été fait sur Android 16 et fonctionne.

## Ne PAS signaler

- Le choix des trois accès et leur ordre (décidés), l'absence d'étape « applis inutilisées » (l'hibernation épargne
  une appli dont l'écouteur de notifications est actif).
- Le style des traductions, le nommage, les commentaires, le formatage, les suggestions d'architecture générales.

## Ce qui est demandé (au plus 6 constats)

Des défauts RÉELS, chacun avec un **scénario d'échec concret** et votre degré de certitude. En priorité : un
utilisateur bloqué sans issue ; un état affiché faux (« en marche » alors que la protection ne marche pas, ou
l'inverse) ; le cycle de vie (rotation, mort du processus pendant le dialogue ou dans les réglages, retour) ; les
versions d'Android 10 à 16 et les fabricants (page de réglages absente) ; un texte qui ne dit pas ce que fait le
code. « Aucun défaut réel » est une réponse attendue : ne remplissez pas.
