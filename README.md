# Angel Sheriff Mafia

A Wild West party game of Mafia for Android that runs from **one phone**. It deals the
roles, collects everyone's secret night moves as the phone goes round the circle,
announces the morning out loud, runs the vote and keeps score. Nobody has to sit out as
the moderator.

## How a round works

1. **Set up.** Type everyone's names in seating order and pick house rules.
2. **Deal.** The phone goes round once. Each player holds their thumb down to see their
   wanted-poster role card.
3. **Night.** The phone goes round again. *Every* living player takes a turn on the same
   screen: the Mafia pick a target, the Angel protects, the Sheriff investigates, and
   villagers guess who the Mafia will go after. Each turn has a minimum time and a
   "pass to X" cover screen, so passing the phone gives nothing away.
4. **Morning.** Put the phone in the middle. The narrator says what happened.
5. **Discussion and vote.** A countdown, then an open vote (point on three) or a secret
   vote (pass the phone).
6. **Replay.** At the end, the app shows every move of the game.

The Mafia agree in turn: the first Mafia member on the phone picks, later ones see the
choice and can switch, and the last one decides. A different player starts each night,
so the final say moves around.

## Project layout

| Module | What it is |
| --- | --- |
| `engine` | The game rules in pure Kotlin, with no Android code. One reducer: `Game.reduce(state, action)`. Fully unit-tested. |
| `app` | The Android app: Jetpack Compose screens, a ViewModel, the narrator and saved games. |
| `preview` | A desktop harness that compiles the app's Compose screens without the Android SDK and renders screenshots of every screen. Optional. |

## Building

Requirements: JDK 17 and the Android SDK (API 35).

```sh
./gradlew :engine:test           # rules engine tests
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions builds the debug APK on every push and pull request and attaches it to
the run as an artifact.

Without the Android SDK you can still run the engine tests and render the screens:

```sh
./gradlew -PengineOnly=true :engine:test
./gradlew -PengineOnly=true -Ppreview=true :preview:run   # writes preview/out/*.png
```

## Narrator

Narrator lines are designed to ship as recorded audio clips in
`app/src/main/assets/narrator/`. Until a clip is added, the phone's own text-to-speech
voice reads the line. Player names are always spoken by the on-device voice, with an
optional "say it like" spelling per player. The app has no network permission and no API
keys. See [docs/narrator-audio.md](docs/narrator-audio.md).

## Privacy on a shared phone

- Secret screens only show behind a "pass to X" cover that the named player must hold to open.
- Role cards and the Sheriff's answer show only while a thumb is held down.
- Every role sees the same layout, timing, colours and sounds on its night turn.
- Screenshots and the recent-apps preview are blocked while the app is open.
- The game is saved after every move, so a locked screen or closed app loses nothing.

## Credits

Fonts are from Google Fonts under the SIL Open Font License: Rye (Sorkin Type), Lilita One
(Juan Montoreano) and Hind (Indian Type Foundry). Licences are in `licenses/`.
