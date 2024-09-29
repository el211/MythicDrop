# MythicDrop

**MythicDrop** is a lightweight plugin designed to enhance your MythicMobs experience by adding customizable drops upon killing MythicMobs. With **MythicDrop**, you can configure specific items, commands, or even give players in-game currency as rewards for defeating MythicMobs.

## Features
- Customizable drops for each MythicMob.
- Support for items, commands, and in-game currency as rewards.
- Easily configurable through a simple YAML file.
- Color-coded messages to enhance player experience.
- LuckPerms support for per-group configured drops!
- **NEW:** Added Damage Processing option to choose whether the player with the most damage or the last-hit player gets the reward.

## How to Use
1. Install **MythicDrop** on your Spigot server.
2. Configure custom drops for each MythicMob in the `config.yml` file.
3. Customize messages and rewards to match your server's gameplay.
4. Start slaying MythicMobs and watch as your players enjoy the custom rewards!

## Configuration Example

```yaml
# Reward processing options
reward-processing:
  most-damage: false   # If true, the player who dealt the most damage will receive the reward
  # If false, the player who delivers the last hit will receive the reward

# If you set most-damage to true, you have to modify the mythicmobs mob config and add the "ThreatTable: true" option
# Example:
# BigScaryBoss:
#   Type: zombie
#   Display: '&6Zombie'
#   Health: 20000
#   Modules:
#     ThreatTable: true
# Configuration for different mobs and their drops

Skeleton-king:
  drops:
    default:
      drop1:
        command: "give %player% diamond 1"
        chance: 0.5
        message: "&b&lYou got a diamond from the Skeleton King!" # Custom message supporting color codes
      drop2:
        command: "give %player% iron_sword 1"
        chance: 0.3
        message: "&e&lYou got an iron sword from the Skeleton King!"
      drop3:
        command: "money give %player% 100"
        chance: 0.2
        message: "&a&lYou received 100 money for defeating the Skeleton King!"
    vip:
      drop1:
        command: "give %player% diamond 2"
        chance: 0.6
        message: "&b&lVIP! You got 2 diamonds from the Skeleton King!" # VIP reward message
      drop2:
        command: "give %player% iron_sword 1"
        chance: 0.4
        message: "&e&lVIP! You got an iron sword from the Skeleton King!"
      drop3:
        command: "money give %player% 200"
        chance: 0.3
        message: "&a&lVIP! You received 200 money for defeating the Skeleton King!"

Zombie-lord:
  drops:
    default:
      drop1:
        command: "give %player% emerald 1"
        chance: 0.4
        message: "&2&lYou got an emerald from the Zombie Lord!"
      drop2:
        command: "give %player% iron_helmet 1"
        chance: 0.25
        message: "&6&lYou got an iron helmet from the Zombie Lord!"
      drop3:
        command: "money give %player% 150"
        chance: 0.1
        message: "&a&lYou received 150 money for defeating the Zombie Lord!"
    vip:
      drop1:
        command: "give %player% emerald 2"
        chance: 0.6
        message: "&2&lVIP! You got 2 emeralds from the Zombie Lord!"
      drop2:
        command: "give %player% iron_chestplate 1"
        chance: 0.3
        message: "&6&lVIP! You got an iron chestplate from the Zombie Lord!"
      drop3:
        command: "money give %player% 300"
        chance: 0.25
        message: "&a&lVIP! You received 300 money for defeating the Zombie Lord!"
