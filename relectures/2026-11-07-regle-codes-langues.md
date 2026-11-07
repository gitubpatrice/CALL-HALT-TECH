# Relecture Claude : la règle des SMS de code élargie à d'autres langues (audit M1 à M3), avant commit — 2026-11-07

> Agent `general-purpose`, consigne : chercher les **faux négatifs** que le changement crée, c'est-à-dire les vrais SMS de
> code qu'il fait manquer. Méthode : un harnais compile le fichier du dépôt sans y toucher, la version de `main` (tirée
> de `git show`) et deux variantes de moteur, `(?U)` pour un `\b` Unicode proche d'ICU et un `\b` ASCII, celui du JDK 19
> et suivants. Il rend le verdict des quatre sur 126 SMS construits : 96 faux négatifs candidats, 30 messages ordinaires
> pour mesurer ce que coûtent les correctifs. 0 $ (sous-agent).
>
> Le harnais et les cas ne sont pas versionnés : ils restent dans le dossier de travail de la session. Ce qui en reste
> au dépôt, ce sont les 22 lignes `W40` à `W68` du corpus.

## Verdict

**La première version du changement créait des faux négatifs.** 49 SMS de code passaient de C (code) à L (pas un code).
Aucun de ces écarts ne dépendait du moteur. Les trois familles :

| Famille | Gravité | Exemples (tous C avant, L après) | Correctif |
|---|---|---|---|
| **M1, « personne » resserré** : un « code » nu, un veto de livraison et une mise en garde sans « jamais ». C'est l'arnaque au faux livreur | élevée | « Ne le communiquez à aucune personne » ; « Personne, pas même un livreur, ne doit vous demander ce code » ; « à une personne se présentant comme livreur » ; « à » décomposé (a + U+0300) | quatre formes indépendantes du moteur (« à / avec [une tierce] personne », « aucune [autre] personne », « personne ne / n' / d'autre », « personne se présentant comme / se disant / se faisant passer », « Personne… ne » en tête de phrase) |
| **M3, veto en cinq langues, mises en garde en deux** | moyenne | « Ihr Code: 482913. Wir schicken niemals Boten an Ihre Tür. » ; « Non darlo a nessuno » ; « Zahlung … TOR-APOTHEKE » | mises en garde et paiements en allemand, italien, espagnol (pas « nie », faux positif sur « vergiss ihn nie » ; pas « mai », le mois français) |
| | moyenne | « keypad » du téléphone et du distributeur (jumeau de M12) ; « buzón de voz » (décision O04) ; « Tor » nu (Tor Browser, un prénom, Tor Vergata) ; « chiavi di accesso » ; « Schlüsseln » | « keypad » sauf après ATM ou phone ; « buzón » sauf « de voz » ; « Tor » après un article ou dans « Torcode » ; plus de « chiavi » seul ; « Schlüssel » seulement en boîte à clés |
| **M2, « No. » et « Nr. » suivis de deux mots** effaçaient le code lui-même | moyenne | « Your PIN No. is 4821 » ; « Mobile No. Verification OTP: 482913 » (l'expression forte ne voit plus de nombre) ; « Reply YES or NO. Code: 482913 » | « No. » et « Nr. » seulement juste devant leur nombre, jamais après « PIN », « code », « OTP » |
| | faible | « Call 0800 123 456. 482913 is your verification code. » (le numéro franchissait « . ») ; « Anwendung 4821 », « pending 4821 » | un séparateur doit être suivi d'un chiffre ; « ending » et « endung » en début de mot |

**Le commentaire du code sur le JVM était faux.** Il disait le `\b` du JVM des tests « ASCII ». Mesuré sur le JDK 17.0.18
(poste local, et Temurin 17 en CI) : `\bà` après une espace est vrai, `r\b` devant « ö » faux. Le JDK 17 suit donc les
lettres Unicode, comme Android ; le `\b` ASCII n'arrive qu'au JDK 19. Le choix « pas après une lettre » reste le bon,
puisqu'il ne dépend d'aucun moteur. Seule la raison écrite était fausse.

**Le décompte annoncé était faux** : 33 lignes `W`, pas 34 (159 SMS, pas 160).

## Tri

| Constat | Suite |
|---|---|
| Les trois familles | **corrigées** ; la règle reprise rend, sur les 126 SMS du harnais, le verdict de la version de l'agent, sur les trois moteurs (vérifié par une seconde passe du harnais sur le fichier du dépôt, avant l'ajout de « Kartenendung » ci-dessous, seul écart voulu) |
| « Kartenendung 4821 » devenait un code (le bord gauche ajouté pour « Anwendung ») | **corrigé** en plus : `(?:karten)?endung` ; ligne `W66` |
| Le commentaire sur le `\b` du JVM | **corrigé** : le KDoc dit que le sens d'un bord de mot près d'une lettre non ASCII a changé au JDK 19 |
| Le décompte | **corrigé** dans D-021 (55 lignes `W`, 181 SMS) |
| B7, « Ring Alarm » (« Use it to sign in to Ring Alarm ») | **accepté** : faible, pas de correctif sans perte |
| C4, mois abrégés suivis d'un code (« Votre code : sept 4821 ») | **accepté** : formulations artificielles, aucune vraie date suivie d'un code trouvée |
| P07, P16 (« Toute personne qui vous le demande… est un escroc », « Si une personne vous le demande… raccrochez ») restent manqués | **accepté** : les rattraper ferait un code de « Toute personne munie de ce code pourra retirer votre colis » |
| `\b[ée]tage` échouerait sur un JDK 19+ (hors de l'axe) | **noté** : passer la chaîne de build au JDK 21 ferait échouer `ValidationCodeTest` alors qu'Android a raison ; `ValidationCodeOnDeviceTest` tranche |

## Ce que les correctifs coûtent, mesuré et accepté

Dans le doute, c'est un code : chacun de ces SMS ordinaires devient un STOP s'il arrive pendant un appel.

- Un code de porte allemand ou espagnol suivi d'une mise en garde ou d'un paiement : « Der Code für die Haustür ist
  4821. Gib ihn niemandem weiter. », « El PIN de la caja de llaves es 4821. El pago de la estancia se hace a la
  llegada. » C'est la règle déjà retenue en français (« Paiement de 1234 € accepté. Code porte 4821. »).
- « Colis au relais : vous pouvez confier le code 4821 à une tierce personne. »
- « Il PIN per le chiavi è 4821. », « Der Code für den Schlüssel ist 4821. », « Ihre PIN wurde geändert. Nr. der Karte:
  4821 ».

## Vérifications

- `ValidationCodeTest` (JVM, JDK 17) : 182 tests, 0 échec (181 SMS + le test de comptage).
- `ValidationCodeOnDeviceTest` sur l'émulateur API 36 (moteur ICU d'Android) : les 181 SMS rendent leur verdict.
- Gate local : `assembleDebug`, `testDebugUnitTest` (582 tests), `lintDebug`, `detekt` verts.
