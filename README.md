# Snake Auto Player

Android helper bot for **Snake Classic** (`com.pranta.snakeclassic`).

The app is intended for **single-player testing**. It uses Android screen capture to read the board and an Accessibility Service to send swipe gestures. It does not modify Snake Classic.

## Download / build

GitHub Actions automatically builds a debug APK.

Workflow: **Build Snake Auto Player APK**

The build artifact is named:

`snake-auto-player-debug-apk`

and contains:

`snake-auto-player-debug.apk`

## Install on Android

1. Download `snake-auto-player-debug.apk`.
2. If Android blocks it, allow **Install unknown apps** for the browser/files app you used to open the APK.
3. Install and open **Snake Auto Player**.
4. Enable the requested **Accessibility Service** for Snake Auto Player.
5. Allow **screen capture** when Android prompts.
6. Open Snake Classic.
7. For the first test use:
   - Single-player **Classic**
   - **20 x 20** board
   - default snake skin/theme
   - **Snap Movement** enabled
   - start a fresh game
8. Use **CAL** to mark the playable board's top-left and bottom-right corners.
9. Press **START**. The floating control remains available over the game.
10. Press **STOP** whenever you want control back.

## How it plays

For compatible even-sized boards the bot can use a Hamiltonian safety route, which prevents it from trapping itself as the snake becomes long. It also includes board recognition and conservative pathfinding/fallback behaviour.

Snake Classic's levels are score-driven rather than a finite campaign, so the goal is continuous automatic progression rather than reaching a fixed final level.

## Notes

- Android 11+ is required by this first build.
- The first version should be tested on the 20 x 20 board before trying other sizes.
- Screen recognition can need calibration when display scaling, themes, skins or device layouts differ.
- Do not use the bot in multiplayer, tournaments or competitive leaderboard play.
