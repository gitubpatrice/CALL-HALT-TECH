# Sécurité — Call Halt Tech

🇬🇧 [English version](SECURITY.md) · Français

## État

**En développement (0.1.0). Aucune version n'a été publiée, et celle-ci ne protège encore personne :**
elle contient la logique de détection et une démonstration de l'écran STOP. La conception, ses décisions
et ce qui reste à mesurer sont dans [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Modèle de menace

Call Halt Tech arrête l'utilisateur quand une action sensible survient pendant un appel ou juste après :
un code reçu par SMS, une demande de validation de l'appli bancaire, une appli de prise en main à
distance. Elle ne se fie jamais au numéro de l'appelant, qui s'usurpe en quelques secondes.

- **Aucun réseau.** L'appli ne déclare aucune permission `INTERNET`, et ne le peut pas : la CI compare le
  manifeste fusionné de la release à une liste exacte, avec un contrôle négatif
  (`tools/check-manifest-permissions.py`). La surface d'attaque réseau est nulle par construction.
- **Données au repos.** La mémoire du détecteur et le journal des alertes (quand, pourquoi, ce qui a été
  choisi, le numéro qui appelait) restent dans l'espace privé de l'appli, protégé par le chiffrement des
  fichiers d'Android. Ils ne sont pas chiffrés une seconde fois (docs/ARCHITECTURE.md, D-009). La
  sauvegarde Android et le transfert vers un autre téléphone sont **désactivés**.
- **Hors périmètre, par conception.** L'appli n'entend pas l'appel, ne l'enregistre pas et n'utilise
  aucun service d'accessibilité. Une action faite sur un autre appareil lui échappe.

## Signaler une faille

Vous pensez avoir trouvé une faille ? **N'ouvrez pas de ticket public.** Deux canaux, au choix :

- **le signalement privé de GitHub** : onglet *Security* du dépôt, puis *Report a vulnerability*
  (https://github.com/gitubpatrice/CALL-HALT-TECH/security/advisories/new) ;
- **par courriel** : **contact@files-tech.com**, objet `[SECURITY] Call Halt Tech — <résumé>`.

Merci de laisser 90 jours avant toute divulgation publique d'un problème non corrigé.
