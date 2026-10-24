---
type: Handoff
title: AETHER relay integration for the next developer
description: Implemented server-side AETHER pairing and MCP routing, per-profile avatars, wire contract and remaining client work.
timestamp: 2026-10-24
tags: [aether, relay, mcp, handoff]
---
# Message pour l'agent de développement

Le propriétaire a autorisé l'extension du relais existant pour connecter ses avatars AETHER à Hermes sur le VPS. AETHER vise tous les supports ; priorité : finir et tester macOS avec Hermes avant Windows. Chaque profil Hermes peut avoir ses propres avatars (décision du propriétaire, 2026-10-24). L'utilisateur colle un texte affiché par AETHER à l'agent du profil choisi, sans terminal ni configuration côté client. Aucun push, build Docker ou déploiement n'a été effectué. Le propriétaire a explicitement demandé de conserver le commit sur la branche actuelle `dev`, malgré la mention `main` dans AGENTS.md. Aucun secret de production n'a été consulté ni modifié.

Le relais côté serveur est implémenté et testé avec un client WebSocket simulé. Le connecteur AETHER (macOS d'abord) et son écran d'appairage restent à développer dans `/Users/yoann/Developer/project-aether`. Ne pas annoncer une intégration réelle Hermes–Mac déjà opérationnelle.

# Organisation et compatibilité

- `aether_protocol.py` : schémas stricts et définitions des deux outils MCP.
- `aether_relay.py` : connexions, commandes, délais et corrélation, sans opérations Android.
- `aether_routes.py` : WebSocket `/ws/aether`, authentification et validation.
- `aether_routes.py` porte aussi l'appairage `/api/aether/pair/start` et `/api/aether/pair/poll` ; `database.py` la table `aether_claims`.
- `server.py` : branchements courts dans l'appairage et le MCP existants ; authentification de profil réutilisée.
- `database.py` : migrations additives `device_kind` dans `paired_devices` et `pairing_codes` (valeur historique `android`) et `profile` dans `paired_devices` (profil propriétaire d'un avatar, `NULL` pour Android). `init_db` est réexécutable. Les codes, jetons et remplacements d'identité ne traversent pas les catégories `android` / `aether`.

Le protocole Android `mobile-control/1`, ses codes d'erreur et ses sessions ne sont pas modifiés. Le contrôle Android reste réservé à `john`. Les outils AETHER sont visibles pour tout profil authentifié, mais chaque profil ne voit et ne pilote que les avatars qu'il possède. L'avatar d'un autre profil répond `avatar_unavailable`, exactement comme un avatar absent ; un avatar sans propriétaire n'est joignable par personne. On appaire un avatar, pas une machine : une machine peut porter plusieurs avatars de profils différents. Chaque mouvement désigne explicitement son `device_id`.

# Appairage piloté par l'agent

L'adresse du relais est embarquée par défaut dans AETHER (`https://mobile-relay-hermes.john-world.store`, modifiable dans ses réglages). Aucun jeton administrateur n'intervient.

1. AETHER appelle `POST /api/aether/pair/start` avec `{"device_id":"<nouvel identifiant stable de l'avatar>","device_name":"<nom affiché>"}` (champs inconnus refusés). Réponse : `claim_code` (format `XXXX-XXXX`), `poll_token`, `expires_in_seconds:300` et `agent_prompt`, un texte prêt à coller qui nomme l'outil et le code.
2. AETHER affiche `agent_prompt`. L'utilisateur le colle à l'agent Hermes du profil voulu, qui appelle l'outil MCP `aether_claim_avatar` avec `{"code":"..."}`. Le relais rattache l'avatar au profil authentifié de l'appel ; la réponse contient `device_id` et `device_name`, jamais de jeton.
3. AETHER interroge `POST /api/aether/pair/poll` avec `{"poll_token":"..."}` toutes les quelques secondes : `{"status":"pending"}` tant que rien n'est réclamé, puis une seule fois `{"status":"paired","device_token":"...","device_id":"...","profile":"..."}`. Ensuite 404, comme pour un code expiré ou inconnu.
4. AETHER stocke `device_token` dans le coffre du système (Trousseau sur macOS), jamais dans une URL, Git, les logs ou le contexte LLM. `poll_token` reste privé à AETHER et ne figure jamais dans `agent_prompt`.

Règles : code à usage unique, haché en base, valable 300 s ; 5 démarrages par source sur 5 minutes et 10 demandes en attente au maximum ; un `device_id` déjà appairé renvoie 409 (AETHER doit générer un nouvel identifiant pour réappairer) ; relancer `start` pour un même `device_id` remplace la demande précédente. `/api/pair/*` est redevenu réservé à Android et refuse `device_kind:"aether"` (422). Derrière Traefik sans `FORWARDED_ALLOW_IPS`, toutes les sources se confondent : la limite de 5 démarrages devient globale.

# Contrat effectivement implémenté

Ce contrat est la référence de cette tranche. Il diffère de la proposition d'audit du VPS : endpoint `/ws/aether`, outil `aether_avatar_move_to`, statut multi-appareils, réponses terminales simples sans champs `accepted_at`/`completed_at`. Ne pas implémenter aveuglément les anciens exemples : les champs inconnus sont rejetés.

Connexion sortante d'AETHER : `wss://<origine-du-relais>/ws/aether`, sous-protocole `aether.v1`, en-tête `Authorization: Bearer <device_token>`. Le relais conserve son endpoint MCP existant `/mcp` et son authentification de profil ; aucune deuxième configuration MCP n'est nécessaire si Hermes utilise déjà ce relais, mais il doit rafraîchir sa découverte d'outils.

Toute trame JSON contient exactement :

```json
{"version":"aether.v1","type":"client.hello","message_id":"identifiant-unique","sent_at":"2026-10-23T12:00:00Z","payload":{"device_id":"mac-id","client_version":"0.1.0","capabilities":["telemetry.status","avatar.move_to"]}}
```

`client.hello` doit arriver sous 5 secondes. `server.ready` fournit `session_id`, `heartbeat_interval_seconds:20` et `max_command_ttl_seconds:30`. Le client envoie une trame `heartbeat` avec `payload:{}` toutes les 20 secondes ; le relais répond à cette trame. Après 40 secondes sans message entrant, il ferme la connexion. Les messages applicatifs sont limités à 16 Kio ; cette vérification vient après la réception par le transport, dont la limite globale reste celle d'Uvicorn pour préserver les captures Android.

`telemetry.status` accepte uniquement :

```json
{"availability":"ready","avatar":{"screen_x":0.4,"screen_y":0.7,"posture":"walk","moving":false}}
```

`availability` : `ready`, `busy` ou `unavailable`. Coordonnées normalisées `[0,1]`, postures `walk`, `run`, `sneak`, `cautious`. Pas de capture, nom de fenêtre, presse-papiers, batterie ou autre donnée personnelle. Le statut mis en cache expire après 40 secondes sans nouvelle télémétrie (les heartbeats ne le rafraîchissent pas).

Commande sortante `command.avatar_move_to`, payload :

```json
{"command_id":"id-unique","expires_at":"2026-10-23T12:00:20Z","screen_x":0.4,"screen_y":0.7,"posture":"walk"}
```

Réponses du client :

- `command.accepted` : `{"command_id":"id-unique"}` ; ne termine pas l'appel MCP.
- `command.completed` : `{"command_id":"id-unique","avatar":{"screen_x":0.4,"screen_y":0.7,"posture":"walk","moving":false}}`. Le relais vérifie l'arrêt, la posture et une tolérance de 0,01 par axe autour de la cible. Il s'agit toujours d'une déclaration du client, pas d'une mesure indépendante.
- `command.error` : `{"command_id":"id-unique","code":"execution_failed"}`. Codes AETHER uniquement : `invalid_payload`, `unsupported_capability`, `avatar_unavailable`, `command_expired`, `command_rejected`, `execution_failed`, `session_lost`, `unauthorized`, `protocol_error`.

# Outils MCP et cycle des commandes

- `aether_get_status`, arguments `{}` : liste `devices` des avatars connectés du profil appelant, avec `device_id`, connexion et dernier statut autorisé ou `null`.
- `aether_claim_avatar`, arguments `code` : rattache un avatar en attente au profil appelant (voir l'appairage).
- `aether_avatar_move_to` (avatars du profil appelant uniquement), arguments `device_id`, `screen_x`, `screen_y`, `posture` optionnelle (`walk`), `ttl_seconds` optionnel (20, intervalle 1–30). Un seul mouvement en vol par appareil. L'appel MCP attend un résultat terminal et renvoie du JSON dans son bloc texte, selon le format déjà utilisé par le relais.

Une déconnexion/remplacement de connexion termine les appels en attente par `session_lost`. Aucun mouvement n'est mis en file ou renvoyé à la reconnexion. Les réponses d'une ancienne connexion, d'un autre appareil ou d'une commande expirée ne valident pas la nouvelle commande. L'autorité du jeton est revérifiée avant chaque mouvement et à chaque message entrant ; sa rotation rend l'ancienne connexion inapte à recevoir de nouveaux mouvements. Il n'y a pas de nouvel écran/API de révocation dans cette tranche.

IMPORTANT : expiration et perte de session signifient que le résultat est inconnu, pas que le mouvement physique est arrêté. Il n'existe pas de commande d'annulation ici. Le client doit rejeter les commandes reçues après `expires_at`, ne jamais rejouer un identifiant de commande et ne pas déclarer « terminé » à la simple acceptation locale. La politique d'arrêt physique à la déconnexion reste à convenir avec le connecteur AETHER. Ne pas réessayer automatiquement un mouvement dont le résultat est inconnu.

# Déploiement et suite

1. Relire ces changements et sauvegarder la base avant la migration. Conserver un seul worker/processus du relais : les connexions et futures sont en mémoire, comme pour Android. Pas de répartition de charge multi-workers.
2. Aucune nouvelle variable d'environnement. Conserver la terminaison TLS, le proxy WebSocket et la configuration de confiance des proxys existants (`FORWARDED_ALLOW_IPS`). Aucun nouveau port n'est requis.
3. Déployer uniquement après instruction explicite du propriétaire ; rafraîchir les outils MCP des profils concernés.
4. Dans AETHER : appairage `start`/`poll` avec affichage du texte pour l'agent, enregistrer le jeton dans le coffre du système, ouvrir WSS en sortie, implémenter validation/déduplication/expiration, reconnexion et retour de télémétrie. L'API AETHER locale reste une API REST privée ; elle n'est pas soudain devenue un serveur MCP.
5. Tester le vrai parcours Hermes → relais → avatar natif macOS, avec deux profils distincts, puis les pertes réseau, rotation du jeton et reconnexion sans rejeu. Ces essais ne sont pas effectués ici.

Ne pas toucher aux changements préexistants : suppression de `compose-hermes-openbao.yaml`, fichiers/dossiers non suivis `AGENTS.md`, `compose-hermes-openbao-mobile-relay.yaml`, `dashboard-web/`, `design&refonte/`, `feature/`.

# Validation TDD

Base avant changements : 258 tests du relais passants. Première tranche : 273 tests passants (15 nouveaux). Tranche multi-profils du 2026-10-24 : 286 tests passants, documentation OKF conforme et `git diff --check` sans erreur. Les tests AETHER ont été ajoutés en échec avant implémentation (typage d'appareils, migration, routage, révocation, lien d'appairage et résultat terminal). Tests ciblés : jetons refusés dans les deux sens, profil refusé, payload privé rejeté, résultat corrélé, acceptation distincte de la fin, position finale incorrecte, déconnexion/remplacement, expiration et ancien schéma SQLite.

Commande utilisée (aucune construction Docker/Android) :

```sh
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=mobile-relay /Users/yoann/Developer/project-aether/aether-brain/.venv/bin/python -m pytest -q mobile-relay/tests
```

Cet environnement satisfait les dépendances du relais ; pour un autre poste, utiliser l'environnement Python du dépôt avec `mobile-relay/requirements.txt`. Avertissement existant : dépréciation Starlette/httpx du TestClient.
