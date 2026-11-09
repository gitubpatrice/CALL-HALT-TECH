# Relecture Claude : l'historique des alertes (fonction 8, audit #1, D-041) — 2026-11-09

> Agent `android-code-quality-deep-dive`, lecture seule, sur le commit `9441beb` ; consigne : le regroupement contre ce
> que le moteur écrit réellement, ce que dit le texte partagé, Android et Compose, la cohérence, les tests. 0 $
> (sous-agent).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| 1 | Le regroupement suppose l'ordre d'écriture, mais la requête triait par l'heure civile : une horloge reculée entre un STOP et sa réponse donnait la réponse à un autre STOP (ou la perdait) — une preuve fausse remise à la banque | **réel** | **corrigé** : `DetectorDao.journal()` lit par `id` (l'ordre d'écriture) ; tests : `alerts` avec une heure reculée (JVM), `RoomJournal` sur une vraie base (appareil) |
| 2 | Le texte partagé ne dit pas le fuseau horaire : ambigu après un voyage, ou dans l'heure qui revient à l'heure d'hiver | **réel** | **corrigé** : la date porte son décalage, « 9 nov. 2026, 10:44 (UTC+01:00) » (mesuré à l'écran) |
| 3 | Le retour d'un STOP (FN-4) que le téléphone bloque fait dire « le téléphone a empêché l'écran de s'afficher » à l'écran d'origine, vu puis quitté | **réel** | **corrigé** : « non affiché » ne vaut que pour un écran encore sans réponse ; test |
| 4 | Un historique de milliers d'alertes dépasserait ce qu'une appli peut passer à une autre (environ 1 Mo) : « Partager » planterait | **réel, improbable** (≈ 2 500 alertes) | **corrigé** : l'échec est rattrapé, l'appli continue ; écrit (D-041) |
| 5 | Une ligne STOP illisible (écrite par une autre version) disparaît, et ses réponses vont au STOP précédent | **réel, rare** (un retour à une version plus ancienne) | **limite écrite** (D-041) |
| 6 | Le regroupement de 10 000 lignes sur le fil principal | **réel** (quelques dizaines de ms) | **corrigé** : hors du fil principal (`flowOn`) |
| 7 | TalkBack lit l'heure seule, puis chaque ligne ; pas de clé aux éléments de la liste | **réel** (a) ; (b) mineur | (a) **corrigé** : chaque alerte lue d'un bloc ; (b) laissé : une alerte n'a pas d'identifiant stable, une nouvelle en tête décale la liste |

Vérifié sans défaut par l'agent : le STOP et son « non affiché » de même heure, une réponse par déclencheur d'un écran
qui en tenait plusieurs, le STOP revenu (FN-4) et ses deux réponses, les rafales absorbées ; ni numéro ni texte de
message dans le texte partagé ; `remember(LocalConfiguration.current)`, l'état « en lecture » ; les 5 langues.

## Vérifications après correction

Gate de la CI vert en local : **669 tests** (135 domaine, 534 appli) ; sur l'appareil (émulateur Android 16) :
`RoomDetectorStoreTest` et `HistoryTextOnDeviceTest`, 13 sur 13.
