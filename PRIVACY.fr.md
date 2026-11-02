# Confidentialité — Call Halt Tech

[English](PRIVACY.md)

*Décrit la version en développement (0.1.0). Chaque permission que l'appli obtiendra sera ajoutée ici dans
la même modification que dans l'appli, et le build vérifie que l'appli ne demande rien d'autre.*

## Ce que l'appli demande

- **Téléphone** (`READ_PHONE_STATE`) : savoir quand un appel commence et se termine. L'appli ne lit pas le
  numéro (il faudrait une autre permission, qu'elle ne demande pas) et n'entend jamais l'appel. Cette
  version ne la demande pas encore à l'écran : rien n'est détecté tant qu'elle n'est pas accordée dans les
  réglages du téléphone et que l'appli n'a pas été ouverte une fois.

Le manifeste porte aussi une ligne ajoutée par une bibliothèque d'Android pour l'usage interne de l'appli
(`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) ; elle ne donne accès à rien.

## Ce que l'appli garde

Sur ce téléphone seulement, dans l'espace privé de l'appli :

- ses réglages ;
- la mémoire de son détecteur : pour le dernier appel seulement, quand il a commencé et fini, et s'il était
  entrant ou sortant — jamais le numéro ; et le dernier état d'appel signalé par le téléphone ;
- un journal des alertes affichées (quand, pourquoi, ce qui a été choisi). Dans cette version, rien n'y
  écrit encore.

La sauvegarde Android et le transfert vers un autre téléphone sont **désactivés** pour cette appli : rien
de tout cela ne quitte le téléphone.

## Ce que l'appli envoie

**Rien.** Elle n'a ni permission Internet, ni compte, ni serveur, ni statistiques d'usage, ni services
Google Play.
