# Call Halt Tech — où on en est, et comment reprendre

> **À lire en premier** en reprenant le projet. Écrit le 2026-11-01, à la fin de la première journée.
> Tenu à jour à chaque étape importante : s'il contredit le code, c'est le code qui fait foi, et ce fichier
> est à corriger.

## En une phrase

Une appli Android contre les arnaques au téléphone (faux conseiller bancaire…) : quand une action
dangereuse arrive **pendant un appel ou juste après** (un code reçu par SMS, une validation dans l'appli
de la banque, une appli de prise en main à distance), elle affiche un écran **STOP** : *« Ni votre banque,
ni la police, ni les impôts, ni votre assurance ne vous appelleront jamais pour vous demander cela. Si on vous
dit d'ignorer cet écran, c'est une arnaque. Raccrochez, puis appelez vous-même un numéro que vous connaissez. »*

## Reprendre ici (2026-11-07)

**La fonction 7 de la fiche : le SMS au proche (D-034).** Depuis l'accueil, « Prévenir un proche » : un contact choisi
(sans permission de lire les contacts) ou un numéro tapé. L'appli demande alors l'autorisation d'envoyer des SMS (pas
avant : pas de proche, pas de SMS), puis un premier SMS dit au proche qu'il sera prévenu. Ensuite, un SMS part quand
l'écran STOP s'affiche pendant un appel ou juste après, et quand « Continuer » est choisi malgré lui : « Message
automatique de l'appli Call Halt Tech : pendant un appel, l'écran STOP vient de s'afficher sur ce téléphone (un code reçu
par SMS). C'est peut-être une arnaque. Appelez ce numéro dès que possible. » Jamais le code ni un mot d'un message.
Remplacé ou retiré, le proche reçoit un dernier SMS (contre « retirez le numéro de votre fille »). **Les choix de
Patrice** (huit, tous les recommandés) : les textes, le premier et le dernier SMS, un SMS par sorte d'écran STOP en un
quart d'heure mais **un pour chaque « Continuer »**, l'en-tête « Message automatique de l'appli… » et « Appelez ce
numéro » (« Call Halt Tech… Call » se lisait « appelez Halt Tech »), la phrase qui rassure sur la question du téléphone
(« envoyer et afficher des SMS »). Au plus 20 SMS par jour ; un numéro court surtaxé refusé.

**Mesuré** sur émulateurs Android 14, 15 et 16 (chaque SMS lu dans la boîte d'envoi : les quatre alertes, le limiteur,
l'autorisation retirée, le contact choisi, l'adieu, deux « Continuer ») et sur le **Galaxy S9** (sans SIM : « Le SMS …
n'est pas parti », « Renvoyer le SMS » ; le numéro d'essai retiré ensuite). **Trouvé en mesurant** : installée depuis un
fichier, **Android 15 et 16 refusent l'autorisation SMS** jusqu'à « Autoriser les paramètres restreints » (Android 14,
non) : l'aide de l'activation est reprise sur cet écran ; et le clavier, resté ouvert, cachait le résultat (corrigé).
**Pas mesuré** : la réception par un autre téléphone (le relais entre émulateurs ne passe pas) — à faire avec une vraie
SIM, à la décision de Patrice (le S24 est en observation).

**Quatre relectures** (trois passes Claude, GPT 0,022 $) : 13 corrections, dont un « Continuer » touché le lendemain qui
envoyait « juste après un appel », un SMS raté qui retenait le suivant, le « (0) » de « +49 (0)171… », un double toucher
qui envoyait deux SMS ; gardé exprès : le SMS part même si l'écran STOP n'a pas pu s'afficher (le proche est alors la
seule protection). **480 tests.**

**Puis, le même jour : « Retirer l'accès » sur le STOP d'accessibilité (D-033 ; Patrice : « fais ce qui te semble le
mieux », « l'appli doit être facile, intuitive, solide, fiable »).** Éteindre coupe la session de l'escroc, pas l'accès,
qui revient au démarrage — et le STOP, lui, ne revient pas. Ce STOP offre donc en premier « Retirer l'accès » (après
l'appel) ou « Raccrocher et retirer l'accès » (pendant un appel que l'appli peut couper ; l'appel est coupé d'abord) :
la page Accessibilité des réglages s'ouvre, celle du téléphone seulement (l'appli malveillante pourrait en imiter une),
et le conseil dit « touchez l'appli, puis désactivez-la. Éteignez ensuite votre téléphone… ». **Trouvé en mesurant, et
corrigé aussi pour « Appeler ma banque »** (défaut d'avant) : téléphone verrouillé par code, le composeur ou la page
s'ouvrait derrière le verrou, le STOP parti, sans un mot — le code est maintenant demandé par-dessus le STOP ; et la
page restait dans la tâche du STOP, d'où elle revenait après le « Continuer » d'un STOP suivant. **Mesuré** sur
Android 15 et 16 (l'appel coupé avant la demande du code ; RustDesk désactivé en deux touches et une confirmation).
**Trois relectures** (Claude justesse et sécurité, GPT 0,019 $) : aucune faille ; corrigés et mesurés, un second STOP
perdu s'il arrivait pendant la demande du code (le SMS de code de l'escroc à ce moment-là) et l'écran recréé pendant
cette demande (Android jette alors le rappel). **486 tests.**

**Puis, le même jour : le premier audit de publication, appli entière** (`relectures/2026-11-07-audit-publication-1.md`).
La grille du score (`docs/GRILLE-PUBLICATION.md`) est fixée et commitée **avant** le premier agent. Sécurité au palier
profond (5 chercheurs, 3 vérificateurs adverses par candidat), faux positifs, faux négatifs, motifs de défaut, trois
axes, cohérence, langues. **Un constat élevé, bloquant, mesuré (FN-1)** : après un redémarrage, un appel décroché avant
le premier déverrouillage n'est pas vu (aucun composant n'est `directBootAware`, et rien ne relit l'état réel de
l'appel, P-7) ; toute l'arnaque passe ensuite sans STOP, et l'escroc l'obtient d'une phrase (« éteignez et rallumez »).
Mesuré aussi : 55 SMS de numéros distincts saturent Google Messages (plafond de 50 notifications d'Android) et cachent
le code (N-3). Sécurité : 3 constats retenus sur 15 (le dernier écran perd « Retirer l'accès », N-1 ; une
superposition, C-2 ; le canal Sécur'Pass imité, S-2). **18 / 100 avant corrections** ; environ 74 après les corrections
techniques ; 95 avec les mesures sur de vrais téléphones et la relecture des natifs. Patrice a pris les quatre
recommandations (§ 6 : le STOP quitté revient et prévient le proche ; « Désinstaller » sur le STOP d'installation ; un STOP
pour une rafale de SMS ; un écran « Historique »), et les relectures externes peuvent aussi passer par Gemini ou l'API
Claude.

**FN-1 corrigé le même soir (D-035)** : l'état réel de l'appel est lu à chaque démarrage de l'appli et avant chaque action
sensible, réduit à ce qui ne peut pas contredire la diffusion (un appel WhatsApp seul n'est dit dans aucun sens).
**Mesuré** sur l'émulateur Android 16 : la demande Sécur'Pass pendant l'appel décroché avant le déverrouillage montre « Vous
êtes au téléphone » ; sans action pendant l'appel, un code reçu après montre « Votre appel vient de se terminer ». Trois
relectures (Claude justesse et robustesse, GPT Sol 0,437 $) : 6 corrections, 5 limites écrites. **501 tests** sur
ordinateur, et un test sur l'appareil de plus.

**Puis N-1 (D-017)** : un STOP qui tient pour plusieurs déclencheurs — un second STOP avant la réponse, ou une
notification de repli remplacée avant d'être touchée — parle du plus grave (l'accessibilité, puis une prise en main, puis
le reste ; le plus récent à égalité). Un SMS de code envoyé exprès par l'escroc n'ôte plus « Retirer l'accès ».
**Mesuré** sur l'émulateur Android 16, écran ouvert et écran bloqué (notification), dans les deux ordres. Relectures :
Claude (1 moyen, 3 faibles corrigés) et **Gemini, une première** (0,286 $ ; 2 réels corrigés, dont un défaut antérieur :
un « Raccrocher » échoué cachait les boutons du STOP suivant). **511 tests.**

**Puis « échouer du côté protecteur »** (audit C1, motifs 4 et 12, trois axes Q3) : une notification de SMS illisible
compte comme un message masqué, donc comme un code (elle laissait passer un code en silence) ; les réglages retombent sur
les valeurs protectrices pour toute erreur ; l'écran du proche dit tout échec d'écriture au lieu de rester occupé ; le
lanceur du STOP rattrape tout ce que `startActivity` lève, pour que la notification de repli prenne le relais. Relectures
Claude et GPT Luna (0,013 $). **518 tests.**

**Puis C2** : le lanceur du STOP isolé d'Android (`ScreenStarter`, `StopNotice`), son code inchangé, et ses règles
testées en temps virtuel (10 tests : l'écran attendu, la notification quand il est bloqué ou tout de suite sans
l'affichage par-dessus, l'écran seul quand aucune notification ne part, la course R-02, les déclencheurs reportés).
**527 tests.**

**Puis M1 à M3, la règle des SMS de code hors du français** (D-021) : les nombres qui ne sont jamais un code, les
autres sortes de code et les mises en garde sont lus en anglais, allemand, italien et espagnol ; « personne » ne
refait un code que dans une mise en garde. La relecture Claude a mesuré que la première version perdait 49 vrais
codes (« à aucune personne », le clavier du distributeur, « PIN No. 4821 »…) : rattrapés ; GPT Luna (0,024 $) a
ajouté la date espagnole et son « de ». Corpus de 184 SMS, vert sur le JVM et sur l'émulateur API 36. **585 tests.**

**Puis M4, la suite d'accessibilité de Google installée depuis Play** (D-033) : exemptée par son certificat,
plus seulement préinstallée. Google signe chaque suite de deux clés, celle de Play et celle des copies
préinstallées ; la première version ne portait que la seconde (la relecture Claude a demandé une source
indépendante, APKMirror l'a montré). GPT Luna (0,014 $) a trouvé Switch Access, paquet à part, qui faisait un STOP
même préinstallé. Mesuré sur les émulateurs Android 15 (sans Google) et 16. **592 tests.**

**Puis U1 à U4, l'écran STOP lisible à toute taille de police et senti à l'oreille** (D-036) : à 200 %, le
premier écran montre le message entier et les boutons sûrs (avant : coupé à « Si on vous dit d'ignorer ») ;
l'état des accès sous leur titre ; une bordure visible aux boutons secondaires ; deux vibrations brèves à
l'ouverture, avec la permission `VIBRATE` (le retour haptique sans permission se tait quand la vibration au toucher
est coupée, comme sur le S9 — mesuré). Relectures Claude et GPT Luna (0,017 $). **600 tests.**

**Mots tranchés par Patrice le 2026-11-07** (les quatre recommandés) : FN-4, le SMS « quitté sans réponse » ne
part que si le retour du STOP est ignoré lui aussi ; E-1, sur l'accueil « Un appel sur votre téléphone fixe
n'est pas vu. Si, au fixe, on vous demande un code reçu par SMS, c'est une arnaque : raccrochez. » ; le premier
SMS au proche perd « Il ne vous envoie rien d'autre. » ; FN-2, sur l'accueil « Votre appli SMS cache le texte
des messages dans ses notifications : un code reçu par SMS n'est pas vu. Dans ses réglages, affichez l'aperçu
des messages. »

**Puis E-1 et le premier SMS au proche** : la phrase du fixe sur l'accueil, avec la limite existante, dès que la
protection voit quelque chose (activée, ou affaiblie) — la limite n'était montrée qu'activée ; README et PROJET
le disent ; « Il ne vous envoie rien d'autre » retiré. Relectures Claude et GPT Luna (0,010 $).

**Puis FN-2, l'aperçu masqué de l'appli SMS** (D-037) : SMS Tech réglée pour masquer écrit « Nouveau message »
à la place de chaque message ; l'appli le reconnaît sur un message reçu, garde le nom de l'appli (jamais le
texte), et l'accueil le dit en gras tant qu'elle est l'appli SMS par défaut, avec ce qu'il faut faire. Mesuré
sur l'émulateur avec SMS Tech (masqué, rétabli, changement d'appli, écouteur reconnecté). Relectures Claude
(3 réels) et GPT Luna (0,013 $). **607 tests.**

**Puis FN-4, un STOP quitté revient une fois, puis le proche est prévenu** (D-038) — **sur la branche
`fix/stop-comes-back`, poussée, PAS encore fusionnée** (Patrice : « après les corrections on s'arrête »).
Fait : les règles (`StopReturns`), la table `stop_return` (migration 2 → 3), les réveils enfin exécutés
(`ProcessWakeUps`, réarmés au démarrage), la notification balayée (`StopNotificationSwiped`), le SMS « quitté
sans réponse » (5 langues) ; mesuré sur l'émulateur Android 16 (retour 15 s après, SMS au balayage ; raccroché
→ rien) ; relecture Claude (1 grave et 4 réels corrigés, `relectures/2026-11-07-claude-stop-revient.md`) ; gate
vert, **628 tests**. **À faire en reprenant** : (1) la relecture externe (GPT `gpt-5.6-sol`, code critique :
moteur et base) ; (2) mesurer que **toucher** la notification revenue n'envoie pas de SMS ; (3) les tests de
stockage sur l'émulateur (migration) après les corrections ; (4) README de relectures, PR, CI, fusion.
**Ensuite** : FN-3, N-3, l'écran Historique ; les mots d'E-2 (README) restent à proposer.

## Le 2026-11-05

**La fonction 4 de la fiche, première moitié : une appli de prise en main installée (D-031).** Pendant un appel ou
dans les 15 minutes qui suivent, l'installation d'AnyDesk, TeamViewer QuickSupport, RustDesk… ouvre l'écran STOP
(« Raccrochez et éteignez votre téléphone ») ; hors appel, une notification la nomme (Patrice : « notification avec
le nom ») et ouvre sa page dans les réglages, avec « Désinstaller ». **49 identifiants**, chacun vérifié sur sa page
Google Play (RustDesk, retiré de Google Play à cause des arnaques, dans son APK) ; l'appli ne voit que ces applis-là,
pas les autres (pas de permission de plus). **Mesuré** sur les émulateurs Android 16 et 11, avec le vrai RustDesk :
STOP pendant l'appel (0,5 s) et juste après, rien pour une mise à jour ni pour une autre appli, la notification hors
appel, notifications refusées sans rien casser. **Trois relectures Claude** : la liste juste ; deux renforcements
faits ; trois défauts de mots corrigés (Patrice, les trois recommandations) — « voir ou contrôler », la règle du STOP
« ne vous appelleront JAMAIS pour vous faire installer » (des banques allemandes font installer QuickSupport au client
qui les appelle), et l'avertissement « Si vous ne l'avez pas installée de vous-même, désinstallez-la ».

**La seconde moitié le soir : une telle appli en marche pendant un appel (D-032, Patrice : « fais tout ce qu'il
faut », « fais tous les tests nécessaires »).** Vue dans ses notifications, sans accès de plus : chaque nouvelle
notification d'une appli de la liste (son partage démarré, une demande de connexion, une session établie) ouvre le
STOP pendant un appel ou juste après — « … est en marche » (Patrice). **Mesuré avec une vraie session RustDesk**,
pilotée depuis un second émulateur comme le ferait l'escroc : STOP au partage démarré, de nouveau à la demande de
connexion, juste après l'appel ; rien hors appel ; l'écouteur relié en plein appel voit ce qui est venu pendant sa
coupure. **P-15 mesuré** : l'escroc voit l'écran STOP noir, et un service d'accessibilité qui n'est pas un outil
déclaré ne peut pas appuyer sur « Continuer » (contrôle positif fait). **Limite mesurée** : si la victime refuse les
notifications de RustDesk, son partage marche sans notification, et rien n'est vu — d'où la suite décidée par
Patrice : **un service d'accessibilité activé pendant un appel → STOP** (fiche, § 9.2, idée 1).

**Puis D-033, fait le même soir : un service d'accessibilité activé pendant un appel, ou juste après → STOP** (« Une
appli vient de recevoir l'accès « accessibilité » : elle peut voir votre écran et agir à votre place », Patrice). Lu
dans le réglage d'Android qui liste ces services, sans permission. **Trouvé en mesurant : Android 16 bloque lui-même
l'activation pour une appli téléchargée pendant un appel** (« Action impossible pendant un appel ») ; juste après, il
la laisse faire, et l'écran STOP s'ouvre (« Votre appel vient de se terminer ») ; sur **Android 15**, rien ne bloque, et
l'écran STOP s'ouvre pendant l'appel (mesuré, par les Réglages, comme une victime). **Ensuite** : le SMS au proche
(fonction 7), puis les audits.

**Le S24 FE, deux jours après** : redémarré le matin du 2026-11-05, l'appli relancée dans la même seconde, en vie
9 h 53 plus tard, écouteur relié, toujours exemptée de la veille. Débranché ensuite (absent d'`adb`).

## Le 2026-11-04

**Le soir (Patrice : « tu peux reprendre, par contre je n'ai pas de téléphone avec Android Go »)** : aucun téléphone
branché, tout sur émulateurs.
- **Android Go** : plus de « vrai téléphone Go » à attendre ; tout est mesuré sur émulateurs en mémoire réduite, et
  c'est écrit comme une limite (P-21). **Android 12 Go mesuré** (le seul de 11 à 13 qui manquait) : comme 11 et 13.
- **P-3, Android 15 mesuré** : installée depuis un fichier, Android y bloque l'affichage par-dessus et l'accès aux
  notifications, comme sous 16 ; l'aide de l'appli mène au déblocage, puis tout s'accorde, et un code pendant un appel
  ouvre le STOP. **Une impasse trouvée et corrigée** : à une appli installée depuis un fichier, Android 14 et 15 ne
  donnent pas d'office l'autorisation plein écran (Android 16, si) ; après « Autoriser » à l'étape 5, le bouton
  redemandait une permission déjà accordée, et rien ne se passait. L'étape relit maintenant ses permissions, et
  propose « Ouvrir les réglages », qui ouvre la page « plein écran » de l'appli : mesuré sous 14 et 15 jusqu'à
  « Protection activée ». Une installation par F-Droid n'est pas mesurée.
- **Samsung Messages mesuré sur le S24 FE** (avec Patrice) : réactivé et mis par défaut le temps du test, puis tout
  remis comme avant. Un code reçu pendant un appel → l'écran STOP s'ouvre 60 ms après la notification. Conversation
  ouverte, Samsung Messages n'affiche aucune notification : rien à lire (limite de toute lecture par notification ;
  dans l'arnaque, la conversation est fermée). Pour SMS Tech, appris au passage : installée depuis un fichier, elle
  ne peut reprendre le rôle d'appli SMS par défaut qu'avec ses paramètres restreints autorisés.
- **P-3, Android 11 mesuré** : parcours complet, « Protection activée ». **Le compteur d'étapes corrigé** : il
  finissait sur « Étape 4 sur 5 » sur tout téléphone avant Android 13 (le S9 ; « 5 sur 6 » sur un Xiaomi comme le
  Redmi) ; il compte désormais les étapes de l'activation elle-même (« 1 sur 4 » à « 4 sur 4 », mesuré).

**Le S24 FE, un jour après** : l'appli est restée éveillée (redémarré dans la nuit, processus vivant, écouteur relié,
toujours exemptée de la veille) ; l'écouteur de mesure, resté autorisé, a été retiré. L'observation continue.

**P-13 fait (D-026) : deux horloges dans chaque signal.** L'heure du téléphone se règle (par l'utilisateur, par le
réseau) : reculée pendant un appel, un code qui suivait paraissait le précéder, et le STOP était manqué ; d'autres
réglages faisaient perdre un appel, ou laissaient un appel fantôme après un redémarrage. Chaque signal porte désormais
aussi le temps écoulé depuis le démarrage du téléphone, que personne ne règle, avec le numéro du démarrage : le moteur
décide sur lui. Quand l'heure est juste, rien ne change pour l'utilisateur. La base passe en version 2 (une migration
qui n'ajoute que des colonnes, testée). **Mesuré sur l'émulateur Android 16** : heure reculée d'une heure en plein
appel, un code → STOP ; un redémarrage d'Android seul (sans le téléphone) — laissé « à vérifier » par D-020 — fait
avancer le compteur de démarrages et laisse le temps écoulé continuer, ce que le moteur prend en compte ; un vrai
redémarrage en plein appel ferme l'appel à l'allumage du téléphone. Relecture (`relectures/2026-11-04-claude-horloge.md`) :
aucun constat de sécurité ; deux défauts réels corrigés, dont un faux STOP que ce changement introduisait. **Trouvé en
mesurant : P-22** (une écriture récente peut être perdue à une coupure de courant), fait le même jour (ci-dessous).

**P-22 fait (D-027) : chaque enregistrement est sur le disque avant que l'appli passe à la suite.** La base laissait
Android décider quand écrire : une coupure nette (batterie qui lâche, redémarrage forcé) effaçait l'appel commencé
quelques secondes avant, et avec lui le STOP « l'appel vient de se terminer » après le redémarrage. L'appli impose
désormais son mode d'écriture. Il fallait l'imposer : sur le Galaxy S9, Samsung change les valeurs d'Android pour
d'autres, moins sûres (mesuré par le nouveau test). **Mesuré sur l'émulateur Android 16** : appel validé puis coupure
nette 10 s après, l'appel perdu avant, gardé après ; gardé aussi avec une coupure 1 s après ; au démarrage, un code
reçu 36 s plus tard ouvre le STOP ; la mise à jour depuis l'ancienne version garde ce qu'elle avait écrit. Relecture
(`relectures/2026-11-04-claude-ecriture.md`) : aucun constat de sécurité ; le constat principal, réfuté par la source
de SQLite et par la mesure à 1 s. Ensuite, l'affinage de D-020, fait le même jour (ci-dessous).

**L'affinage de D-020 fait (D-028) : une extinction propre se dit.** Un téléphone éteint en plein appel puis rallumé
des heures après arrêtait la demande bancaire de l'utilisateur lui-même juste après l'allumage : l'appel coupé était
fermé à l'allumage, et la fenêtre de 15 min courait de là. Android annonce désormais l'arrêt à l'appli (le menu
d'arrêt, la batterie au niveau critique, une mise à jour) : l'appel en cours finit à cet instant, et la fenêtre court
de là. « Éteignez et rallumez votre téléphone » garde ce qui restait de la fenêtre. **Le compromis**, écrit dans
D-028 : éteint plus de 15 min, la fenêtre est passée au rallumage, comme après un raccrochage suivi d'une attente aussi
longue ; l'escroc doit rappeler, et cet appel est protégé. **Mesuré** sur l'émulateur Android 16 (redémarrage et arrêt
propres) et sur le **Galaxy S9** (Android 10, par le menu d'arrêt : Samsung refuse cette commande à `adb`). La piste
notée dans D-020 (l'heure de mort du processus) ne marche pas : mesuré, Android n'en garde rien. Relecture
(`relectures/2026-11-04-claude-extinction.md`) : aucun constat de sécurité ; le compromis, écrit, et trois tests
ajoutés.

**Android Go (P-21) : la règle était fausse à partir d'Android 14.** L'appli disait « Ce téléphone ne permet pas la
protection » à tout téléphone Go (peu de mémoire), d'après la documentation d'Android. Lu dans les sources d'Android,
puis mesuré sur l'émulateur Android 16 passé en mémoire réduite : à partir d'Android 14, les réglages laissent
accorder l'affichage par-dessus, et l'écran STOP s'ouvre pendant un appel. L'appli refuse désormais la protection à un
téléphone Go avant Android 14 seulement ; après, elle propose l'activation. Relecture
(`relectures/2026-11-04-claude-android-go.md`) : la frontière vérifiée, aucun autre blocage trouvé. Aucun vrai
téléphone Go (Patrice n'en a pas) : limite déclarée, P-21.

**P-11 fait (D-029) : le journal garde chaque ligne 14 mois, et 10 000 lignes au plus** (Patrice : « fais ce qu'il y
a de mieux »). Le journal montre qu'un appel était en cours, ou venait de finir, quand une opération a été demandée :
la preuve qu'une banque demande à qui la conteste. La loi laisse 13 mois à compter du débit pour contester (Code
monétaire et financier, art. L133-24), et une carte à débit différé est débitée jusqu'à un mois après : 14 mois. Le
plafond arrête une appli qui remplirait le journal. Élagué dans la transaction de chaque signal et au contrôle de
l'accueil ; la politique de confidentialité le dit, avec l'effacement par la désinstallation. Tests sur téléphone avec
contrôle négatif. Relecture : aucun constat élevé ni moyen. **Reste de P-11** : dédoublonner l'avertissement « appli
de prise en main installée », à faire avec son capteur, qui n'existe pas encore (tranché le 2026-11-05 avec lui :
pas de dédoublonnage, une diffusion par installation, D-031).

**Android Go de 11 à 13 fait (D-030) : protégé par la notification du STOP seule** (Patrice : « ok fais le »). Avant,
ces téléphones à petit prix, souvent ceux des personnes âgées, n'avaient aucune protection. **Mesuré sur deux émulateurs
passés en mémoire réduite, Android 11 et Android 13** : l'accès aux notifications s'accorde, l'affichage par-dessus est
refusé par Android ; pendant un appel, un code fait paraître l'alerte en haut de l'écran, et un toucher ouvre le STOP ;
écran éteint, le STOP est là dès qu'on le rallume. L'activation ne demande plus l'affichage par-dessus sur ces
téléphones, et l'accueil dit « Protection affaiblie » — « Sur ce téléphone, l'écran STOP ne peut pas s'afficher de
lui-même : l'appli vous préviendra par une alerte. » **Au passage, sur tout téléphone** : sans l'affichage par-dessus,
l'alerte part tout de suite au lieu d'attendre 5 s (mesuré : 0,2 s). Android 12 mesuré le soir
(ci-dessus) ; aucun vrai téléphone Go : limite déclarée.

**Prochaine étape** : la suite de la liste « avant toute publication » ci-dessous.

## Le 2026-11-03, après-midi

**Après la pause : P-10 fait** (la panne du stockage sur l'accueil). Tant que l'appli n'arrive plus à enregistrer,
l'accueil dit « **Protection affaiblie** » (Patrice : ces deux mots, sans verbe ; « le reste ça va »), la cause
probable (le téléphone plein) et « Ouvrir le stockage du téléphone » (sur le S24 FE : la « Gestion du stockage » de
Samsung, mesuré). À chaque retour sur l'accueil, l'appli réessaie d'écrire : le message disparaît dès que ça repasse.
Mesuré sur le S24 FE (base remplacée par un dossier, puis rendue). Relecture (`relectures/2026-11-03-claude-stockage.md`) :
aucun constat de sécurité ; l'essai ne faisait que lire, un stockage passé en lecture seule passait pour sain →
corrigé, avec test et contrôle négatif. Au passage, un test sur téléphone qui dépendait de l'état laissé par une mesure
(l'accès aux notifications déjà accordé) part maintenant d'un état propre.

**Les deux autres états en forme courte aussi** (Patrice : « oui ») : « **Protection activée** », « **Protection
désactivée** », les 5 langues en miroir.

**P-19 construit (D-024, Patrice : « ok »)** — le repli quand l'écran STOP ne passe pas : une notification qui ouvre
l'écran STOP, et une **5ᵉ étape d'activation**, « **Alerte si l'écran STOP est bloqué** » (depuis Android 13, le
téléphone demande « Autoriser Call Halt Tech à vous envoyer des notifications ? »). Raison : le STOP bloqué était
invisible. **Mesuré sur l'émulateur Android 16**, l'affichage par-dessus retiré pendant un appel : notification 5 s
après, un toucher ouvre le STOP ; écran éteint, la notification plein écran rallume l'écran et ouvre le STOP seule.
Deux permissions de plus (`POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`), dans la garde CI et la politique de
confidentialité. **Mesuré sur le S24 FE avec Patrice** (vrai appel, le 123, l'écran STOP bloqué exprès) : écran
allumé, la notification par-dessus l'écran d'appel, un toucher ouvre le STOP ; **à l'oreille, un trou trouvé** (écran
éteint par le capteur, mais téléphone ni endormi ni verrouillé : Android n'ouvre rien et n'affiche pas la bannière)
**et fermé** : l'appli repose la notification quand l'écran se rallume, la bannière paraît quand on éloigne le
téléphone, un toucher ouvre le STOP (Patrice : « ça a marché avec l'écran STOP »). **Accueil quand seule cette étape
manque** (Patrice : « ok pour B ») : « Protection affaiblie », pas « désactivée ». Relecture :
`relectures/2026-11-03-claude-repli-stop.md`. **Mesuré aussi sur un Xiaomi**, le Redmi 9C de Patrice (MIUI 12,
Android 10, appli installée depuis un fichier, sans SIM : appel simulé par la version de test) : le réglage de MIUI
« Afficher les fenêtres pop-up pendant que vous parcourez en arrière-plan » est **autorisé d'office** — l'écran STOP
passe ; **refusé par Patrice**, MIUI bloque l'écran STOP, la notification s'affiche en haut de l'écran, un toucher
ouvre le STOP ; écran éteint, la notification plein écran crée l'écran STOP derrière l'écran éteint (MIUI ne le
rallume pas), il est là dès qu'on rallume.

**🔴 Puis, sur ce Redmi, un trou de la protection trouvé et fermé (D-025, Patrice : « fais tout maintenant »)** : le
« Démarrage automatique » de MIUI, refusé d'office ; une fois l'appli arrêtée, MIUI refuse de relier l'écouteur, même
l'appli rouverte, et l'accueil disait « Protection activée » — sourde en fait. **Construit** : l'appli sait si son
écouteur est vraiment relié, **se répare elle-même** à l'ouverture (mesuré sur le Redmi : reliée 1,3 s après, avant que
Patrice touche au réglage), et l'accueil dit « Protection désactivée » si la réparation échoue ; sur les Xiaomi, une
**étape « Démarrage automatique »**, qui ouvre la page de MIUI (mesuré : refusé → l'étape, autorisé par Patrice →
« Autorisé », 6 accès). Ailleurs, l'étape n'existe pas (S24 : 5 accès, mesuré). Relecture :
`relectures/2026-11-03-claude-ecouteur.md`.

**Le 2026-11-03 au matin, sur le S24 FE avec Patrice** (et le S9), deux PR fusionnées (#22, #23 ; #21 fermée, remplacée par
#22) :

- **L'activation comme un utilisateur** (P-3) : ancienne version désinstallée, nouvelle installée depuis un fichier
  (Read Files Tech, source `LOCAL_FILE`). Play Protect demande une analyse, puis « Cette appli semble sûre ».
  **Samsung ne bloque rien** (ni « Vue en premier plan », ni l'accès aux notifications), contrairement à l'émulateur
  Android 16 ; moins d'une minute jusqu'à « en marche ». Le S9 (Android 10) : parcours complet aussi.
- **Vrai appel (messagerie 123) et code envoyé à soi-même** : STOP 27 ms après la notification de SMS Tech,
  « Raccrocher » coupe l'appel. **Téléphone à l'oreille** (écran éteint par le capteur de proximité) : une demande
  Sécur'Pass factice → STOP 35 ms après, devant l'écran d'appel ; le téléphone éloigné, il est là ; « Raccrocher » OK.
- **Décidé (Patrice, « ok pour tout »)** : « Raccrocher et appeler ma banque » reste proposé pour tous les déclencheurs.
- **Un vrai défaut trouvé dans le journal, corrigé** : l'appli ne voyait pas l'appli SMS par défaut (« aucune » pendant
  5 min), faute de `<queries>` ; le code n'était lu que parce qu'Android montre à l'écouteur l'appli de chaque
  notification. La requête que nomme la documentation d'Android (`SMS_DELIVER`) ne marche pas (diffusion protégée,
  vérifié dans AOSP) ; `SENDTO smsto` la trouve du premier coup (émulateur 16, S24). Test sur téléphone avec contrôle
  négatif ; relecture de sécurité : aucun constat (`relectures/2026-11-03-claude-visibilite-sms.md`).
- **P-21 mesuré** : arrêt forcé → écouteur relié de nouveau en moins d'une seconde (Android 16 et Samsung) ; veille
  ordinaire, écran éteint 8 min → reçu en 1 s. **🔴 Veille profonde de Samsung : la protection est sourde** (appli tuée,
  écouteur délié en moins de 2 min). L'accueil le dit (accès lu refusé), mais rallumer l'interrupteur ne répare pas :
  Samsung le remet à « éteint ». Seule la sortie de la veille profonde répare (« Applis jamais mises en veille » : reçu
  en 1 s, écran éteint).
- **✅ Construit (D-023, Patrice : « ok pour tous bien sûr »)** : une 4ᵉ étape, « **Active même quand vous ne l'ouvrez
  pas** », pour tous les téléphones, avant l'accès aux notifications : le dialogue du téléphone met l'appli en « Non
  restreinte », ce qui la sort de la veille profonde et l'écarte des listes de mise en veille de Samsung (contrôle
  négatif : en « Optimisée », elle y réapparaît). Mesuré sur le S24 : Samsung demande « Arrêter optimisation utilisation
  batterie ? » (« OK »), pas les mots d'Android d'origine — le texte ne les cite donc plus ; l'exemption survit à une
  mise à jour. « Passer cette étape » après un refus (relecture : sinon l'accès aux notifications devenait inaccessible).
  Nouvelle permission `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (ne lit rien), dans la garde CI et la politique de
  confidentialité.
- **Les mots de l'accueil** (Patrice) : « La protection est **activée** » (« en marche, ça ne se dit pas ») et « La
  protection est **désactivée** » (au lieu de « ne fonctionne pas ») — devenus l'après-midi « Protection activée » et
  « Protection désactivée » ; les titres des accès sans verbe ni article
  (« Quand vous êtes au téléphone », « L'écran STOP devant les autres applis », « Active même quand vous ne l'ouvrez
  pas », « Demandes de votre banque et codes reçus ») ; les 5 langues en miroir.
- **Question de Patrice** : « Raccrocher et appeler ma banque » s'affiche même quand on n'est pas au téléphone avec sa
  banque. Réponse : voulu, l'opération visée est bancaire quel que soit le rôle joué ; un code SMS non bancaire
  (WhatsApp…) rend le bouton inutile mais pas nuisible. Gardé, décision ci-dessus.

**Prochaine étape** (P-10, P-19 et D-025 faits et mesurés l'après-midi, ci-dessus) : la liste « avant toute
publication » ci-dessous (P-11, P-13, Android 15, HyperOS…). À observer sur plusieurs jours sur le S24 : l'appli en
« Non restreinte » n'est jamais mise en veille.

**État des téléphones à la pause** : S24 FE = version de test de `4dc03b3` (installée par adb), les 4 accès accordés,
« Non restreinte », seul le vrai écouteur actif (celui de mesure coupé), `POST_NOTIFICATIONS` accordée pour la demande
factice ; laissée active pour l'observation. S9 = version de test du matin, accès aux notifications retiré.

## Le 2026-11-02

**Les deux mesures qui décidaient sont passées : le projet continue** (décision 11). Fait le 2026-11-02, onze
PR, chacune relue par un ou deux agents Claude (`relectures/`) :

1. Les textes de l'écran STOP (PR #7).
2. Le capteur de l'état de l'appel (PR #8) — vrais appels sur le S24 FE, émulateurs Android 11, 16, 17.
3. L'écran STOP réel (PR #9) — **P-1** : pendant un vrai appel, il passe devant l'appli Crédit Coopératif,
   « Raccrocher » coupe l'appel ; écran verrouillé compris (émulateur Android 11).
4. La mesure **P-2** (PR #10) — la demande Sécur'Pass est lisible : canal `securpass_id`, titre qui nomme
   l'opération, `REASON_CLICK` quand on appuie dessus.
5. **Ce que font déjà les banques** (PR #12, `PROJET.md` § 9.6) — certaines applis bancaires détectent l'appel,
   chacune pour elle ; rien de transversal en France ; la méthode ne change pas.
6. **Le capteur des notifications** (PR #13) — une demande de validation bancaire, reconnue à son canal
   (`securpass_id`, D-019), affiche le STOP pendant l'appel ou juste après. Mesuré de bout en bout sur émulateur
   (Android 16 et 17) avec une demande factice, et sur le S9 (Android 10, sans appel). L'écouteur ne lit que
   l'appli et le canal de chaque notification.
7. **Un appel ne survit pas à un redémarrage** (PR #14, D-020) — un téléphone éteint en plein appel gardait
   l'appel « en cours » jusqu'à 8 h, et la demande bancaire de l'utilisateur lui-même montrait un faux STOP.
   Mesuré sur émulateur (Android 16), appel coupé net puis redémarrage à froid.
8. **La mesure des SMS de code** (PR #15, P-20) — sur Android 16 et 17 (détecteur de Google), un code **en
   anglais** est masqué dans la notification de l'appli SMS, **un code en français ne l'est pas**, même téléphone
   réglé en français. Le masquage ne suffit donc pas en France : reconnaître un code demande de lire le texte.
9. **Les mesures sur le S24 FE, avec Patrice** (PR #17) — **de bout en bout, vrai appel, vraie demande
   Sécur'Pass** : STOP 0,12 s après la notification, devant tout ; « Raccrocher » ; journal juste ; ni republication
   ni confirmation sur le canal. **Samsung ne masque aucun code SMS**, ni français ni anglais. La banque de Patrice
   confirme l'opération par SMS, 20 s après l'appel.
10. **Le capteur des SMS de code** (PR #18, D-021 — Patrice : « ok a ») — le dernier message de la notification de
    l'appli SMS par défaut, lu en mémoire, sans permission de plus. La règle des codes a été jugée « pas prête » par
    l'agent des faux positifs (72 erreurs sur 126 SMS construits : les pubs « STOP au 36111 », les codes de
    livraison pendant l'appel du livreur…) et réécrite en deux niveaux ; ces 126 SMS sont son test, sur
    l'ordinateur et sur téléphone. Mesuré sur émulateur : pendant un appel, un code → STOP, une pub ou un code de
    livraison → rien, un code anglais masqué par Android → STOP ; sans appel → rien.
11. **L'écran d'activation et l'état de la protection** (D-022, P-3, P-10) — l'appli demande elle-même ses trois accès,
    un par écran (téléphone, affichage par-dessus, accès aux notifications) ; l'accueil dit « en marche » seulement
    quand tout est accordé, et ce qu'elle surveille, rien de plus. **Mesuré (P-3)**, appli installée depuis un
    fichier : Android 14 bloque l'accès aux notifications, **Android 16 aussi l'affichage par-dessus** ; le menu
    « Autoriser les paramètres restreints » n'apparaît qu'après un premier refus ; un déblocage ouvre les deux. Parcours
    complet fait sur l'émulateur Android 16, puis un code SMS pendant un appel → STOP. Trois relectures (deux Claude,
    GPT) : 12 défauts corrigés, dont l'accueil qui promettait toutes les banques, l'aide qui pouvait servir aux escrocs,
    des pages de réglages qu'une autre appli pouvait imiter.

L'étape suivante prévue ce soir-là (l'activation sur le S24 FE, puis un vrai appel et un code) est **faite le
2026-11-03** (en tête de ce fichier). Le SMS de confirmation de la banque juste après un appel reste une piste (P-20).

**Avant toute publication** : HyperOS (D-025 mesuré sur MIUI 12 seulement),
P-21 (la mise en veille de Samsung sur plusieurs jours : en observation sur le S24), P-3 (la source
d'une installation par F-Droid, lue dans son code : « boutique » dans le cas courant, à mesurer une fois publiée), l'écran d'appel qui repasse devant le STOP après un redémarrage d'Android seul (D-026,
observé), les deux SIM (Patrice : « plus tard ce sera possible »), la relecture des traductions par des natifs, les audits
(95/100, D-016 ; la grille, fixée le 2026-11-07 avant le premier audit : `docs/GRILLE-PUBLICATION.md`).

Trust Code Tech : **pas arrêtée**, Patrice veut en reparler (`J:\applications\trust_code_tech\PROJET.md`).

**Mesurer sur un téléphone** (version de test seulement) : appli `com.filestech.callhalt.debug`, accès par l'écran
d'activation, ou par adb (`pm grant … READ_PHONE_STATE`, `… ANSWER_PHONE_CALLS`, `appops set … SYSTEM_ALERT_WINDOW
allow`). **Installée par adb, elle échappe aux paramètres restreints** (source `OTHER`) : pour les mesurer, la pousser
dans Téléchargements (`adb push … /sdcard/Download/`) et l'ouvrir depuis l'appli Fichiers (source `LOCAL_FILE`) ;
une action sensible injectée : `adb shell am broadcast -a com.filestech.callhalt.debug.SIGNAL --es kind
CODE_MESSAGE -n com.filestech.callhalt.debug/com.filestech.callhalt.platform.debug.DebugSignalReceiver` ;
l'écouteur de mesure : `adb shell cmd notification allow_listener com.filestech.callhalt.debug/com.filestech.callhalt.platform.debug.MeasureNotificationListener`
(et `disallow_listener` après). Le vrai capteur : même commande avec
`com.filestech.callhalt.debug/com.filestech.callhalt.platform.notification.NotificationListener` ; une demande
bancaire factice (après `pm grant … POST_NOTIFICATIONS`) : `adb shell am broadcast -a
com.filestech.callhalt.debug.BANK_REQUEST --ei id 1 -n com.filestech.callhalt.debug/com.filestech.callhalt.platform.debug.StandInBankRequestReceiver`
(le même `id` met la notification à jour). Sur émulateur : `adb emu gsm call|accept|cancel <numéro>`, une fois la SIM
« LOADED ». Le S9 n'a pas de SIM : vrais appels et SMS sur le S24 FE.

## Où en est le projet (2026-11-01, complété le 2026-11-02)

- **Le squelette est posé et vérifié.** Depuis le 2026-11-02 au soir, l'appli demande ses accès elle-même et, une fois
  activée, protège pendant un appel contre un code reçu par SMS et une demande Sécur'Pass. Elle n'est pas publiée.
- Ce qui existe : le **moteur de détection** (la méthode, testée règle par règle), sa **mémoire** (qui
  survit quand Android arrête l'appli), les **réglages**, l'**écran STOP en démonstration**, le thème du
  portefeuille (damier bleu et rouge), **5 langues**.
- **Premier capteur branché (2026-11-02)** : l'appli sait quand un appel commence et se termine, même
  tuée entre deux appels, par une file unique où passeront tous les capteurs.
- **L'écran STOP réel (2026-11-02)** : lancé depuis l'arrière-plan, devant l'appli bancaire, verrou compris ;
  « Raccrocher » coupe l'appel.
- **Premier capteur d'action sensible (2026-11-02)** : la demande de validation de la banque (Sécur'Pass),
  vue dans sa notification.
- **Le capteur des SMS de code (2026-11-02)** et **l'écran d'activation** (le même soir, D-022).
- Ce qui manque encore : la liste « avant toute publication » ci-dessus (les applis de prise en main, installées ou en
  marche, et un service d'accessibilité activé sont vus depuis le 2026-11-05, D-031 à D-033 ; le **SMS au proche** est
  fait le 2026-11-07, D-034 ; le **repli par notification**, P-19, le 2026-11-03 : D-024).
- Vérifications : 607 tests sur ordinateur (628 sur la branche FN-4), 31 tests sur téléphone (Galaxy S9 sous Android 10, Galaxy S24 FE
  sous Android 16, émulateurs), lint et detekt à zéro, CI verte, CodeQL sans alerte.
- Dépôt **public** : https://github.com/gitubpatrice/CALL-HALT-TECH — branche `main` protégée (la CI doit
  être verte avant toute fusion), analyse des secrets et signalement privé de failles activés.

## Où trouver quoi

| Fichier | Ce qu'il contient |
|---|---|
| `PROJET.md` | le **pourquoi** et les **fonctions** ; § 9 : la revue du 2026-11-01 (corrections, idées retenues et écartées, le nom) |
| `docs/ARCHITECTURE.md` | **comment** c'est construit : modules, décisions D-001 à D-034, ce qu'Android permet (vérifié sur sources officielles et mesuré, § 6), points ouverts P-1 à P-22 (§ 7), comment travailler (§ 8) |
| `relectures/` | chaque relecture (agents Claude, GPT) et ce qui a été fait de chaque constat ; `README.md` = l'index et les coûts |
| `SECURITY.md`, `PRIVACY.md` | menace, signalement d'une faille ; ce que l'appli demande et garde (en anglais et en français) |

## Les décisions qui comptent (détail et raisons dans `docs/ARCHITECTURE.md`)

1. **Le nom : Call Halt Tech** (« Halt ! » = « Stop ! » dans les 5 langues). INPI : rien ; domaine libre.
   Le projet s'appelait d'abord « Call Guard Tech », un nom pris plusieurs fois.
2. **La logique de détection vit dans un module qui ne peut pas toucher à Android** : chaque règle est un
   test de quelques millisecondes.
3. **L'écran STOP sera un vrai écran (Activity), jamais une fenêtre par-dessus** : les applis bancaires
   masquent les fenêtres superposées.
4. **targetSdk 36, pas 37** : Android 17 retarde de 3 heures les SMS de code pour les applis qui le ciblent.
5. **Aucune permission sans le composant qui s'en sert**, ajoutée dans la même modification que la
   vérification CI et la politique de confidentialité. Jamais de permission Internet.
6. **Pas de service d'accessibilité** : les banques les détectent et refusent de fonctionner.
7. **Cohabite avec Saracroche** : l'appli ne prend aucun rôle exclusif (filtrage d'appels, téléphone, SMS).
8. **Le proche est prévenu par SMS** (Patrice, 2026-11-01) : quand un STOP s'affiche, quand « Continuer » est
   choisi malgré l'alerte, quand la protection est coupée.
9. **Publication sur F-Droid** (Patrice, 2026-11-01), comme Agenda Tech et SMS Tech : build reproductible,
   fiche fastlane en 5 langues, l'APK servi est le nôtre. La chaîne de build (AGP, Kotlin, Gradle) monte
   avec tout le portefeuille, jamais seule.
   ⛔ **Mais rien n'est publié tant que l'appli n'est pas fiable, solide et sécurisée au mieux, avec un score
   d'au moins 95 sur 100** (Patrice). La grille du score se fixe avant le premier audit de publication :
   aucun constat critique ou élevé ouvert, mesures faites sur de vrais téléphones, relecture externe du
   code qui décide (`docs/ARCHITECTURE.md`, D-016).
10. **Les mots de l'écran STOP** (Patrice, 2026-11-01) : ils disent ce qui vient d'arriver, puis « si on
    vous dit d'ignorer cet écran, c'est une arnaque », nomment qui ne vous appellera jamais pour cela
    (banque, police, impôts, assurance), et préviennent que le numéro ou le nom affiché ne prouve rien.
    Après l'appel, l'écran ne dit plus « vous êtes au téléphone » et ne propose plus de raccrocher ; pour
    une prise en main à distance, il dit d'éteindre le téléphone (D-017).
11. **La mesure décide de la suite** (Patrice, 2026-11-01) : si l'écran STOP ne passe pas par-dessus l'appli
    bancaire (P-1), ou si ses demandes de validation sont illisibles (P-2), **le projet s'arrête** (D-018).

## La prochaine étape : le prototype de mesure

⛔ **Elle décide si le projet continue** (décision 11 ci-dessus) : mesurer P-1 et P-2 avant tout le reste.

Brancher les capteurs un par un, chacun avec sa permission, sa vérification et son test, dans cet ordre :

1. ✅ **L'état de l'appel** (début, fin) — récepteur `PHONE_STATE`, permission « téléphone » ; **fait le
   2026-11-02**, mesuré sur émulateurs (Android 11, 16, 17) et **sur le S24 FE avec de vrais appels** (sortant,
   reçu décroché, reçu refusé). Le numéro (`READ_CALL_LOG`) viendra avec le rappel d'appel long.
2. ✅ **L'écran STOP réel** et l'exécution des décisions — la mesure **P-1** : il s'affiche par-dessus l'appli
   bancaire, **passée le 2026-11-02 sur le S24 FE** (Android 16) et sur l'émulateur Android 11, écran
   verrouillé compris. Reste Android 10 lui-même, sur un téléphone avec SIM.
3. ✅ **Les SMS de code** — lus dans la notification de l'appli SMS par défaut (D-021, PR #18), la règle relue par
   l'agent des faux positifs.
4. **Les notifications de la banque** — mesure **P-2** : ✅ **lisibles, mesuré le 2026-11-02 sur le S24 FE**
   (Sécur'Pass, canal `securpass_id`). ✅ **Capteur branché le 2026-11-02** (PR #13), ✅ mesuré pendant un vrai appel
   sur le S24 FE (PR #17). Restent d'autres banques.
5. ✅ **Le parcours d'activation** (« paramètres restreints » d'Android 13+) — mesure **P-3** : construit et mesuré
   sur émulateurs le 2026-11-02 (D-022), sur le S24 FE et le S9 le 2026-11-03 ; restent Android 15 et F-Droid.

## Ce qui attend Patrice

- ~~Renommer le dossier~~ — **fait par Patrice le 2026-11-01** : `J:\applications\call_halt_tech`.
- **Faire relire les phrases de sécurité** allemandes, italiennes et espagnoles par quelqu'un dont c'est la
  langue : une consigne mal traduite ferait faire l'inverse. Les SMS au proche (D-034) et « Retirer l'accès »
  (D-033) en font partie.
- **Mesurer la réception d'un SMS au proche sur un vrai téléphone** (D-034) : une SIM côté téléphone protégé, une
  autre côté proche ; le S24 est en observation, à ta décision.
- ~~Trancher SMS au proche, F-Droid, Dependabot~~ — **tranché le 2026-11-01** : SMS au proche oui, F-Droid
  oui ; DataStore 1.3.0 fusionné, AGP 9.5.0 reporté à la prochaine montée de tout le portefeuille.
- ~~Un Xiaomi, à emprunter~~ — **fait le 2026-11-03** : Patrice a branché un Redmi 9C (MIUI 12, Android 10, sans SIM) ;
  mesures dans D-024, D-025 et P-21 (le « Démarrage automatique » de MIUI, trouvé et traité le même jour).
- Facultatif : la recherche de marque sur TMview (Union européenne) ; la calibration de GPT Luna
  (moins de 0,10 $) pour savoir s'il trouve autant que Sol.

## Pièges à connaître

- Les **tests sur téléphone désinstallent l'appli** à la fin : la réinstaller pour la voir.
- Toujours **viser un seul téléphone** pour ces tests (`ANDROID_SERIAL=<numéro>`, donné par `adb devices`).
- **Jamais `git add -A`** : nommer les fichiers. Message de commit dans un fichier (`git commit -F`).
- Un **nom proposé n'est pas un nom choisi** : rien ne se renomme sans un « on prend X » explicite.
- Relectures : un agent Claude d'abord (gratuit), puis **une** relecture GPT (`gpt-5.6-luna` par défaut,
  `gpt-5.6-sol` pour un code critique). Chaque relecture va dans `relectures/` avec son coût et son tri.
