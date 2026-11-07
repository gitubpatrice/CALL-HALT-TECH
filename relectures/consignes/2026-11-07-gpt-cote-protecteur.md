# Relecture de code : échouer du côté protecteur, dans une appli Android anti-arnaque

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. L'appli interrompt l'utilisateur (« écran STOP ») quand
une action sensible survient pendant un appel ou dans les 15 minutes qui suivent : un code reçu par SMS (lu dans la
notification de l'appli SMS par défaut), une demande de validation de la banque, etc. Une appli de protection doit échouer
du côté où l'utilisateur est prévenu, jamais du côté du silence.

## Le changement (le diff ci-dessous)

Quatre constats d'un audit de publication :
1. Une notification de l'appli SMS par défaut qu'on ne peut pas lire comptait comme « aucun code » ; elle compte
   maintenant comme un message masqué, donc comme un code (`latestSmsOrMasked`).
2. `SettingsRepository` relançait toute erreur autre qu'une `IOException` jusqu'au moteur (le signal, donc le STOP,
   perdu) ; il retombe maintenant sur les réglages par défaut, protecteurs, pour toute erreur.
3. `GuardianRepository` et `GuardianChoice` : une erreur autre que de disque laissait l'écran du proche « occupé », ses
   boutons éteints ; elle est maintenant dite, l'annulation passant toujours.
4. `ActivityStopLauncher.started` ne rattrapait que `ActivityNotFoundException` : toute autre exception de
   `startActivity` sortait avant la notification de repli et le SMS au proche ; il rattrape maintenant toute
   `RuntimeException` et rend `false`.

## Ne PAS signaler

- Qu'une erreur de programmation sur les réglages est maintenant journalisée seulement (pas d'arbre de journal en version
  publiée) : c'est le contrat voulu (« ne lève jamais »).
- Qu'une notification illisible reposée en boucle refait un STOP une fois le précédent répondu : écrit (D-021).
- Que le lanceur n'a pas de test : à venir.
- Style, nommage, commentaires, formatage, architecture générale.

## Ce qui est demandé (au plus 5 constats)

Des défauts RÉELS, chacun avec un **scénario d'échec concret** et votre degré de certitude : un chemin qui échoue encore
en silence, une annulation avalée, un faux STOP que ce changement rend fréquent, un test qui resterait vert sur une
implémentation fausse. « Aucun défaut réel » est une réponse attendue : ne remplissez pas.
