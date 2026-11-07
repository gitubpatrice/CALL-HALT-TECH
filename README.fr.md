# Call Halt Tech

[English](README.md)

Une appli Android contre les **arnaques au téléphone** : faux conseiller bancaire, faux technicien, faux
service des fraudes. Elle est pensée d'abord pour les personnes âgées et les proches qui veillent sur elles.

**État : en développement, non publiée.** Une fois ses accès accordés (elle les demande un par un), elle
affiche l'écran STOP quand, pendant un appel ou juste après, un code arrive par SMS, l'appli d'une banque demande de
valider une opération (Sécur'Pass), une appli de prise en main à distance est installée ou mise en marche, ou une appli
reçoit l'accès « accessibilité ». Elle peut alors prévenir un proche par SMS. Rien n'est publié avant que l'appli soit
fiable.

## La méthode

Ne jamais se fier au numéro de l'appelant : il s'usurpe en quelques secondes. S'appuyer plutôt sur ce que
l'escroc ne peut pas éviter. Il faut que la victime **fasse quelque chose d'irréversible sur son téléphone,
pendant l'appel ou juste après** : donner un code reçu par SMS, valider une opération dans l'appli de la
banque, installer une appli de prise en main à distance. Si cela arrive pendant un appel ou juste après,
l'appli interrompt avec un écran STOP : *ni votre banque, ni la police, ni les impôts, ni votre assurance
ne vous appelleront jamais pour vous demander cela. Si on vous dit d'ignorer cet écran, c'est une arnaque.
Raccrochez, puis appelez vous-même un numéro que vous connaissez.*

Elle ne bloque rien. C'est une pause, au bon moment.

## Promesses, vérifiées par le build

- **Aucune permission Internet**, jamais : la CI lit le manifeste fusionné de la release et le compare à
  une liste exacte (`tools/check-manifest-permissions.py`, avec son contrôle négatif).
- Pas de compte, pas de serveur, pas de services Google Play. Rien ne sort du téléphone, sauf les SMS au proche, si
  vous en choisissez un : depuis votre numéro, jamais un code ni le contenu d'un message ([PRIVACY.fr.md](PRIVACY.fr.md)).
- Anglais, français, allemand, italien, espagnol.

## Construire

JDK 17 et le SDK Android, puis `./gradlew :app:assembleDebug`. Le gate local complet, identique à la CI,
est décrit dans [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) § 8.

## Licence

Apache License 2.0 : voir [LICENSE](LICENSE) et [NOTICE](NOTICE).
