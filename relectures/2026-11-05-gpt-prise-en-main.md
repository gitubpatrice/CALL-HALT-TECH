# Relecture GPT — le capteur des applis de prise en main installées (D-031) — 2026-11-05

> `gpt-5.6-luna`, effort `high` ; consigne : `consignes/2026-11-05-gpt-prise-en-main.md`. Envoyé : le diff du code
> seul (`git diff main..5d14367 -- app/src/main/java domain/src/main app/src/main/AndroidManifest.xml`), puis
> `DetectionEngine.kt`, `SensorQueue.kt` et `ProcessSignalUseCase.kt` entiers ; 57 553 caractères. 13 818 jetons
> d'entrée, 15 490 de sortie : **0,021 $**.

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Une installation dans un profil de travail n'est pas entendue | réel, une limite : une installation dans ce profil passe par l'employeur, pas par l'escroc, qui guide la victime dans le sien | **écrit** dans D-031 |
| 2 | L'installation traitée avant le début de l'appel qui la précède : un avertissement au lieu d'un STOP | **écarté** : la file garde l'ordre d'arrivée, le début de l'appel arrive en moins d'une seconde (mesuré, D-020 et § 6), une installation guidée par l'escroc vient des minutes après | rien |
| 3 | Le nom affiché n'est pas prouvé par la signature : un RustDesk recompilé par l'escroc, avec le même identifiant, serait annoncé « RustDesk » | réel en fait, **sans conséquence** : le STOP part quand même, et l'avertissement reste vrai (une appli installée sous ce nom, qui permet de voir ou de contrôler, à désinstaller) ; vérifier 49 certificats ne changerait rien à ce qu'on dit | rien |

Vérifié sans défaut par lui : la diffusion non forgeable, le Play Store et l'installateur par session, Android 10,
l'exécuteur qui ne journalise jamais « non affiché » un STOP montré.

## Le rapport, tel que rendu

## Constats

### 1. Installation dans un profil de travail manquée — certitude élevée

Les broadcasts `ACTION_PACKAGE_ADDED` sont envoyés dans le profil utilisateur concerné. Un processus de l’application installé dans le profil personnel ne reçoit pas les installations effectuées dans le profil de travail.

**Scénario :**

1. L’application anti-arnaque est installée et son `NotificationListenerService` est actif dans le profil personnel.
2. Un gestionnaire de périphériques ou un utilisateur installe `com.carriez.flutter_hbb` dans le profil de travail.
3. Le broadcast `PACKAGE_ADDED` est diffusé au profil de travail.
4. Le `InstallSensor` du profil personnel ne le reçoit jamais ; aucun STOP ni avertissement n’est produit.

Il faudrait soit déclarer explicitement que seul le profil personnel est protégé, soit installer/activer le capteur dans chaque profil pris en charge. Les `<queries>` sont eux aussi évalués par utilisateur.

---

### 2. Course entre l’état d’appel et l’installation — certitude élevée

La file ordonne les signaux selon leur arrivée, mais le moteur ne sait traiter une installation « pendant l’appel » que si `CallStarted` a déjà été appliqué à l’état.

**Scénario :**

1. L’appel devient actif côté téléphonie.
2. La remontée de `CallStarted` vers l’application est retardée, ou attend derrière un autre traitement.
3. Une installation est effectuée.
4. `ACTION_PACKAGE_ADDED` arrive et est placé dans la file avant `CallStarted`.
5. L’installation est traitée avec `state.calls` vide : `outsideCall()` produit un avertissement, et non un STOP.
6. `CallStarted` est traité ensuite, alors que l’installation a déjà été consommée.

Le timestamp de l’installation ne corrige pas ce cas : `CallWindow.callAt()` ne peut pas deviner un appel qui n’est pas encore présent dans l’état. C’est une installation manquée du point de vue de la règle métier, même si aucun processus n’est mort et si les deux signaux finissent bien par être reçus.

---

### 3. Le nom affiché n’est pas authentifié par la signature de l’APK — certitude technique élevée, impact dépendant de la menace retenue

Le code considère le seul nom de package comme une identité de l’application :

```kotlin
REMOTE_CONTROL_APPS[packageName]
```

Or Android n’empêche pas, si le package officiel n’est pas déjà installé, l’installation d’un APK signé par un tiers qui reprend le même `applicationId`.

**Scénario :**

1. Le vrai RustDesk n’est pas installé.
2. L’escroc fournit un APK reconditionné ayant le package `com.carriez.flutter_hbb`, mais signé avec la clé de l’escroc.
3. Android l’installe et émet légitimement `ACTION_PACKAGE_ADDED`.
4. La notification affiche « RustDesk », même si l’APK n’est pas celui publié par RustDesk.
5. Le toucher ouvre bien la page système de cet APK imposteur, mais le texte et le nom affichés donnent une identité fausse.

Cela ne permet pas de forger le broadcast — celui-ci reste émis par le système — et le STOP serait quand même déclenché pendant un appel. En revanche, l’avertissement et le journal attribuent une provenance non vérifiée. Si les APK sideloadés font partie de la menace, le package name seul n’est pas une identité suffisante ; il faut aussi vérifier le certificat de signature attendu.

## Points vérifiés qui ne constituent pas un défaut ici

- `RECEIVER_NOT_EXPORTED` empêche une application ordinaire d’envoyer directement un faux `ACTION_PACKAGE_ADDED`. Le broadcast de package est émis par le système ; les mesures fournies confirment la réception sur Android 11 et 16.
- Une installation via `PackageInstaller.Session` ou le Play Store aboutit normalement au même `ACTION_PACKAGE_ADDED`, après le commit effectif. Une mise à jour porte `EXTRA_REPLACING`, donc le filtrage est cohérent avec le contrat annoncé.
- Android 10 n’applique pas encore la visibilité des paquets d’Android 11 ; les `<queries>` ne font donc pas manquer ces installations.
- Je ne vois pas, dans le code fourni, de chemin où l’échec d’un avertissement transforme un STOP affiché en `NotShown` : `WarningNotifier` est appelé séparément et ses exceptions sont absorbées par `posted()`. Sous réserve que le contrat de `StopLauncher.show()` soit respecté, ce point ne révèle pas de défaut supplémentaire.