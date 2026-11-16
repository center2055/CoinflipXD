Coinflip betting for your server's economy. One player puts money in, another matches it, and a coin decides who takes the pot. Works with any Vault economy, has a clean GUI, and gives Bedrock players (Geyser/Floodgate) native forms.

I made CoinflipXD because every coinflip plugin I tried was abandoned, had dupe glitches, or broke on new Minecraft versions.

## Features

- **Public and private bets.** `/cf 5k` posts a bet anyone can take, `/cf Steve 5k` challenges one player.
- **Bet browser GUI** with confirm screens, and **Bedrock forms** for Floodgate players.
- **Stakes can't get lost.** Every stake is recorded the moment it is taken. If the server crashes or stops in the middle of a bet, it is refunded automatically on the next start.
- **Fair coin** from a cryptographically secure random generator. Every result is logged to the console.
- **Tax** on winnings, removed from the economy or paid to a player account.
- **Limits:** min and max bet, max % of a player's balance, one open bet per player, and a confirmation screen above an amount each player can set.
- **Shorthand amounts** like `50k`, `1.5m` or `2b`.
- **Stats:** wins, losses, money won and lost.
- **English and German** messages, all editable with MiniMessage.
- **Minecraft 1.21 to 26.x** on Paper, Purpur, Spigot, Bukkit and Folia. One jar for every version.

## Commands

`/coinflip` works as well as `/cf`.

| Command | Permission | What it does |
| --- | --- | --- |
| `/cf` | `coinflip.use` | Open the bet browser |
| `/cf <amount>` | `coinflip.use` | Post a public bet |
| `/cf <player> <amount>` | `coinflip.private` | Challenge a player |
| `/cf <player> accept` / `deny` | `coinflip.use` | Answer a challenge (or click the buttons in chat) |
| `/cf cancel` | `coinflip.use` | Cancel your bet and get your money back |
| `/cf stats [player]` | `coinflip.use` | Wins, losses and totals |
| `/cf confirmmin <amount\|default>` | `coinflip.use` | Only ask for confirmation above this amount |
| `/cf cancel <player>` | `coinflip.admin` | Cancel someone else's bet |
| `/cf reload` | `coinflip.admin` | Reload the config and messages |

`coinflip.use` is given to everyone by default; `coinflip.private` to no one. `coinflip.bypass.minmax` ignores the bet limits, and `coinflip.notify` (op by default) gets a message if a payout or refund ever fails.

## Installation

1. Install [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin such as EssentialsX.
2. Put CoinflipXD in `plugins/` and restart.
3. Adjust `plugins/CoinflipXD/config.yml` and run `/cf reload`.

For Bedrock players, install Geyser and Floodgate. CoinflipXD switches to forms for them automatically.

## Configuration

<details>
<summary>config.yml</summary>

```yaml
economy:
  min-bet: 100
  max-bet: 100000
  # Maximum percentage of a player's balance allowed per bet (0-100)
  max-balance-percent: 100.0
  require-whole-numbers: true

tax:
  enabled: true
  # Percentage of the pot taken from each win
  percent: 10.0
  # "server" removes taxed money from the economy. Or give a player name or UUID to receive it.
  recipient: "server"

ui:
  browser:
    rows: 6
    items-per-page: 45
  # Seconds before public bets and private challenges expire (and get refunded)
  expire-seconds: 120
  private-expire-seconds: 60
  # Bets below this amount skip the confirm screen (0 = always confirm)
  create-confirm-min: 0
  # Bukkit sound names (UI_BUTTON_CLICK) or keys (minecraft:ui.button.click). "none" for silence.
  sounds:
    open: UI_BUTTON_CLICK
    accept: ENTITY_EXPERIENCE_ORB_PICKUP
    win: UI_TOAST_CHALLENGE_COMPLETE
    lose: BLOCK_ANVIL_LAND

limits:
  one-active-per-player: true

broadcast:
  # Announce new public bets in chat
  enabled: false
  message: "<gray>%player% created a coinflip for <aqua>$%amount%</aqua>.</gray>"

messages-format: MINI_MESSAGE
messages-language: "en" # en or de
```

</details>

## Good to know

- Bets that nobody takes expire and are refunded. Leaving the server cancels your bet and refunds it.
- With the tax recipient set to `server`, taxed money leaves the economy, which makes coinflips a money sink.
- Stats and open stakes are stored in `plugins/CoinflipXD/data.db`.

## Support

Bugs and ideas: [GitHub issues](https://github.com/center2055/CoinflipXD/issues) or [Discord](https://discord.gg/3HZZ353Tzg).
