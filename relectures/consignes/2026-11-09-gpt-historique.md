# Relecture de code : l'historique des alertes, lisible et partageable (D-041)

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. L'appli anti-arnaque montre un « écran STOP »
pendant un appel téléphonique ou juste après ; chaque STOP, sa réponse, son éventuel « non affiché » et les
avertissements sont écrits dans un journal Room gardé 14 mois — la preuve qu'un appel était en cours, à remettre à la
banque pour contester une opération.

## Le changement (le diff ci-dessous)

- `domain/history/Alert.kt` : `alerts()` regroupe les lignes du journal (dans l'ordre d'écriture) en alertes, la plus
  récente en haut ; chaque réponse et chaque « non affiché » vont au dernier STOP de leur déclencheur ; « non affiché »
  ne vaut que pour un écran encore sans réponse (un STOP quitté revient une fois ; son retour peut être bloqué).
- `data/store/RoomJournal.kt` et `DetectorDao.journal()` : le journal lu par `id`, jamais par l'heure civile.
- `ui/history/` : l'écran « Historique des alertes » (depuis l'accueil), les mots (`HistoryText`, une seule fonction
  pour l'écran et pour le texte partagé, la date avec son décalage UTC), « Partager » = tout l'historique en texte par
  `ACTION_SEND` et le menu de partage d'Android.
- Décisions du propriétaire : une alerte et sa réponse par ligne, tout l'historique partagé, un bouton sur l'accueil.

## Mesuré (2026-11-09, émulateur Android 16)

L'écran liste les alertes du jour avec leurs réponses (« Quitté sans réponse, puis Raccrocher » pour un STOP revenu) ;
« Partager » ouvre le menu de partage, le texte en aperçu ; la date « 9 nov. 2026, 10:07 (UTC) ».

## Ne PAS signaler (limites écrites dans D-041, ou choix faits)

- Une ligne écrite par une autre version que celle-ci ne lit pas ; un historique de plus d'environ 1 Mo (le partage
  refusé, rattrapé) ; pas de clé stable aux éléments de la liste ; pas d'effacement de l'historique dans l'appli
  (voulu : un escroc ne doit pas pouvoir faire effacer la preuve) ; les mots (choisis par le propriétaire).
- Style, nommage, formatage, architecture générale.

## Ce qui est demandé (au plus 5 constats)

Des défauts RÉELS, chacun avec un **scénario concret** et votre degré de certitude : une alerte fausse dans
l'historique (une réponse au mauvais STOP, un STOP perdu, une date fausse), ce que le texte partagé dit de trop ou de
faux, un plantage, un état de l'écran faux, une fuite, un test qui resterait vert sur une implémentation fausse.
« Aucun défaut réel » est une réponse attendue : ne remplissez pas.
