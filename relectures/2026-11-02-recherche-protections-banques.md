# Recherche : ce que font déjà les banques, Google, Samsung et l'État (2026-11-02)

- **Question** (Patrice) : « je me demande si les banques n'ont pas déjà ce type de protection ?? comme ma
  banque... »
- **Chercheur** : Claude, sous-agent `general-purpose`, recherche web (72 recherches et lectures de pages).
  0 $ (sous-agent).
- **Consigne** : les mesures contre le faux conseiller des grandes banques françaises (BPCE et Sécur'Pass,
  Crédit Agricole, BNP Paribas, Société Générale, La Banque Postale, Crédit Mutuel et CIC, LCL, BoursoBank,
  Revolut, N26), ce que Google et Samsung offrent en France, le cadre français et européen (vérification du
  bénéficiaire, authentification des appels, chiffres de l'OSMP), les applis comparables. Pour chaque fait :
  la source, sa date, une confiance.
- **Vérifié ensuite par Claude, à la source** : la page de la CNIL (15/07/2025), le billet de Google sur la
  protection pendant l'appel (03/12/2025), celui sur les appels financiers vérifiés (12/05/2026). Tout le reste
  est le rapport de l'agent, tel que rendu ci-dessous.
- **Suite** : `PROJET.md` § 9.6 (et le renvoi du § 2).

---

## Banques, Google, Samsung et État contre la fraude au faux conseiller : ce qui existe en France (recherche web, 2026-11-02)

Ce que vous saviez déjà n'est pas contredit. Une nuance s'ajoute : la protection de Google exige un appelant
hors contacts, un partage d'écran actif et une appli bancaire partenaire
(https://security.googleblog.com/2025/12/android-expands-pilot-in-call-scam-protection-financial-apps.html).
Je n'ai trouvé aucune extension à la France en 2026. Le message du Crédit Coopératif reste introuvable en
ligne : seul votre constat l'établit.

**Point clé :** la CNIL, le 15/07/2025, dit que « certaines applications bancaires » détectent l'existence et
la durée d'un appel, avec consentement, et peuvent alerter ou bloquer
(https://www.cnil.fr/fr/appel-en-cours-pendant-lutilisation-dune-application-bancaire-vos-droits). JeChange
(15/09/2026) et Selectra (06/10/2026) le redisent. Aucune de ces trois sources ne nomme de banque, et je n'ai
pas pu les identifier.

### 1. Banques

| Banque | Mesure trouvée | Détection objective de l'appel | Source, date | Confiance |
|---|---|---|---|---|
| BPCE (BP, CE, Sécur'Pass) | Bénéficiaire actif immédiatement avec Sécur'Pass, 72 h par SMS ; « Sécur'Pass ne peut pas annuler » | non trouvée | caisse-epargne.fr et banquepopulaire.fr, FAQ « ajout bénéficiaire » (2026) | moyenne |
| Crédit Agricole (SécuriPass) | Délai de 24 h avant le 1er virement à un nouveau bénéficiaire, dans certaines caisses (Ille-et-Vilaine dès le 20/05) ; chaque caisse décide | non trouvée | moneyvox.fr/…/108791, 13/05/2026 | haute pour l'Ille-et-Vilaine, liste inconnue |
| BNP Paribas (Clé Digitale) | Écran de validation avec « motif » ; alerte : se méfier du motif « Appel en cours avec le service client » | non trouvée | mabanque.bnpparibas…/alertes-securite (juin 2026 ; page refusée, lue par extraits) | moyenne |
| Société Générale (Pass Sécurité) | Conseils seulement ; alerte usurpation du 02/05/2026 | non trouvée | particuliers.sg.fr/securite/faux-conseiller-bancaire, 25/11/2025 | moyenne |
| La Banque Postale (Certicode Plus) | Détail de l'opération affiché ; conseils | non trouvée | labanquepostale.fr, page fraude téléphone (non datée) | faible |
| Crédit Mutuel / CIC (Confirmation Mobile) | Conseils seulement | non trouvée | creditmutuel.fr, page arnaques ; cmso.com 27/03/2026 | faible |
| LCL | SMS interactif (1 ou 2), RCS certifié | non trouvée | checkmarcelo.fr/blog/faux-sms-lcl, oct. 2026 (source tierce) | faible |
| BoursoBank | Bénéficiaire immédiat ; e-mail d'alerte quand le nom ne correspond pas au bénéficiaire | non trouvée | adcf.org 17/05/2026 ; colman-avocats.fr (CA Colmar 14/09/2026) | moyenne |
| **Revolut** | « Call Identification » : à l'ouverture de l'appli, détecte un appel en cours (VoIP comprise), bannière verte ou rouge. Active pour tous sur iOS, à activer sur Android. Partenaire Google des appels vérifiés | **OUI**, mais seulement à l'ouverture de son appli et seulement « est-ce Revolut qui appelle ? » | igen.fr 13/01/2026 ; thepaypers.com | haute (France non citée explicitement) |
| N26 | N'appelle jamais sans rendez-vous pris dans l'appli | non | support.n26.com/fr-fr/…/comment-n26-vous-contactera | haute |
| Sumeria (hors liste) | « Appel certifié » en 2024, puis appels uniquement dans l'appli | non (c'est l'appel sortant qui est authentifié) | banque.meilleurtaux.com, juil. 2024 | haute |

### 2. Google et Samsung

**(a) Protection pendant l'appel pour les applis financières :** pilotes au Royaume-Uni, au Brésil, en Inde
et aux États-Unis (déc. 2025). Pas la France (presse-citron.net). Rien trouvé de nouveau en 2026.

**(b) Appels financiers vérifiés :** annoncés le 12/05/2026, Android 11 et plus
(https://blog.google/security/whats-new-in-android-security-privacy-2026/).
- Partenaires : Revolut, Itaú, Nubank, puis Banco do Brasil (16/10/2026, helpnetsecurity.com).
- Aucune banque française. Aucun pays précisé. La page d'aide dit seulement « varie selon l'établissement »
  (https://support.google.com/android/answer/17080867).
- En France, cela ne concerne au mieux que les clients Revolut (**non confirmé**).
- Début décembre 2026, Android bloquera aussi le renvoi d'appel activé en arrière-plan par une appli (Pixel et
  certains appareils).

**(c) Android 16 :** impossible d'autoriser « installer des applis inconnues » ou d'activer l'accessibilité
pendant un appel (androidauthority.com, févr.–mai 2025). C'est intégré au système, donc probablement actif en
France, mais **aucune liste de pays trouvée**. Deux limites :
- l'escroc fait raccrocher, installer, puis rappelle ;
- AnyDesk vient du Play Store : ce n'est pas une installation hors Store, donc ce blocage ne l'arrête pas.

**Google, détection d'arnaques sur Pixel :** arrivée en France le 15/09/2026 pour les messages seulement, pas
pour les appels (jechange.fr).

**Samsung One UI 9** (déploiement depuis sept. 2026) : « Phishing App Risk Alert » avertit ou bloque une appli
installée dans les heures qui suivent un appel suspect, ou installée par prise de contrôle à distance
(sammyfans.com 15/06/2026). Disponibilité en France **non confirmée**. La détection des appels d'hameçonnage
vocal est réservée à des « marchés choisis », d'abord la Corée.

### 3. Cadre français et européen

- **Vérification du bénéficiaire** depuis le 09/10/2025. Inefficace quand le compte destinataire est bien au
  nom indiqué, ce qui est le cas typique du faux conseiller (green-got, mars 2026).
- **Fichier national des comptes signalés pour risque de fraude** depuis mai 2026 (banque-france.fr).
- **Authentification des appels (MAN)** : déploiement depuis oct. 2024. Signalements d'usurpation à l'Arcep :
  531 en 2023, plus de 19 000 en 2025. Depuis le 01/01/2026, un numéro mobile non authentifié appelant de
  l'étranger est seulement masqué, pas bloqué. Enquête de l'Arcep sur tous les opérateurs fin janvier 2026.
- **Opérateurs** : depuis juin 2026, ils testent la coupure des appels émis depuis des numéros de banque
  réservés à la réception. Un numéro court unique, sur le modèle du 159 britannique, est à l'étude
  (senioractu.com 19/10/2026).
- **Bouygues Telecom** jugé responsable d'un appel usurpé non bloqué (TJ Paris 15/01/2026, appel en cours ;
  quechoisir.org).
- **Rapport OSMP 2025** (publié le 07/09/2026) : fraude totale 1,24 Md€ ; fraude par manipulation 516 M€
  (+34 %), soit 41,6 % du total, dont 376 M€ par virement en banque en ligne.
- **Cybermalveillance.gouv.fr** : ne jamais valider une opération dont on n'est pas à l'origine, raccrocher et
  rappeler soi-même. Fiche de 2024 ; une mise à jour du 07/05/2026 est **non vérifiée**.
- **Règlement européen sur les services de paiement** : accord du 27/11/2025, textes publiés le 23/04/2026.
  Remboursement obligatoire en cas d'usurpation d'un employé de banque, avec plainte. Pas encore applicable
  (date **non vérifiée**).

### 4. Applis comparables

- **Dyleth** (France, gratuite, Android et iOS) : vérifie numéros inconnus et SMS sur ses serveurs, donc
  **nécessite Internet** ; écran d'appel coloré, alerte famille. Ne détecte ni code ni validation pendant un
  appel.
- **Bouygues Anti-Spam** (réseau + Hiya, 2 €/mois depuis le 05/10/2026) : signale les appels suspects, ne les
  bloque pas.
- **Orange Téléphone** et Saracroche : filtrage anti-spam gratuit.
- **VoxSafe** : écoute l'appel par IA, alerte un proche, orienté États-Unis.

Aucune appli trouvée, ni d'opérateur ni de banque, ne relie l'arrivée d'un code ou d'une validation bancaire à
un appel en cours.

### Conclusion

**Ce que personne ne couvre aujourd'hui en France, d'après mes recherches :**
- le déclenchement objectif « code SMS reçu pendant un appel », toutes banques confondues ;
- une validation de n'importe quelle banque pendant un appel : Revolut ne le fait que pour sa propre appli, à
  l'ouverture ;
- un écran STOP plus un SMS à un proche au moment même de l'action sensible ;
- l'ouverture d'AnyDesk déjà installé pendant un appel ;
- le fonctionnement hors ligne.

**Où Call Halt apporte peu :**
- l'installation hors Store et l'accessibilité pendant un appel (Android 16) ;
- l'appel usurpé d'une banque partenaire (Revolut seulement) ;
- l'erreur de nom sur le bénéficiaire (vérification européenne) ;
- les délais de 24 h de certaines caisses du Crédit Agricole.

**Risque à surveiller :** des banques françaises non identifiées détectent déjà l'appel dans leur propre appli,
avec un cadre CNIL qui l'autorise. Pour leurs clients, notre ajout serait surtout transversal (toutes banques,
SMS, AnyDesk) et familial.

**Non vérifié :** quelles banques font cette détection selon la CNIL ; la disponibilité en France des appels
vérifiés (Revolut), d'Android 16 et de One UI 9 ; le texte complet de la page BNP.
