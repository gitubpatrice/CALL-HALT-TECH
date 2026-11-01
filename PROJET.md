# Call Halt Tech — fiche du projet (d'abord appelée « Call Guard Tech »)

> Idée consignée le 2026-10-31 au soir, à la demande de Patrice (« consigne le nouveau projet »).
> **2026-11-01 : squelette posé** (moteur de détection testé, stockage, écran STOP de démonstration, CI) —
> il ne protège encore personne. Structure et décisions : `docs/ARCHITECTURE.md`. Revue de cette fiche
> (vérifications, relecture GPT, idées) : § 9. **Nom retenu le 2026-11-01 : Call Halt Tech** (§ 9.4).
> Prochaine étape : le prototype de mesure (§ 8, complété en § 9.5). **Il décide de la suite** (Patrice,
> 2026-11-01) : si l'écran STOP ne passe pas par-dessus l'appli bancaire, ou si ses demandes de validation
> sont illisibles, le projet s'arrête (`docs/ARCHITECTURE.md`, D-018).

## 1. Le but

Protéger des **arnaques téléphoniques** — faux conseiller bancaire, faux technicien, faux service des
fraudes, faux gendarme — et non du simple démarchage. Public : tout le monde, et d'abord les personnes
âgées et les proches qui veillent sur elles (« protégez vos proches des arnaques téléphoniques »).

Comme les autres applis Files Tech : **aucune permission Internet**, rien ne sort du téléphone, pas de
compte, pas de Google Play Services, français d'abord puis les 5 langues du portefeuille.

## 2. L'existant (vérifié le 2026-10-31)

| Appli | Ce qu'elle fait | Ce qu'elle ne fait pas |
|---|---|---|
| [Saracroche](https://cbouvat.com/blog/saracroche-android/) — française, libre, F-Droid, Android 10+ | bloque les 12 préfixes ARCEP de démarchage + ~15 millions de numéros signalés | les arnaques depuis des numéros ordinaires ou usurpés |
| [SpamBlocker](https://github.com/aj3423/SpamBlocker) — libre, F-Droid | règles (expressions, contacts, STIR, appel répété, numéros appelés, appli récente, horaires) | rien après le décroché ; réglages pour initiés |
| [ScamRadar](https://scamradar.fr/) — Android, hors ligne | analyse un SMS, un lien ou un QR code collé à la main | rien pendant un appel |
| [Protection Android pendant l'appel](https://blog.google/security/android-expands-pilot-in-call-scam-protection-financial-apps/) — Google, pilote Royaume-Uni puis États-Unis, Android 11+ | appli bancaire **participante** ouverte + **partage d'écran** + appel d'un numéro **hors contacts** → alerte, pause de 30 s, raccrocher en un geste ; [Android 17](https://www.bleepingcomputer.com/news/security/android-17-to-expand-banking-scam-call-and-privacy-protections/) : la banque confirme si l'appel vient vraiment d'elle | sans Google Play Services ; hors banques participantes ; sans partage d'écran ; numéro usurpé enregistré dans les contacts |
| Truecaller, Hiya, anti-spam Google/Samsung, Orange Téléphone (de mémoire) | identification par base en ligne | envoient appels et contacts sur leurs serveurs |

⚠️ **Google construit la même idée** (l'appli bancaire ouverte pendant un appel d'un inconnu). Avant
d'investir, relire où en est sa protection **en France** et pour quelles banques. Notre différence doit
tenir même si Google la déploie ici :
1. **sans Google** (téléphones dégooglisés, utilisateurs de F-Droid) ;
2. **au-delà du partage d'écran** : codes reçus par SMS, demandes de validation des applis bancaires,
   installation d'une appli de prise en main à distance ;
3. **ne se fie pas aux contacts** : un numéro de banque usurpé, que la victime a enregistré, déclenche
   quand même ;
4. **prévient un proche** ;
5. **banques françaises**, textes français.

## 3. La méthode (le « béton »)

**Ne jamais se fier au numéro** : il s'usurpe. S'appuyer sur ce que l'escroc ne peut pas éviter — il lui
faut que la victime fasse, **pendant l'appel ou juste après**, une action irréversible sur son téléphone.
Et une vraie banque ne demande jamais de valider une opération ou de donner un code pendant un appel.

> **Appel en cours (ou terminé depuis moins de 15 min)** **ET** action sensible :
> - un SMS contenant un code de validation arrive ;
> - l'appli bancaire affiche une demande de validation, ou s'ouvre ;
> - une appli de prise en main à distance est installée ou ouverte.

→ **Écran STOP** par-dessus tout (textes revus avec Patrice le 2026-11-01, `docs/ARCHITECTURE.md`, D-017) :
« Vous êtes au téléphone. Vous venez de recevoir un code. **Si on vous dit d'ignorer cet écran, c'est une
arnaque.** Ni votre banque, ni la police, ni les impôts, ni votre assurance ne vous appelleront JAMAIS pour
vous demander un code ou de valider une opération. Le numéro ou le nom affiché ne prouve rien : un escroc
peut afficher le vrai. Raccrochez. Puis appelez vous-même un numéro que vous connaissez : celui de votre
carte ou d'un courrier, jamais celui qu'on vous donne. »
Les phrases vont par ordre d'importance : ce qui ne tient pas à l'écran défile, et on ne fait pas défiler
sous pression. La deuxième dit ce qui vient d'arriver (un texte par déclencheur). Pour une appli de prise
en main, la règle parle de l'appli et le conseil devient « éteignez votre téléphone » (raccrocher ne coupe
pas la prise de contrôle). Dans les minutes qui suivent l'appel : « Votre appel vient de se terminer » et
« Ne faites pas ce qu'on vous a demandé ».
Boutons : **Raccrocher** (appel téléphonique en cours seulement ; pas sur WhatsApp, qu'Android ne sait pas
raccrocher) · **Raccrocher et appeler ma banque**, puis **Appeler ma banque** après l'appel (numéro officiel
saisi depuis la carte) · **Continuer** (actif après 10 s). En option : **SMS à un proche**.

Ça ne bloque rien : c'est une pause au bon moment, avec la seule vérification fiable au téléphone —
raccrocher et rappeler soi-même.

## 4. Fonctions

**Le cœur**
1. La détection ci-dessus, avec une fenêtre après l'appel réglable (15 min par défaut).
2. Appel long avec un inconnu (plus de 10 min, réglable) → rappel des consignes, et alerte du proche.
3. Codes reçus par SMS : mots-clés français (code, « ne le communiquez à personne », validation,
   virement, bénéficiaire, ajout de carte à Google Pay / Apple Pay / un portefeuille — l'ajout de la carte
   de la victime au portefeuille de l'escroc est une arnaque fréquente), puis les autres langues.
4. Applis de prise en main à distance (AnyDesk, TeamViewer QuickSupport, RustDesk, AirDroid…) : alerte à
   l'installation même hors appel, STOP si ouverte pendant un appel.
5. Numéro officiel de sa banque + bouton « Appeler ma banque ».

**Le proche**
6. Mode « proche protégé » : réglages et désactivation sous code — l'escroc dira « désactivez cette
   appli ».
7. SMS au proche : alerte STOP, « Continuer » choisi malgré l'alerte, protection coupée depuis l'appli.
   (La désinstallation, elle, ne peut pas être détectée par l'appli elle-même : limite à écrire.)
8. Journal des alertes, lisible sur le téléphone (ce qui s'est passé, quand, ce qui a été choisi).
9. Bouton « Essayer l'écran STOP », pour que le proche montre ce qui arrivera.

**Le socle des appels** (attendu, sans rivaliser avec la base de Saracroche)
10. Numéros masqués, préfixes ARCEP de démarchage, international, plages horaires.
11. « J'attends un appel » : tous les inconnus passent 1, 2 ou 4 h, puis le filtre se remet seul.
12. Inconnus autorisés autour d'un rendez-vous de l'agenda (lecture du calendrier, sur option) —
    inédit à notre connaissance.
13. Signalement au 33700 (SMS « spam vocal » + numéro, préparé ; format à revérifier).

**Partout**
14. Gros texte, TalkBack, mots simples ; 5 langues.
15. Aucune permission Internet, contrôlée en CI sur le manifeste fusionné (comme Agenda Tech).
16. Lien avec SMS Tech (même logique anti-arnaque, « Maman, j'ai changé de numéro ») : à concevoir —
    les deux applis n'ont pas la même clé de signature.
17. Pont avec **Trust Code Tech** (`J:\applications\trust_code_tech\PROJET.md`, idée du même soir) :
    l'écran STOP propose « Demandez-lui son code de confiance » et ouvre l'appli si elle est installée.
    Recommandé : deux applis séparées, Trust Code d'abord (voir sa fiche, § 6).

## 5. Ce qu'Android permet (à confirmer par le prototype)

| Besoin | Moyen | Accès demandé |
|---|---|---|
| Savoir qu'un appel est en cours, et sa durée | `TelephonyCallback` | état du téléphone |
| Filtrer avant la sonnerie (numéros hors contacts) | `CallScreeningService`, rôle « filtrage des appels », Android 10+ | rôle accordé par l'utilisateur |
| Voir un SMS de code | réception des SMS, ou lecture des notifications | SMS, ou accès aux notifications |
| Voir la demande de validation d'une appli bancaire | `NotificationListenerService` | accès aux notifications |
| Savoir qu'une appli bancaire ou de prise en main s'ouvre | `UsageStatsManager` | accès à l'utilisation des applis |
| Voir une installation | `ACTION_PACKAGE_ADDED` (récepteur actif pendant l'appel) + `<queries>` ciblées | — |
| Afficher STOP par-dessus l'appli bancaire | fenêtre superposée | affichage par-dessus les autres applis |
| Raccrocher | `TelecomManager.endCall` | réponse aux appels |
| Prévenir le proche | envoi de SMS | SMS |

minSdk probable : 29 (Android 10, rôle de filtrage).

> ⚠️ **Tableau corrigé le 2026-11-01** (§ 9.1) : plusieurs lignes ne tiennent pas telles quelles —
> superposition masquée par les banques, rôle de filtrage exclusif, codes SMS retardés par Android 17,
> installations filtrées par la visibilité des paquets. La version vérifiée, avec ses sources, est
> `docs/ARCHITECTURE.md` § 6.

## 6. Limites, à écrire telles quelles

- **Pas du 100 %** : l'appli n'entend pas l'appel. Une action faite sur un ordinateur, ou une carte remise
  à un « coursier », échappe au téléphone.
- **Accès lourds** : défendables pour une appli de protection sans Internet, mais Google Play la
  refuserait probablement, et les antivirus risquent de la signaler (vécu avec SMS Tech).
- **Paramètres restreints** (Android 13+) : l'accès aux notifications est bloqué pour une appli installée
  depuis un APK téléchargé, jusqu'à une manipulation dans les réglages.
- **Hors de portée** : décrocher pour interroger l'appelant, enregistrer ou transcrire un appel (réservé
  à l'appli téléphone ou au système).

## 7. Décisions ouvertes (à Patrice)

- ~~Nom définitif (« Call Guard Tech » provisoire ; vérifier qu'aucune appli connue ne le porte).~~
  **Tranché le 2026-11-01 : Call Halt Tech** (§ 9.4). INPI : rien ; domaine libre.
- ~~SMS au proche (permission d'envoi) : oui / non / en option.~~ **Oui** (Patrice, 2026-11-01).
- ~~F-Droid ou GitHub seul.~~ **F-Droid : oui** (Patrice, 2026-11-01). Conséquences : `docs/ARCHITECTURE.md`,
  D-016. ⛔ **Rien n'est publié tant que l'appli n'est pas fiable, solide et sécurisée au mieux, avec un
  score d'au moins 95 sur 100** (Patrice, même jour).
- ~~Continuer ou arrêter le projet (« il y a déjà plein d'applis qui le font »).~~ **Tranché le 2026-11-01** :
  on continue jusqu'au prototype de mesure, qui décide (§ 8 ; `docs/ARCHITECTURE.md`, D-018). En France, rien
  ne fait la même chose en français, sans Google ni Internet (§ 9.1) ; les vrais risques sont techniques
  (§ 8) et la protection de Google, si elle arrive en France.
- Revérifier, au lancement, la protection de Google en France.

## 8. Prochaine étape : un prototype de mesure (1 à 2 jours, S9 + émulateur)

Trois questions, avant tout cahier des charges :
1. L'écran STOP s'affiche-t-il par-dessus l'appli bancaire (Android 10 sur le S9, 14-15 sur émulateur) ?
2. Les demandes de validation des applis bancaires françaises sont-elles lisibles par l'accès aux
   notifications ?
3. Comment guider simplement l'utilisateur à travers les « paramètres restreints » d'Android 13+ ?

Si les trois passent, la méthode tient. Si l'une bloque, on le sait sans avoir investi des semaines.
**Décidé le 2026-11-01 (Patrice)** : si la 1 ou la 2 échoue, le projet s'arrête (`docs/ARCHITECTURE.md`, D-018).

## 9. Revue du 2026-11-01 (Patrice : « vérifie bien le projet, ajoute d'autres idées, ou demande à GPT »)

Trois sources, croisées : deux agents de recherche sur sources primaires (developer.android.com, code
AOSP par branche de release, Arcep, service-public.fr), et une relecture **indépendante** de la fiche par
GPT (`gpt-5.6-sol`, effort `high`, **0,575 $**). GPT n'avait pas vu mes idées, pour ne pas l'ancrer. Ses
12 corrections et 12 idées sont triées ci-dessous ; une de ses API était fausse (§ 9.2, n° 9). Les
rapports bruts et leurs consignes sont dans `relectures/`.

### 9.1 Ce qui change dans la fiche

1. **La méthode tient, et elle est confirmée par l'existant.** Google la met lui-même en œuvre, mais
   **ni en France, ni sans Google** :
   - la protection pendant un appel : Royaume-Uni, États-Unis, Brésil et Inde ;
   - les « appels financiers vérifiés » : Brésil et Revolut, avec Google Play Services obligatoire.

   Android 16 bloque, dans AOSP, l'installation hors magasin et l'octroi de l'accessibilité pendant un
   appel avec un inconnu. Samsung One UI 9 (juillet 2026) bloque une appli installée juste après un appel
   suspect. **L'appli la plus proche : CallShield** (GitHub, MIT), qui alerte quand un code arrive pendant
   un appel ou dans les 10 minutes. Elle utilise Internet, n'est pas française et ne prévient aucun proche.
   La différence du § 2 tient, mais il ne faut plus écrire « inédit » sans vérifier.

   **Concurrent direct trouvé le même jour, en cherchant un nom : VoxSafe** (« Phone Scam Prevention »,
   Google Play et App Store, 2026). Il vise les seniors, leur fait raccrocher avant la fraude et prévient
   leur enfant adulte. Mais il « analyse » les appels d'inconnus (filtrage par IA, sans doute sur un
   serveur), et coûte 9,99 $ par mois. La différence tient : aucune analyse de l'appel, aucun réseau,
   gratuit, libre, français, vérifiable. À étudier de près avant toute publication.
2. **L'écran STOP ne peut pas être une fenêtre superposée** : une appli bancaire la masque
   (`setHideOverlayWindows`, permission accordée sans demande). Il sera une **Activity**, qu'Android laisse
   lancer depuis l'arrière-plan à qui détient « affichage par-dessus » (confirmé jusqu'à Android 17).
3. **Android 17 retarde de 3 heures les SMS de code** pour toute appli qui *cible* Android 17, sauf
   l'appli SMS par défaut. ⇒ targetSdk 36. Et depuis Android 15, l'accès aux notifications reçoit **masqué**
   le texte d'un SMS de code. Le masquage lui-même peut toutefois servir de signal.
4. **Le rôle de filtrage d'appels est exclusif** : le prendre retire Saracroche. Le cœur de l'appli
   n'en a pas besoin, puisqu'il écoute l'état du téléphone par un autre moyen confirmé. Le « socle des
   appels » (§ 4.10-12) devient **optionnel**, pour qui n'a pas d'autre bloqueur.
5. **§ 4.4 se contredisait** : « alerte à l'installation **même hors appel** » mais « récepteur actif
   **pendant** l'appel ». Corrigé : l'appli relit les installations à son prochain réveil
   (`getChangedPackages`). Il lui faut alors voir toutes les applis (`QUERY_ALL_PACKAGES`), sinon les
   installations inconnues lui sont cachées.
6. **Les paramètres restreints** (§ 6) ne bloquent pas que l'accès aux notifications : ils bloquent aussi
   l'affichage par-dessus, l'accès à l'utilisation et les permissions SMS. Presque tout le parcours
   d'activation est concerné.
7. **Les appels WhatsApp, Signal et Telegram** échappaient à la méthode, or les escrocs s'en servent. Le
   mode audio du téléphone les trahit probablement, sans permission : à mesurer.
8. **« Appeler ma banque »** : seulement **après** la fin de l'appel, en composant le numéro sans le
   lancer. Appeler pendant l'appel mettrait l'escroc en attente au lieu de le couper (GPT).
9. **Le code du proche ne protège que l'interface** (GPT) : les réglages d'Android permettent toujours
   d'arrêter l'appli ou de lui retirer ses accès. À écrire dans les limites (§ 6). Repli : voir § 9.2, idée 5.
10. **§ 4.13, signalement au 33700** : le format est confirmé, « spam vocal » suivi du numéro
    (service-public.fr). Le 33700 vise surtout les appels qui poussent à rappeler un numéro surtaxé.
11. **§ 4.10, préfixes de l'Arcep** : il y en a 12 en métropole, plus ceux de l'outre-mer. S'y ajoutent,
    depuis le 1er janvier 2026, des numéros « d'intérêt général » (01510, 02810…) **qu'il ne faut pas
    bloquer**.
12. **Le numéro d'une banque s'usurpe toujours en 2026** : plus de 19 000 signalements en 2025 selon
    l'Arcep, et le résultat de l'authentification n'arrive pas jusqu'au téléphone. « Ne jamais se fier au
    numéro » reste le bon principe.

### 9.2 Idées retenues (de moi, de GPT, ou des deux), par valeur et coût

| # | Idée | Signal (vérifié) | Coût |
|---|---|---|---|
| 1 | **Un service d'accessibilité ou un accès aux notifications activé pendant ou après l'appel** → STOP (les applis de prise en main et les voleuses de codes en ont besoin) | liste lisible sans permission (confirmé) | faible |
| 2 | **Renvoi de tous les appels activé** → alerte (l'escroc détourne les appels de vérification de la banque) | indicateur de renvoi (confirmé, inconditionnel seul) ; Google l'ajoute sur Pixel en décembre | faible |
| 3 | **Les appels WhatsApp, Signal et Telegram comptent comme des appels** | mode audio (à mesurer) | faible |
| 4 | **Toute installation pendant l'appel**, pas seulement la liste AnyDesk et TeamViewer ; noter ce que l'appli installée peut faire (accessibilité, origine) | `getChangedPackages` + `QUERY_ALL_PACKAGES` (confirmé) | moyen |
| 5 | **État de la protection** : chaque accès en vert ou en rouge ; le proche est prévenu si un accès est retiré | contrôle au réveil ; déconnexion de l'écouteur | faible |
| 6 | **« Je crois que je me suis fait avoir »** : marche à suivre hors ligne, avec les numéros officiels vérifiés (Info Escroqueries 0 805 805 817, gratuit ; opposition carte 0 892 705 705 ; 17Cyber ; Perceval ; Thésée) | aucun | faible |
| 7 | **Relevé d'incident à remettre à la banque ou à la police** : le journal prouve qu'un appel était en cours pendant la validation, utile pour obtenir un remboursement | partage, sans réseau | faible |
| 8 | **Appel présenté avec le numéro de la banque** (celui saisi dans l'appli) → « ce numéro peut être usurpé » | numéro entrant (confirmé) | faible |
| 9 | Appli SMS ou téléphone par défaut changée, administrateur de l'appareil activé | `Telephony.Sms.getDefaultSmsPackage`, `TelecomManager.getDefaultDialerPackage`, `getActiveAdmins` — **GPT proposait `RoleManager.getRoleHolders`, réservé au système** | faible |
| 10 | Code reçu **juste avant** l'appel ou pendant la sonnerie → rappel léger (`docs/ARCHITECTURE.md`, P-6) | — | faible |
| 11 | Trois questions après un long appel avec un inconnu (« vous a-t-on demandé un code, de l'argent, d'installer une appli ? ») | aucun | moyen |
| 12 | Message STOP lu à voix haute (option) | synthèse vocale du téléphone | faible |
| 13 | Administrateur d'appareil en option : avertir quand on veut désinstaller, et prévenir le proche | `onDisableRequested` / `onDisabled` ; risque de signalement par les antivirus | moyen |
| 14 | Pont avec SMS Tech malgré des clés différentes : permission `knownSigner` (Android 12+). SMS Tech, appli SMS par défaut, échappe au délai d'Android 17 | à concevoir | moyen |
| 15 | L'appui sur « Valider » dans une notification bancaire pendant l'appel (l'escroc : « appuyez sur Valider ») (GPT) | notification retirée par un clic (`REASON_CLICK`) ; toutes les banques ne la retirent pas | moyen, à mesurer |

### 9.3 Idées écartées, et pourquoi

- **Détecter un VPN** (GPT) : il faudrait `ACCESS_NETWORK_STATE`, ce qui romprait la promesse « aucune
  permission réseau » ; le signal est faible de toute façon.
- **Détecter l'enregistrement d'écran** (GPT) : l'API d'Android 15 ne voit que l'enregistrement de *nos*
  propres fenêtres.
- **Accusé de réception du proche par SMS** (GPT) : complexe, et une réponse risquerait d'être prise
  pour une autorisation. À reconsidérer plus tard.
- **Suivre le parcours Réglages → installateur** (GPT) : les noms d'écrans changent d'un fabricant à
  l'autre. Les idées 1 et 4 couvrent le danger réel.
- **Service d'accessibilité pour l'appli elle-même** : les applis bancaires le détectent et refusent de
  tourner (`docs/ARCHITECTURE.md`, D-010).
- **Corréler une séquence plutôt qu'un événement isolé** (GPT) : **déjà la méthode**. La règle du moteur
  n'alerte jamais sur une action seule, toujours sur « appel + action ». Un score cumulé (appel →
  réglages → installation → accessibilité) n'apporterait rien de plus que les idées 1 et 4, qui
  déclenchent chacune un STOP dès la première étape dangereuse.

### 9.4 Le nom (décision de Patrice, avant toute publication)

« Call Guard » est pris :
- sur Google Play : SmarTone (Hong Kong, plus de 100 000 téléchargements), Xfinity Call Guard (Comcast),
  quatre « CallGuard » (Vietnam, Inde, contrôle parental…) ;
- sur l'App Store : un « CallGuard » ;
- Cybora CallGUARD, appli à venir dans le même domaine (trouvée par Patrice) ;
- le domaine **callguard.tech**, qui vend de la détection de fraude vocale ;
- la marque américaine CALLGUARD n° 6011194, active.

F-Droid n'a rien de ce nom ; l'INPI et l'EUIPO n'ont pas pu être consultés automatiquement. **Le nom
retenu se vérifie à la main** sur data.inpi.fr et TMview avant d'être adopté. L'`applicationId` se fixe
avec lui : il ne pourra plus changer après le premier APK publié.

**Décision de Patrice, 2026-11-01 : Call Halt Tech** (`com.filestech.callhalt`). « Halt ! » se reconnaît
dans les 5 langues (*Halt!*, *Halte !*, *Alt!*, *¡Alto!*), et c'est l'écran STOP de l'appli. Rien trouvé
sous ce nom. Une trentaine de noms ont été vérifiés avant lui ; deux trouvailles valent pour le projet
lui-même : **VoxSafe** (concurrent direct, § 9.1) et **Vigiphone** (appli française anti-démarchage, 2025).
**Vérifié par Patrice le même jour : rien à l'INPI, et le nom de domaine est libre (ICANN).** Reste,
facultatif : TMview (marques de l'Union européenne).

### 9.5 Le prototype de mesure (§ 8), complété

Les trois questions restent, et deux s'ajoutent : les appels VoIP (P-4) et ce qui garde l'appli éveillée
pendant l'appel (P-5). Le détail est dans `docs/ARCHITECTURE.md` § 7.

**Le S9 n'est pas rooté** (Patrice, 2026-11-01 ; mesuré : ni `su` ni Magisk, firmware Samsung officiel).
Une appli bancaire peut donc s'y essayer pour la question 2 (lire les demandes de validation). Seule
réserve : son chargeur d'amorçage est déverrouillé, ce que certaines banques refusent aussi. Si elle
refuse, la mesure passe au S24 FE, **à la décision de Patrice**, et sans test automatisé sur ce téléphone
(ces tests effacent les données de l'appli testée).
