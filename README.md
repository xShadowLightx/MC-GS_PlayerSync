# 📊 MC-GAPI — Minecraft Google Sheets Player Integration

Latest Release: V-2.1.1

Export player join data from your Paper server directly into Google Sheets — automatically.

## ✨ Features
- 📥 Automatic tracking — exports player data every time someone joins
- 📊 Google Sheets integration — no database required
- 🧾 First & last join dates — pulled directly from Paper
- 🔁 Backfill support — export all existing players anytime
- 👤 Single player export — useful for testing or moderation
- ⚙️ Fully configurable — webhook, secret, logging, and date source
- 🔔 Update notifications — console and authorized players get the release download link
- 🔐 Shared-secret validation for webhook requests
📌 What It Tracks

For every player:

- UUID
- Username
- First join date
- Last join date (configurable: `lastLogin` or `lastSeen`)
- Highest role (LuckPerms only)
- Play hours
- Balance (Vault economy)
- Deaths
- Player kills
- Logout location (`X,Y,Z` from the player's previous disconnect)

Each field except UUID can be enabled or disabled individually in the `fields` section of `plugins/MC-GAPI/config.yml`. Disabled fields show `N/A` in their existing sheet columns. Review these settings before enabling exports and turn off every field you do not need.

You can list usernames or UUIDs under `redaction.blacklist` to replace a matching player's exported values with `redacted`, while preserving UUID and player name. Username matching ignores letter case.


## 🚀 Commands
| Command | Description | Permission |
| --- | --- | --- |
| `/mcgapi export` | Export all known players | `mcgapi.export` |
| `/mcgapi mydata` | Privately display your own export data in chat | Any player |
| `/mcgapi ingame <name>` | Privately view another player's export data | `mcgapi.view-others` |
| `/mcgapi player <name>` | Export one player | `mcgapi.export` |
| `/mcgapi delete <player\|uuid>` | Delete a player's exported row | `mcgapi.delete` |
| `/mcgapi reload` | Reload the plugin config | `mcgapi.reload` |

Legacy commands remain available:

| Command | Description | Permission |
| --- | --- | --- |
| `/exportjoins` | Export all known players | `joinexporter.export` (legacy) |
| `/exportplayer <name>` | Export one player | `joinexporter.export` (legacy) |
| `/joinexporterreload` | Reload the plugin config | `joinexporter.reload` (legacy) |

## 🔑 Permission Nodes

| Permission node | Allows | Default |
| --- | --- | --- |
| `mcgapi.export` | Export all players or one player | Operators |
| `mcgapi.delete` | Delete a player's exported row | Operators |
| `mcgapi.view-others` | View another player's exported data in game | Operators |
| `mcgapi.reload` | Reload the plugin configuration | Operators |
| `mcgapi.update-notify` | Receive update notices when joining | Operators |
| `joinexporter.export` | Legacy export permission; still accepted | Operators |
| `joinexporter.reload` | Legacy reload permission; still accepted | Operators |
| `joinexporter.update-notify` | Legacy update notice permission; still accepted | Operators |


## ⚙️ Requirements

- Paper 1.21 or newer
- A Google account and Google Apps Script web app
- Review the Paper version and Java runtime table below before starting your server

### Paper version and Java runtime

These are Paper's currently documented Java recommendations for the server runtime. Install the matching Java version for your Paper server; the plugin's Java build target does not override the server's Java requirement. See [Paper's current requirements](https://docs.papermc.io/paper/getting-started/) because the table may change.

| Paper server version | Recommended Java runtime |
| --- | --- |
| 1.21.0–1.21.11 | Java 21 |
| 26.1 and newer | Java 25 |

MC-GAPI currently declares Paper API `1.21` and is built for Java 21. This does not guarantee compatibility with every future Paper/Minecraft release; use a newer MC-GAPI build if one is provided for your server version.

## 🔒 Privacy and Security for Server Admins

MC-GAPI sends the enabled player fields from your server to your Google Apps Script web app, which writes them into your Google Sheet. The shared secret helps reject unauthorized webhook requests, but it does not make a publicly shared spreadsheet private. The server owner is responsible for restricting access to the Apps Script project, its deployment URL and secret, and the spreadsheet.

Before using the plugin, review every option under `fields` in `plugins/MC-GAPI/config.yml`. Optional fields are enabled by default, so disable any data you do not need. UUID is required to match a player to their existing row.

Logout location is especially sensitive: it records a player's last `X,Y,Z` coordinates when they disconnect. Do not enable it for a public spreadsheet; use it only when there is a clear need and access to the sheet is restricted to trusted staff. Balance, role, and play statistics may also be sensitive depending on your server.

Turning a field off prevents its value from being included in future exports, but does not erase values already in Google Sheets. Previously collected logout coordinates also remain in `plugins/MC-GAPI/logout-locations.yml`; remove that file and clear the corresponding sheet cells yourself if you need to delete retained location data. Protect the Google webhook URL and shared secret, and rotate the secret if it is exposed.

Example: to avoid exporting logout coordinates, set `fields.logout-location: false` before starting or reloading the plugin.



## 🧠 How It Works
MC-GS-PlayerSync sends player data to a lightweight Google Apps Script webhook, which writes it directly into your Google Sheet.

No databases. No APIs to manage. No plugins on other services.

## 🛠 Setup Overview
put mcgsplayersync.jar in your /plugins folder
restart server
Create a Google Sheet
create a "Players_API_DO_NOT_TOUCH" tab
follow steps on wiki for google sheets guide
Add the provided Apps Script
Deploy it as a Web App
Paste the /exec URL into config.yml
Restart the plugin

Done.

## 🔒 Data Handling
Only the fields enabled in `config.yml` are sent to the configured Google Apps Script. Google Sheets sharing settings determine who can read the stored data. MC-GAPI does not control those permissions.


## 💡 Use Cases
Staff analytics
Player retention tracking
Whitelist/history logs
Server growth tracking
Discord bot integrations (via Sheets)


## 📎 Notes
First join dates include historical data (even before plugin install)
Use /exportjoins once after install to backfill
Large servers may take time to export all players
Automaticly updates data for each player on Join


## ❤️ Why This Plugin?

Most join trackers require databases or external services.
MC-GAPI keeps it simple:

➡️ Minecraft → Google Sheets → Done





## 📦 Support / Issues

If you run into problems or have feature requests, open an issue or reach out.

Please join the development discord for issues or requests:
https://discord.gg/FMvSubmg3M

