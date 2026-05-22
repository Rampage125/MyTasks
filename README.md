# MyTasks

Minimal terminal-style task & notes manager for Android.
Built as a WebView wrapper around a single self-contained HTML file.
max@admin:~/tasks $ ./export
create a backup file? [y/n] y
ok: backup.json saved.
## Features

- **Two sections**: `./tasks` and `./notes`, switched via tabs
- **Three task states**: `[ ]` todo, `[~]` in progress, `[x]` done
  - Short tap toggles `todo ↔ done`
  - Long press (500ms) toggles `progress`
- **Console-style command input** in the same field used for typing tasks
- **Local-first storage**: data persists in WebView's localStorage
- **Manual backup/restore** to `/Download/MyTasks/` as JSON
- **Backup history** with "old" warning if last backup is older than a week

## Commands

Typed in the bottom prompt — same field as new task input.

| Command | Description |
|---|---|
| `./export` | Save backup to `/Download/MyTasks/backup_YYYY-MM-DD_HH-mm.json` |
| `./import` | Load most recent backup and merge with current data |
| `./list` | List all backup files in `/Download/MyTasks/` |
| `./history` | Show backup history with timestamps |
| `c` | Clear console output |

## Build

Requires JDK 17, Android SDK with `build-tools 34.0.0` and `platforms/android-34`.

```bash
git clone https://github.com/Rampage125/MyTasks.git
cd MyTasks
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleDebug
Output APK: app/build/outputs/apk/debug/app-debug.apk
Stack
UI: pure HTML/CSS/JS (no frameworks), single file in app/src/main/assets/notes_app.html
Container: Android WebView, Kotlin MainActivity
Backup bridge: native AndroidBackup JS interface with MediaStore Downloads API
applicationId: com.rampage125.mytasks
minSdk: 21 · targetSdk: 34
License
MIT
