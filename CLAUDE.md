# CLAUDE.md — Conventions du repo hermes-android

## Contexte
Client Android natif pour Hermes WebUI (`ghcr.io/nesquena/hermes-webui`), plus un
relais VPS (`mobile-relay`) qui laisse l'agent Hermes piloter le téléphone via un
service d'accessibilité. Usage personnel, profil `john`.
**Deux problèmes à égalité : piloter Hermes depuis le mobile (chat, profils, sessions)
ET laisser l'agent agir sur le téléphone (contrôle mobile).** En cas de conflit de
priorité, ne sacrifier aucun des deux sans le dire.

## Objectifs
1. Client de chat Android fiable (SSE, outils, approbations, profils).
2. Contrôle mobile par l'agent, borné et révocable (protocole `mobile-control/1`).

## Contraintes
- Public : usage personnel, pas de distribution publique.
- Le contrôle mobile est restreint au profil `john`, côté app ET relais.
- Validation humaine finale pour toute action irréversible (ex. publier un post).

## Décisions techniques
- App : Kotlin, Jetpack Compose, Material 3, MVI/MVVM, OkHttp + SSE, DataStore (`android-app/`).
- Relais : Python, SQLite, WebSocket + MCP Streamable HTTP (`mobile-relay/`).
- WebUI : `webui-backend/`, modifiable (périmètre ouvert).
- Hébergement : VPS via Coolify, images publiées sur ghcr par GitHub Actions
  (`.github/workflows/`). URL relais : `https://mobile-relay-hermes.john-world.store`.

## Méthode
- TDD et commits incrémentaux : voir CLAUDE.md global.
- Git : branche `main`, commits locaux ; push uniquement sur demande explicite.
- Autonomie : avancer seul et rendre compte ; s'arrêter avant push, build/déploiement
  et toute décision de sécurité.

## Règles métier clés
- Contrat de protocole : `MOBILE_CONTROL_SPEC.md` ; API WebUI : `API_SPEC.md`. Le code
  s'y conforme, pas l'inverse sans accord.
- Jamais de secret commité (tokens relais, `.env`, keystores) ; les `*.apk` sont ignorés.
- Les 15 codes d'erreur normalisés du relais sont définis dans la spec : ne pas en inventer.

## Commandes
- App : `cd android-app && ./gradlew test` · `./gradlew assembleDebug`
- Relais : `_à décider_` (commande de test pytest à confirmer)
- WebUI : `_à décider_`
- Déploiement : `HERMES_MOBILE_CONTROL_DEPLOYMENT.md`

BRAIN: ~/brain/hermes-android

## Journal d'erreurs

Quand un bug non trivial est résolu, append une ligne à `$BRAIN/bag.ndjson` :

{"trigger":"", "symptom":"", "root_cause":"", "fix":"", "severity":1, "date":"YYYY-MM-DD"}

- `trigger` : les termes techniques exacts qui identifient le contexte
  ("relation polymorphe Strapi v5"), pas une description du bug.
  C'est la clé de regroupement.
- `severity` : 1 friction · 2 rework · 3 irréversible (perte de données,
  CI verte à tort, prod)
- Append only, jamais d'édition, une ligne par incident.

Si le `trigger` n'est pas formulable en termes techniques précis, le diagnostic
n'est pas terminé : le dire plutôt que de logger une entrée floue.
Un bug résolu par hasard ne se logge pas.
Si aucune ligne `BRAIN:` n'est présente dans ce CLAUDE.md, ne rien logger et le signaler.
