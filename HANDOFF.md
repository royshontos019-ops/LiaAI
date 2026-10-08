# Lia AI: project hand-off (paste this into a new chat to continue)

## Who and how
- Owner: Shontos Roy (GitHub: royshontos019-ops, repo LiaAI). Beginner. Works ONLY from an Android phone.
- Reply in simple BANGLA (Bangla script), short, step by step, mobile-friendly. Commands one per code block.
- Build flow: Claude makes a zip of changed files -> user downloads it -> in Termux:
  `cd ~/LiaAI && unzip -o ~/storage/downloads/<zip>` then `git add .`, `git commit -m "..."`, `git push`
  -> GitHub Actions (.github/workflows/build.yml) builds both APKs AND runs unit tests -> user downloads
  artifact `lia-apks` (needs GitHub login) -> uninstalls old app (debug signature changes each build) -> installs.
- Claude cannot compile here (no network / no Android SDK): the first GitHub build is the real compile.
  If a build is red, ask for a screenshot of the last error lines.
- Never ask for, store or print API keys / GitHub tokens.

## App
- "Lia AI", package/namespace com.Lia.assistant, Kotlin + Compose + Material3, minSdk 26, target/compile 36, Java 17.
- AGP 9 (built-in Kotlin), Gradle 9.1.0, versions in gradle/libs.versions.toml. OkHttp + org.json only.
- Flavors (dimension "distribution"): `direct` (Accessibility screen control, overlay) and `play` (all of that removed
  at compile time, safe stubs). Same-named objects exist in both source sets: ActionExecutor, LiaToolCatalog,
  LiaCapabilityPrompts, AccessKeyManager, OverlayEdgeGlowController, FlavorRoutes, AccessibilitySettingsRows, Forge.
- Packages: data (prefs, DataStore, NovaAppState), voice (Gemini Live client, audio, session manager, text chat client),
  action (phone actions), ui/theme, ui/components, ui/screens/chat.

## Steps done (each one built green on GitHub unless noted)
1 scaffold, flavors, manifests. 2 persistence (ApiKeyStore, NovaPreferences, PersonalityRepository, NovaAppState).
3 personalities (10), LanguageDetector, ConversationMemory, PersonalityPromptBuilder.
4 GeminiLiveClient (WebSocket, model constant in ONE place), ToolCallDeduplicator.
5 LiveAudioIO + EchoGuard. 6 VoiceSessionManager, VoiceForegroundService, VoiceLatencyTracker.
7 phone actions (open_app, call_contact, message_contact; direct also read_screen, tap_text, type_text,
  scroll_screen, go_back, go_home via NovaAccessibilityService). 8 typed chat (GeminiTextClient walks past
  404/429/5xx models; ChatScreen). Verified on a phone: chat replies and read-aloud work.
9 design system (ui/theme + ui/components, Instrument Serif bundled, Manrope NOT yet added, see FONTS.md).
  STATUS: written but NOT yet built or tested on GitHub.

## Known open items
- Forge (website builder) is an empty placeholder (`Forge.start` returns false); `build_website` tool is not declared yet.
- No Settings screen yet: the Gemini API key is pasted on the home screen (temporary).
- Runtime permission requests (RECORD_AUDIO, READ_CONTACTS, CALL_PHONE, POST_NOTIFICATIONS) have no UI yet.
- No screen starts the voice session (VoiceForegroundService.start) yet, so mic/voice is untested on a device.
- Android 10+ blocks starting apps from the background: open_app works while Lia is on screen.
- Old empty stub files (comment only) are safe to delete: LiaAccessibilityService.kt, InstalledAppLabelCache.kt (direct/play),
  LiaTheme.kt, ui/screens/chat/NovaMessageBubble.kt, VoiceService.kt.
- Light-theme marigold (#B86A00) with white text is about 4.1:1 contrast (a little under AA for small text).

## Design rules (step 9)
Deep indigo night, ONE warm marigold accent for everything actionable, lotus (#FF6FAE) + lagoon (#3FE0D0) only for the
orb/visualizer. Screens never use hex colours: read NovaTheme.colors / NovaTheme.type, spacing 4/8/12/16/24/32/48,
touch targets >= 48dp, every animation honours LocalNovaReducedMotion.
