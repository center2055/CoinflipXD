<h1 align="center">CoinflipXD</h1>

<p align="center">Coinflip betting for Minecraft servers with a Vault economy.</p>

<p align="center">
  <a href="https://modrinth.com/plugin/coinflipxd">Modrinth</a> ·
  <a href="https://github.com/center2055/CoinflipXD/issues">Issues</a> ·
  <a href="https://discord.gg/3HZZ353Tzg">Discord</a>
</p>

---

One player puts money in, another matches it, and a coin decides who takes the pot. Works with any Vault economy, has a clean GUI, and gives Bedrock players (Geyser/Floodgate) native forms.

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

## How the money works

Stakes are taken when a bet is created and when it is accepted, and written to an escrow table in `plugins/CoinflipXD/data.db` at the same time. The pot is paid out (minus tax) right after the flip and the escrow entry is removed. Expired, cancelled and declined bets are refunded the same way. Anything still in escrow at startup, after a crash for example, is refunded before the plugin accepts new bets.

## Building

Needs Java 21 or newer.

```bash
./gradlew build
```

The plugin jar is `build/libs/CoinflipXD-<version>.jar`. To check the code against the newest APIs:

```bash
./gradlew compileJava -PapiTarget=paper
```
