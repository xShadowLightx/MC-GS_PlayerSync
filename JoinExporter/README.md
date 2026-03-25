# JoinExporter

JoinExporter is a Paper plugin that exports player first join and last join data to Google Sheets.

## Features

- Export player data automatically on join
- Export all known players with `/exportjoins`
- Export one player with `/exportplayer <name>`
- Reload config with `/joinexporterreload`
- Configurable Google Apps Script webhook
- Configurable shared secret
- Supports `lastLogin` or `lastSeen` for last-date source

## Requirements

- Paper 1.21+
- Java 21
- A Google account
- A Google Sheet + Apps Script web app
- Gradle installed locally, or generate a wrapper with `gradle wrapper`

## Project layout

- `src/main/java/.../JoinExporterPlugin.java` - plugin source
- `src/main/resources/plugin.yml` - Paper metadata
- `src/main/resources/config.yml` - user config
- `google-apps-script.js` - paste into Apps Script for your Sheet

## Google Sheet setup

Create a Google Sheet with a tab named `Players` and this header row:

`UUID | Name | First Join | Last Join`

Format the last two columns as date/time.

## Apps Script setup

1. Open the Google Sheet.
2. Go to **Extensions -> Apps Script**.
3. Paste the contents of `google-apps-script.js`.
4. Replace `SHARED_SECRET` with a long random string.
5. Deploy as a Web App.
6. Copy the `/exec` deployment URL.

## Plugin setup

1. Build the jar.
2. Put the jar into your Paper server `plugins/` folder.
3. Start the server once.
4. Edit `plugins/JoinExporter/config.yml`.
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

- `/exportjoins` - export all known players
- `/exportplayer <name>` - export one player
- `/joinexporterreload` - reload config

## Notes

- `firstJoin` comes from Paper's first-played timestamp.
- `lastJoin` can use either `lastLogin` or `lastSeen`.
- Existing players can be backfilled with `/exportjoins`.
