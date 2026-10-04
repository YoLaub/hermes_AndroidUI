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

### Réglages de sécurité du relais (variables d'environnement)

À ajouter **sur le seul service `mobile-relay`**, dans les variables d'environnement de Coolify. Le fichier
`compose-hermes-openbao.yaml` du dépôt n'est pas modifié : rien n'oblige à redéployer les autres services
(`gateway-john`, `gateway-mario`, `gateway-gaston`, WebUI…), et il faut comparer ce fichier à la pile réellement
déployée avant de le réutiliser.

| Variable | Défaut | Rôle |
|---|---|---|
| `FORWARDED_ALLOW_IPS` | non défini | Réseau du proxy, pour que le relais voie la vraie adresse du client : `10.0.0.0/8,172.16.0.0/12,192.168.0.0/16`. Non défini, les clients d'Internet ressemblent au proxy (adresse interne) et la limite ci-dessous est **inactive pour eux** (personne n'est bloqué). Avec `*`, l'en-tête `X-Forwarded-For` est cru de n'importe quel pair : un attaquant peut le falsifier pour contourner la limite ou bloquer une victime (vérifié sur un vrai processus). Le relais avertit au démarrage dans les deux cas. Si un CDN est placé devant, ses plages doivent aussi être listées. |
| `MOBILE_RELAY_AUTH_MAX_FAILURES` | `10` | Échecs d'authentification (token présenté mais invalide) par adresse avant blocage en 429, y compris pour un bon token. `0` désactive la limite. |
| `MOBILE_RELAY_AUTH_WINDOW_SECONDS` | `300` | Fenêtre de comptage et durée du blocage. |
| `MOBILE_RELAY_AUTH_THROTTLE_INTERNAL` | `0` | Les adresses internes (réseau Docker, proxy, loopback) ne sont **jamais** bloquées : les passerelles et la WebUI atteignent le relais depuis des adresses internes (par exemple `gateway-john`, vue en `10.0.2.9` dans les logs) et un token périmé d'un profil ne doit pas bloquer John. `1` les soumet aussi à la limite. |
| `MOBILE_RELAY_AUDIT_RETENTION_DAYS` | `90` | Âge maximal des lignes d'audit. `0` les garde indéfiniment. |
| `MOBILE_RELAY_PURGE_INTERVAL_SECONDS` | `3600` | Fréquence de la purge (aussi faite au démarrage). |

Il n'y a plus de CORS : l'app et les clients MCP n'en ont pas besoin.

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

### Captures d'écran (repli visuel, facultatif)

Désactivées par défaut. L'utilisateur les autorise **session par session** sur le téléphone (Android 14 ou plus
récent ; après une mise à jour, couper puis rallumer le service d'accessibilité pour qu'il reprenne la capacité de capture).
Pour que le modèle de John lise l'image :

- si son modèle lit les images, mettre `model.supports_vision: true` dans la configuration que l'agent lit.
  Constat des tests : l'agent lit cette clé dans la configuration **racine** de `HERMES_HOME`, pas dans celle du
  profil. À vérifier sur le déploiement réel (non testé ici) ;
- sinon, configurer `auxiliary.vision` : un modèle de vision décrit alors l'image et John ne reçoit que du texte ;
- sans l'un des deux, John reçoit seulement une légende texte.

L'image part vers ce fournisseur et n'est jamais stockée ni journalisée par le relais.

Règles de comportement à donner à John :

1. Observer d'abord (`mobile_observe`) ; ne demander une capture que si l'arbre d'accessibilité est inutilisable.
2. Cliquer par élément ; `mobile_tap_xy` seulement juste après une capture, avec son `screen_revision`.
3. Ne jamais appuyer sur « Publier » ou équivalent sans ordre explicite de l'utilisateur.

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
