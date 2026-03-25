📊 Minecraft-GoogleSheets Player Sync (mc-gs)

Export player join data from your Paper server directly into Google Sheets — automatically.

✨ Features
📥 Automatic tracking — exports player data every time someone joins
📊 Google Sheets integration — no database required
🧾 First & last join dates — pulled directly from Paper
🔁 Backfill support — export all existing players anytime
👤 Single player export — useful for testing or moderation
⚙️ Fully configurable — webhook, secret, logging, and date source
🔐 Secure — uses a shared secret to protect your data
📌 What It Tracks

For every player:

UUID
Username
First join date
Last join date (configurable: lastLogin or lastSeen)


🚀 Commands
Command	Description
/exportjoins	Export all known players
/exportplayer <name>	Export a single player
/joinexporterreload	Reload config


⚙️ Requirements
Paper 1.21+
Java 21
Google account (for Sheets)
google apps script (follow wiki instructions)
very basic dev knoweledge (edit configs, follow wiki instructions)



🧠 How It Works
MC-GS-PlayerSync sends player data to a lightweight Google Apps Script webhook, which writes it directly into your Google Sheet.

No databases. No APIs to manage. No plugins on other services.

🛠 Setup Overview
put mcgsplayersync.jar in your /plugins folder
restart server
Create a Google Sheet
create a "players" tab
follow steps on wiki for google sheets guide
Add the provided Apps Script
Deploy it as a Web App
Paste the /exec URL into config.yml
Restart the plugin

Done.

🔒 Security
Uses a shared secret to validate requests
Your data is only written to your own Google Sheet
No external services or tracking


💡 Use Cases
Staff analytics
Player retention tracking
Whitelist/history logs
Server growth tracking
Discord bot integrations (via Sheets)


📎 Notes
First join dates include historical data (even before plugin install)
Use /exportjoins once after install to backfill
Large servers may take time to export all players
Automaticly updates data for each player on Join


❤️ Why This Plugin?

Most join trackers require databases or external services.
JoinExporter keeps it simple:

➡️ Minecraft → Google Sheets → Done





📦 Support / Issues

If you run into problems or have feature requests, open an issue or reach out.