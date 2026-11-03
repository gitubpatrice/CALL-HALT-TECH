# Relecture — le repli de l'écran STOP : la notification qui l'ouvre (D-024, P-19) — 2026-11-03

> Agent `android-code-quality-deep-dive`, axes sécurité et correction, lecture statique ; 0 $ (sous-agent). Objet :
> `StopNotifier` (canal, notification plein écran, `canPost`), le repli dans `ActivityStopLauncher`, `StopActivity` qui la
> retire, la cinquième étape d'activation (`Access.ALERTS`, `runtimePermissions`, textes), les deux permissions, la garde
> CI et la politique de confidentialité, avant commit. Lui étaient donnés comme acquis : le gate local et les mesures sur
> l'émulateur Android 16 (l'étape et le dialogue, le STOP bloqué écran allumé puis éteint).

## Tri

| N° | Constat | Verdict | Suite |
|---|---|---|---|
| R-08 | Sécurité : le PendingIntent (explicite, `IMMUTABLE`, vers une activité non exportée) ne se détourne ni ne se modifie ; la page des réglages reste servie par le système (`MATCH_SYSTEM_ONLY`) ; la notification de l'appli ne se redéclenche pas elle-même (ni canal bancaire, ni appli SMS) ; seule la release poste, et une seule notification | accord | rien à faire |
| R-01 | **Moyen** : un canal mis en silencieux par l'utilisateur passait `canPost` ; il ne s'affiche ni en haut de l'écran ni en plein écran, et la notification comptait « montrée » | **réel** | **corrigé** : importance haute exigée ; **mesuré** (émulateur) : canal « Silencieux » → l'accès manque ; « Par défaut » sans « Afficher sur l'écran » → manque encore (Android n'ouvre pas en plein écran en dessous de l'importance haute) ; rétabli → « Protection activée » |
| R-02 | Faible à moyen : l'écran créé entre la fin de l'attente et l'envoi laissait une notification orpheline | **réel** (fenêtre étroite) | **corrigé** : après l'envoi, si l'écran est déjà passé (`StopDisplay.isShown`), la notification est retirée ; test JVM |
| R-03 | Moyen : sur Android 14+, l'autorisation plein écran peut être coupée ; non lue, la promesse « l'ouvre aussitôt » ne tenait plus — le cas du téléphone à l'oreille | **réel** | **corrigé** : elle fait partie de l'accès (`canOpenFullScreen`) ; **mesuré** : coupée → « Non autorisé », l'accueil le dit ; la politique de confidentialité le nuance |
| R-04 | Faible : permission accordée mais notifications coupées ou baissées, « Autoriser » ne faisait rien et rien ne menait aux réglages | **réel** | **corrigé** : l'étape ne demande que les permissions qui manquent ; sinon elle ouvre les réglages, avec un texte qui dit quoi faire ; **mesuré** (canal en silencieux : « Ouvrir les réglages », la page des notifications de l'appli) |
| R-05 | Faible : la cinquième étape seule manquante donne « Protection désactivée », alors que l'écran STOP marche sur la plupart des téléphones | décision produit | **question posée à Patrice** (A : tel quel ; B, recommandé : « Protection affaiblie ») |
| R-06 | Faible : pas d'expiration ; une notification ignorée reste, et un toucher tardif rouvre un STOP | réel | **laissé** : sans l'orpheline (R-02), le STOP rouvert tard est celui qu'on n'a pas vu, et sa règle reste vraie ; l'écran se corrige seul si l'appel est fini |
| R-07 | Faible : publique sur l'écran verrouillé, elle dit « code reçu » ou « demande de la banque » ; la politique disait « rien de personnel » | réel | **texte corrigé** : ni le numéro, ni le code, ni la banque — l'écran STOP, au-dessus de l'écran verrouillé, en dit autant |
| — | Mise à jour d'une notification affichée : le plein écran se relance-t-il ? | à mesurer | sans effet visible (`singleTask`, même écran) |

**Bilan** : aucun constat de sécurité ; 3 moyens et 2 faibles corrigés (dont 4 mesurés sur l'émulateur), 1 décision posée
à Patrice, 1 faible laissé avec sa raison.
