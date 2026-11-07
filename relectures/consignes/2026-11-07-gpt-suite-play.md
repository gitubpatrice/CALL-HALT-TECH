# Relecture de code : exempter la suite d'accessibilité de Google installée depuis Play, dans une appli anti-arnaque

Vous êtes un ingénieur Android senior, attentif à la sécurité. Répondez en français. L'appli affiche un écran STOP
quand un service d'accessibilité est activé pendant un appel ou juste après : une appli de prise en main à distance en a
besoin, et l'escroc au téléphone fait installer une telle appli à sa victime. Les fonctions du téléphone lui-même
(TalkBack, Voice Access, le menu d'accessibilité, la suite de Samsung…) en sont exemptées.

## Le changement (le diff ci-dessous)

Jusqu'ici, un paquet de la liste n'était exempté que préinstallé (`FLAG_SYSTEM`). Sur un Samsung, la suite de Google
vient de Play, sans ce drapeau, et son TalkBack faisait un STOP. Désormais, un paquet de la liste est exempté s'il est
préinstallé OU signé d'une des empreintes SHA-256 écrites (`hasSigningCertificate`, `CERT_INPUT_SHA256`). Google signe
chaque suite de deux clés : celle des copies de Play (lue sur APKMirror, pour plusieurs versions) et celle des copies
préinstallées (lue par apksigner sur l'émulateur Android 16). Mesuré : sur un émulateur Android 15 sans les applis
Google, le TalkBack installé comme une appli ordinaire faisait « accès donné » avant, « fonction du téléphone » après.

## Ne PAS signaler

- Que la copie Play n'a pas été mesurée sur un téléphone (limite écrite ; l'échec resterait un STOP de trop).
- Style, nommage, formatage ; le message de journal de mise au point.

## Ce qui est demandé (au plus 4 constats)

Des défauts RÉELS, chacun avec un **scénario concret** et votre degré de certitude : surtout un moyen, pour une appli
installée par la victime à la demande d'un escroc, d'obtenir l'exemption (identifiant, clé, rotation, historique de
signataires, `sharedUserId`, ordre d'installation, visibilité des paquets) ; puis une suite authentique qui resterait
un STOP ; puis un test qui resterait vert sur une implémentation fausse. « Aucun défaut réel » est une réponse
attendue : ne remplissez pas.
