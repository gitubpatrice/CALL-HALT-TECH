# Relecture Claude : la suite d'accessibilité de Google installée depuis Play (audit M4), avant commit — 2026-11-07

> Agent `android-code-quality-deep-dive`, lecture seule ; consigne : sécurité (une appli peut-elle obtenir
> l'exemption ?), justesse, tests, textes. 0 $ (sous-agent). Envoyé : le changement non commité, sans mes conclusions,
> avec les mesures déjà faites à contester.

## Verdict

**Aucun défaut dans le code de production.** Sécurité : sans constat. Même identifiant sous une autre clé : refusé.
Historique de rotation (v3) : il faudrait que Google ait signé l'ascendance de l'attaquant. Plusieurs signataires :
tous doivent correspondre. Paquet absent : `false`. `sharedUserId` : impossible sans la clé de Google. Ordre
d'installation : Android refuse deux paquets de même nom sous deux clés. Identifiant hors liste : refusé en premier.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Aucun test ne garde le cas « préinstallé mais signé d'une autre clé » : une variante qui ne regarderait que le certificat passerait les tests, et ferait un STOP du TalkBack d'une ROM sans Google | **réel** (moyen) | **corrigé** : test ajouté |
| 2 | L'ajout à D-033 coupe une phrase en deux ; la justification du « préinstallé » se lit après « Pas mesuré » | **réel** (moyen) | **corrigé** : la phrase d'origine rétablie, l'ajout à part |
| 3 | Les mesures ne prouvent pas que la copie Play est signée comme la copie de l'émulateur : l'APK installé est celui de l'émulateur, et le test sur appareil relit la même copie ; il recopie aussi l'appel du capteur au lieu de l'appeler | **réel** (moyen), et **plus grave que dit** | **corrigé** : recoupé sur APKMirror, **la copie Play est signée d'une autre clé** (« Google Eyes-Free Project », `7b5ad551…` ; Voice Access `7a425040…`). La première version n'aurait donc pas corrigé M4 sur un Samsung. Les deux clés de chaque suite sont écrites ; l'appel est une fonction partagée (`isSignedWith`) que le test appelle |
| 4 | `hasSigningCertificate` pourrait lever dans le rappel de l'observateur | **faible** : seulement si le service système meurt, comme `getApplicationInfo` avant ; rien de nouveau | rien |
| 5 | Le message de journal à chaque fonction exemptée | info (journal de mise au point seulement) | rien |

## Vérifications après correction

- `AccessibilityGrantsTest` : 18 tests, 0 échec ; gate local vert (592 tests).
- Émulateur Android 15 sans les applis Google, le TalkBack de l'émulateur Android 16 installé comme une appli
  ordinaire : version de `main` → « Accessibility access given to com.google.android.marvin.talkback » ; version
  corrigée, avant et après le passage à deux clés → « …the phone's own com.google.android.marvin.talkback: nothing ».
  Remis en état : TalkBack désinstallé, le menu d'accessibilité seul actif.
- `SuiteCertificatesOnDeviceTest` vert sur l'émulateur Android 16, non sauté.
- **Pas mesuré** : la copie Play sur un téléphone. Sa clé vient d'APKMirror seul (plusieurs versions). Fausse, elle
  laisserait un STOP de trop, côté qui avertit.
