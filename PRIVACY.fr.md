# Confidentialité — Call Halt Tech

[English](PRIVACY.md)

*Décrit la version en développement (0.1.0). Chaque permission que l'appli obtiendra sera ajoutée ici dans
la même modification que dans l'appli, et le build vérifie que l'appli ne demande rien d'autre.*

## Ce que l'appli demande

- **Téléphone** (`READ_PHONE_STATE`) : savoir quand un appel commence et se termine. L'appli ne lit pas le
  numéro (il faudrait une autre permission, qu'elle ne demande pas) et n'entend jamais l'appel. Cette
  version ne la demande pas encore à l'écran : rien n'est détecté tant qu'elle n'est pas accordée dans les
  réglages du téléphone et que l'appli n'a pas été ouverte une fois.
- **Affichage par-dessus les autres applis** (`SYSTEM_ALERT_WINDOW`) : mettre l'écran STOP devant l'appli
  que vous utilisez — celle de votre banque comprise — quand une action à risque arrive pendant un appel.
  L'appli ne dessine aucune fenêtre par-dessus les autres : l'écran STOP est un écran entier, à part, exclu des
  captures d'écran et du partage d'écran.
- **Répondre aux appels** (`ANSWER_PHONE_CALLS`) : seulement pour mettre fin à l'appel quand vous appuyez sur
  « Raccrocher », ou « Raccrocher et appeler ma banque », dans l'écran STOP. Sans elle, aucun de ces deux
  boutons n'apparaît pendant un appel. L'appli ne décroche et ne passe jamais d'appel d'elle-même : « appeler
  ma banque » ouvre le clavier du téléphone, et c'est vous qui appelez.

Le manifeste porte aussi une ligne ajoutée par une bibliothèque d'Android pour l'usage interne de l'appli
(`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) ; elle ne donne accès à rien.

## Ce que l'appli garde

Sur ce téléphone seulement, dans l'espace privé de l'appli :

- ses réglages ;
- la mémoire de son détecteur : pour le dernier appel seulement, quand il a commencé et fini, et s'il était
  entrant ou sortant — jamais le numéro ; et le dernier état d'appel signalé par le téléphone ;
- un journal des alertes affichées (quand, pourquoi, ce qui a été choisi). Dans cette version, aucun capteur
  ne déclenche encore d'alerte : il reste vide.

La sauvegarde Android et le transfert vers un autre téléphone sont **désactivés** pour cette appli : rien
de tout cela ne quitte le téléphone.

## Ce que l'appli envoie

**Rien.** Elle n'a ni permission Internet, ni compte, ni serveur, ni statistiques d'usage, ni services
Google Play.
