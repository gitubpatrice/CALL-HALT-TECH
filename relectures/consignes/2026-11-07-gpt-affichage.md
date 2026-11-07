# Relecture de code : l'écran STOP d'une appli anti-arnaque, lisible à toute taille de police et senti à l'oreille

Vous êtes un ingénieur Android senior (Jetpack Compose). Répondez en français. L'écran STOP est une activité plein écran
qui interrompt une personne âgée pendant un appel, quand un escroc lui fait faire une action sensible. Il doit rester
lisible et ses boutons sûrs (« Raccrocher »…) à portée, à toute taille de police.

## Le changement (le diff ci-dessous)

- **Mise en page** (`StopLayout`) : à 200 %, l'écran s'arrêtait à « Si on vous dit d'ignorer », les textes défilant
  dans ce que les boutons leur laissaient. Un `Layout` dans un `verticalScroll` décide en une seule mesure : si tout
  tient, textes en haut et boutons en bas, comme avant ; sinon tout défile, les boutons sous l'avertissement,
  l'explication après, « Continuer » en dernier. `stopPlacement` est une fonction pure testée.
- **Accueil** : l'état de chaque accès sous son titre ; **boutons** secondaires bordés de 2 dp.
- **Vibration** : deux vibrations brèves à l'ouverture d'un STOP, permission `VIBRATE` et usage d'alarme (le retour
  haptique sans permission suit le réglage « vibration au toucher », coupé sur le téléphone de test).
- Mesuré : émulateur Android 16 à 100 %, 200 % et en paysage ; l'ordre de l'arbre d'accessibilité suit l'affichage ;
  la vibration journalisée par le service sur un Galaxy S9.

## Ne PAS signaler

- Style, nommage, formatage ; le choix de la permission `VIBRATE` (décidé, documenté).

## Ce qui est demandé (au plus 4 constats)

Des défauts RÉELS, chacun avec un **scénario concret** et votre degré de certitude : un cas où un bouton sûr devient
inatteignable ou caché, où l'écran plante ou mesure faux (contraintes, recomposition, rotation, police, langue), où
la vibration part quand elle ne doit pas ou fait échouer l'écran, ou un test qui resterait vert sur une
implémentation fausse. « Aucun défaut réel » est une réponse attendue : ne remplissez pas.
