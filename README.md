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
- **Paper Native**: Optimized for Paper servers.

## Supported Versions
- **Minecraft**: 1.21.x
- **Java**: Java 21 or newer required
- **Platform**: Paper (and forks)

## Commands & Permissions
- `/cf` - Open the Coinflip GUI (Permission: `coinflip.use`)
- `/cf <amount>` - Create a public game
- `/cf <player> <amount>` - Challenge a specific player (Permission: `coinflip.private`)
- `/cf cancel` - Cancels current Coinflip (with refund)
- `/cf help` - View help menu
- `/cf reload` - Reload configuration (Permission: `coinflip.admin`)

## Installation
1. Download the JAR.
2. Place it in your server's `plugins` folder.
3. Ensure you have **Vault** and an economy plugin (like EssentialsX) installed.
4. Restart your server.
