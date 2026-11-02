# Relecture de code : le capteur des SMS de code d'une appli Android anti-arnaque

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. L'appli interrompt l'utilisateur (« écran
STOP ») quand une action sensible survient pendant un appel téléphonique ou dans les 15 minutes qui suivent. Ici :
un SMS portant un code de validation, celui que le faux conseiller fait lire à la victime.

## Le capteur (le diff ci-dessous, et les fichiers complets)

- `NotificationListener` (un `NotificationListenerService` déjà en place pour les demandes bancaires) : pour les
  notifications de l'appli SMS PAR DÉFAUT seulement, il lit le DERNIER message (dernier de la conversation
  `MessagingStyle`, sinon le texte), en mémoire. Le texte que met Android à la place d'un message qu'il masque
  (ressource système `redacted_notification_message`, lue à son nom) compte comme un code.
- `isValidationCode` (pur) : un mot de code ET un nombre en forme de code, moins les « code » qualifiés autrement.
- `notificationSignal` (pur) : la demande bancaire par son canal, ou le code par le texte.
- La promesse (`PRIVACY.md`) : seul le dernier message de l'appli SMS est lu, en mémoire, jamais gardé ni envoyé.

## Mesuré (2026-11-02)

Le masquage d'Android 15+ voit les codes anglais, pas les français (Google) ; Samsung ne masque rien. Une
notification arrive parfois deux fois à 0,3 s d'écart : le moteur absorbe la répétition tant que le STOP n'a pas
reçu de réponse (60 s au plus).

## Ne PAS signaler

- Le choix de lire le texte (décision du propriétaire, D-021), ni l'absence de `RECEIVE_SMS`.
- Les faux positifs de l'expression régulière elle-même : un autre relecteur s'en charge.
- Une répétition APRÈS la réponse au STOP qui montre un nouveau STOP : côté protecteur, voulu.
- Style, nommage, commentaires, formatage, suggestions d'architecture générales.

## Ce qui est demandé (au plus 6 constats)

Des défauts RÉELS, chacun avec un **scénario d'échec concret** et votre degré de certitude. En priorité : un texte
lu, gardé ou journalisé au-delà de la promesse ; un code manqué à cause de la lecture (forme de notification d'une
appli SMS, résumé de groupe, mise à jour, MMS/RCS, profil de travail) ; le coût sur le fil principal ; la
comparaison avec le texte de masquage. « Aucun défaut réel » est une réponse attendue : ne remplissez pas.
