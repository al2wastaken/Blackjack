# Blackjack

A physical, 3D blackjack-table plugin for Minecraft servers. Create tables in-world, let players sit down, place Vault-backed bets, and play standard blackjack with card displays.

## Version 3.0

- English is now the default language.
- Turkish is available through the plugin configuration.
- Vault economy integration, player statistics, quick bets, double down, configurable tables, and PlaceholderAPI support are included.

## Installation

1. Download `Blackjack-3.0.jar` from the latest GitHub release.
2. Install [Vault](https://www.spigotmc.org/resources/vault.34315/) and a Vault-compatible economy plugin.
3. Put the JAR in your server's `plugins` folder and restart the server.
4. Configure `plugins/Blackjack/config.yml` as needed.

Requires Java 21 and a Minecraft 1.21-compatible Paper, Spigot, or compatible server.

## Language

English is the default. To use Turkish, set this in `plugins/Blackjack/config.yml`, then run `/bj reload` or restart the server:

```yaml
language: tr
```

Supported values are `en` and `tr`.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/bj createtable` | Create a table | `blackjack.admin` |
| `/bj removetable` | Remove the nearest table | `blackjack.admin` |
| `/bj settable <setting> <value>` | Configure the nearest table | `blackjack.admin` |
| `/bj reload` | Reload configuration | `blackjack.admin` |
| `/bj join`, `/bj leave` | Join or leave a table | `blackjack.play` |
| `/bj bet <amount>` | Place or update a bet | `blackjack.play` |
| `/bj start`, `/bj hit`, `/bj stand`, `/bj doubledown` | Play blackjack | `blackjack.play` |
| `/bj stats [player]` | View player statistics | `blackjack.play` |

## Credits

This project was forked from [DefectiveVortex/Blackjack](https://github.com/DefectiveVortex/Blackjack) and was inspired by [aematsubara/Roulette](https://github.com/aematsubara/Roulette).

## License

This project is licensed under the [MIT License](LICENSE).
