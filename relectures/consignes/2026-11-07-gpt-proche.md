# Relecture de code : le SMS au proche, dans une appli Android anti-arnaque

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. L'appli interrompt l'utilisateur (« écran STOP »)
quand une action sensible survient pendant un appel téléphonique ou dans les 15 minutes qui suivent (un code reçu par
SMS, une demande de validation de la banque, une appli de prise en main installée…). Ici : elle prévient par SMS un
proche que l'utilisateur a choisi.

## Le changement (le diff ci-dessous)

- Le moteur décidait déjà `NotifyGuardian` ; l'événement porte maintenant `duringCall` (pendant l'appel ou juste après).
- `AndroidDecisionExecutor` : le STOP d'abord, puis le SMS ; un SMS qui échoue ne lève rien (sinon le STOP serait
  journalisé « non affiché »). Envoyé même si le STOP n'a atteint l'utilisateur d'aucune façon (voulu : le proche est
  alors la seule protection qui reste).
- `SmsGuardianNotifier` : lit le proche du moment, applique `GuardianLimiter` (un SMS par sorte de STOP — par
  déclencheur — en 15 minutes ; chaque « Continuer » en envoie un ; 20 par 24 h ; en mémoire ; un SMS qui échoue rend sa
  place), compose le texte (jamais le code ni le contenu d'un message), l'envoie. Un « Continuer » touché hors de la
  fenêtre d'un appel (l'écran laissé sur un téléphone en veille) n'envoie rien.
- Le numéro : « (0) » après un indicatif retiré, marques invisibles et tirets typographiques acceptés, marques qui
  inverseraient l'affichage des chiffres refusées.
- `SmsTextSender` : `SmsManager.sendMultipartTextMessage`, un `PendingIntent` immuable par SMS vers un récepteur non
  exporté (`SmsSentReceiver`) ; le résultat de chaque partie est agrégé.
- Le choix du proche (`GuardianChoice`, sans Android ; `GuardianScreen`) : un contact choisi (`ACTION_PICK` sur
  `Phone.CONTENT_URI`, lu par l'autorisation ponctuelle, seulement si l'autorité est `com.android.contacts`) ou un numéro
  tapé (8 à 15 chiffres, « + » initial ; les numéros courts surtaxés refusés) ; `SEND_SMS` demandée à ce moment ; le
  proche est gardé, puis un premier SMS lui dit qu'il sera prévenu ; remplacé ou retiré, un dernier SMS le lui dit (sauf
  le même numéro écrit autrement : les 9 derniers chiffres).
- « Le proche est prévenu » = un numéro valable enregistré (`SettingsMapping.guardian`).

## Mesuré (2026-11-07)

Émulateurs Android 14, 15, 16, et un Galaxy S9 (Android 10, sans SIM) : chaque SMS dans la boîte d'envoi, le limiteur,
l'autorisation retirée (STOP sans SMS), le contact choisi, l'échec sans SIM (« n'est pas parti »). Installée depuis un
fichier, Android 15 et 16 refusent `SEND_SMS` jusqu'à « Autoriser les paramètres restreints » ; Android 14, non.

## Ne PAS signaler

- Les choix de produit : les textes (formulation validée), un SMS par sorte de STOP et un par « Continuer », le premier
  et le dernier SMS, l'envoi même quand le STOP n'a pas pu s'afficher.
- Les constats déjà traités par deux relectures Claude : les 9 derniers chiffres comme « même personne », un proche déjà
  en ligne qui reçoit « pendant un appel », les numéros à 6 chiffres de Nouvelle-Calédonie (l'indicatif à taper).
- Les limites écrites : deux SIM sans SIM par défaut ; un retrait après le retrait de l'autorisation n'envoie pas
  d'adieu ; le limiteur en mémoire (un processus mort : un SMS de plus) ; « Renvoyer » après 60 s peut doubler le
  premier SMS ; le mode « proche protégé » (réglages sous code) n'existe pas encore.
- Style, nommage, commentaires, formatage, suggestions d'architecture générales.

## Ce qui est demandé (au plus 6 constats)

Des défauts RÉELS, chacun avec un **scénario d'échec concret** et votre degré de certitude. En priorité : un SMS envoyé
à tort (au mauvais numéro, deux fois, à un numéro qu'un escroc aurait fait saisir) ; un SMS qui devait partir et ne
part pas (le limiteur, l'ordre des opérations, un fil d'exécution, la mort du processus) ; une fuite d'un code ou d'un
contenu de message ; ce qu'une autre appli pourrait faire par le récepteur, le `PendingIntent` ou le choix du contact ;
un plantage. « Aucun défaut réel » est une réponse attendue : ne remplissez pas.
