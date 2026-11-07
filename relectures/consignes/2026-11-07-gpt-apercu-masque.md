# Relecture de code : dire sur l'accueil que l'appli SMS cache ses messages, dans une appli anti-arnaque

Vous êtes un ingénieur Android senior. Répondez en français. L'appli lit la notification de l'appli SMS par défaut pour
y reconnaître un code de validation pendant un appel, et affiche alors un écran STOP. Une appli SMS réglée pour masquer
ses aperçus (SMS Tech écrit « Nouveau message » à la place de chaque message) rend tout code invisible, en silence.

## Le changement (le diff ci-dessous)

`SmsPreview` compare le texte lu de chaque message de l'appli SMS par défaut au texte fixe connu de cette appli (une
table par paquet, cinq langues), garde un oui ou un non (SharedPreferences, jamais le texte), et l'accueil affiche
alors, en gras : « Votre appli SMS cache le texte des messages dans ses notifications : un code reçu par SMS n'est pas
vu. Dans ses réglages, affichez l'aperçu des messages. » Un autre texte efface le oui. Mesuré sur un émulateur Android
16 avec SMS Tech : masqué → l'avertissement ; rétabli → parti.

## Ne PAS signaler

- Que le message masqué n'est pas lu comme un code (décidé : « un code n'est pas vu ») ; que Samsung Messages et Google
  Messages ne sont pas dans la table (limite écrite) ; style, nommage, formatage.

## Ce qui est demandé (au plus 4 constats)

Des défauts RÉELS, chacun avec un **scénario concret** et votre degré de certitude : l'avertissement affiché à tort ou
absent à tort, une fuite du texte d'un message (journal, stockage), une concurrence entre le service et l'écran, un
test qui resterait vert sur une implémentation fausse. « Aucun défaut réel » est une réponse attendue : ne remplissez
pas.
