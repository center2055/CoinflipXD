# CoinflipXD

A robust, bug-free Coinflip wagering plugin for modern Minecraft servers.

## Why this exists
I coded this because every other coinflip plugin I tried either had critical bugs, dupe glitches, or simply didn't work for the newest Minecraft versions. This project aims to provide a stable, reliable, and up-to-date solution for server owners who are tired of broken abandoned plugins.

## Features
- **Vault Economy Integration**: Seamlessly works with your existing economy.
- **Modern GUI**: Clean and intuitive inventory interface for browsing and creating games.
- **Public & Private Games**: Challenge the whole server or a specific player.
- **Configurable**: Customize messages, sounds, bet limits, taxes, and UI settings.
- **Stats Tracking**: Keeps track of wins, losses, and earnings (SQLite backend).
- **Adventure API**: Modern text formatting and MiniMessage support.
- **Multi-Platform Support**: Optimized for Paper servers with enhanced compatibility for Bukkit and Spigot.
- **Net Worth Protection**: Configure maximum percentage of a player's balance that can be wagered per bet to prevent excessive losses.
- **Geyser Integration**: Native support for Bedrock Edition players through GeyserMC, with custom form interfaces for better cross-platform experience.
- **Interactive Help System**: Built-in help book accessible via the book icon in the `/cf` GUI that showcases all available commands and features.

## Supported Versions
- **Minecraft**: 1.21.x
- **Java**: Java 21 or newer required
- **Platform**: Paper (and forks), enhanced support for Bukkit and Spigot
- **Cross-Platform**: Native GeyserMC support for Bedrock Edition players

## Commands & Permissions
- `/cf` - Open the Coinflip GUI (Permission: `coinflip.use`)
- `/cf <amount>` - Create a public game
- `/cf <player> <amount>` - Challenge a specific player (Permission: `coinflip.private`)
- `/cf cancel` - Cancels current Coinflip (with refund)
- `/cf help` - View help menu (text format)
- `/cf reload` - Reload configuration (Permission: `coinflip.admin`)

## Installation
1. Download the JAR.
2. Place it in your server's `plugins` folder.
3. Ensure you have **Vault** and an economy plugin (like EssentialsX) installed.
4. Restart your server.
