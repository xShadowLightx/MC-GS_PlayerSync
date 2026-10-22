# MC-GAPI

MC-GAPI is a Paper plugin that exports player data to Google Sheets.

## Features

- Export player data automatically on join
- Export all known players with `/exportjoins`
- Export one player with `/exportplayer <name>`
- Reload config with `/joinexporterreload`
- Check GitHub Releases for updates and notify the console and authorized players
- Configurable Google Apps Script webhook
- Configurable shared secret
- Supports `lastLogin` or `lastSeen` for last-date source

## Requirements

- Paper 1.21+
- A Google account
- A Google Sheet + Apps Script web app
- Gradle installed locally, or generate a wrapper with `gradle wrapper`

### Paper version and Java runtime

The following are Paper's currently documented Java recommendations for the **server runtime**. Install the Java version for your server version. MC-GAPI's build target does not change Paper's runtime requirement. Check [Paper's current requirements](https://docs.papermc.io/paper/getting-started/) for updates.

| Paper server version | Recommended Java runtime |
| --- | --- |
| 1.21.0–1.21.11 | Java 21 |
| 26.1 and newer | Java 25 |

MC-GAPI currently declares Paper API `1.21` and is built for Java 21. Compatibility with future Paper/Minecraft releases is not guaranteed; check for an MC-GAPI update if a newer server version has issues.

## Project layout

- `src/main/java/.../JoinExporterPlugin.java` - plugin source
- `src/main/resources/plugin.yml` - Paper metadata
- `src/main/resources/config.yml` - user config
- `google-apps-script.js` - paste into Apps Script for your Sheet

## Google Sheet setup

THIS PART IS REQUIRED REGARDLESS OF CONFIGURED OPTIONS IN THE PLUGIN CONFIGURATION

CASE SENSITIVE*

Create a Google Sheet with a tab named `Players_API_DO_NOT_TOUCH` and this header row:

`UUID | Name | First Join | Last Join | Role | Play Time Hours | Balance | Deaths | Player Kills | Logout Location`

Format the `First Join` and `Last Join` columns as date/time. Balance as number/s

## Choose which player fields are exported

Use the `fields` section in `plugins/MC-GAPI/config.yml` to turn individual columns on or off. For example, set `balance: false` to write `N/A` in the Balance column. Disabled values are marked `N/A`, and the existing column order stays the same. UUID is always sent because it identifies the row to update. All optional fields default to enabled. Logout location is saved to `logout-locations.yml` when a player disconnects and exported with that player's next export; unknown locations show as `N/A`.

Add usernames or UUIDs under `redaction.blacklist` to redact a player's exported fields. Username matching ignores letter case. UUID and player name remain visible; all other columns are replaced with `redacted`. Existing rows are redacted the next time those players are exported.

```yaml
redaction:
  blacklist:
    - "ExamplePlayer"
    - "01234567-89ab-cdef-0123-456789abcdef"
```

Review these toggles before enabling the plugin and disable every field you do not need. Logout coordinates can reveal where players were when they disconnected. Do not export them to a public spreadsheet; enable that field only when needed and keep the sheet restricted to trusted staff. Balance, role, and play statistics may also be sensitive for your server.

Disabling a field affects future exports only. It does not remove existing spreadsheet values. Previously recorded logout locations remain in `plugins/MC-GAPI/logout-locations.yml` until you delete that file; clear any existing location cells in the sheet separately if needed.

## Privacy and security

Enabled player fields are sent from your server to the Google Apps Script web app and stored in the configured Google Sheet. A shared secret is used to validate requests, but spreadsheet sharing settings still control who can access the data. Keep the sheet, Apps Script project, deployment URL, and secret restricted to trusted administrators. Do not post the secret or config file publicly; rotate the secret if it is exposed.

UUID is always exported because the Apps Script uses it to match rows. All other fields can be disabled individually in `config.yml`.

## Apps Script setup (DO THIS PART FIRST)

1. Open the Google Sheet.
2. Go to **Extensions -> Apps Script**.
3. Paste the contents of `google-apps-script.js`.
4. Replace `SHARED_SECRET` with a long random string.
5. Deploy as a Web App.
6. Copy the `/exec` deployment URL.

## Plugin setup (DO THIS NEXT)

1. Build the jar.
2. Put the jar into your Paper server `plugins/` folder.
3. Start the server once.
4. Edit `plugins/MC-GAPI/config.yml`. On the first run after upgrading, the plugin copies your existing `plugins/MC_GAPI/config.yml` automatically if present. (or `plugins/JoinExporter/config.yml`  if using version/s before 2.0.0)
5. Set:
   - `google.webhookUrl`
   - `google.secret`
6. Restart the server.

## Build

If you already have Gradle installed:

```bash
gradle build
```

If you want wrapper files in this project, run this once on your machine:

```bash
gradle wrapper
```

Then you can build with:

```bash
./gradlew build
```

On Windows:

```bat
gradlew.bat build
```

The jar will be created in `build/libs/`.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/mcgapi export` | Export all known players | `mcgapi.export` |
| `/mcgapi mydata` | Privately show your own export data in chat | Any player |
| `/mcgapi ingame <name>` | Privately view another player's export data | `mcgapi.view-others` |
| `/mcgapi player <name>` | Export one player | `mcgapi.export` |
| `/mcgapi delete <player\|uuid>` | Delete that player's Google Sheets row | `mcgapi.delete` |
| `/mcgapi reload` | Reload config | `mcgapi.reload` |

Legacy commands remain available:

| Command | Description | Permission |
| --- | --- | --- |
| `/exportjoins` | Export all known players | `joinexporter.export` (legacy) |
| `/exportplayer <name>` | Export one player | `joinexporter.export` (legacy) |
| `/joinexporterreload` | Reload config (kept for compatibility) | `joinexporter.reload` (legacy) |

## Permissions

| Permission node | Purpose | Default |
| --- | --- | --- |
| `mcgapi.export` | Run `/mcgapi export` and `/mcgapi player <name>` | Operators |
| `mcgapi.delete` | Run `/mcgapi delete <player|uuid>` | Operators |
| `mcgapi.view-others` | View another player's data with `/mcgapi ingame <name>` | Operators |
| `mcgapi.reload` | Run `/mcgapi reload` | Operators |
| `mcgapi.update-notify` | Receive update notices when joining | Operators |
| `joinexporter.export` | Legacy export permission; still accepted | Operators |
| `joinexporter.reload` | Legacy reload permission; still accepted | Operators |
| `joinexporter.update-notify` | Legacy update notice permission; still accepted | Operators |

## Notes

- `firstJoin` comes from Paper's first-played timestamp.
- `lastJoin` can use either `lastLogin` or `lastSeen`.
- `role` contains the LuckPerms primary group when LuckPerms is installed; otherwise it is blank.
- `playHours` is the player's recorded playtime, rounded to two decimal places.
- `balance` is read from the active Vault economy provider, including CMI when its Vault build or economy injector is installed. It is blank when no Vault economy provider is available.
- `deaths` is the player's recorded death count.
- `playerKills` counts player kills and excludes mob kills.
- Current permission nodes are `mcgapi.export`, `mcgapi.reload`, `mcgapi.delete`, and `mcgapi.update-notify` (all default to operators). The former `joinexporter.*` nodes continue to work for existing permission setups. Update checks can be disabled with `options.update-checker.enabled`.
- `/mcgapi delete` removes the matching Google Sheets row and the player's saved logout coordinates. If they remain in the redaction blacklist, a later export can recreate a redacted row.
- Bump the version in `plugin.yml` and `build.gradle`, then publish a matching GitHub Release with the plugin JAR attached for a direct download link. If no JAR asset is attached, the notice links to the release page.
- Existing players can be backfilled with `/exportjoins`.
