# Spécification Technique de l'API Hermes WebUI

> Ce document fournit une analyse exhaustive et détaillée de l'API du service `hermes-webui` (image `ghcr.io/nesquena/hermes-webui:0.51.92`), issue du reverse-engineering du code source extrait (`webui-backend`).

---

## 1. Vue d'ensemble de l'Architecture Serveur

### 1.1 Framework et Pile Technologique
- **Framework utilisé :** Python Standard Library (`http.server.ThreadingHTTPServer` et `http.server.BaseHTTPRequestHandler`).
- **Dépendances :** Aucune dépendance lourde de framework web (pas de FastAPI, Flask, Starlette ou Tornado). Uniquement la bibliothèque standard Python et `pyyaml >= 6.0`.
- **Modèle de concurrence :** Multi-threadé (`ThreadingHTTPServer`, `daemon_threads = True`), avec synchronisation via verrous `threading.Lock()` et queues mémoires pour le streaming d'événements.
- **Port et Hôte par défaut :** `0.0.0.0:8000` (ou configurable via `HERMES_WEBUI_PORT` et `HERMES_WEBUI_HOST`).

### 1.2 Point d'entrée et Routage
- `server.py` : Instancie `QuietHTTPServer` (sous-classe de `ThreadingHTTPServer` avec gestion propre des déconnexions réseau brusques et keepalive TCP).
- `api/routes.py` : Contient les fonctions de dispatching :
  - `handle_get(handler, parsed)`
  - `handle_post(handler, parsed)`
  - `handle_patch(handler, parsed)`
  - `handle_delete(handler, parsed)`
- **Format d'échange :** JSON UTF-8 pour les requêtes/réponses REST, et `text/event-stream` (Server-Sent Events) pour le streaming temps réel. Support de la compression `gzip` pour les réponses JSON > 1 Ko lorsque le client envoie `Accept-Encoding: gzip`.

---

## 2. Authentification et Sécurité

### 2.1 Configuration de l'authentification
L'authentification est **désactivée par défaut**. Elle devient active si :
1. La variable d'environnement `HERMES_WEBUI_PASSWORD` est définie et non vide, ou
2. Un hash de mot de passe est enregistré dans le fichier `settings.json` (`password_hash`).

### 2.2 Hachage et Validation du Mot de Passe
- **Algorithme :** `PBKDF2-HMAC-SHA256` avec **600 000 itérations** (recommandation OWASP).
- **Sel (Salt) :** Clé de 32 octets persistée dans `STATE_DIR/.pbkdf2_key` (permissions `0600`).
- **Mise en cache du hash :** Calculé une seule fois puis mis en cache thread-safe (`_AUTH_HASH_CACHE`) pour éviter une pénalité CPU de ~1 seconde par requête entrante.
- **Limiteur de tentatives (Brute-force protection) :**
  - Maximum **5 tentatives** erronées par fenêtre de **60 secondes** par adresse IP.
  - Au-delà, renvoie `HTTP 429 Too Many Attempts`.

### 2.3 Sessions et Cookies
- **Création de session :** Génération d'un token aléatoire cryptographique de 32 octets (`secrets.token_hex(32)`).
- **Signature HMAC :** Signature SHA256 avec la clé secrète stockée dans `STATE_DIR/.signing_key`.
- **Valeur du Cookie :** `<raw_token>.<signature_hmac_hex>`
- **Nom du Cookie :** `hermes_session`
- **Attributs du Cookie :**
  - `Path=/`
  - `HttpOnly=True`
  - `SameSite=Lax`
  - `Max-Age` : Durée de validité (défaut = 30 jours, configurable via `HERMES_WEBUI_SESSION_TTL` entre 60s et 365j).
  - `Secure` : Activé si HTTPS ou contexte sécurisé (`HERMES_WEBUI_SECURE=1` ou `X-Forwarded-Proto: https`).
- **Persistance des sessions :** Stockées sous forme de dictionnaire JSON `token -> expiry_timestamp` dans `STATE_DIR/.sessions.json`. Nettoyage paresseux à chaque vérification.

### 2.4 Protection CSRF
- Les clients navigateurs doivent envoyer le header `X-Hermes-CSRF-Token`.
- **Règle cruciale pour les applications clientes natives (Android) :** Le backend détecte les clients non-navigateurs (absence des headers `Origin` et `Referer`). Les requêtes HTTP natives sans `Origin` / `Referer` ne sont pas soumises au blocage CSRF.
- Pour s'authentifier depuis l'application mobile :
  1. Appeler `POST /api/auth/login` avec le mot de passe.
  2. Récupérer le header `Set-Cookie: hermes_session=...`.
  3. Renvoyer le header `Cookie: hermes_session=...` sur toutes les requêtes suivantes.

---

## 3. Gestion des Profils d'Agents

Hermes supporte l'exécution sous plusieurs identités/profils (ex: `default`, `john`, `mario`, `gaston`). Chaque profil dispose de son propre répertoire racine `$HERMES_HOME/profiles/<profile_name>` avec ses mémoires, sessions, skills, persona (`SOUL.md`), configuration et clés API.

### 3.1 Isolation par Client (Per-Request Profile Context)
Pour éviter que deux utilisateurs ou deux clients mobiles ne s'écrasent mutuellement, le backend utilise un thread-local context (`_tls.profile`) alimenté par le cookie :
- **Cookie :** `hermes_profile=<nom_du_profil>`
- Si le cookie est absent, le serveur bascule sur le profil actif global (`_active_profile`).

### 3.2 Endpoints REST des Profils

#### `GET /api/profiles`
Liste tous les profils existants et indique le profil actif pour le client.
- **Réponse (200 OK) :**
```json
{
  "profiles": [
    {
      "name": "default",
      "path": "/home/user/.hermes",
      "is_default": true,
      "is_active": true,
      "gateway_running": false,
      "model": "anthropic/claude-3-5-sonnet",
      "provider": "openrouter",
      "has_env": true,
      "skill_count": 8
    },
    {
      "name": "mario",
      "path": "/home/user/.hermes/profiles/mario",
      "is_default": false,
      "is_active": false,
      "gateway_running": false,
      "model": "openai/gpt-4o",
      "provider": "openai",
      "has_env": true,
      "skill_count": 3
    }
  ],
  "active": "default"
}
```

#### `GET /api/profile/active`
Retourne le nom et le chemin absolu du profil actif.
- **Réponse (200 OK) :**
```json
{
  "name": "default",
  "path": "/home/user/.hermes"
}
```

#### `POST /api/profile/switch`
Bascule vers un profil spécifié. Renvoie un cookie `Set-Cookie: hermes_profile=<name>; Path=/; HttpOnly; SameSite=Lax`.
- **Corps de la requête :**
```json
{
  "name": "mario"
}
```
- **Réponse (200 OK) :**
```json
{
  "ok": true,
  "active": "mario",
  "hermes_home": "/home/user/.hermes/profiles/mario"
}
```

#### `POST /api/profile/create`
Crée un nouveau profil avec clonage optionnel depuis un profil existant.
- **Corps de la requête :**
```json
{
  "name": "gaston",
  "clone_from": "default",
  "clone_config": true,
  "default_model": "anthropic/claude-3-5-sonnet",
  "model_provider": "openrouter",
  "api_key": "sk-or-..."
}
```

#### `POST /api/profile/delete`
Supprime un profil nommé (le profil `default` ne peut pas être supprimé).
- **Corps de la requête :**
```json
{
  "name": "gaston"
}
```

---

## 4. Gestion des Sessions de Chat

Les sessions correspondent aux fils de discussion. Chaque session contient son historique de messages, son modèle sélectionné, son workspace et son statut d'exécution.

### 4.1 Endpoints REST des Sessions

#### `GET /api/sessions`
Récupère la liste de toutes les sessions.
- **Paramètres Query :**
  - `all_profiles=1` : (Optionnel) Retourne les sessions de tous les profils. Sans ce paramètre, seules les sessions associées au profil actif sont retournées.
- **Réponse (200 OK) :**
```json
{
  "sessions": [
    {
      "session_id": "0192e4ab-1234-7890-abcd-ef0123456789",
      "title": "Optimisation du script de parsing",
      "workspace": "/home/user/workspace/project-1",
      "model": "anthropic/claude-3-5-sonnet",
      "model_provider": "openrouter",
      "message_count": 14,
      "created_at": 1727500000.0,
      "updated_at": 1727504200.0,
      "last_message_at": 1727504200.0,
      "pinned": false,
      "archived": false,
      "profile": "default"
    }
  ],
  "other_profile_count": 3
}
```

#### `GET /api/session?session_id=<id>`
Récupère les détails complets d'une session et ses messages.
- **Paramètres Query :**
  - `session_id` : UUID de la session (obligatoire).
  - `messages=1` : Si `0`, renvoie uniquement les métadonnées sans les messages (rapide).
  - `msg_limit=50` : Limite le nombre de messages récents retournés.
  - `msg_before=<index>` : Pagination ascendante vers les messages plus anciens.
- **Réponse (200 OK) :**
```json
{
  "session": {
    "session_id": "0192e4ab-1234-7890-abcd-ef0123456789",
    "title": "Optimisation du script de parsing",
    "workspace": "/home/user/workspace/project-1",
    "model": "anthropic/claude-3-5-sonnet",
    "model_provider": "openrouter",
    "profile": "default",
    "created_at": 1727500000.0,
    "updated_at": 1727504200.0,
    "pinned": false,
    "archived": false,
    "messages": [
      {
        "role": "user",
        "content": "Peux-tu inspecter main.py et corriger le bug de fuite de mémoire ?",
        "timestamp": 1727500100
      },
      {
        "role": "assistant",
        "content": "J'analyse le fichier main.py...",
        "timestamp": 1727500105,
        "tool_calls": [
          {
            "id": "call_12345",
            "name": "read_file",
            "args": {"path": "main.py"}
          }
        ]
      }
    ],
    "_messages_truncated": false,
    "_messages_offset": 0
  }
}
```

#### `POST /api/session/new`
Crée une nouvelle session de discussion vierge.
- **Corps de la requête :**
```json
{
  "workspace": "/home/user/workspace",
  "model": "anthropic/claude-3-5-sonnet",
  "model_provider": "openrouter",
  "profile": "default"
}
```
- **Réponse (200 OK) :**
```json
{
  "session_id": "0192e4ba-4567-7890-abcd-123456789abc",
  "session": { ... }
}
```

#### `POST /api/session/delete`
Supprime définitivement une session.
- **Corps :** `{"session_id": "..."}`

#### `POST /api/session/rename`
Renomme le titre de la session.
- **Corps :** `{"session_id": "...", "title": "Nouveau titre"}`

#### `POST /api/session/clear`
Efface tous les messages de la session sans la supprimer.
- **Corps :** `{"session_id": "..."}`

#### `POST /api/session/undo`
Annule le dernier tour de discussion (supprime le dernier message assistant et utilisateur).
- **Corps :** `{"session_id": "..."}`

#### `POST /api/session/pin`
Épingle ou désépingle une session en haut de liste.
- **Corps :** `{"session_id": "...", "pinned": true}`

#### `POST /api/session/archive`
Archive ou désarchive une session.
- **Corps :** `{"session_id": "...", "archived": true}`

---

## 5. Protocole de Streaming (Chat, LLM & Tool Calls)

Le système de conversation fonctionne en deux étapes asynchrones :
1. Déclenchement de l'exécution via une requête `POST /api/chat/start`.
2. Consommation du flux temps réel via une connexion **Server-Sent Events (SSE)** sur `GET /api/chat/stream`.

### 5.1 Étape 1 : Démarrage du Turn (`POST /api/chat/start`)
- **URL :** `/api/chat/start`
- **Méthode :** `POST`
- **Headers :** `Content-Type: application/json`
- **Corps de la requête :**
```json
{
  "session_id": "0192e4ab-1234-7890-abcd-ef0123456789",
  "message": "Analyse les performances du serveur et liste les fichiers modifiés",
  "workspace": "/home/user/workspace",
  "model": "anthropic/claude-3-5-sonnet",
  "model_provider": "openrouter",
  "profile": "default",
  "attachments": [
    {
      "filename": "screenshot.png",
      "path": "/home/user/.hermes/attachments/.../screenshot.png",
      "mime": "image/png"
    }
  ]
}
```
- **Réponse (200 OK) :**
```json
{
  "stream_id": "4a7b9c1d2e3f4051a6b7c8d9e0f1a2b3",
  "session_id": "0192e4ab-1234-7890-abcd-ef0123456789",
  "pending_started_at": 1727504200.123,
  "turn_id": "turn_abc123",
  "title": "Analyse des performances du serveur"
}
```
*(Si une exécution est déjà active dans cette session, le serveur répond avec un code HTTP `409 Conflict`).*

---

### 5.2 Étape 2 : Flux Server-Sent Events (`GET /api/chat/stream`)
- **URL :** `/api/chat/stream?stream_id=<stream_id>`
- **Méthode :** `GET`
- **Headers requis :** `Accept: text/event-stream`
- **Headers de réponse du serveur :**
  - `Content-Type: text/event-stream; charset=utf-8`
  - `Cache-Control: no-cache`
  - `Connection: keep-alive`
  - `X-Accel-Buffering: no`
- **Heartbeat :** Le serveur émet périodiquement un commentaire `: heartbeat\n\n` toutes les 15 secondes pour maintenir la connexion active contre les timeouts réseau.

---

### 5.3 Catalogue Exhaustif des Frames SSE

Chaque frame suit le standard W3C SSE :
```text
event: <nom_evenement>
data: <json_string>

```

#### 1. `token` (Token de génération de texte)
Émis incrémentalement au fur et à mesure que le LLM génère sa réponse textuelle finale.
```json
event: token
data: {"text": "Voici l'analyse des "}
```

#### 2. `reasoning` (Trace de réflexion / Thinking)
Émis lors de la phase de "Thinking" (ex: modèles raisonneurs Claude 3.7 Thinking, o1, DeepSeek-R1).
```json
event: reasoning
data: {"text": "L'utilisateur demande un diagnostic mémoire, commençons par inspecter htop..."}
```

#### 3. `tool` (Début d'exécution d'un outil)
Notifie que l'agent a décidé d'appeler un outil (tool-call).
```json
event: tool
data: {
  "event_type": "tool.started",
  "name": "execute_command",
  "preview": "ps aux --sort=-%mem | head -n 10",
  "args": {
    "command": "ps aux --sort=-%mem | head -n 10"
  }
}
```

#### 4. `tool_complete` (Fin d'exécution d'un outil)
Notifie la complétion de l'appel d'outil avec durée et statut d'erreur.
```json
event: tool_complete
data: {
  "event_type": "tool.completed",
  "name": "execute_command",
  "preview": "USER       PID %CPU %MEM    VSZ   RSS TTY ...",
  "args": {
    "command": "ps aux --sort=-%mem | head -n 10"
  },
  "duration": 0.35,
  "is_error": false
}
```

#### 5. `approval` (Demande d'approbation humaine requise)
Émis lorsque l'agent souhaite exécuter une action sensible nécessitant la validation explicite de l'utilisateur.
```json
event: approval
data: {
  "id": "appr_789abc",
  "tool": "execute_command",
  "command": "rm -rf /tmp/build_cache",
  "description": "Supprimer le répertoire de cache temporaire"
}
```

#### 6. `clarify` (Demande de clarification de l'agent)
Émis lorsque l'agent pose une question à choix multiples ou demande une précision pour poursuivre.
```json
event: clarify
data: {
  "question": "Souhaitez-vous déployer en environnement de Staging ou Production ?",
  "options": ["Staging", "Production"]
}
```

#### 7. `metering` (Télémétrie et consommation de tokens)
Transmet la consommation temps réel de tokens et l'estimation des coûts.
```json
event: metering
data: {
  "session_id": "0192e4ab-1234-7890-abcd-ef0123456789",
  "tokens_in": 1420,
  "tokens_out": 350,
  "total_tokens": 1770,
  "cost_usd": 0.0084,
  "usage": {
    "prompt_tokens": 1420,
    "completion_tokens": 350
  }
}
```

#### 8. `compressing` & `compressed` (Compactage de contexte)
Indique que le contexte dépasse la fenêtre maximale et qu'un compactage automatique est en cours.
```json
event: compressing
data: {
  "session_id": "0192e4ab-1234-7890-abcd-ef0123456789",
  "message": "Auto-compressing context to continue..."
}
```

#### 9. `warning` (Avertissements opérationnels)
Notifie un repli de modèle (fallback) ou un rate-limit temporaire.
```json
event: warning
data: {
  "type": "fallback",
  "message": "Rate limit hit on primary provider, falling back to backup..."
}
```

#### 10. `done` (Fin de l'exécution du tour)
Transmet l'état final de la session avec tous les messages consolidés.
```json
event: done
data: {
  "session": {
    "session_id": "0192e4ab-1234-7890-abcd-ef0123456789",
    "messages": [ ... ]
  },
  "usage": {
    "total_tokens": 2100
  }
}
```

#### 11. `stream_end` (Clôture définitive du flux)
Signal final indiquant que la connexion SSE peut être fermée proprement par le client.
```json
event: stream_end
data: {
  "session_id": "0192e4ab-1234-7890-abcd-ef0123456789"
}
```

#### 12. `cancel` (Annulation par l'utilisateur)
```json
event: cancel
data: {
  "message": "Cancelled by user"
}
```

#### 13. `apperror` (Erreur d'exécution de l'agent)
```json
event: apperror
data: {
  "error": "Provider authentication failed",
  "message": "Invalid API key"
}
```

---

### 5.4 Commandes d'Intervention en cours de Stream

- **Annuler une génération en cours :**
  - `POST /api/chat/cancel` avec `{"stream_id": "..."}`
- **Intervenir / Guider l'agent en direct (Steering) :**
  - `POST /api/chat/steer` avec `{"stream_id": "...", "message": "Arrête cette recherche et concentre-toi sur le fichier database.py"}`
- **Répondre à une demande d'approbation :**
  - `POST /api/approval/respond` avec `{"session_id": "...", "approval_id": "...", "approved": true}`
- **Répondre à une clarification :**
  - `POST /api/clarify/respond` avec `{"session_id": "...", "answer": "Option sélectionnée"}`

---

## 6. Gestion des Workspaces et des Fichiers

### 6.1 Exploration de fichiers
- `GET /api/workspaces` : Liste tous les dossiers workspaces enregistrés.
- `GET /api/list?session_id=<id>&path=<rel_path>` : Liste le contenu d'un répertoire.
  - **Réponse :**
  ```json
  {
    "entries": [
      {
        "name": "src",
        "path": "src",
        "is_dir": true,
        "size": 0,
        "modified": 1727400000.0
      },
      {
        "name": "README.md",
        "path": "README.md",
        "is_dir": false,
        "size": 2400,
        "modified": 1727401000.0
      }
    ],
    "path": "."
  }
  ```

### 6.2 Lecture et Téléchargement de fichiers
- `GET /api/file?session_id=<id>&path=<rel_path>` : Lit le contenu textuel d'un fichier.
- `GET /api/file/raw?session_id=<id>&path=<rel_path>&download=1` : Télécharge le flux binaire brut d'un fichier (avec support des `Range` headers HTTP).
- `GET /api/media?path=<abs_path>` : Affiche les images générées ou capturées par l'agent (ex: captures d'écran du navigateur d'agent).

### 6.3 Téléversement (Upload) de pièces jointes
- **URL :** `/api/upload`
- **Méthode :** `POST`
- **Content-Type :** `multipart/form-data`
- **Champs du formulaire :**
  - `session_id` : Identifiant de la session liée.
  - `file` : Données binaires du fichier.
- **Réponse (200 OK) :**
```json
{
  "filename": "logs.txt",
  "path": "/home/user/.hermes/attachments/0192.../logs.txt",
  "size": 45120,
  "mime": "text/plain",
  "is_image": false
}
```
*(Le `path` renvoyé peut ensuite être passé dans le tableau `attachments` de `POST /api/chat/start`).*

---

## 7. Santé et Métadonnées Système

- `GET /health` :
```json
{
  "status": "ok",
  "sessions": 12,
  "active_streams": 0,
  "active_runs": 0,
  "uptime_seconds": 3840.5
}
```
- `GET /api/models` : Liste des modèles LLM disponibles configurés sur l'instance (ex: Claude 3.5 Sonnet, GPT-4o, Llama 3.3, Mistral Large).
