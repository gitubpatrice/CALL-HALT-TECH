# Confidentialité — Call Halt Tech

[English](PRIVACY.md)

*Décrit la version en développement (0.1.0). Chaque permission que l'appli obtiendra sera ajoutée ici dans
la même modification que dans l'appli, et le build vérifie que l'appli ne demande rien d'autre.*

## Ce que l'appli demande

**Aucune permission.** La seule ligne de son manifeste est ajoutée par une bibliothèque d'Android pour
l'usage interne de l'appli (`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) ; elle ne donne accès à rien.

## Ce que l'appli garde

Sur ce téléphone seulement, dans l'espace privé de l'appli :

- ses réglages ;
- la mémoire de son détecteur et un journal des alertes affichées (quand, pourquoi, ce qui a été choisi).
  Dans cette version, rien n'y écrit encore.

La sauvegarde Android et le transfert vers un autre téléphone sont **désactivés** pour cette appli : rien
de tout cela ne quitte le téléphone.

## Ce que l'appli envoie

**Rien.** Elle n'a ni permission Internet, ni compte, ni serveur, ni statistiques d'usage, ni services
Google Play.
