# Relecture de code : la règle qui reconnaît un SMS de code de validation, élargie à d'autres langues

Vous êtes un ingénieur Kotlin/Android senior, attentif aux expressions régulières. Répondez en français. L'appli
anti-arnaque affiche un écran STOP si, pendant un appel, arrive un SMS de code de validation : celui qu'un faux conseiller
fait lire à sa victime. `isValidationCode` décide en mémoire : des nombres qui ne sont jamais un code sont d'abord
effacés (`NEVER_A_CODE`) ; il faut ensuite un nombre en forme de code ; une expression forte suffit, un mot « code » nu
suffit sauf si le message dit une autre sorte de code (`OTHER_KIND_OF_CODE`, un veto), sauf si une mise en garde ou un
paiement le refait (`WARNING_OR_PAYMENT`). Un faux code coûte un STOP pendant l'appel ; un code manqué, le code lu à
l'escroc : dans le doute, c'est un code.

## Le changement (le diff ci-dessous)

Un audit a montré des faux positifs hors du français : un avis de carte allemand, un code de boîte à clés italien. Les
nombres jamais codes et les veto sont lus en anglais, allemand, italien, espagnol ; « personne » ne refait un code que
dans une mise en garde. Une première version perdait 49 vrais codes (mesuré par une relecture) ; celle-ci les
rattrape : mises en garde et paiements dans les trois langues, « Tor » après un article, « No. »/« Nr. » juste devant
leur nombre, pas après « PIN », etc. Le corpus de 181 SMS décidés (fichier joint) passe sur le JVM (JDK 17) et sur le
moteur ICU d'Android (émulateur API 36). Les coûts acceptés sont écrits dans le corpus et dans le KDoc.

## Ne PAS signaler

- Qu'un vrai code allemand, italien ou espagnol sans « code » ni « PIN » (« Bestätigungscode », « codice ») n'est pas
  vu : limite connue, écrite sur l'accueil de ces langues.
- Les faux positifs déjà acceptés et listés (un code de porte suivi d'une mise en garde redevient un code).
- Style, nommage, longueur de l'expression, performance (un SMS à la fois, en mémoire).

## Ce qui est demandé (au plus 4 constats)

Des défauts RÉELS, chacun avec **le SMS exact** qui le montre et le verdict attendu, et votre degré de certitude :
surtout un vrai SMS de code, français ou anglais, que le changement fait manquer (un faux négatif) ; puis une expression
qui se comporte autrement sur ICU (Android) et sur le JVM ; puis un faux positif fréquent nouveau. « Aucun défaut réel »
est une réponse attendue : ne remplissez pas.
