# Relectures : les superpositions masquées sur l'écran STOP (C-2), S-2 et E-2 — 2026-11-09

> Claude, agent `android-code-quality-deep-dive` (axe sécurité, 0 $), puis GPT `gpt-5.6-luna`, effort `high` ;
> consigne : `consignes/2026-11-09-gpt-superpositions.md`. Envoyé à GPT : le diff (C-2, S-2, E-2) et `StopActivity.kt`
> entier ; 22 904 jetons d'entrée, 7 290 de sortie : **0,013 $**.

## Tri

| Relecteur | Constat | Verdict | Suite |
|---|---|---|---|
| Claude | Le limiteur décrit « un par sorte d'alerte et par quart d'heure » : seul le SMS « STOP affiché » est retenu 15 min ; les autres ne sont bornés que par les 20 par jour | **réel** (rédaction ; vérifié dans `GuardianLimiter.allows`) | **corrigé** (KDoc de `NotificationSignal`, D-019) |
| Claude | Le reste : l'appel au bon endroit (`onCreate`, avant la fenêtre), rejoué à la recréation, gardé par `onNewIntent` ; aucune régression (FLAG_SECURE, écran verrouillé, repli) ; aucune fenêtre de l'appli elle-même masquée ; la permission normale, déclarée partout | sans défaut | rien |
| GPT 1 | Les fenêtres d'un service d'accessibilité ne sont pas masquées : « aucune autre appli ne peut dessiner par-dessus » est trop large | **réel** (rédaction) | **corrigé** : PRIVACY (EN, FR), KDoc, D-036 disent ce qui est masqué et ce qui ne l'est pas ; un service activé pendant un appel est un STOP à lui (D-033) |
| GPT 2 | Les fenêtres du système restent visibles ; le texte était absolu | **réel** (rédaction) | **corrigé** de même |

## Vérifications après correction

Gate de la CI vert en local (670 tests) ; contrôle des permissions du manifeste : la liste revue, sans réseau.
**Mesuré** (émulateur Android 16) : la permission accordée, la fenêtre du STOP porte `HIDE_NON_SYSTEM_OVERLAY_WINDOWS`.

## Le rapport GPT, tel que rendu

### 1. Le masquage ne couvre pas les fenêtres d’accessibilité — **certitude élevée**

`setHideOverlayWindows(true)` masque les fenêtres non-système classiques (`TYPE_APPLICATION_OVERLAY`), mais les fenêtres d’un service d’accessibilité (`TYPE_ACCESSIBILITY_OVERLAY`) sont traitées comme des fenêtres de confiance et ne sont pas nécessairement masquées.

Scénario concret : un service d’accessibilité malveillant est déjà activé avant le démarrage du processus de Call Halt Tech. Le capteur D-033 ne le considère alors pas comme une nouvelle activation. Pendant un appel, il affiche ensuite une vue opaque par-dessus le STOP. `HIDE_NON_SYSTEM_OVERLAY_WINDOWS` ne garantit pas son retrait ; `FLAG_SECURE` empêche surtout la capture, pas l’affichage de cette vue.

Le correctif C-2 règle bien le cas `SYSTEM_ALERT_WINDOW`, mais la documentation disant qu’« aucune autre appli ne peut dessiner par-dessus » est trop large. Il faut soit limiter explicitement la promesse aux superpositions ordinaires, soit traiter séparément les services d’accessibilité.

### 2. Le texte de confidentialité est absolu alors que les fenêtres système restent visibles — **certitude élevée**

`PRIVACY.fr.md`, `PRIVACY.md` et le commentaire de `StopActivity` affirment en substance qu’« aucune autre appli » ne peut dessiner par-dessus le STOP. Le code précise pourtant lui-même que les fenêtres système restent visibles.

Scénario concret : le panneau de volume, une invite biométrique, une fenêtre de l’interface système ou une autre UI privilégiée apparaît au-dessus du STOP. La protection C-2 ne prétend pas masquer ces fenêtres. Le texte devrait dire par exemple :

> Les applications ordinaires qui utilisent « l’affichage par-dessus les autres applis » ne peuvent pas couvrir l’écran STOP. Certaines fenêtres système et fenêtres de confiance, notamment liées à l’accessibilité, restent possibles.

Ce n’est pas un défaut de déclaration de permission : `HIDE_OVERLAY_WINDOWS` est bien la permission normale attendue et son appel est correctement protégé par le test d’API.

### Points non retenus

Je ne vois pas de défaut réel concernant le moment de l’appel ou la recréation : l’appel est fait dans `onCreate`, avant l’affichage de la fenêtre, et il est donc rejoué après rotation ou recréation du processus. `onNewIntent` réutilise la même fenêtre, qui conserve l’attribut. Aucun défaut supplémentaire du README E-2 ou de S-2 n’est démontrable à partir des fichiers fournis.