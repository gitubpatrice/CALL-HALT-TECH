# Relecture de code : le STOP d'une rafale de SMS, et l'appli SMS trop pleine dite sur l'accueil (D-040)

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. L'appli interrompt l'utilisateur (« écran STOP »)
quand une action sensible survient pendant un appel téléphonique ou dans les 15 minutes qui suivent ; à chaque STOP, un
proche choisi reçoit un SMS. Elle lit les notifications par un `NotificationListenerService` (callbacks sur le fil
principal), dont celles de l'appli SMS par défaut, pour y reconnaître un code de validation.

## Le défaut corrigé (N-3, mesuré)

Android n'affiche pas plus de 50 notifications d'une même appli. Un escroc fait envoyer des dizaines de SMS pendant
l'appel : l'appli SMS atteint ce plafond, et le SMS de code arrive sans notification — aucun STOP.

## Le changement (le diff ci-dessous)

- `SmsAppNotices` compte les notifications de l'appli SMS par défaut affichées (clé ajoutée à la publication, retirée
  à la suppression, recomptée à la connexion de l'écouteur). Une rafale : ce nombre franchit 30, puis 45, en montant,
  avec au moins 5 notifications arrivées dans les 10 dernières minutes. Elle devient `SensitiveActionKind.SMS_BURST` :
  pendant un appel ou juste après, un STOP « Vous recevez beaucoup de SMS d'un coup : un code est peut-être caché
  parmi eux. » ; hors appel, rien.
- Dès 45, l'accueil dit « Votre appli SMS a trop de messages non lus : un code reçu par SMS pourrait ne pas
  s'afficher. Ouvrez-la et lisez-les. » (`ProtectionViewModel`, `StateFlow` du compteur).
- Une première version (10 conversations différentes en 2 minutes) a été refaite : SMS Tech fait une notification par
  message, et elle déclenchait sur un seul correspondant bavard.

## Mesuré (2026-11-09, émulateur Android 16, Google Messages)

Pendant un appel, 35 SMS de 35 numéros : le STOP au franchissement de 30, le SMS au proche envoyé ; juste après
l'appel, 12 de plus : le STOP au franchissement de 45 ; l'accueil dit l'appli trop pleine à 48, et ne le dit plus une
fois les notifications balayées.

## Ne PAS signaler (limites écrites dans D-040, ou choix faits)

- Une appli SMS déjà à son plafond avant l'appel ; un flot plus lent qu'un SMS toutes les deux minutes ; une appli qui
  mettrait tout dans une seule notification ; les seuils publics ; un STOP de trop, rare, pour un téléphone qui garde
  des dizaines de messages non lus.
- Les mots (choisis par le propriétaire du produit) ; style, nommage, formatage.

## Ce qui est demandé (au plus 5 constats)

Des défauts RÉELS, chacun avec un **scénario concret** et votre degré de certitude : un compte faux (une suppression
manquée, une notification d'une autre appli comptée, un changement d'appli SMS par défaut, la reconnexion de
l'écouteur, l'horloge), un franchissement manqué ou compté deux fois, un STOP qui manque pendant une vraie rafale, un
avertissement de l'accueil faux ou qui reste, une concurrence entre le fil principal et l'écran d'accueil, une fuite de
ce qui est gardé, un test qui resterait vert sur une implémentation fausse. « Aucun défaut réel » est une réponse
attendue : ne remplissez pas.
