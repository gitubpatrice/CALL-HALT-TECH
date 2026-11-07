# Relecture GPT — la règle des SMS de code élargie à d'autres langues (audit M1 à M3) — 2026-11-07

> `gpt-5.6-luna`, effort `high` ; consigne : `consignes/2026-11-07-gpt-regle-codes-langues.md`. Envoyé : le diff de la
> branche (`main..a15b6e5`), puis `ValidationCode.kt` et le corpus ; 81 005 caractères. 24 673 jetons d'entrée, 15 779
> de sortie : **0,024 $**. Après la relecture Claude (`2026-11-07-regle-codes-langues.md`).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | « Your verification code: No. 482913 » manqué : la garde « pas après PIN, code, OTP » n'admettait qu'un caractère ; « : » plus l'espace en font deux | **réel** | **corrigé** : un ou deux caractères (`[ :-]{1,2}`) ; ligne `W53` |
| 2 | `\b[ée]tage` ne voit plus « étage » sur un JDK 19 et suivants | **déjà noté** par la relecture Claude ; ni le JDK 17 (poste, CI) ni Android ne sont touchés, et `ValidationCodeTest` échouerait bruyamment le jour d'une montée | rien |
| 3 | « Tu PIN de entrega es 4821. Entrégalo al repartidor. » pris pour un code : aucun mot de livraison espagnol | **refusé** : un veto de livraison dans trois langues ouvrirait des codes manqués, la famille que la relecture Claude vient de mesurer, pour un faux positif que rien ne montre fréquent ; dans le doute, c'est un code | rien |
| 4 | Une date espagnole s'écrit « 12 de diciembre de 2026 » : l'ajout des mois espagnols ne servait à rien | **réel** | **corrigé** : un « de » admis avant le mois et avant l'année ; lignes `W69`, `W70` |

Après correction : le harnais de la relecture Claude rend le même verdict qu'avant sur ses 126 SMS ; `ValidationCodeTest`
185 tests, 0 échec ; `ValidationCodeOnDeviceTest` vert sur l'émulateur API 36 ; gate local vert (585 tests).

## Le rapport, tel que rendu

J’ai relevé **quatre défauts réels**.

1. **Faux négatif : `No.` après une ponctuation n’est pas protégé**

   **SMS exact :**

   ```text
   Your verification code: No. 482913
   ```

   **Verdict attendu : C.**

   `NEVER_A_CODE` applique pourtant :

   ```regex
   (?<!pin[ \u00A0-])(?<!code[ \u00A0-])(?<!otp[ \u00A0-])\b(?:nr|no)\.[ \u00A0:]*[0-9]+
   ```

   Devant `No.`, le texte immédiat est `code: `, et non `code ` : le lookbehind ne protège donc pas ce cas. `No. 482913` est effacé, puis il ne reste plus aucun nombre pour `CODE_NUMBER`.

   **Certitude : élevée.** Le même défaut existe avec `PIN: No. 4821`, `OTP: No. 482913`, ou avec un tiret cadratin.

2. **Divergence de moteur autour de `\b` et des lettres accentuées**

   **SMS exact :**

   ```text
   Code de l'étage : 4821
   ```

   **Verdict attendu : L.**

   Le veto devrait reconnaître `étage` via :

   ```regex
   \b[ée]tage\b
   ```

   Sur ICU/Android, la frontière devant `é` est Unicode et le SMS est rejeté comme code de porte. Avec le comportement des JDK 19+ signalé dans le diff, la même frontière peut ne plus être reconnue : le veto ne s’applique plus et `code` + `4821` donnent alors **C**.

   Ce n’est pas une divergence du couple précisément testé JDK 17/API 36, mais c’est une divergence JVM/ICU réelle dès qu’un build JVM passe au comportement JDK 19+.

   **Certitude : élevée pour JDK 19+, hors périmètre JDK 17 actuel.**

3. **Faux positif de l’extension espagnole : le vocabulaire de livraison est incomplet**

   **SMS exact :**

   ```text
   Tu PIN de entrega es 4821. Entrégalo al repartidor.
   ```

   **Verdict attendu : L.**

   `PIN` est reconnu comme mot de code, mais aucun terme espagnol de livraison (`entrega`, `repartidor`, `paquete`, etc.) ne figure dans `OTHER_KIND_OF_CODE`. Le message est donc pris pour un code de validation.

   **Certitude : élevée.** C’est le même cas que les codes de livraison français et anglais déjà exclus par la règle ; l’extension annoncée aux autres langues ne couvre pas cette famille fréquente.

4. **Faux positif sur les dates espagnoles écrites avec `de`**

   **SMS exact :**

   ```text
   Su PIN fue enviado el 12 de diciembre de 2026 por correo.
   ```

   **Verdict attendu : L.**

   La règle des dates accepte `12 diciembre 2026`, mais pas la forme espagnole usuelle `12 de diciembre de 2026`. `2026` reste donc un nombre de quatre chiffres ; avec `PIN`, le message devient **C**.

   **Certitude : élevée.** Le même problème touche notamment `el 2 de noviembre de 2026` et les avis bancaires espagnols similaires.
