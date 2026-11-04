# Relecture — l'extinction annoncée (D-028, affinage de D-020) — 2026-11-04

> Agent `android-code-quality-deep-dive`, axes correction puis sécurité, lecture statique avec les sources d'AOSP
> (Android 16 : `ShutdownThread`, `PowerManagerService`, `ActivityManagerService`, `BroadcastSkipPolicy`) ; 0 $
> (sous-agent). Objet : le signal `PhoneShuttingDown`, sa règle dans le moteur, `ShutdownSensor` (récepteur enregistré
> à l'exécution, non exporté), son enregistrement dans l'application, les tests, la phrase de la politique de
> confidentialité, avant commit. Lui étaient donnés comme acquis : la diffusion lue dans AOSP (Android 10 et 16), les
> mesures sur l'émulateur Android 16, `ApplicationExitInfo` vide après un arrêt propre, 6 tests et leur contrôle négatif.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| M1 | Moyen : un escroc qui fait éteindre le téléphone plus de 15 min trouve la fenêtre passée au rallumage ; avant, elle courait 15 min après l'allumage | réel, **le compromis du changement** | **écrit** dans D-028 : c'est le cas d'un raccrochage suivi d'une attente aussi longue, que la fenêtre ne couvre pas non plus ; l'ancienne règle protégeait l'extinction plus que le raccrochage, par accident. L'escroc doit rappeler, et cet appel est protégé. Test ajouté (éteint 20 min : aucun STOP au rallumage, STOP pendant l'appel suivant). À reprendre si Patrice veut une fenêtre plus longue après une extinction |
| F2 | Faible : un redémarrage d'Android seul passe aussi par `ShutdownThread` (vérifié dans AOSP) ; si l'appel continue, il est déjà fini, et la téléphonie le rapporte comme un nouvel appel | réel, sans trou | **test ajouté** : la fenêtre de l'appel fini couvre les secondes entre les deux, puis le nouvel appel protège |
| F3 | Faible : un processus gelé reçoit la diffusion quand il redevient actif, et Android n'attend que 10 s | réel, improbable (l'écouteur relié tient le processus actif) | **écrit** dans la KDoc et dans D-028 : la règle d'avant s'applique alors |
| F4 | Faible : derrière une longue tâche de la file, la diffusion est rendue à 8 s, et le travail peut être perdu | réel, improbable (une transaction) | **écrit** dans la KDoc et dans D-028 ; pas de file prioritaire, la règle d'avant s'applique |
| F5 | Faible : un arrêt annulé après la diffusion fermerait un appel en cours | **sans objet** | l'agent n'a trouvé aucun chemin d'annulation après la diffusion dans AOSP |
| F6 | Faible : la phrase ajoutée à la politique de confidentialité (« c'est tout ce qu'elle en garde ») se lisait comme si l'appli ne gardait que la fin de l'appel | réel | **corrigée** dans les deux langues : l'appli note la fin de l'appel en cours, et rien d'autre de l'extinction |
| Q2 | Question : avant Android 13, `ContextCompat` protège un récepteur non exporté par une permission de signature ; le système la passe-t-il ? | vérifié dans AOSP 16 (`checkComponentPermission` : le système passe toujours), pas lu pour Android 10 | **mesuré** sur le Galaxy S9 (Android 10), par le menu d'arrêt : le capteur entend l'annonce, l'appel est fermé à l'arrêt |

**Sécurité** (par l'agent, vérifié dans AOSP) : `ACTION_SHUTDOWN` est une diffusion protégée, que seul le système
envoie ; le récepteur n'est pas exporté. Une autre appli ne peut pas l'envoyer pour clore un appel et taire un STOP.

**Tests ajoutés après la relecture** : éteint plus longtemps que la fenêtre, un appel qui continue après l'arrêt, les
deux canaux et deux annonces (9 tests en tout).

**Bilan** : aucun constat critique, élevé ni de sécurité ; un moyen réel, le compromis du changement, écrit avec sa
raison ; trois faibles écrits ou couverts par un test, un corrigé, un sans objet ; la question de l'agent tranchée par
une mesure.
