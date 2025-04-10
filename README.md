# InventoryAPI

A powerful library for creating interactive GUI menus in Spigot/Bukkit plugins with support for animations, patterns, and various inventory types.

## Features

- Support for all inventory types (chest, dispenser, hopper, etc.)
- Button click handlers with cooldowns
- Inventory patterns/layouts
- Animated buttons
- Pagination support
- Thread-safe implementation
- Event cancellation and protection

## Installation

1. Add lib in your project:

### Maven

```xml
<repository>
  <id>daycube-repo</id>
  <url>https://repo.daycube.su/releases</url>
</repository>

<dependency>
  <groupId>su.daycube</groupId>
  <artifactId>InventoryAPI</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Gradle 

```
maven {
    url = uri("https://repo.daycube.su/releases")
}

implementation("su.daycube:InventoryAPI:1.0.0")
```

2. Initialize the API in your plugin's onEnable():
```java
private InventoryAPI inventoryAPI;

@Override
public void onEnable() {
    inventoryAPI = new InventoryAPI(this);
}
```

## Basic Usage

```java
// Create a template
InventoryTemplate template = inventoryAPI.createChestTemplate("main_menu", "Main Menu", 3);

// Add a button
template.setButton(10, new InventoryButton(
    new ItemStack(Material.DIAMOND),
    event -> {
        Player player = (Player) event.getWhoClicked();
        player.sendMessage("You clicked the diamond!");
    }
));

// Open the menu for a player
inventoryAPI.openInventory(player, "main_menu", null);
```

## Advanced Features

### Patterns

```java
template.addPattern(new Pattern(
    "XXXXXXXXX",
    "XXXXYXXXX",
    "XXXXXXXXX"
).set('X', Material.GLASS_PANE)
 .set('Y', Material.DIAMOND));
```

### Animations

```java
List<ItemStack> frames = Arrays.asList(
    new ItemStack(Material.RED_WOOL),
    new ItemStack(Material.YELLOW_WOOL),
    new ItemStack(Material.GREEN_WOOL)
);

template.setButton(13, new InventoryButton(
    frames.get(0),
    event -> event.getWhoClicked().sendMessage("Clicked!")
).setAnimation(frames, 10));

// Start animation when opening
InventorySession session = inventoryAPI.openInventory(player, "animated_menu", null);
session.startAnimation(10);
```

### Different Inventory Types

```java
// Create a dispenser menu
InventoryTemplate dispenserTemplate = inventoryAPI.createTemplate(
    "dispenser_menu", 
    "Dispenser Menu", 
    InventoryType.DISPENSER
);
```