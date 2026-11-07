# Audit de publication n° 1 — Call Halt Tech, appli entière (2026-11-07)

Révision auditée : `2226060`, branche `audit/publication-1`. Le code est celui de `main` `d17b005` ; les deux commits
de plus fixent la grille (`docs/GRILLE-PUBLICATION.md`), commitée et poussée **avant** le premier agent. Coût : 0 $
(sous-agents). Tous les agents en lecture pure ; les mesures, par Claude, sur l'émulateur Android 16 (`revue-api36`,
version de test) et en lecture seule sur le Galaxy S9.

## Résumé exécutif

- **Un constat élevé, donc bloquant (B1), mesuré : FN-1.** Un appel décroché avant le premier déverrouillage qui suit
  un démarrage n'existe pas pour l'appli ; toute l'arnaque passe ensuite sans STOP. L'escroc l'obtient d'une phrase
  (« éteignez et rallumez, je vous rappelle »). Correctif connu : P-7, lire l'état réel de l'appel.
- **Sécurité** : 5 chercheurs, 15 candidats, 3 vérificateurs adverses par candidat. **3 retenus, aucun au-dessus de
  moyen** ; 7 réfutés, avec leur raison. Aucune fuite du code, des mots d'un SMS ni du numéro qui appelle.
- **Le reste** : surtout la règle des codes hors du français, l'écran STOP qui perd son conseil le plus grave quand
  un second déclencheur arrive, l'affichage à gros caractères, et des textes qui promettent plus que le code.
- **Score avant corrections : 18 / 100, et bloqué (B1).** C'est le point de départ, pas un verdict : la grille note
  le commit corrigé. Les corrections techniques sont à faire ; quatre décisions sont à Patrice (§ 6).

## 1. Couverture

| Audit | Agent | Résultat |
|---|---|---|
| Sécurité, palier profond (axe A) | 5 × `mobile-secu-chercheur` (notifications reçues ; composants exportés ; contournement du STOP ; abus de l'envoi de SMS ; stockage et fuites), puis 3 × `mobile-secu-verificateur` par candidat (accessibilité, impact, défenses ; 2 sur 3 pour garder) | § 2 |
| Faux positifs, toutes les règles (B) | `regles-faux-positifs-auditor`, harnais sur les classes compilées, 0 régression sur les 126 SMS du corpus | 5 moyens, 5 faibles |
| Faux négatifs, arnaques réelles (B) | `general-purpose`, 9 scénarios minutés sur le moteur compilé, 31 SMS, recherche des formulations bancaires | 1 élevé, 5 moyens, 3 faibles |
| Motifs de défaut (B, C, E) | `android-motif-defauts-auditor` | 6 moyens (2 limites connues), 7 faibles |
| Trois axes (C) | `android-3-axes-auditor` | 5 moyens, 11 faibles |
| Cohérence (C) | `android-architecture-coherence-checker` | 2 moyens, 11 faibles |
| Langues (E) | `android-i18n-strings-cleaner`, contrôles du dépôt relancés | mécanique propre ; 4 textes faux ou ambigus |

Inventaire : `app/` et `domain/` inspectés en entier (`app/src/debug` vérifié absent du manifeste fusionné de la
version publiée, reconstruit le jour même) ; `tools/`, `.github/`, `config/`, `gradle/` lus par les audits de cohérence
et de langues ; `docs/`, `relectures/`, `PROJET.md`, `REPRISE.md`, README, PRIVACY, SECURITY confrontés au code (motif
5) ; `build/` écarté (généré), sauf le manifeste fusionné et les classes compilées des harnais.

## 2. Sécurité : le panel

| Candidat | Trouvé par | Vote | Verdict |
|---|---|---|---|
| **N-1** Un second STOP d'une autre sorte (un SMS de code, de n'importe quel expéditeur ; une notification sur le canal `securpass_id`) prend l'écran : le STOP d'accessibilité ou de prise en main perd « Retirer l'accès » et « éteignez », « Raccrocher » répond pour tous, et ce STOP ne revient jamais (`StopSession.kt:33,40`, `StopActivity.kt:105`, `StopText.kt:73-103`, `AccessibilityGrants.kt:57`) | notifications ; aussi motifs n° 2 et faux positifs M5 | 3 VP (moyen, moyen, faible) | **RETENU** ; compté une fois, moyen, axe B |
| **C-2** Aucune protection contre une superposition d'une autre appli : elle couvre le STOP sans le fermer (`StopActivity.kt:67-71`, pas de `setHideOverlayWindows`) | STOP ; composants exportés | 2 VP (moyen, faible), 1 FP | **RETENU, faible** (le toucher à l'aveugle sous Android 10-11 : réfuté, « Continuer » prévient le proche) |
| **S-2** Une appli quelconque crée un canal `securpass_id` : STOP et SMS au proche, sans `SEND_SMS` (`NotificationSignal.kt:44`) | SMS ; composants exportés ; notifications | 3 VP (faible) | **RETENU, faible**. La variante « faire retenir le SMS de la vraie alerte » est réfutée (même texte déjà reçu). D-019 (« un STOP de trop, du côté protecteur ») est faux depuis D-034 |
| C-1 Une activité passée devant, ou « Accueil » d'un service d'accessibilité, ferme le STOP (`StopActivity.kt:151-154`) | STOP ; composants exportés | 1 VP, 2 FP | **Réfuté** : le proche est prévenu dès la décision du STOP, et la position de l'attaquant lui donne déjà ce gain. Reste FN-4 (§ 3) et une limite à écrire dans P-15 : la marque « accessibilité sensible » n'arrête pas une action globale |
| C-3 « Appeler ma banque » ouvre le composeur par une intention implicite | STOP | 3 FP | **Réfuté** : le rôle de composeur fixe la cible ; le rôle lui-même donne déjà plus |
| S-1 Un numéro de 8 chiffres, ou un indicatif inventé, passe pour « le même proche » : pas d'adieu | SMS | 1 VP, 2 FP | **Réfuté** en sécurité (retirer l'autorisation SMS puis le proche mène au même état) ; défaut de logique gardé, axe C |
| S-3 Un numéro court surtaxé écrit avec son indicatif passe le plancher de 8 chiffres | SMS | 3 FP | **Réfuté** (acheminement et facturation non établis ; l'appli SMS fait déjà la même chose) ; texte faux gardé, axe E |
| L-1 Le plafond de 20 SMS par jour repart à zéro avec le processus | stockage | 3 FP | **Réfuté** (personne n'y gagne) ; texte faux gardé, axe E |
| L-2 Le numéro du proche se lit sur l'accueil pendant un partage d'écran | stockage | 3 FP | **Réfuté** (il est déjà dans la conversation de l'appli SMS) ; une ligne à écrire dans D-034 |
| I-3 Sous Android 10, une appli qui déclare la même affinité de tâche attire le STOP dans sa tâche | composants exportés | 1 VP, 2 FP | **Réfuté** : l'UID préfixé à l'affinité est lu dans Android 13 et 14 ; Android 10 à 12 non tranché, mesurable sur `revue-api30` (Android 11) |

**Cherché sans rien trouver** : forger un état d'appel (`PHONE_STATE` est protégé), lancer ou répondre au STOP depuis
une autre appli, un `PendingIntent` détourné (tous explicites et immuables), un composant de la version de test dans la
version publiée, une page imitée à la place des réglages, le code ou les mots d'un SMS gardés ou journalisés (aucun arbre
Timber en version publiée, `toString` masqué), la sauvegarde et le transfert d'appareil (coupés), une expression
régulière qui plante l'écouteur, usurper l'appli SMS ou une appli de prise en main (`packageName` attesté).

## 3. Les constats, par axe

Statut : C = confirmé (lu ou mesuré), P = probable. Décision : T = technique, je la prends ; **P** = Patrice.

### Axe B — Justesse de la détection

| # | Gravité | Statut | Constat | Où | Décision |
|---|---|---|---|---|---|
| **FN-1** | **Élevé** | **C, mesuré** | Appel décroché avant le premier déverrouillage après un démarrage : aucun composant n'est `directBootAware`, la diffusion `OFFHOOK` est perdue, rien ne relit l'état réel de l'appel (P-7). La demande Sécur'Pass est vue, pas de STOP | `CallStateReceiver`, `CallHaltApplication.kt:29-42` | T : lire `TelecomManager.isInManagedCall()` au démarrage du processus, au déverrouillage (`ACTION_USER_UNLOCKED`) et avant chaque action sensible ; un appel en cours inconnu du moteur = `CallStarted` d'abord |
| N-1 | Moyen | C | voir § 2 | | T : texte, règle, conseil et boutons composés sur tous les déclencheurs de l'écran, le plus grave d'abord (accessibilité, prise en main, le reste) |
| N-3 | Moyen | C, mesuré | Saturer l'appli SMS : 55 SMS de numéros distincts, Google Messages atteint le plafond de 50 notifications d'Android ; le SMS de code pendant l'appel n'a pas de notification, pas de STOP | `NotificationListener.kt:99` | **P** (§ 6) |
| FN-2 | Moyen | C (code) | SMS Tech réglée sur l'aperçu masqué : ses notifications portent « Nouveau message », aucun code n'est jamais vu | `LatestSms.kt:25,29` ; SMS Tech `IncomingMessageNotifier.kt:152-157` | T : détecter un texte fixe et le dire sur l'accueil (mots **P**) |
| FN-3 | Moyen | C (moteur) | Une appli de prise en main installée pendant un appel reste installée : pas d'avertissement « Désinstaller », et sa session 20 min plus tard passe | `DetectionEngine.kt:142-170` | **P** (§ 6) |
| FN-4 | Moyen | P | « Accueil » (un service d'accessibilité, ou l'outil de prise en main) ferme le STOP en une seconde | `StopActivity.kt:151-154` | **P** (§ 6) ; à mesurer avec la session RustDesk |
| M1 | Moyen | C | « personne », « achat », « paiement » refont un code d'une livraison ou d'une réservation | `ValidationCode.kt:82-86` | T pour « personne » (mesuré, 0 régression) ; **P** pour « achat »/« paiement » (perd « Paiement … chez RELAIS COLIS : code ») |
| M2 | Moyen | C | Avis de carte ou de PIN en anglais, allemand, italien, espagnol = un code (le français non) | `ValidationCode.kt:33-41` | T (mesuré, 0 régression) |
| M3 | Moyen | C | Codes de porte et de boîte à clés hors du français | `ValidationCode.kt:71-79` | T (mesuré, 0 régression) |
| M4 | Moyen | C (S9) | Exemption de l'accessibilité réservée au préinstallé : sur Samsung, la suite de Google installée depuis Play n'est pas exemptée (le S9 n'a que la suite de Samsung en système) | `AccessibilitySensor.kt:80-88` | T : exempter aussi un identifiant de la liste signé du certificat de Google ou de Samsung |
| C1 | Moyen | C (code) | Une notification de l'appli SMS illisible donne `null`, donc aucun STOP : l'échec penche du côté silencieux | `NotificationListener.kt:127-133` | T : la lire comme masquée (`Masked`) |
| — | Faible | | Italien « code » (files d'attente) ; « STOP an 81234 » ; « code de confirmation » d'une réservation ; un « Continuer » = autant de SMS que de déclencheurs (2 STOP, 4 SMS mesuré) ; D-032 jamais passé aux faux positifs (AirDroid) ; un nom de commerçant fait taire un code (FN-5) ; repost au redémarrage (annexe) | `ValidationCode.kt`, `StopAnswers.kt:48-52`, `RemoteControlNotices.kt:64-69` | T |

### Axe C — Solidité du code (et affichage)

| # | Gravité | Statut | Constat | Où | Décision |
|---|---|---|---|---|---|
| U1 | Moyen | C, mesuré | Police à 200 % : la liste des accès de l'accueil coupe ses titres au milieu des mots (italien : « scherm/ata », « bloccat/a ») | `HomeScreen.kt:217-234` | T : statut sous le titre |
| U2 | Moyen | C, mesuré | Police à 200 % : l'écran STOP s'arrête à « Si on vous dit d'ignorer » ; la règle et le conseil sont sous les boutons (le paysage est pire) | `StopScreen.kt:88-154` | T : défilement d'ensemble quand la police est grande ou l'écran bas ; la phrase « c'est une arnaque » avant les boutons |
| U3 | Moyen | C (vu) | La bordure des boutons secondaires du STOP est presque invisible sur le rouge (WCAG 1.4.11) | `StopScreen.kt:130-136` | T |
| U4 | Moyen | C | Le STOP ne vibre pas quand il s'ouvre (téléphone à l'oreille) | `StopActivity.kt:65-116` | T, sauf avis contraire : une vibration brève, sans permission |
| #4 | Moyen | C | Le lanceur du STOP ne rattrape qu'`ActivityNotFoundException` : toute autre exception sort avant la notification de repli et le SMS au proche | `StopLauncher.kt:67-73` | T |
| C2 | Moyen | C | Ce lanceur, la logique la plus dense, n'a aucun test | `StopLauncher.kt:27-65` | T : interface de la notification, 6 tests |
| — | Faible | | `sameSubscriber` (S-1) ; expression quadratique sur le fil principal (Q1) ; appels au système à chaque reprise (Q2) ; `GuardianRepository` relance hors `try` (Q3) ; exception du moteur prise pour une panne de stockage (Q4) ; `SettingsRepository` (#12) ; second `notify` sans `catch` (C4) ; `when … else` (C5) ; cycles de paquets (C6) ; horloges (C7) ; aides dupliquées (C9) ; unités (C10) ; nommage (C11) ; ligne Room illisible abandonnée sans journal (C12) ; notification d'avertissement sans `CLEAR_TASK` (#9) ; contraste du décompte (U6) ; TalkBack et le décompte (U7) ; titres plus petits que le texte (U8) ; accueil qui promet un SMS quand la protection est coupée (U9) ; second appui = SMS d'activation renvoyé (U10) ; clignotement (U11) ; aucun test d'interface (T1) | | T |

### Axe E — Textes, promesses, langues

| # | Gravité | Statut | Constat | Où | Décision |
|---|---|---|---|---|---|
| #1 | Moyen | C | Le journal de 14 mois n'a aucun lecteur, alors que PRIVACY le justifie par la preuve à remettre à la banque, et que la fiche promet la fonction 8 | `DetectorDao.kt:44-46`, `PRIVACY.md:88-94` | **P** (§ 6) |
| E-1 | Moyen | C | Un appel reçu sur le fixe ou un autre téléphone n'est pas vu ; ce n'est écrit nulle part | `strings.xml` (`home_on_*`), README, `PROJET.md` § 6 | T ; mots **P** |
| E-2 | Moyen | C/P | « valider une opération dans l'appli de la banque » promet plus que le code : seule la notification Sécur'Pass est vue | README « La méthode » | T ; mots **P** |
| — | Faible | | Le premier SMS au proche dit « Il ne vous envoie rien d'autre » (5 langues), faux ; « Er » en allemand et « No avisar a nadie más » en espagnol ambigus ; exemples de numéros par langue ; « aucune autre notification » (étape alerte) faux ; SECURITY dit l'appli « ne protège encore personne » et cite « le numéro qui appelait » ; PRIVACY « jamais plus de 20 par jour » ; « numéros courts refusés partout » ; D-019, P-2 et le KDoc de `NotificationSignal` faux depuis D-034 ; deux outils cités par `check-translations.py` absents ; Banque Populaire et Caisse d'Épargne nommées sans mesure ; pas de lien vers la confidentialité dans l'appli ; quatre précisions de PRIVACY (`pending_stop`, clé et heure lues, avertissement public, montre appairée) ; codes allemands, italiens, espagnols non vus, dit dans l'appli mais pas dans le README | | T ; mots **P** |

### Axe D — Fiabilité mesurée

Inchangé par cet audit : la liste du § 4 de la grille. Ajoutés à mesurer : I-3 sur Android 11, FN-4 avec la session
RustDesk, N-3 avec Samsung Messages, l'aperçu masqué de Samsung Messages.

## 4. Mesures faites pendant l'audit

- **FN-1** (émulateur Android 16, code PIN) : `adb reboot` ; téléphone verrouillé, stockage chiffré indisponible,
  l'appli ne tourne pas ; appel reçu et décroché (`mCallState=2`), aucun `PHONE_STATE` pour l'appli ; déverrouillage,
  l'appli démarre, écouteur relié, l'appel toujours en cours ; demande Sécur'Pass factice →
  `BANK_VALIDATION_REQUEST` vu, **aucun STOP**, l'écran d'appel reste devant. **Contrôle positif** : appel suivant,
  décroché après le déverrouillage → `OFFHOOK` entendu, même demande → STOP au premier plan.
- **N-3** (émulateur Android 16, Google Messages) : 55 SMS de 55 numéros → 49-50 notifications actives ; Android
  refuse la suivante (« Package has already posted or enqueued 50 notifications. Not showing more ») ; pendant un appel,
  le SMS de code arrive dans la boîte de réception, aucune notification, aucun STOP. **Contrôle positif** : les
  notifications de Messages vidées, le même SMS de code pendant le même appel → `CODE_MESSAGE`, STOP au premier plan.
- **U1, U2, U3, U6, U8** (émulateur Android 16, police à 200 %, captures) : voir les tableaux.
- **M4** (Galaxy S9, lecture seule) : `pm list packages -s` ne montre que la suite de Samsung ; celle de Google est
  absente.

Remis en état : appel terminé, code retiré, police à 100 %, langue de l'appli rendue au téléphone. Le S24 n'a pas été
touché.

## 5. Score avant corrections (grille `docs/GRILLE-PUBLICATION.md`)

| Axe | Retraits | Score |
|---|---|---|
| A. Sécurité | C-2, S-2 : 2 faibles | 18 |
| B. Justesse | FN-1 élevé (bloquant) ; 10 moyens ; faibles au plafond | 0 |
| C. Solidité | 6 moyens ; faibles au plafond | 0 |
| D. Fiabilité mesurée | les mesures attendues (− 18) et D-026 (− 3) | 0 |
| E. Textes | 3 moyens ; faibles au plafond ; 3 langues non relues (− 6) | 0 |
| **Total** | **bloqué par B1 (FN-1)** | **18 / 100** |

**Après les corrections techniques**, si elles passent leurs relectures : A 20, B 20, C 20, E 14. L'axe D reste à 0
tant que les mesures sur de vrais téléphones manquent : environ **74**. Les **95** demandent ces mesures (une vraie SIM
de chaque côté, l'installation par F-Droid, Android 10 avec SIM, plusieurs jours sur Samsung, HyperOS, deux SIM,
AnyDesk, D-026) et la relecture par des natifs.

## 6. Décisions de Patrice

1. **FN-4**, un STOP quitté sans réponse (par « Accueil ») pendant un appel : reposter la notification plein écran,
   et/ou prévenir le proche, ou écrire la limite.
2. **FN-3**, une appli de prise en main installée pendant un appel : un bouton « Désinstaller » sur ce STOP et
   l'avertissement après l'appel, ou l'avertissement seul.
3. **N-3**, l'appli SMS saturée pendant un appel : un STOP « vous recevez beaucoup de SMS d'un coup », ou une limite
   écrite.
4. **#1**, le journal : un écran « Historique » lisible et partageable (fonction 8), ou réécrire PRIVACY.
5. Les mots des textes nouveaux ou corrigés (E-1, E-2, FN-2, le premier SMS au proche) ; « achat » et « paiement »
   (M1).

## 7. Plan

1. **Avant tout** : FN-1 (bloquant), puis N-1, C1, #4, M1 à M4.
2. Puis les décisions du § 6, les moyens de l'axe C (U1 à U4, C2), les textes.
3. Les faibles.
4. Chaque correction avec ses relectures (Claude, puis GPT) et sa mesure ; puis la relecture externe du commit corrigé
   (B3) et le score.
