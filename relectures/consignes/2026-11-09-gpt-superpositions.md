# Relecture de code : les superpositions masquées sur l'écran STOP (C-2), un texte corrigé (S-2), le README (E-2)

Vous êtes un ingénieur Android senior, axe sécurité. Répondez en français. L'appli anti-arnaque montre un « écran STOP »
(une Activity plein écran, `FLAG_SECURE`, par-dessus l'écran verrouillé) pendant un appel téléphonique.

## Le changement (le diff ci-dessous)

- **C-2** : une appli qui a « l'affichage par-dessus les autres applis » pouvait couvrir l'écran STOP sans le fermer.
  À partir d'Android 12, `window.setHideOverlayWindows(true)` dans `onCreate`, avec la permission normale
  `HIDE_OVERLAY_WINDOWS` (manifeste, la liste du contrôle des permissions, PRIVACY). Mesuré sur Android 16 : la
  fenêtre du STOP porte `HIDE_NON_SYSTEM_OVERLAY_WINDOWS`.
- **S-2**, laissé par choix : une demande bancaire est reconnue par son canal de notification, de n'importe quelle
  appli (une liste de paquets jamais mesurés ferait manquer de vraies demandes) ; seuls les textes qui disaient « un
  STOP de trop, du côté protecteur » sont corrigés : un faux STOP envoie aussi un SMS au proche, borné.
- **E-2** : le README ne promet plus toutes les banques, seulement la demande Sécur'Pass.

## Ce qui est demandé (au plus 4 constats)

Des défauts RÉELS, chacun avec un scénario concret et votre degré de certitude : le masquage qui ne s'appliquerait
pas (moment de l'appel, recréation, autre fenêtre), une régression de l'écran STOP, une permission mal déclarée, un
texte faux. « Aucun défaut réel » est une réponse attendue : ne remplissez pas.
