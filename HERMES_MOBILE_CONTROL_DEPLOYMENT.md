# Guide de Déploiement & Utilisation — Contrôle Mobile Hermes

**Version :** 1.0  
**Date :** 1er octobre 2026

---

## 1. Déploiement du Service Relais VPS (`mobile-relay`)

Le service relais est ultra-léger (< 256 Mio de RAM, base SQLite autonome).

### Bloc Docker Compose pour Coolify / VPS

Ajoutez le service suivant dans votre `docker-compose.yml` Coolify :

```yaml
  mobile-relay:
    build:
      context: ./mobile-relay
      dockerfile: Dockerfile
    container_name: hermes-mobile-relay
    restart: unless-stopped
    mem_limit: 256m
    pids_limit: 100
    security_opt:
      - no-new-privileges:true
    volumes:
      - mobile-relay-data:/data
    environment:
      - MOBILE_RELAY_DB_PATH=/data/mobile_relay.db
      - MOBILE_RELAY_ADMIN_TOKEN=${MOBILE_RELAY_ADMIN_TOKEN}
      - MOBILE_CONTROL_TOKEN_MARIO=${MOBILE_CONTROL_TOKEN_MARIO}
      - MOBILE_CONTROL_TOKEN_GASTON=${MOBILE_CONTROL_TOKEN_GASTON}
      - MOBILE_CONTROL_TOKEN_JOHN=${MOBILE_CONTROL_TOKEN_JOHN}
    ports:
      - "8765:8765"
    networks:
      - default

volumes:
  mobile-relay-data:
```

---

## 2. Configuration MCP pour le Profil Pilote Hermes (`mario`)

Dans la configuration MCP du profil Hermes (fichier `config.yaml` ou `mcp_config.json` de `mario`), ajoutez l'endpoint Streamable HTTP authentifié avec son token dédié :

```json
{
  "mcpServers": {
    "mobile_control": {
      "url": "http://mobile-relay:8765/mcp",
      "headers": {
        "Authorization": "Bearer ${MOBILE_CONTROL_TOKEN_MARIO}"
      }
    }
  }
}
```

---

## 3. Guide Utilisateur — Activation & Contrôle LinkedIn

### Étape 1 : Activer le Service d'Accessibilité sur Android
1. Ouvrez l'application **Hermes** sur votre téléphone.
2. Cliquez sur l'icône **Contrôle Mobile** (icône smartphone en haut à droite).
3. Cliquez sur **Activer** à côté de *"Service d'accessibilité Hermes"*.
4. Dans les paramètres Android, sélectionnez **Hermes** et activez le service.

### Étape 2 : Appairer le Téléphone avec le Relais VPS
1. Générez un code à usage unique (valable 5 min) depuis un poste de confiance, avec le token administrateur du relais :
   ```bash
   curl -s -X POST "$RELAY_URL/api/pair/generate" \
     -H "Authorization: Bearer $MOBILE_RELAY_ADMIN_TOKEN" \
     -H "Content-Type: application/json" -d '{}'
   ```
2. Cliquez sur l'icône d'engrenage dans l'encadré *Relais VPS*.
3. Vérifiez l'URL de votre serveur (ex: `https://mobile-relay-hermes.john-world.store` ou domaine configuré).
4. Saisissez le code, puis cliquez sur **Enregistrer** : c'est le relais qui vérifie le code et délivre le token de l'appareil.
   L'indicateur passe à « Connecté » seulement quand le relais a authentifié le téléphone.
5. Si le téléphone est déjà enregistré sur le relais (erreur « déjà enregistré »), son enregistrement doit être supprimé côté relais avant un nouvel appairage.

### Étape 3 : Démarrer une Session pour LinkedIn
1. Dans l'écran **Contrôle par Hermes**, cliquez sur **Démarrer une session de contrôle**.
2. Sélectionnez :
   - **Application cible :** LinkedIn (`com.linkedin.android`)
   - **Profil autorisé :** John (`john`)
   - **Mode :** Interaction (ou Observation seule)
   - **Durée :** 15 min (défaut)
3. Cliquez sur **Démarrer**.
   - Une notification Android persistante s'affiche immédiatement avec le bouton rouge **"ARRÊTER LE CONTRÔLE"**.

### Étape 4 : Scénario LinkedIn par Hermes
1. L'agent Hermes peut vérifier l'état avec `mobile_control_status`.
2. L'agent observe le post avec `mobile_observe` et vous propose un commentaire.
3. L'agent prépare le texte dans le champ de commentaire avec `mobile_set_text`.
4. **Validation humaine finale :** Vous vérifiez le texte à l'écran sur votre téléphone et cliquez vous-même sur "Publier" !
5. Vous pouvez interrompre la session à tout moment en cliquant sur **"ARRÊTER LE CONTRÔLE"** dans l'app ou dans la barre de notification.
