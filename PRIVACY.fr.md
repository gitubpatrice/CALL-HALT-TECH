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
- **Répondre aux appels** (`ANSWER_PHONE_CALLS`) : seulement pour mettre fin à l'appel quand vous appuyez, dans
  l'écran STOP, sur « Raccrocher », « Raccrocher et appeler ma banque » ou « Raccrocher et retirer l'accès ». Sans
  elle, aucun de ces boutons n'apparaît pendant un appel. L'appli ne décroche et ne passe jamais d'appel d'elle-même : « appeler
  ma banque » ouvre le clavier du téléphone, et c'est vous qui appelez.
- **Demander à fonctionner en arrière-plan sans restriction** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) : seulement
  pour afficher, pendant l'activation, la question du téléphone « Toujours autoriser en arrière-plan ? ». Une appli de
  protection, on ne l'ouvre presque jamais, et le téléphone endort les applis qu'on n'ouvre pas ; endormie, elle ne
  verrait plus rien. Cette permission ne lit rien ; c'est vous qui répondez, et vous pouvez revenir en arrière dans
  les réglages de batterie de l'appli.
- **Accès aux notifications** (accordé dans les réglages du téléphone ; ce n'est pas une permission) : voir
  l'appli de votre banque vous demander de valider une opération, et un code de validation arriver par SMS. De
  chaque notification du téléphone, l'appli lit l'appli qui l'a envoyée et son canal (l'identifiant que cette
  appli donne à un type de notification). Elle reconnaît une demande de la banque à ce seul canal : aujourd'hui,
  les demandes Sécur'Pass (banques du groupe BPCE). **Des seules notifications de votre appli SMS**, elle lit aussi
  le dernier message, en mémoire, pour savoir si c'est un code de validation (« Votre code de validation est … ») ;
  le texte est oublié aussitôt, jamais gardé, jamais envoyé. Des notifications des applis de prise en main de sa
  liste (AnyDesk, RustDesk…), elle note aussi si chacune est nouvelle, jamais ce qu'elle dit : pendant un appel, une
  telle appli en marche ouvre l'écran STOP. Des notifications de toute autre appli, elle ne lit ni
  le titre ni le texte. L'écran d'activation ouvre pour lui la page des réglages du téléphone : vous seul pouvez
  l'accorder.
- **Notifications** (`POST_NOTIFICATIONS`, et `USE_FULL_SCREEN_INTENT`) : si le téléphone empêche l'écran STOP de
  s'afficher, l'appli vous prévient par une notification qui l'ouvre ; téléphone verrouillé ou écran éteint, elle
  l'ouvre aussitôt, tant que le téléphone le lui permet (un réglage d'Android, « notifications plein écran », peut le
  retirer). Elle dit ce qui vient d'arriver (un code reçu, une demande de
  votre banque) — l'écran STOP le dit aussi, par-dessus l'écran verrouillé — mais ni le numéro qui appelle, ni le
  code, ni le nom de votre banque. À partir d'Android 13, le téléphone vous demande votre accord ; vous pouvez le
  retirer dans les réglages de notifications de l'appli. L'appli envoie une autre notification, hors appel : quand une
  appli qui permet à quelqu'un d'autre de contrôler le téléphone vient d'être installée (AnyDesk, TeamViewer
  QuickSupport, RustDesk…), elle la nomme et ouvre sa page dans les réglages du téléphone, où on la désinstalle.
- **Vibreur** (`VIBRATE`) : deux vibrations brèves quand l'écran STOP s'ouvre, pour le sentir le téléphone à l'oreille.
  Accordé à l'installation, sans question ; il ne lit rien.
- **Envoyer des SMS** (`SEND_SMS`) : seulement si vous choisissez un proche à prévenir, et demandé sur cet écran, pas
  par l'activation. L'appli s'en sert pour envoyer, jamais pour lire : la question d'Android dit « envoyer et afficher
  des SMS », le nom de tout le groupe, mais l'appli ne demande aucune permission de lire vos messages (ce qu'elle lit
  d'un code passe par l'accès aux notifications, plus haut). Elle n'écrit **qu'au numéro que vous avez choisi**, depuis votre numéro,
  comme un SMS ordinaire — Android range chacun dans votre appli SMS, dans votre conversation avec cette personne :
  - quand vous la choisissez, que ce téléphone la préviendra si l'écran STOP s'affiche pendant un appel ;
  - quand l'écran STOP s'affiche pendant un appel ou juste après, ou que « Continuer » est choisi malgré lui : ce qui
    est arrivé en quelques mots (un code reçu par SMS, une demande de la banque, une appli de prise en main…), et si
    l'appel était encore en cours — **jamais le code, ni aucun mot d'un message**, ni le numéro qui appelle. Un SMS
    par sorte d'écran STOP en un quart d'heure, un pour chaque « Continuer », et jamais plus de 20 par jour ;
  - quand vous choisissez quelqu'un d'autre, ou plus personne, que ce téléphone ne la préviendra plus.

  Sans cette permission, l'écran STOP fonctionne tout de même ; seuls les SMS ne partent pas.

Quand vous choisissez ce proche dans vos contacts, votre appli de contacts transmet à l'appli le nom et le numéro de ce
seul contact, et rien d'autre : l'appli n'a pas accès à vos contacts.

Pour voir ces installations, l'appli peut voir si une des applis de prise en main de sa liste (une cinquantaine,
nommées dans son code et son manifeste) est installée, et Android lui dit quand l'une d'elles l'est. Elle ne voit pas
les autres applis du téléphone, et ne demande pas à les voir.

Pour n'afficher son aide sur les « paramètres restreints » d'Android que là où elle peut servir, l'appli lit aussi
comment elle a été installée sur ce téléphone : par une boutique, ou depuis un fichier. Elle n'en garde rien. Sur un
téléphone Xiaomi, elle lit de même si le téléphone lui permet de « démarrer automatiquement », pour ne demander ce réglage
que s'il manque ; et elle sait si son propre accès aux notifications est en service, pour le dire sur l'accueil. Elle
lit aussi la liste des services d'accessibilité activés que tient Android, sans permission : pendant un appel, une appli
qui reçoit cet accès ouvre l'écran STOP ; elle n'en garde que le nom de paquet de l'appli, au journal. Quand le
téléphone s'éteint ou redémarre, Android le dit aux applis qui tournent : l'appli note alors la fin de l'appel en cours,
s'il y en a un, et rien d'autre de l'extinction.

Le manifeste porte aussi une ligne ajoutée par une bibliothèque d'Android pour l'usage interne de l'appli
(`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) ; elle ne donne accès à rien.

## Ce que l'appli garde

Sur ce téléphone seulement, dans l'espace privé de l'appli :

- ses réglages ;
- le numéro du proche à qui elle écrit, et son nom quand vous l'avez choisi dans vos contacts, jusqu'à ce que vous
  choisissiez quelqu'un d'autre ou plus personne ;
- la mémoire de son détecteur : pour le dernier appel seulement, quand il a commencé et fini, et s'il était
  entrant ou sortant — jamais le numéro ; et le dernier état d'appel signalé par le téléphone. Ses heures sont gardées
  deux fois : à l'horloge du téléphone, et en temps écoulé depuis son démarrage, avec le compte de ses démarrages que
  tient Android — une horloge reculée ou avancée n'y change ainsi rien ;
- un journal des alertes affichées : quand, pourquoi, de quelle appli venait la demande ou le code, ou quelle appli
  de prise en main a été installée (son nom de paquet, comme `com.credit_coop.android.mobilebanking`), et ce qui a été
  choisi — jamais le texte d'un message. Une demande ou un code hors appel, ou au-delà des minutes qui en suivent
  un, n'est pas gardé ; une appli de prise en main installée à ce moment-là l'est, avec son avertissement. Chaque ligne est
  gardée **14 mois**, puis effacée : les 13 mois que la loi vous laisse pour contester une opération auprès de votre
  banque, comptés depuis son débit, et un de plus pour une carte débitée en fin de mois — le journal montre qu'un
  appel était en cours, ou venait de finir, quand elle a été demandée. Jamais plus de 10 000 lignes : au-delà, les plus anciennes partent
  d'abord.

Désinstaller l'appli, ou effacer ses données dans les réglages du téléphone, efface tout d'un coup.

La sauvegarde Android et le transfert vers un autre téléphone sont **désactivés** pour cette appli : rien
de tout cela ne quitte le téléphone.

## Ce que l'appli envoie

**Rien par Internet.** Elle n'a ni permission Internet, ni compte, ni serveur, ni statistiques d'usage, ni services
Google Play.

Seuls quittent le téléphone les SMS au proche, si vous en avez choisi un — par SMS, depuis votre numéro, vers le sien
seulement, comme décrit plus haut.
