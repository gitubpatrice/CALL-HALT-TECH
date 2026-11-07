# Relecture Claude — l'écran STOP parle du plus grave de ses déclencheurs (N-1), 2026-11-07, avant commit

Un agent `general-purpose` (justesse, avec un harnais sur les classes compilées), sur l'arbre de travail de
`fix/stop-gravest-trigger`. Coût : 0 $ (sous-agent).

| # | Constat (gravité, certitude) | Tri | Suite |
|---|---|---|---|
| 1 | N-1 restait entier par la notification de repli : remplacée avant d'être touchée, elle ne gardait que le dernier déclencheur, et l'écran qu'elle ouvre aussi ; et un écran lancé ensuite effaçait la notification de l'accessibilité (moyen ; chemin lu) | **Réel, corrigé** | Le notifier retient les déclencheurs de la notification affichée, en parle du plus grave, et les fait passer dans la suivante et dans l'écran (`StopRequest.earlier`) ; le lanceur y joint ceux d'une notification encore affichée. **Mesuré** (affichage par-dessus retiré, Android 16, pendant un appel), dans les deux ordres : accessibilité puis code, et code puis accessibilité — la notification parle de l'accessibilité, l'écran qu'elle ouvre aussi, « Raccrocher et retirer l'accès » répond pour les deux |
| 2 | À gravité égale, ce n'était pas « le plus récent », et une recréation de l'écran changeait la phrase (l'ordre de l'énumération remplaçait l'ordre d'arrivée) (faible ; mesuré au harnais) | **Réel, corrigé** | `gravest` dans l'ordre d'arrivée (`inArrivalOrder` : un déclencheur revenu est le plus récent) ; la sauvegarde garde cet ordre ; test « installée, ouverte, puis un code » |
| 3 | Les tests ne fixaient pas toutes les gravités, et un nom de test énonçait une règle devenue fausse (faible) | **Réel, corrigé** | `GravestTest` : le rang de chaque déclencheur écrit, toutes les paires ordonnées ; test renommé |
| 4 | Le texte ajouté à D-017 disait « le plus récent » à tort, oubliait la notification, et coupait le passage sur le verrou (faible) | **Réel, corrigé** | Réécrit et déplacé ; la KDoc de `StopActivity` dit le plus grave |
| 5 | La décision de l'audit disait « composés sur tous les déclencheurs » ; le correctif n'affiche que le plus grave (à trancher) | **Choisi** (technique, aucun mot nouveau) | Le plus grave seul : son conseil couvre l'autre, et un écran plus long déborde déjà à gros caractères (U2). Écrit dans D-017 |

Vérifié, sans défaut : la réponse vaut pour tous les déclencheurs ; le journal et le SMS au proche ne lisent ni le dernier
ni le plus grave ; « Appeler ma banque » toujours offert ; après la fin de l'appel, « Retirer l'accès » puis « Appeler ma
banque » ; jamais `REMOVE_ACCESS` sur un appel qu'on ne peut pas couper ; la démonstration intacte.
