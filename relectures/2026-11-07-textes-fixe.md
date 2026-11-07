# Relectures : la phrase du fixe sur l'accueil (E-1) et le premier SMS au proche — 2026-11-07

Mots français de Patrice (2026-11-07) ; les quatre autres langues sont des traductions.

## Claude (agent `general-purpose`, lecture seule, 0 $)

Traductions et premier SMS bons : « Festnetztelefon », « telefono fisso », « teléfono fijo », « landline » ;
« Legen Sie auf », « riattacchi », « cuelgue », « hang up » ; le vouvoiement de chaque fichier ; les apostrophes
échappées ; la phrase « c'est une arnaque » ne contredit aucune autre chaîne ; le premier SMS reste une phrase complète.

| N° | Constat | Suite |
|---|---|---|
| 1 | README et PROJET disent « l'accueil le dit » : vrai seulement protection activée — la phrase, et la limite existante, n'étaient montrées que dans l'état « activée », pas dans les états affaiblis qui protègent encore | **corrigé** : les deux phrases s'affichent dès que la protection voit quelque chose (activée, ou affaiblie mais en marche) |
| 2 | README : « elle ne voit que les appels de ce téléphone » laisse croire qu'elle les voit tous | **corrigé** : « les appels téléphoniques de ce téléphone, pas ceux par Internet (WhatsApp…) » |
| 3 | PROJET : « le code reçu pendant ce temps ne fait pas de STOP » est faux dans les 15 minutes qui suivent un appel sur ce téléphone | **corrigé** |
| 4 | Allemand : « ist es Betrug » → « ist das Betrug », comme `stop_ignore_warning` | **corrigé** |
| 5 | Espagnol : « en el fijo » → « por el fijo » | **corrigé** |
| 6 | Sur l'accueil, la phrase du fixe venait après « la règle ci-dessous », et pouvait passer pour elle | **corrigé** : la phrase du fixe d'abord, puis la limite |

## GPT `gpt-5.6-luna` (high)

> Consigne : `consignes/2026-11-07-gpt-textes-fixe.md`. Envoyé : le diff (`origin/main..be01445`), 22 024 caractères.
> 5 686 jetons d'entrée, 7 623 de sortie : **0,010 $**.

| Langue | Constat | Suite |
|---|---|---|
| Anglais | « If, on the landline, you are asked… » maladroit ; « text message » plus clair | **corrigé** : « If someone asks you, during that call, for a code you received by text message, it is a scam: hang up. » (« it is », comme le reste de l'appli), et le README |
| Allemand | « am Festnetz » peu idiomatique | **corrigé** : « Wenn Sie bei einem Anruf über das Festnetz nach einem per SMS erhaltenen Code gefragt werden, ist das Betrug: Legen Sie auf. » |
| Italien, espagnol | rien à redire | — |
