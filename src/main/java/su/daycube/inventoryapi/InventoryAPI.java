package su.daycube.inventoryapi;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class InventoryAPI {

    private final Plugin plugin;
    private final Map<UUID, InventorySession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, InventoryTemplate> templates = new ConcurrentHashMap<>();

    public InventoryAPI(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(new InventoryListener(), plugin);
    }

    public InventoryTemplate createTemplate(String id, String title, InventoryType type) {
        InventoryTemplate template = new InventoryTemplate(id, title, type);
        templates.put(id, template);
        return template;
    }

    public InventoryTemplate createChestTemplate(String id, String title, int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("Rows must be between 1 and 6");
        }
        return createTemplate(id, title, InventoryType.CHEST).setSize(rows * 9);
    }

    public InventoryTemplate getTemplate(String id) {
        return templates.get(id);
    }

    public InventorySession openInventory(Player player, String templateId, Map<String, Object> data) {
        InventoryTemplate template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }

        closeInventory(player);

        InventorySession session = new InventorySession(player, template, data);
        activeSessions.put(player.getUniqueId(), session);
        session.updateInventory();

        return session;
    }

    public void closeInventory(Player player) {
        InventorySession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            session.close();
        }
    }

    public InventorySession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    public static class InventoryTemplate {
        private final String id;
        private final String title;
        private final InventoryType type;
        private int size = -1;
        private final Map<Integer, InventoryButton> buttons = new HashMap<>();
        private Consumer<InventorySession> updateHandler;
        private Consumer<InventorySession> closeHandler;
        private final List<Pattern> patterns = new ArrayList<>();

        public InventoryTemplate(String id, String title, InventoryType type) {
            this.id = id;
            this.title = title;
            this.type = type;
        }

        public InventoryTemplate setSize(int size) {
            if (type != InventoryType.CHEST && size != -1) {
                throw new IllegalArgumentException("Size can only be set for CHEST inventory type");
            }
            if (size % 9 != 0) {
                throw new IllegalArgumentException("Size must be a multiple of 9");
            }
            this.size = size;
            return this;
        }

        public InventoryTemplate setButton(int slot, InventoryButton button) {
            buttons.put(slot, button);
            return this;
        }

        public InventoryTemplate setUpdateHandler(Consumer<InventorySession> handler) {
            this.updateHandler = handler;
            return this;
        }

        public InventoryTemplate setCloseHandler(Consumer<InventorySession> handler) {
            this.closeHandler = handler;
            return this;
        }

        public InventoryTemplate addPattern(Pattern pattern) {
            this.patterns.add(pattern);
            return this;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public InventoryType getType() {
            return type;
        }

        public int getSize() {
            return size;
        }

        public Map<Integer, InventoryButton> getButtons() {
            return buttons;
        }

        public Consumer<InventorySession> getUpdateHandler() {
            return updateHandler;
        }

        public Consumer<InventorySession> getCloseHandler() {
            return closeHandler;
        }

        public List<Pattern> getPatterns() {
            return patterns;
        }
    }

    public static class Pattern {
        private final String[] layout;
        private final Map<Character, ItemStack> items = new HashMap<>();

        public Pattern(String... layout) {
            this.layout = layout;
        }

        public Pattern set(char character, ItemStack item) {
            items.put(character, item);
            return this;
        }

        public Pattern set(char character, Material material) {
            return set(character, new ItemStack(material));
        }

        public void apply(Inventory inventory) {
            for (int row = 0; row < layout.length; row++) {
                String rowStr = layout[row];
                for (int col = 0; col < rowStr.length(); col++) {
                    char c = rowStr.charAt(col);
                    if (items.containsKey(c)) {
                        int slot = row * 9 + col;
                        if (slot < inventory.getSize()) {
                            inventory.setItem(slot, items.get(c).clone());
                        }
                    }
                }
            }
        }
    }

    public static class InventoryButton {
        private ItemStack icon;
        private final Consumer<InventoryClickEvent> clickHandler;
        private boolean updatable = false;
        private long clickCooldown = 0;
        private boolean animation = false;
        private List<ItemStack> animationFrames = new ArrayList<>();
        private int animationSpeed = 20;
        private int currentFrame = 0;

        public InventoryButton(ItemStack icon, Consumer<InventoryClickEvent> clickHandler) {
            this.icon = icon;
            this.clickHandler = clickHandler;
        }

        public InventoryButton setUpdatable(boolean updatable) {
            this.updatable = updatable;
            return this;
        }

        public InventoryButton setClickCooldown(long cooldown) {
            this.clickCooldown = cooldown;
            return this;
        }

        public InventoryButton setAnimation(List<ItemStack> frames, int speed) {
            if (frames == null || frames.isEmpty()) {
                throw new IllegalArgumentException("Frames cannot be null or empty");
            }
            this.animation = true;
            this.animationFrames = new ArrayList<>(frames);
            this.animationSpeed = speed;
            this.icon = frames.get(0).clone();
            return this;
        }

        public void updateAnimation() {
            if (animation && !animationFrames.isEmpty()) {
                currentFrame = (currentFrame + 1) % animationFrames.size();
                icon = animationFrames.get(currentFrame).clone();
            }
        }

        public ItemStack getIcon() {
            return icon;
        }

        public Consumer<InventoryClickEvent> getClickHandler() {
            return clickHandler;
        }

        public boolean isUpdatable() {
            return updatable;
        }

        public long getClickCooldown() {
            return clickCooldown;
        }

        public boolean isAnimation() {
            return animation;
        }

        public int getAnimationSpeed() {
            return animationSpeed;
        }
    }

    public class InventorySession implements InventoryHolder {
        private final Player player;
        private final InventoryTemplate template;
        private final Map<String, Object> data;
        private final Inventory inventory;
        private final Map<Integer, Long> clickCooldowns = new HashMap<>();
        private boolean closed = false;
        private int animationTaskId = -1;

        public InventorySession(Player player, InventoryTemplate template, Map<String, Object> data) {
            this.player = player;
            this.template = template;
            this.data = data != null ? new HashMap<>(data) : new HashMap<>();

            if (template.getType() == InventoryType.CHEST && template.getSize() > 0) {
                this.inventory = Bukkit.createInventory(this, template.getSize(), template.getTitle());
            } else {
                this.inventory = Bukkit.createInventory(this, template.getType(), template.getTitle());
            }
        }

        public void updateInventory() {
            if (closed) return;

            inventory.clear();

            for (Pattern pattern : template.getPatterns()) {
                pattern.apply(inventory);
            }

            for (Map.Entry<Integer, InventoryButton> entry : template.getButtons().entrySet()) {
                int slot = entry.getKey();
                InventoryButton button = entry.getValue();

                if (button.isUpdatable() && template.getUpdateHandler() != null) {
                    template.getUpdateHandler().accept(this);
                }

                inventory.setItem(slot, button.getIcon());
            }

            if (template.getUpdateHandler() != null) {
                template.getUpdateHandler().accept(this);
            }

            if (!player.getOpenInventory().getTopInventory().equals(inventory)) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.openInventory(inventory);
                    }
                }.runTask(plugin);
            }
        }

        public void startAnimation(int interval) {
            if (animationTaskId != -1) {
                Bukkit.getScheduler().cancelTask(animationTaskId);
            }

            animationTaskId = new BukkitRunnable() {
                @Override
                public void run() {
                    if (closed) {
                        cancel();
                        return;
                    }

                    for (InventoryButton button : template.getButtons().values()) {
                        if (button.isAnimation()) {
                            button.updateAnimation();
                        }
                    }

                    updateInventory();
                }
            }.runTaskTimer(plugin, 0, interval).getTaskId();
        }

        public void stopAnimation() {
            if (animationTaskId != -1) {
                Bukkit.getScheduler().cancelTask(animationTaskId);
                animationTaskId = -1;
            }
        }

        public void close() {
            if (closed) return;
            closed = true;

            stopAnimation();

            if (template.getCloseHandler() != null) {
                template.getCloseHandler().accept(this);
            }

            if (player.getOpenInventory().getTopInventory().equals(inventory)) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.closeInventory();
                    }
                }.runTask(plugin);
            }
        }

        public void handleClick(InventoryClickEvent event) {
            if (closed) return;

            int slot = event.getRawSlot();
            if (slot < 0 || slot >= inventory.getSize()) return;

            InventoryButton button = template.getButtons().get(slot);
            if (button == null) return;

            if (button.getClickCooldown() > 0) {
                long lastClick = clickCooldowns.getOrDefault(slot, 0L);
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastClick < button.getClickCooldown()) {
                    event.setCancelled(true);
                    return;
                }
                clickCooldowns.put(slot, currentTime);
            }

            if (button.getClickHandler() != null) {
                button.getClickHandler().accept(event);
            }

            event.setCancelled(true);
        }

        public Player getPlayer() {
            return player;
        }

        public InventoryTemplate getTemplate() {
            return template;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public boolean isClosed() {
            return closed;
        }

        public boolean isAnimationRunning() {
            return animationTaskId != -1;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private class InventoryListener implements Listener {
        @EventHandler
        public void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            InventorySession session = activeSessions.get(player.getUniqueId());

            if (session != null && event.getInventory().getHolder() == session) {
                session.handleClick(event);
            }
        }

        @EventHandler
        public void onInventoryClose(InventoryCloseEvent event) {
            if (!(event.getPlayer() instanceof Player player)) return;
            InventorySession session = activeSessions.get(player.getUniqueId());

            if (session != null && event.getInventory().getHolder() == session) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.getOpenInventory().getTopInventory().getHolder() != session) {
                            activeSessions.remove(player.getUniqueId());
                            session.close();
                        }
                    }
                }.runTaskLater(plugin, 1L);
            }
        }

        @EventHandler
        public void onInventoryDrag(InventoryDragEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            InventorySession session = activeSessions.get(player.getUniqueId());

            if (session != null && event.getInventory().getHolder() == session) {
                for (int slot : event.getRawSlots()) {
                    if (slot < event.getInventory().getSize()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }
}