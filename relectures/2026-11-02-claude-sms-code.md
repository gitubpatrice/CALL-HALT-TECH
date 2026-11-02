# Relecture Claude — capteur des SMS de code — 2026-11-02

> Trois sous-agents Claude, en lecture seule, sur le commit `5eb542a`, sans connaître le raisonnement de l'auteur :
> `regles-faux-positifs-auditor` (la règle des codes, obligatoire pour une règle de détection), `general-purpose`
> (justesse, comportement d'Android, promesse de confidentialité) et `android-code-quality-deep-dive`, axe
> sécurité. 0 $ (sous-agents). Menées en même temps que la relecture GPT (`2026-11-02-gpt-sms-code.md`).
> Rapports condensés fidèlement.

## La règle des codes — chasse aux faux positifs

**Verdict de l'agent : « pas prête ».** Sur 126 SMS construits mot à mot (les 23 tests, plus 103 messages tels que
les écrivent opérateurs, transporteurs, commerces, banques, médecins, proches), la première règle en jugeait **72
de travers** (corpus adverse : pas un taux réel). Deux familles dominent, d'une même cause : presque tout SMS porte
un nombre en forme de code (le « STOP au 36111 » des SMS commerciaux, une année, un code postal, un numéro à
appeler), et le français dit « code » pour bien des choses qui n'en sont pas.

- **Les SMS commerciaux** (« … avec le code KIABI30 ! Stop SMS au 36111 ») : un STOP à chaque pub reçue pendant
  un appel.
- **Les codes de livraison** (« Code de livraison à communiquer au livreur : 4821 ») : ils arrivent PENDANT
  l'appel du livreur — un STOP chaque semaine, et un SMS au proche quand D-015 existera.
- Codes de porte et de boîte à clés envoyés par un proche, code de la route, QR code, « code-barres », dates
  écrites en lettres ou avec des tirets, montants en « euros », numéros de service, fins de carte, IBAN.
- L'anglais met le qualificatif avant le mot (« discount code ») : les exclusions anglaises ne voyaient rien.
- Une seconde mention du même code (« ce code », « Code valable … ») annulait chaque exclusion (le décompte).
- **Manqués** : « Code:482913 » (les deux-points dans la garde de gauche), le Certicode de La Banque Postale,
  « Vos codes de connexion », « Code d'accès à usage unique ».

**Suite : la règle est réécrite en deux niveaux**, sur le prototype de l'agent, mesuré par lui (72 → 17 erreurs,
les 23 tests inchangés) puis complété : effacer d'abord les nombres qui ne sont jamais un code (STOP 36xxx, dates
écrites et à tirets, année après « année », numéro à appeler, fin de carte, IBAN et code banque, référence après
« n° » ou « numéro ») ; une **expression forte** (« code de validation », « usage unique », « Certicode », « pour
valider votre achat »…) fait un code ; un **« code » nu** en fait un sauf si le message dit quel autre code c'est
(livraison, promo, porte, réservation, QR…) ; **et une mise en garde ou un paiement** (« jamais », « personne »,
« paiement », « achat »…) en refait un code — « ne le communiquez jamais, même à un livreur », c'est l'arnaque au
faux livreur elle-même. Espaces et chiffres écrits en toutes lettres (`[0-9]`, `\u00A0`, `\u202F`) : le moteur
d'Android lit `\s` et `\d` en Unicode, celui des tests sur la JVM en ASCII.

**Le corpus devient le test de la règle** (`app/src/test/resources/validation-code-sms.txt`, 126 messages, lu par
`ValidationCodeTest` sur la JVM **et** par `ValidationCodeOnDeviceTest` sur un téléphone — vert sur Android 16).
Décidé contre le premier verdict, écrit en tête du fichier : RIO, PIN/PUK et messagerie vocale restent des codes
(outils de l'arnaque) ; codes de retrait aussi (le retrait sans carte en est un vrai) ; « le code c'est 4821 » d'un
proche aussi (seul l'expéditeur le distinguerait). Manques acceptés, rares : « 48 29 13 » par paires, un code en
lettres et chiffres, « 482.913 », un code suivi d'un code promo dans le même SMS.

## Relecture générale — tri

| N° | Gravité | Constat | Verdict | Suite |
|---|---|---|---|---|
| 1 | moyen | `getDefaultSmsPackage` pour CHAQUE notification du téléphone : 7 requêtes au gestionnaire de paquets et une au gestionnaire de rôles à chaque fois (lu dans `SmsApplication.java`, sources d'Android 36.1), sur le fil principal | **réel** | **corrigé** : le canal bancaire d'abord ; l'appli SMS relue une fois par minute. **Mesuré ensuite** : juste après l'installation, la réponse est « aucune » pendant ~10 s — gardée une minute, elle faisait manquer les codes (mesuré : « Code:482913 » sans STOP). Une réponse vide n'est jamais gardée |
| 2 | moyen-faible | Un vieux code reposté (fin d'un report, mise à jour de la notification) montrait « Vous venez de recevoir un code » | **réel** | **corrigé** : un message de plus de 10 min n'est pas un code reçu maintenant ; le même message reposté (même conversation, même heure) ne compte qu'une fois ; jamais un résumé de groupe (tests sur téléphone) |
| 3 | faible | Le texte de masquage lu dans la langue de l'APPLI (que l'utilisateur peut régler à part), et figé tant que l'écouteur vit | **réel** | **corrigé** : lu dans la langue du TÉLÉPHONE (`LocaleManager.systemLocales`), relu quand elle change |
| 4 | faible | La lecture de la conversation d'une autre appli, sans garde, sur le fil principal : une exception arrêterait le processus | **réel** (aussi en sécurité) | **corrigé** : erreur écartée, seule sa classe journalisée |
| 5 | faible | Plus lu que « le dernier message » : toute la conversation construite en mémoire ; texte d'une autre entrée quand la dernière n'en a pas | **réel** (aussi chez GPT, n° 1) | **corrigé** : la dernière entrée lue directement, rien d'autre si elle est sans texte ; le grand texte avant le texte court |
| 6 | faible | Les tests ne prouvaient pas la lecture d'une vraie notification | **réel** | **corrigé** : `LatestSmsOnDeviceTest` (5 tests, sur téléphone), le corpus sur téléphone |
| 7 | faible | Doc périmée : D-008 (sa raison tombe avec D-021), § 6 (`SMS_RECEIVED`), § 4 | **réel** | **corrigé** ; Samsung Messages non mesuré : écrit |

## Relecture de sécurité — condensée

Aucun constat exploitable. Le texte n'atteint aucun journal en version publiée, ni la base, ni la file, ni une
sauvegarde (coupée) ; une appli ne peut pas se faire passer pour l'appli SMS (le paquet est vérifié par le système) ;
les expressions sont linéaires (aucun retour arrière catastrophique) ; l'escroc ne choisit pas le texte du vrai SMS
de code. Deux renforcements, **faits** : une garde autour de la lecture (n° 4 ci-dessus), et `SmsMessage.Text` qui
n'affiche jamais son texte.
