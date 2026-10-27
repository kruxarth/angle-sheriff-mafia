# Narrator audio

The narrator's fixed lines can ship as recorded clips. Until a clip exists, the phone's
built-in text-to-speech voice reads the line, so the game always has a narrator and the
app never needs an API key or a network connection.

## Adding clips

1. Record or generate each line below (for example once, on your own account, with a
   voice service whose licence allows shipping the audio in an app).
2. Save it as `app/src/main/assets/narrator/<key>.ogg` (or `.mp3`), using the key from the
   table. Mono, 44.1 kHz or 48 kHz, trimmed with about 150 ms of silence at each end.
3. Rebuild. Any line without a clip keeps using the on-device voice.

Player names are always read by the on-device voice, with each player's
"say it like" spelling from setup. A sentence such as "Kabir was found dead this morning"
plays as the name followed by the `was_killed` clip, so record those clips as sentence
endings.

| Key | Line |
| --- | --- |
| `deal_roles` | Welcome to Dry Gulch. Pass the phone around, and each of you check your role in secret. |
| `night_falls` | Night falls on Dry Gulch. Pass the phone around, and keep your eyes to yourself. |
| `morning` | The sun is up, and the rooster is crowing. |
| `was_killed` | ...was found dead this morning. |
| `saved` | Someone was attacked in the night, but the Angel saved them. |
| `was_mafia` | They were Mafia. |
| `was_angel` | They were the Angel. |
| `was_sheriff` | They were the Sheriff. |
| `was_villager` | They were a villager. |
| `discuss` | Talk it over. Who among you is Mafia? |
| `time_up` | Time's up. It's time to vote. |
| `tie` | It's a tie. Vote again between the tied players. |
| `run_out` | ...has been run out of town. |
| `nobody_out` | Nobody leaves town today. |
| `town_wins` | The Mafia are gone. The town of Dry Gulch is safe. |
| `mafia_wins` | The Mafia have taken over Dry Gulch. |

The source of truth for keys and wording is `NarratorLine` in
`app/src/main/java/com/angelsheriffmafia/app/audio/Narrator.kt`.

Only public information is ever spoken. The Sheriff's result and everyone's night picks
stay on the screen of the player holding the phone.
