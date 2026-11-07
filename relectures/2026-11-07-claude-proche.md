# Relectures Claude — le SMS au proche (D-034), 2026-11-07, avant commit

Deux agents, en parallèle, sur l'arbre de travail de `feat/guardian-sms` : `android-code-quality-deep-dive` (sécurité et
justesse) et `regles-faux-positifs-auditor` (la règle du numéro, le limiteur, les textes). Coût : 0 $ (sous-agents).

## 1. `android-code-quality-deep-dive` — sécurité et justesse

Rapport condensé fidèlement ; « vérifié, aucun défaut » : le récepteur non exporté et le `PendingIntent` explicite et
immuable (aucune appli ne peut forger un résultat), le contrôle d'autorité du contact choisi, le numéro revalidé à chaque
lecture, aucun code ni contenu de message dans un SMS ou un journal, les horloges du limiteur, les `IOException`, un
téléphone sans téléphonie, la garde CI (`SEND_SMS` seule), la concurrence (file unique, méthodes synchronisées), l'ordre
STOP puis SMS.

| # | Constat (gravité, certitude) | Tri | Suite |
|---|---|---|---|
| 1 | Un SMS « l'écran STOP vient de s'afficher » part même quand le STOP n'a atteint l'utilisateur d'aucune façon ; le limiteur retient ensuite celui du STOP réaffiché (élevé, confirmé) | **Voulu, écrit** | Si ni l'écran ni sa notification ne passent (écran bloqué ET notifications coupées), l'utilisateur n'a rien vu : le proche est la seule protection qui reste, et le code ou la demande est bien arrivé pendant l'appel. Ne pas l'envoyer, comme proposé, le priverait de l'alerte au pire moment. Le texte en dit alors un peu plus que ce qui est arrivé ; le SMS retenu ensuite dirait la même chose. Écrit dans `AndroidDecisionExecutor` et D-034 |
| 2 | Le limiteur compte un SMS dès qu'il est remis au téléphone : sans réseau, la même alerte une minute après, le réseau revenu, est retenue 15 min (moyen, probable) | **Réel, corrigé** | `GuardianLimiter.release` : un SMS dont le téléphone dit qu'il n'est pas parti ne compte plus ; sans réponse encore, il compte (attendue 15 min, la durée de la retenue, depuis la relecture GPT, n° 2). Tests `GuardianLimiterTest`, `SmsGuardianNotifierTest` ; **contrôle négatif** : sans l'appel à `release`, le test échoue |
| 3 | Le même proche ressaisi autrement (`0612345678`, `+33612345678`) reçoit « ne vous préviendra plus » puis le premier SMS (faible, confirmé) | **Réel, corrigé** | `sameSubscriber` : les 9 derniers chiffres. Tests `GuardianNumberTest`, `GuardianChoiceTest` |
| 4 | La lecture du contact n'attrape que `SecurityException` et `IllegalArgumentException` : une autre exception du fournisseur de contacts fermerait l'écran (faible, probable) | **Réel, corrigé** | `RuntimeException` attrapée (requête bloquante : aucune annulation à avaler) |
| 5 | « Renvoyer » après les 60 s d'attente peut doubler le premier SMS ; une entrée en attente sans réponse reste en mémoire (faible, probable) | **Écrit** | Limite de D-034 ; quelques octets par SMS sans réponse |
| 6 | Le premier SMS dit « si l'écran STOP s'affiche pendant un appel » ; l'appli écrit aussi juste après et au « Continuer » (faible, confirmé) | **À Patrice → changé** | Patrice, 2026-11-07 : « … si l'écran STOP s'affiche, pendant un appel ou juste après, signe possible d'une arnaque » (« Continuer » ne peut suivre qu'un STOP) |

## 2. `regles-faux-positifs-auditor` — la règle du numéro, le limiteur, les textes

L'agent a mesuré sur les classes compilées réelles (un harnais Java jetable, hors du dépôt), y compris les correctifs de
la relecture 1 apparus pendant son travail. Couvert et écarté : les traductions disent la même chose que le français
(les quatre gabarits, les six descriptions, le premier SMS et l'adieu ; « Continuer » de chaque langue est le bouton de
l'écran) ; les numéros français, des DOM, de Polynésie, de Monaco, de Belgique, d'Allemagne, d'Italie, d'Espagne, du
Luxembourg et du Royaume-Uni acceptés ; les numéros courts surtaxés refusés partout (France 5 chiffres, Belgique 4,
Suisse 3 à 5, Allemagne, Royaume-Uni, Luxembourg 5, Espagne 5 à 6) ; « Raccrocher », « Appeler ma banque » et l'écran
quitté n'envoient rien ; la démonstration n'envoie rien.

| # | Constat (gravité, certitude) | Tri | Suite |
|---|---|---|---|
| 1 | Un « Continuer » touché des heures après le STOP (l'écran reste sur un téléphone en veille) envoie « juste après un appel. Appelez sans attendre » (moyen, confirmé) | **Réel, corrigé** | Le moteur ne décide le SMS d'un « Continuer » que dans la fenêtre d'un appel, comme le STOP ; la réponse reste au journal. Test moteur, **contrôle négatif** fait |
| 2 | « +49 (0)171… », « +33 (0)6… » gardés avec leur 0 : un numéro sans doute injoignable (moyen) | **Réel, corrigé** | Le « (0) » juste après un indicatif est retiré (la relecture GPT l'a fait ancrer là). Tests |
| 3 | Pendant une arnaque à validations successives, le second « Continuer » est retenu 15 min : la fille rassurée (« c'est la banque ») n'apprend pas que les validations continuent (moyen-bas) | **À Patrice → changé** | Patrice, 2026-11-07 : « chaque « Continuer » ». Chaque « Continuer » envoie un SMS ; les STOP répétés restent à un par sorte et par quart d'heure ; le plafond de 20 par jour tient. Mesuré sur l'émulateur Android 16 : deux demandes de la banque, deux « Continuer » → STOP (SMS), « Continuer » (SMS), STOP (retenu), « Continuer » (SMS) |
| 4 | Un numéro copié avec des marques invisibles (WhatsApp), ou écrit avec des tirets typographiques, est refusé (bas, confirmé) | **Réel, corrigé** | Marques invisibles ignorées, tirets typographiques pris pour des séparateurs ; les marques qui afficheraient les chiffres dans un autre ordre restent refusées. Tests |
| 5 | Les numéros à 6 chiffres (Nouvelle-Calédonie, Wallis, Saint-Pierre) sont refusés sans leur indicatif (bas, confirmé) | **Limite écrite** | Tapés avec l'indicatif (+687…), ils passent ; abaisser le plancher laisserait passer des numéros surtaxés à 6 chiffres (« 795123 », Espagne, refusé et testé) |
| 6 | Le proche déjà en ligne avec la personne reçoit « pendant un appel… Appelez sans attendre » (bas, confirmé) | **Limite écrite** | L'interlocuteur n'est pas lu ; et l'escroc qui rappelle pour dire « appuyez sur Continuer » est le même événement |
| 7 | `sameSubscriber` ne reconnaît pas un numéro à 8 chiffres écrit de deux façons (bas, confirmé) | **Réel, corrigé** | Comparés sur autant de chiffres que le plus court en a, 9 au plus. Tests |
| 8 | « Call Halt Tech, … Call as soon as you can » peut se lire « appelez Halt Tech » ; un proche chercherait ce numéro sur Internet, le chemin du faux support (bas, probable) | **À Patrice → changé** | Patrice, 2026-11-07 : « Message automatique de l'appli Call Halt Tech : … Appelez ce numéro dès que possible. », dans les 5 langues. Avec ses mots, le premier SMS dit aussi « pendant un appel ou juste après » (relecture 1, n° 6) |

**Hors axe, pour mémoire** : les alertes françaises partent en UCS-2 (« peut-être », guillemets, insécables) : 3 à 4 SMS
facturés par alerte là où les SMS se paient ; l'anglais tient en 2. Le plafond de 20 par jour en tient compte.

## 3. `android-code-quality-deep-dive` — sécurité, sur ce qui a changé après les relectures 1, 2 et GPT

Demandée par le rappel du poste (fichiers sensibles modifiés après la première relecture), sur le commit `9a9ea73`.
Vérifié, aucun défaut : le contrôle des chiffres strictement ASCII (pleine chasse, arabo-indiques refusés), les marques
de réordonnancement refusées avant tout, `"$1"` (référence de groupe, pas un gabarit Kotlin), le plancher de 8 chiffres
après toutes les transformations, aucune expression coûteuse ; le limiteur (synchronisé, `StopOverridden` toujours
soumis au plafond) ; le moteur ; les gabarits `%1$s` dans les 5 langues, rien de sensible dans les textes ; le manifeste.

| # | Constat (gravité, certitude) | Tri | Suite |
|---|---|---|---|
| 1 | « +33-(0)6-12… », « 0033.(0)6… » : le « (0) » n'est pas retiré (les tirets et points sont encore là quand il est cherché) ; « +330612345678 » passe, injoignable (moyen, confirmé) | **Réel, corrigé** | Séparateurs retirés d'abord, puis le « (0) », puis les parenthèses. Tests (tiret, point, barre oblique) |
| 2 | « Ne plus prévenir personne » reste actif pendant l'envoi du premier SMS : retiré à ce moment, l'écran finit sur « SMS envoyé » d'un proche retiré ; « Annuler » rouvre un second choix concurrent (moyen, confirmé) | **Réel, corrigé** | Pendant l'envoi, le bouton est inactif, « Supprimer » et « Annuler » ne font rien ; la fin de l'envoi n'écrit son résultat que si l'écran l'attend encore. Test, **contrôle négatif** fait |
