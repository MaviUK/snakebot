# Snake 3310 Web

A self-contained, mobile-friendly browser Snake game styled after the classic Nokia 3310 era.

## Manual game

Open `index.html`.

Controls:
- Arrow keys or WASD
- Swipe on the LCD on mobile
- On-screen D-pad
- Space / Enter / Start / OK to start and pause

High score is stored in localStorage.

## Automatic game

Open `auto.html`.

The automatic version:
- starts playing automatically
- chases randomly placed food using safe shortcuts
- switches into a packing route as it grows
- fills every cell on the board
- completes the level at 100% coverage
- automatically starts the next level
- supports pause, speed and sound controls

This web version is isolated in the `web/` folder and does not modify the Android snake bot app.
