# 🃏 Blackjack 3D

[![Modrinth](https://img.shields.io/badge/Modrinth-blackjack--3d-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/plugin/blackjack-3d)
[![GitHub Release](https://img.shields.io/github/v/release/al2wastaken/Blackjack?style=for-the-badge&logo=github&color=blue)](https://github.com/al2wastaken/Blackjack/releases)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21+-ED8106?style=for-the-badge&logo=minecraft&logoColor=white)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-E76F00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

An immersive, physical **3D Blackjack Table** plugin for Minecraft servers. Create realistic casino tables in your world, allow players to sit on physical chairs, place Vault-backed bets, watch virtual croupiers deal physical cards in real time, and enjoy standard casino blackjack directly in vanilla Minecraft without client mods!

Available on **[Modrinth](https://modrinth.com/plugin/blackjack-3d)** and **[GitHub Releases](https://github.com/al2wastaken/Blackjack/releases)**.

---

## 📑 Table of Contents

- [✨ Features](#-features)
- [📥 Installation & Requirements](#-installation--requirements)
- [🎮 How to Play](#-how-to-play)
- [🛠️ In-Game Admin Table Settings GUI](#️-in-game-admin-table-settings-gui)
- [⌨️ Commands & Permissions](#️-commands--permissions)
- [🧩 PlaceholderAPI Support](#-placeholderapi-support)
- [⚙️ Configuration](#️-configuration)
- [🗄️ Database & Storage](#️-database--storage)
- [🌐 Multi-Language (i18n)](#-multi-language-i18n)
- [🔨 Building from Source](#-building-from-source)
- [📜 Credits & License](#-credits--license)

---

## ✨ Features

### 🎲 Physical 3D Tables & Realistic Seating
- **Full In-World 3D Tables:** Built with customizable felt fabrics (Green, Red, Blue, Black, Purple, Lime, Cyan wool) and authentic table geometry.
- **Native ArmorStand Chair System:** Players right-click chairs or the table to sit down smoothly. The camera is aligned naturally toward the table and the croupier.
- **Multi-Player Support:** Tables support between 1 and 8 seats per table with individual card spots and betting zones.

### 🤵 Animated Virtual Croupier NPC (PacketEvents)
- **Zero-Lag Virtual NPCs:** Client-side NPCs handled via [PacketEvents](https://github.com/retrooper/packetevents). No phantom world entities or lag.
- **Cinematic Dealing Animations:** Croupier smoothly turns its head toward each player when dealing cards and swings its arm as cards are dealt with realistic sound effects.
- **Multiple Preset Skins:** Choose from `Classic Tuxedo`, `Lady Croupier`, `Mafia Dealer`, or `Casual` attire.
- **Interactive Holographic Display:** Floating bill-boarded text display directly above the croupier showing real-time table rules, limits, and hand statuses.

### 🃏 ItemDisplay 3D Card Engine
- High-performance `ItemDisplay` cards placed flat onto the felt surface.
- Accurate card spacing, rotations, and clean layout with no clipping.
- Sound effects for dealing cards, winning, losing, natural blackjacks, and pushes.

### 🎯 Intuitive Controls & Turn-Based Flow
- **Left-Click:** Hit (Request another card).
- **Right-Click:** Stand (Lock current hand value).
- **Double Down:** Double your bet on your opening 2 cards for a single extra card.
- **Dynamic BossBar Timer:** Visual countdown timer showing the active player's turn to keep games fast-paced.
- **Clickable Chat Buttons:** Fallback interactive chat buttons (`[KART ÇEK / HIT]`, `[PAS / STAND]`, `[İKİYE KATLA / DOUBLE DOWN]`).

### 💰 Vault Economy & Safe Betting GUI
- Fully integrated with [Vault](https://www.spigotmc.org/resources/vault.34315/) (EssentialsX, CMI, HexaEcon, etc.).
- **Betting GUI:** Opens automatically upon sitting down. Choose custom bets or chips.
- **Persistent Bets ("Play Again"):** Remembers previous bets for seamless round-after-round gameplay.
- **Dismount Safety Protection:** Dismounting (pressing `Shift`) before cards are dealt triggers an automatic **100% full refund**. If cards are already in play, a warning GUI prompts the player that leaving will forfeit the active bet.

### 📊 Advanced Statistics & Leaderboard Readiness
- Tracks hands won, lost, pushed, natural blackjacks, busts, current win/loss streaks, highest win streak, total money earned, and win rates.
- Persistent across restarts using SQLite or MySQL.

---

## 📥 Installation & Requirements

### Server Requirements
- **Minecraft Version:** `1.21.x` (Paper, Spigot, Purpur, or compatible fork)
- **Java Version:** `Java 21` or higher
- **Required Plugins:**
  - [Vault](https://www.spigotmc.org/resources/vault.34315/) + any Vault-compatible economy plugin (e.g., EssentialsX, CMI)
  - [PacketEvents](https://modrinth.com/plugin/packetevents) (v2.13.0+)
- **Optional Dependencies:**
  - [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) (for scoreboards, tablists, and custom chats)
  - [PhysicalEconomy](https://github.com)

### Installation Steps
1. Download the latest `Blackjack-3.1.jar` from **[Modrinth](https://modrinth.com/plugin/blackjack-3d)** or **[GitHub Releases](https://github.com/al2wastaken/Blackjack/releases)**.
2. Ensure **Vault** and **PacketEvents** are in your server's `plugins` folder.
3. Place `Blackjack-3.1.jar` into the `plugins/` folder.
4. Restart your Minecraft server.
5. Edit `plugins/Blackjack/config.yml` to customize settings, then run `/bj reload`.

---

## 🎮 How to Play

1. **Find a Table:** Locate a physical Blackjack table in your casino or world.
2. **Sit Down:** Right-click on an empty chair (or right-click the table felt to take the nearest available seat).
3. **Place Your Bet:** The Betting GUI opens automatically. Select chip values or enter a custom bet.
4. **Game Countdown:** Once bets are placed, the croupier begins dealing cards clockwise to all seated players, followed by the dealer's visible card.
5. **Take Action:** When your turn begins:
   - **Left-Click (Air/Block):** `HIT` – draw another card.
   - **Right-Click (Air/Block):** `STAND` – keep your hand and pass the turn.
   - **Double Down:** Double your active bet and receive exactly one final card (available on first 2 cards).
6. **Dealer Reveal & Payouts:**
   - Once all players stand or bust, the dealer draws until reaching at least soft/hard 17.
   - Payouts are credited instantly via Vault:
     - **Natural Blackjack:** Pays **3:2** (2.5x total bet)
     - **Standard Win / Dealer Bust:** Pays **1:1** (2.0x total bet)
     - **Push (Tie):** Bet is refunded in full
     - **Loss / Player Bust:** Bet is lost
7. **Standing Up / Leaving:**
   - Press **Shift (Sneak)** to dismount.
   - If a round is active, a safety GUI will open to confirm whether you wish to forfeit your active bet.

---

## 🛠️ In-Game Admin Table Settings GUI

Server administrators do not need to memorize complex commands or edit files to customize individual tables.

- **How to Open:** Hold **Shift + Right-Click** on any blackjack table or croupier while having the `blackjack.admin` permission.
- **Configurable In-GUI Options:**
  - 🤵 **Croupier Skin:** Cycle through `Classic Tuxedo`, `Lady Croupier`, `Mafia Dealer`, and `Casual`.
  - 🪙 **Minimum Bet:** Adjust table minimum bet limit (`+10`, `-10`, `+50`, `-50`).
  - 💰 **Maximum Bet:** Adjust table maximum bet limit (`+100`, `-100`, `+500`, `-500`).
  - ⏱️ **Countdown Duration:** Adjust betting phase timer (`5s`, `10s`, `15s`, `20s`, `30s`).
  - 🎨 **Felt Fabric Color:** Real-time felt material change (`Green`, `Red`, `Blue`, `Black`, `Purple`, `Lime`, `Cyan`).

---

## ⌨️ Commands & Permissions

### Commands

Main command aliases: `/blackjack`, `/bj`

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/bj createtable [options]` | Creates a 3D table at your position. | `blackjack.admin` |
| `/bj settable <setting> <value>` | Updates a setting on the nearest table. | `blackjack.admin` |
| `/bj removetable` | Removes the nearest table and its chairs/croupier. | `blackjack.admin` |
| `/bj reload` | Reloads `config.yml` and message files without restarting. | `blackjack.admin` |
| `/bj version` | Displays plugin version, update status, and links. | `blackjack.admin` |
| `/bj stats [player]` | View your own or another player's blackjack statistics. | `blackjack.play` |

#### `createtable` Parameters
You can customize limits during table creation:
```bash
/bj createtable min-bet:25 max-bet:5000 max-players:5 max-join-distance:8.0
```

#### `settable` Options
```bash
/bj settable min-bet 50
/bj settable max-bet 10000
/bj settable max-players 6
/bj settable max-join-distance 12.0
```

### Permissions

| Permission | Description | Default |
| :--- | :--- | :--- |
| `blackjack.admin` | Grants full administrative control (create/remove/set tables, GUI settings, reload, check version). | `op` |
| `blackjack.play` | Grants permission to sit at tables and play blackjack. | `true` (all players) |
| `blackjack.stats.others` | Allows checking the statistics of other players via `/bj stats <player>`. | `op` |

---

## 🧩 PlaceholderAPI Support

The plugin includes an extensive **PlaceholderAPI** expansion covering player statistics, table statuses, live games, and bets.

### 1. Player Statistics (`%blackjack_stats_*%`)
| Placeholder | Description |
| :--- | :--- |
| `%blackjack_stats_hands_won%` | Total hands won by the player |
| `%blackjack_stats_hands_lost%` | Total hands lost by the player |
| `%blackjack_stats_hands_pushed%` | Total hands tied (push) |
| `%blackjack_stats_total_hands%` | Total hands played |
| `%blackjack_stats_blackjacks%` | Natural 21s dealt on opening cards |
| `%blackjack_stats_busts%` | Number of times player exceeded 21 |
| `%blackjack_stats_current_streak%` | Current winning streak |
| `%blackjack_stats_best_streak%` | Highest winning streak ever recorded |
| `%blackjack_stats_win_rate%` | Win rate percentage (e.g. `62.5%`) |
| `%blackjack_stats_win_rate_raw%` | Win rate as decimal (e.g. `0.625`) |
| `%blackjack_stats_total_winnings%` | Total net balance won/lost (raw value) |
| `%blackjack_stats_total_winnings_formatted%` | Formatted balance (e.g. `$1.5K`, `$2.4M`) |
| `%blackjack_stats_has_played%` | Returns `true` if player has played before |

### 2. Table Status (`%blackjack_table_*%`)
| Placeholder | Description |
| :--- | :--- |
| `%blackjack_table_at_table%` | `true` if player is currently seated |
| `%blackjack_table_players%` | Current player count at the player's table |
| `%blackjack_table_max_players%` | Capacity of the table |
| `%blackjack_table_seats_available%` | Available seats remaining |
| `%blackjack_table_is_full%` | `true` if all seats are occupied |
| `%blackjack_table_game_in_progress%` | `true` if a round is active |
| `%blackjack_table_can_join%` | `true` if player can sit at a table |
| `%blackjack_table_world%` | World name of the table |
| `%blackjack_table_location_x%` | Table X coordinate |
| `%blackjack_table_location_y%` | Table Y coordinate |
| `%blackjack_table_location_z%` | Table Z coordinate |

### 3. Active Game State (`%blackjack_game_*%`)
| Placeholder | Description |
| :--- | :--- |
| `%blackjack_game_hand_value%` | Current total value of player's cards |
| `%blackjack_game_hand_cards%` | Total number of cards in player's hand |
| `%blackjack_game_is_turn%` | `true` if it is currently player's turn |
| `%blackjack_game_is_finished%` | `true` if player has stood or busted |
| `%blackjack_game_has_blackjack%` | `true` if player hit a natural Blackjack |
| `%blackjack_game_is_busted%` | `true` if player went over 21 |
| `%blackjack_game_can_double_down%` | `true` if eligible to double down |
| `%blackjack_game_has_doubled_down%` | `true` if player doubled down this round |
| `%blackjack_game_dealer_visible_value%`| Up-card value shown by dealer |
| `%blackjack_game_dealer_card_count%`| Total cards held by dealer |

### 4. Betting (`%blackjack_bet_*%`)
| Placeholder | Description |
| :--- | :--- |
| `%blackjack_bet_current%` | Current bet amount |
| `%blackjack_bet_current_formatted%` | Current bet formatted (e.g. `$250`) |
| `%blackjack_bet_has_bet%` | `true` if player placed an active bet |
| `%blackjack_bet_persistent%` | Saved bet for auto-rebets |
| `%blackjack_bet_persistent_formatted%`| Saved bet with formatting |
| `%blackjack_bet_has_persistent%` | `true` if persistent bet exists |
| `%blackjack_bet_min_bet%` / `max_bet` | Table minimum and maximum bet limits |

### 5. Economy (`%blackjack_economy_*%`)
| Placeholder | Description |
| :--- | :--- |
| `%blackjack_economy_balance%` | Player balance (raw) |
| `%blackjack_economy_balance_formatted%` | Formatted balance (`$12.5K`) |
| `%blackjack_economy_can_afford_min%` | `true` if player can afford minimum bet |
| `%blackjack_economy_can_afford_max%` | `true` if player can afford maximum bet |

---

## ⚙️ Configuration

The `plugins/Blackjack/config.yml` file allows full control over features, visuals, and rules:

```yaml
# Language: en (default), tr
language: en

# Currency symbol used across GUIs and broadcasts
currency-symbol: "$"

# Database storage (SQLITE or MYSQL)
database:
  type: SQLITE
  sqlite:
    file: "blackjack.db"
  mysql:
    host: "localhost"
    port: 3306
    database: "blackjack"
    username: "root"
    password: ""
    ssl: false

# Feature Toggles
features:
  leave-confirm-gui: true        # Prompt confirmation when dismounting with active bet
  double-down: true              # Enable Double Down mechanic
  quick-bets: true               # Show quick-bet chips in chat
  interactive-chat-buttons: true # Clickable chat buttons for actions
  chair-sitting: true            # ArmorStand chair sitting system
  auto-leave-inactivity: true    # Kick AFK players
  auto-leave-distance: true      # Kick players who walk too far away
  stats-tracker: true            # Stats recording and /bj stats
  version-checker: true          # Automatic GitHub update checks

# Default Table Limits
betting:
  min-bet: 10
  max-bet: 10000
  cooldown-ms: 2000

# Table Visuals
table:
  max-join-distance: 10.0
  max-players: 4
  wood-type: DARK_OAK
  felt-color: GREEN
  chair-cushion-color: RED
  hologram:
    enabled: true
    title: "&6&lBLACKJACK"

# Casino Rules
game:
  hit-soft-17: false             # Whether dealer hits on soft 17 (Ace + 6)
  auto-leave-timeout-seconds: 30
```

---

## 🗄️ Database & Storage

Blackjack uses **HikariCP** for high-performance, asynchronous connection pooling.

- **SQLite (Default):** Ready out of the box with zero setup. Data is stored in `plugins/Blackjack/blackjack.db`.
- **MySQL:** Perfect for server networks, BungeeCord, and Velocity setups. Enable `type: MYSQL` in `config.yml` and provide your credentials.
- **Legacy Migration:** Automatically detects and migrates legacy YAML data (`stats.yml` and old table entries) into the database seamlessly on startup.

---

## 🌐 Multi-Language (i18n)

Blackjack supports full internationalization:
- **English (`en`)**: Loaded from `messages_en.yml` (Default).
- **Turkish (`tr`)**: Loaded from `messages.yml`.

To switch languages:
1. Set `language: tr` (or `language: en`) in `plugins/Blackjack/config.yml`.
2. Run `/bj reload` or restart your server.
3. Every message, action bar prompt, GUI title, button label, and broadcast can be customized in the corresponding language file.

---

## 🔨 Building from Source

### Prerequisites
- Java 21 JDK
- Apache Maven 3.8+
- Git

### Build Instructions
```bash
# Clone the repository
git clone https://github.com/al2wastaken/Blackjack.git

# Navigate into the project folder
cd Blackjack

# Compile and package with Maven
mvn clean package
```
The compiled, shaded JAR will be created in `target/Blackjack-3.1.jar`.

---

## 📜 Credits & License

- **Developer:** [Altuğ (al2wastaken)](https://github.com/al2wastaken)
- **Modrinth Project:** [blackjack-3d on Modrinth](https://modrinth.com/plugin/blackjack-3d)
- **Fork Origin:** Forked and modernized from [DefectiveVortex/Blackjack](https://github.com/DefectiveVortex/Blackjack) and inspired by [aematsubara/Roulette](https://github.com/aematsubara/Roulette).
- **License:** Distributed under the [MIT License](LICENSE).
