# Hermes Android Client

Application mobile native Android moderne, réactive et fluide pour le service **Hermes WebUI** (`ghcr.io/nesquena/hermes-webui`).

---

## 📱 Fonctionnalités

- **Connexion & Authentification :**
  - Configuration de l'URL du serveur (support local, émulateur `10.0.2.2`, LAN, domaine distant).
  - Gestion du mot de passe (`HERMES_WEBUI_PASSWORD`) avec persistance sécurisée via DataStore.
  - Test de connectivité en direct (`/health`) affichant l'état du serveur, l'uptime et les flux actifs.
- **Expérience de Chat Temps Réel (Server-Sent Events / SSE) :**
  - Streaming fluide des réponses LLM token par token (`token`).
  - Accordéon dépliable pour les traces de raisonnement / réflexion (`reasoning`).
  - Visualiseur de Tool-calls (`tool`, `tool_complete`) avec nom de l'outil, preview, arguments et durée d'exécution.
  - Cartes d'approbation interactives (`approval`) avec boutons *Approuver* / *Refuser*.
  - Cartes de clarification (`clarify`) avec puces d'options cliquables.
  - Formatage Markdown soigné (gras, italique, code inline, blocs de code avec bouton Copier).
  - Indicateur d'activité en direct (pulsing dot) et bouton d'arrêt d'urgence (`cancel`).
- **Gestion des Profils d'Agents :**
  - Bascule instantanée entre profils (`default`, `john`, `mario`, `gaston`, etc.) via `ModalBottomSheet`.
  - Isolation stricte par client grâce au cookie `hermes_profile`.
- **Gestion des Sessions & Conversations :**
  - Tiroir latéral de navigation dans l'historique des discussions avec recherche intégrée.
  - Création rapide de nouveau chat (`+ New`), suppression et épinglage.

---

## 🛠 Architecture & Pile Technique

- **Architecture :** Clean Architecture / MVI (Model-View-Intent) + MVVM réactif avec Kotlin Coroutines et StateFlows.
- **UI :** Jetpack Compose + Material 3 avec thème sombre élégant (Onyx & Slate) inspiré des interfaces pour développeurs.
- **Réseau & Streaming :** OkHttp 4.12 + `okhttp-sse` (EventSource) garantissant la reconnexion automatique et la gestion des cookies.
- **Sérialisation :** KotlinX Serialization JSON.
- **Persistance locale :** AndroidX DataStore Preferences.

---

## 📂 Structure du Projet

```
android-app/app/src/main/java/com/example/hermes/
├── MainActivity.kt               // Point d'entrée de l'application
├── Navigation.kt                 // Navigation3 entre Auth et Chat
├── core/
│   ├── model/
│   │   └── Models.kt             // Modèles de données REST et événements SSE
│   ├── network/
│   │   ├── AuthInterceptor.kt    // Injection et capture des cookies de session & profil
│   │   ├── HermesApiClient.kt    // Client REST OkHttp pour tous les endpoints
│   │   └── HermesSseClient.kt    // Client SSE émettant des Flow<HermesSseEvent>
│   └── data/
│       ├── HermesPreferences.kt  // Stockage DataStore (URL serveur, cookies, profil actif)
│       └── HermesRepository.kt   // Repository centralisant les données
├── features/
│   ├── auth/
│   │   ├── AuthScreen.kt         // Écran de configuration de l'instance et login
│   │   └── AuthViewModel.kt
│   ├── chat/
│   │   ├── ChatScreen.kt         // Écran principal de conversation
│   │   ├── ChatViewModel.kt      // MVI state machine pour le chat et le flux SSE
│   │   └── components/
│   │       ├── MessageBubble.kt  // Bulles utilisateur / assistant avec avatars
│   │       ├── MarkdownText.kt   // Rendu Markdown et blocs de code
│   │       ├── ToolCallCard.kt   // Visualiseur des appels d'outils
│   │       ├── ReasoningCard.kt  // Volet repliable pour la réflexion (thinking)
│   │       ├── ApprovalCard.kt   // Carte d'approbation humaine
│   │       ├── ClarifyCard.kt    // Carte de choix de clarification
│   │       ├── StreamingIndicator.kt
│   │       └── ChatInputField.kt // Saisie multi-ligne, bouton Stop / Envoyer
│   ├── profiles/
│   │   └── ProfileDrawer.kt      // Sélecteur d'agent
│   └── sessions/
│       └── SessionsDrawer.kt     // Historique des sessions et recherche
└── theme/
    ├── Color.kt                  // Palette Onyx / Slate et accents électriques
    └── Theme.kt                  // Thème Material 3
```

---

## 🚀 Compilation & Exécution

### Prérequis
- JDK 17 ou JDK 21 (ex: `JAVA_HOME=/path/to/jdk-21`)
- Android SDK (API 34+)

### Commandes utiles
```bash
# Se placer dans le répertoire android-app
cd android-app

# Exécuter les tests unitaires
./gradlew test

# Compiler l'application et générer l'APK Debug
./gradlew assembleDebug

# L'APK généré se trouve dans :
# app/build/outputs/apk/debug/app-debug.apk
```
