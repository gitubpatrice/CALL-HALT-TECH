# Relecture de code : « Désinstaller [appli] » sur l'écran STOP d'une appli de prise en main installée (D-039)

Vous êtes un ingénieur Kotlin/Android senior. Répondez en français. L'appli interrompt l'utilisateur (« écran STOP »)
quand une action sensible survient pendant un appel téléphonique ou dans les 15 minutes qui suivent. L'une d'elles :
une appli de prise en main à distance (AnyDesk, RustDesk…, une liste fixe) vient d'être installée — ce qu'un faux
conseiller fait faire à sa victime. Un moteur pur (`DetectionEngine`) rend des décisions ; la plateforme les exécute.

## Le défaut corrigé (FN-3)

Ce STOP disait « Raccrochez et éteignez votre téléphone » et rien d'autre : l'appli restait installée, et une session
ouverte plus tard, hors de la fenêtre de l'appel, ne montrait rien.

## Le changement (le diff ci-dessous)

- Le STOP de cette installation offre « Désinstaller RustDesk » (le nom de notre liste) en premier ; pendant un appel
  que l'appli peut couper, « Raccrocher et désinstaller RustDesk » à la place de « Raccrocher » : l'appel est coupé
  d'abord (règle D-011 : jamais une autre page par-dessus un appel en cours) ; pendant un appel qu'elle ne peut pas
  couper (VoIP, sans permission), aucun bouton. Le bouton ouvre la page de l'appli dans les réglages du système, où
  est « Désinstaller ». Même motif que « Retirer l'accès » pour l'accessibilité (`REMOVE_ACCESS`, déjà en place).
- Le paquet circule : `Decision.ShowStop.source` → le lanceur (gardé pour ce déclencheur et une appli de la liste
  seulement) → `StopRequest` (intent) → `StopSession` (remplacement par un autre STOP, recréation) → les boutons.
- L'avertissement en notification (déjà posté hors appel) part aussi avec ce STOP, silencieux, et reste après l'appel ;
  il est retiré quand l'appli de la liste est désinstallée (`ACTION_PACKAGE_REMOVED`, pas une mise à jour).
- Après une relecture Claude : l'appli en marche depuis (un STOP de même poids qui prend l'écran) garde le bouton ; une
  seconde appli de la liste installée pendant que le STOP de la première est affiché garde son avertissement et sa
  ligne de journal.

## Mesuré (2026-11-09, émulateur Android 16, RustDesk)

Pendant un appel : les deux boutons, l'avertissement posté `SILENT` ; l'appui coupe l'appel et ouvre la page de
RustDesk ; désinstallée de là, l'avertissement part. Juste après l'appel : « Désinstaller RustDesk », puis « Appeler ma
banque ».

## Ne PAS signaler (limites écrites dans D-039, ou choix faits)

- Un STOP « appli en marche » seul (rien d'installé pendant cet appel) n'offre pas de désinstaller (choix du produit) ;
  l'accessibilité, plus grave, prend l'écran et le bouton ; un STOP qui revient (retour unique d'un STOP quitté) ne
  connaît plus l'appli ; une notification STOP remplacée par un
  autre STOP avant d'être touchée la perd ; une appli désinstallée pendant que le processus est mort garde son
  avertissement ; le conseil « éteignez » gardé.
- Ne pas désinstaller directement (`REQUEST_DELETE_PACKAGES`, écarté : la page des réglages, choix du produit).
- Style, nommage, commentaires, formatage, architecture générale.

## Ce qui est demandé (au plus 5 constats)

Des défauts RÉELS, chacun avec un **scénario concret** et votre degré de certitude : un bouton qui viserait la mauvaise
appli ou apparaîtrait pour un autre déclencheur ; une page ouverte par-dessus un appel non coupé ; l'appli perdue ou
gardée à tort par l'écran ; l'avertissement posté en double, avec du son par-dessus le STOP, ou retiré à tort ; un test
qui resterait vert sur une implémentation fausse. « Aucun défaut réel » est une réponse attendue : ne remplissez pas.
