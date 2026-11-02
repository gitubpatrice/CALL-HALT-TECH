# Confidentialité — Call Halt Tech

[English](PRIVACY.md)

*Décrit la version en développement (0.1.0). Chaque permission que l'appli obtiendra sera ajoutée ici dans
la même modification que dans l'appli, et le build vérifie que l'appli ne demande rien d'autre.*

## Ce que l'appli demande

- **Téléphone** (`READ_PHONE_STATE`) : savoir quand un appel commence et se termine. L'appli ne lit pas le
  numéro (il faudrait une autre permission, qu'elle ne demande pas) et n'entend jamais l'appel. L'écran
  d'activation la demande, avec « Répondre aux appels » ci-dessous : Android pose les deux en une seule question.
- **Affichage par-dessus les autres applis** (`SYSTEM_ALERT_WINDOW`) : mettre l'écran STOP devant l'appli
  que vous utilisez — celle de votre banque comprise — quand une action à risque arrive pendant un appel.
  L'appli ne dessine aucune fenêtre par-dessus les autres : l'écran STOP est un écran entier, à part, exclu des
  captures d'écran et du partage d'écran.
- **Répondre aux appels** (`ANSWER_PHONE_CALLS`) : seulement pour mettre fin à l'appel quand vous appuyez sur
  « Raccrocher », ou « Raccrocher et appeler ma banque », dans l'écran STOP. Sans elle, aucun de ces deux
  boutons n'apparaît pendant un appel. L'appli ne décroche et ne passe jamais d'appel d'elle-même : « appeler
  ma banque » ouvre le clavier du téléphone, et c'est vous qui appelez.
- **Accès aux notifications** (accordé dans les réglages du téléphone ; ce n'est pas une permission) : voir
  l'appli de votre banque vous demander de valider une opération, et un code de validation arriver par SMS. De
  chaque notification du téléphone, l'appli lit l'appli qui l'a envoyée et son canal (l'identifiant que cette
  appli donne à un type de notification). Elle reconnaît une demande de la banque à ce seul canal : aujourd'hui,
  les demandes Sécur'Pass (banques du groupe BPCE). **Des seules notifications de votre appli SMS**, elle lit aussi
  le dernier message, en mémoire, pour savoir si c'est un code de validation (« Votre code de validation est … ») ;
  le texte est oublié aussitôt, jamais gardé, jamais envoyé. Des notifications de toute autre appli, elle ne lit ni
  le titre ni le texte. L'écran d'activation ouvre pour lui la page des réglages du téléphone : vous seul pouvez
  l'accorder.

Pour n'afficher son aide sur les « paramètres restreints » d'Android que là où elle peut servir, l'appli lit aussi
comment elle a été installée sur ce téléphone : par une boutique, ou depuis un fichier. Elle n'en garde rien.

Le manifeste porte aussi une ligne ajoutée par une bibliothèque d'Android pour l'usage interne de l'appli
(`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) ; elle ne donne accès à rien.

## Ce que l'appli garde

Sur ce téléphone seulement, dans l'espace privé de l'appli :

- ses réglages ;
- la mémoire de son détecteur : pour le dernier appel seulement, quand il a commencé et fini, et s'il était
  entrant ou sortant — jamais le numéro ; et le dernier état d'appel signalé par le téléphone ;
- un journal des alertes affichées : quand, pourquoi, de quelle appli venait la demande ou le code (son nom de
  paquet, comme `com.credit_coop.android.mobilebanking`), et ce qui a été choisi — jamais le texte d'un message.
  Une demande ou un code hors appel, ou au-delà des minutes qui en suivent un, n'est pas gardé.

La sauvegarde Android et le transfert vers un autre téléphone sont **désactivés** pour cette appli : rien
de tout cela ne quitte le téléphone.

## Ce que l'appli envoie

**Rien.** Elle n'a ni permission Internet, ni compte, ni serveur, ni statistiques d'usage, ni services
Google Play.
