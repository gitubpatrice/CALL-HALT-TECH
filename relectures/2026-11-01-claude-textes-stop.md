# Relecture Claude — les textes de l'écran STOP (2026-11-01, soir)

> Sous-agent Claude (`general-purpose`), lecture seule, 0 $. Consigne : les textes des 5 langues, `StopText.kt`,
> `StopScreen.kt`, `StopTextTest.kt`, `Signal.kt`, et `PROJET.md` § 3 / D-011 / D-017 pour l'intention.
> Il n'avait pas mon raisonnement, seulement les fichiers. Rapport rendu en anglais, condensé fidèlement
> ci-dessous ; la colonne « Suite » dit ce qui en a été fait.

| N° | Gravité | Constat | Suite |
|---|---|---|---|
| H1 | ÉLEVÉE | Pour une appli de prise en main à distance, le conseil dit de raccrocher : ça ne coupe pas la session. L'escroc garde la main et peut appuyer lui-même sur « Continuer ». Vérifier aussi `FLAG_SECURE` sur l'Activity hôte | **Corrigé** : `stop_advice_remote_on_call` / `_after_call` (« éteignez votre téléphone… prévenez votre banque, d'un autre téléphone si possible ») ; test. `FLAG_SECURE` et « Continuer » : P-15 |
| H2 | ÉLEVÉE | La règle « ne vous demanderont JAMAIS, au téléphone, de… valider une opération » est fausse quand l'utilisateur appelle lui-même sa vraie banque ; « ignorer cet écran » et « raccrochez » aussi | **Corrigé** (option b) : « ne vous **appelleront** JAMAIS pour vous demander un code ou de valider une opération », 5 langues, accueil compris. « Ignorer cet écran » **gardé catégorique** : si c'était la vraie banque, l'erreur ne coûte qu'un rappel. Option a (moteur) : P-16 |
| M1 | MOYENNE | « Appeler ma banque » pendant l'appel : D-011 veut raccrocher avant de composer, l'étiquette ne le dit pas. « Raccrocher » doit vraiment raccrocher, VoIP comprise | **Corrigé** : « Raccrocher et appeler ma banque » pendant l'appel. Et **pas de bouton « Raccrocher » sur un appel VoIP** (`endCall` ne le peut pas) : `stopText` lit la `CallSession` ; test |
| M2 | MOYENNE | « l'appel peut venir d'un escroc » : mauvais temps après l'appel ; sur WhatsApp s'affiche un nom, pas un numéro | **Corrigé** : « Le numéro ou le nom affiché ne prouve rien : un escroc peut afficher le vrai. » |
| M3 | MOYENNE | DE `home_status_preview` : après les deux-points, « Sie zeigt » se lit « vous montrez » | **Corrigé** : « … – sie zeigt, wie der Schutz aussehen wird. » |
| M4 | MOYENNE | EN « Make the call yourself » ne renvoie à aucun appel | **Corrigé** : « Then call them yourself, on a number you know… » |
| B1 | FAIBLE | IT « Lei è al telefono » peut se lire « elle » | **Corrigé** : « Sta telefonando. » |
| B2 | FAIBLE | DE « ist es Betrug » → « ist das Betrug » | **Corrigé** |
| B3 | FAIBLE | DE « die man Ihnen nennt » plus étroit que « given » | **Corrigé** : « gibt » / « gegeben hat » |
| B4 | FAIBLE | DE « …oder Ihre Versicherung werden » (pluriel après « oder ») : acceptable | Laissé |
| B5 | FAIBLE | Très grande police : les boutons hors du défilement peuvent serrer le texte | Mesuré sur le S9 à 1,3 : l'essentiel reste visible. 2,0 non mesuré |

**Jugé juste par le relecteur** : aucun écart de sens entre les langues ; JAMAIS / NIEMALS / MAI / NUNCA en
capitales partout ; vouvoiement et formes neutres ; mêmes mots pour la même chose dans chaque langue ;
`StopText.kt` juste pour les 10 combinaisons ; pluriels corrects ; `stop_rule_remote` vrai tel quel.
