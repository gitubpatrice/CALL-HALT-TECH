# Relecture du code par GPT — écran d'activation — 2026-11-02

> Modèle **gpt-5.6-luna**, effort high, **0,019 $** (33 279 tokens d'entrée, 10 457 de sortie dont 9 840 de
> raisonnement) ; le diff `07c058a..4095991` et 9 fichiers complets. Consigne : `consignes/2026-11-02-gpt-activation.md`.
> Menée en même temps que les relectures Claude (`2026-11-02-claude-activation.md`), sans en voir les constats.
> Rapport brut ci-dessous.

## Tri (chaque constat vérifié dans le code et contre la mesure)

| N° | Verdict | Suite |
|---|---|---|
| 1 | **réel, en partie** : « la protection est en marche » ne dit pas ses limites (un code lu dans une conversation ouverte ne fait pas de notification ; une action sur un ordinateur ne touche pas le téléphone) | **corrigé** : une ligne de plus dans la carte « en marche » — « Elle ne voit pas tout : même sans écran STOP, la règle ci-dessous reste vraie. » Le texte principal reste simple : une liste des cas techniques ne servirait pas le lecteur visé |
| 2 | **réel** : l'aide envoyait aux infos de l'appli, alors que le menu « Autoriser les paramètres restreints » n'y apparaît qu'après un premier refus (mesure P-3) | **corrigé** : l'étape 1 dit d'essayer d'abord, et que ce refus est ce qui fait apparaître la suite |

**Bilan** : 2 défauts réels, corrigés. Luna n'a pas vu ce que la relecture de sécurité Claude a trouvé (des pages de
réglages qu'une autre appli pouvait imiter) ; la relecture Claude n'a pas vu l'ordre de l'aide.

---

Je retiens **deux défauts réels**.

### 1. Le texte « la protection est en marche » promet plus que ce que le détecteur peut observer

**Scénario :** les trois accès sont accordés et l’accueil affiche :

> « La protection est en marche. Pendant un appel… l’écran STOP s’affiche quand vous recevez un code par SMS. »

L’utilisateur reçoit ensuite un code dans une application SMS qui :

- ne publie pas de notification exploitable ;
- utilise une vue interne ou un format `InboxStyle` non pris en charge ;
- ou n’est pas l’application SMS par défaut.

Le code ne construit alors aucun signal SMS, et aucun écran STOP n’apparaît, malgré l’état vert. La documentation technique décrit explicitement ces limites (`LatestSms`).

**Certitude : élevée.** Le problème vient du nouveau texte de l’accueil et de l’activation, pas du calcul des trois accès.

Le texte devrait préciser que la détection concerne les codes apparaissant dans les notifications lisibles de l’application SMS par défaut, et ne pas présenter la couverture comme générale.

---

### 2. L’aide « paramètres restreints » est affichée avant que le menu soit disponible

**Scénario concret :** APK installé depuis un fichier sur Android 16, accès « affichage par-dessus » manquant.

`mayBeRestricted()` vaut immédiatement `true`, donc l’aide affiche :

1. fermer le refus du téléphone ;
2. ouvrir les infos de l’application ;
3. choisir « Autoriser les paramètres restreints ».

Mais l’utilisateur n’a encore jamais tenté d’accorder l’accès. D’après la mesure fournie, le menu « Autoriser les paramètres restreints » n’apparaît qu’après un premier refus. En suivant l’aide avant d’appuyer sur « Ouvrir les réglages », il ouvre donc les infos de l’application sans trouver le menu indiqué.

Le même parcours se produit pour l’accès aux notifications.

**Certitude : élevée.** C’est une incohérence directe entre le comportement mesuré et l’ordre d’affichage du code. Ce n’est pas irrécupérable, car l’utilisateur peut revenir, tenter l’accès, puis recommencer, mais le parcours guidé peut donner l’impression d’être bloqué.

L’aide devrait être affichée seulement après un refus constaté, ou expliquer explicitement : « Essayez d’abord d’accorder l’accès ; si Android le refuse, revenez ici et ouvrez les infos de l’application. »

---

Je ne retiens pas de défaut avéré supplémentaire concernant la rotation, le retour des réglages ou la mort du processus : l’état initial est relu par le `ViewModel`, les accès sont relus au retour au premier plan, et le résultat de la demande de permissions est conservé par le mécanisme `ActivityResult`. Les fallbacks de réglages couvrent également une absence explicite d’activité via `ActivityNotFoundException`.
