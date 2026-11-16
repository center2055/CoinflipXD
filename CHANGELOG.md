# Changelog

## 1.3.0

One jar for every server again: Minecraft 1.21 to 26.3 on Paper, Purpur, Spigot, Bukkit and Folia (the separate "Java 25" jar of 1.2 is no longer needed). The jar shrank from 15 MB to about 1 MB because it now uses the SQLite driver that every server already ships.

### Money safety

- Every stake is now recorded the moment it is taken. If the server crashes or stops during a bet, the stake is refunded automatically on the next start. Before, it was gone.
- Bets left open at shutdown are refunded only while the economy plugin is still running; otherwise they are refunded on the next start instead of silently failing.
- Accepting, paying out and refunding happen in one locked step, so a bet can never be accepted twice, even on Folia.
- If paying the winner fails, both players get their stake back.
- Stats written right before a restart are no longer dropped.

### Fixed

- 1.2 crashed on startup on Spigot and Bukkit (it called Paper-only scheduler methods)
- `/cf <player> <amount>`, the command shown in the docs, didn't work; only `/cf <amount> <player>` did. Both work now.
- Items dragged into a coinflip menu disappeared when it closed
- Clicking a bet in the browser could open a different bet if the list changed while the menu was open
- The plugin disabled itself if the economy plugin registered with Vault after it loaded
- `/cf stats <name>` and the tax recipient could freeze the server with a web lookup
- The challenged player was told "Your coinflip expired. Refunded $X" although they never paid; they now get their own messages, and the creator is told when a challenge is declined
- With tax on, payouts of decimal bets were rounded down to whole numbers; they now keep cents
- A "messages.yml already exists" warning on every start

### Changed

- `coinflip.admin` and `coinflip.notify` now default to op
- New `/coinflip` alias
- Sounds can also be given as keys like `minecraft:ui.button.click`, or `none`
- The tax recipient can be a UUID
- On Paper the plugin uses the server's own Adventure instead of the bundled copy
- New messages: `target-busy`, `denied`, `challenge-expired`, `challenge-canceled`, `challenge-declined`, `refund-pending`, `payout-failed` (added to existing messages files automatically)

## 1.2

- Folia support, separate jar for Minecraft 26.x

## 1.1.3

- Shorthand amounts (50k, 1.5m, 2b), per-player confirm threshold, German messages
