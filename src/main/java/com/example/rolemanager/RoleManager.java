package com.example.rolemanager;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RoleManager extends JavaPlugin implements CommandExecutor, Listener {

    public enum MenuType {
        TIER("tier-menu"),
        RANK("rank-menu");

        private final String configPath;

        MenuType(String configPath) {
            this.configPath = configPath;
        }

        public String getConfigPath() {
            return configPath;
        }
    }

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("tier").setExecutor(this);
        getCommand("rank").setExecutor(this);
        getCommand("rolemanager").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("RoleManager initialized with Catppuccin palette.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("rolemanager")) {
            if (!sender.hasPermission("rolemanager.admin")) {
                sender.sendMessage(msg("messages.prefix") + msg("messages.no-permission"));
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                sender.sendMessage(msg("messages.prefix") + msg("messages.reloaded"));
                return true;
            }
            sender.sendMessage(msg("messages.prefix") + color("&#a6adc8Use &#cba6f7/rolemanager reload &#a6adc8to refresh config."));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(msg("messages.prefix") + msg("messages.player-only"));
            return true;
        }

        Player player = (Player) sender;
        MenuType type = command.getName().equalsIgnoreCase("tier") ? MenuType.TIER : MenuType.RANK;
        String permission = type == MenuType.TIER ? "rolemanager.tier" : "rolemanager.rank";

        if (!player.hasPermission(permission)) {
            player.sendMessage(msg("messages.prefix") + msg("messages.no-permission"));
            return true;
        }

        Player target = (args.length > 0) ? Bukkit.getPlayer(args[0]) : player;
        if (target == null) {
            player.sendMessage(msg("messages.prefix") + msg("messages.player-not-found").replace("{player}", args[0]));
            return true;
        }

        openMenu(player, target, type);
        return true;
    }

    private void openMenu(Player viewer, Player target, MenuType type) {
        String path = type.getConfigPath();
        int size = getConfig().getInt(path + ".size", 27);
        String rawTitle = getConfig().getString(path + ".title", "Select Option | {player}");
        String title = color(rawTitle.replace("{player}", target.getName()));

        MenuHolder holder = new MenuHolder(target, type);
        Inventory inv = Bukkit.createInventory(holder, size, title);

        // Nền kính Catppuccin (Gray Stained Glass Pane)
        ItemStack background = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < size; i++) {
            inv.setItem(i, background);
        }

        // Đầu player hiển thị thông tin
        int headSlot = getConfig().getInt(path + ".head-slot", 4);
        String currentRole = getCurrentRole(target, type);
        String currentDisplay = (currentRole != null) ? currentRole.toUpperCase() : "None";

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        if (headMeta != null) {
            headMeta.setOwningPlayer(target);
            String rawHeadName = getConfig().getString(path + ".head-name", "{player}");
            headMeta.setDisplayName(color(rawHeadName.replace("{player}", target.getName())));

            List<String> lore = new ArrayList<>();
            for (String line : getConfig().getStringList(path + ".head-lore")) {
                lore.add(color(line.replace("{current}", currentDisplay).replace("{player}", target.getName())));
            }
            headMeta.setLore(lore);
            head.setItemMeta(headMeta);
        }
        inv.setItem(headSlot, head);

        // Đổ các nút chọn role từ config vào GUI
        ConfigurationSection itemsSection = getConfig().getConfigurationSection(path + ".items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                int slot = itemsSection.getInt(key + ".slot");
                String matName = itemsSection.getString(key + ".material", "STONE");
                Material material = Material.matchMaterial(matName.toUpperCase());
                if (material == null) material = Material.STONE;

                String name = color(itemsSection.getString(key + ".name", key));
                List<String> rawLore = itemsSection.getStringList(key + ".lore");
                List<String> lore = new ArrayList<>();
                for (String l : rawLore) lore.add(color(l));

                inv.setItem(slot, createItem(material, name, lore));
            }
        }

        viewer.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        MenuHolder holder = (MenuHolder) event.getInventory().getHolder();
        Player tester = (Player) event.getWhoClicked();
        Player target = holder.getTarget();
        MenuType type = holder.getType();

        ConfigurationSection itemsSection = getConfig().getConfigurationSection(type.getConfigPath() + ".items");
        if (itemsSection == null) return;

        String selectedGroup = null;
        for (String key : itemsSection.getKeys(false)) {
            if (itemsSection.getInt(key + ".slot") == slot) {
                selectedGroup = itemsSection.getString(key + ".group", key);
                break;
            }
        }

        if (selectedGroup != null) {
            assignRole(tester, target, type, selectedGroup);
        }
    }

    private void assignRole(Player tester, Player target, MenuType type, String newGroup) {
        LuckPerms lp;
        try {
            lp = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            tester.sendMessage(msg("messages.prefix") + msg("messages.luckperms-error"));
            return;
        }

        // Lấy danh sách toàn bộ các group được cấu hình trong menu này để xóa group cũ
        Set<String> managedGroups = new HashSet<>();
        ConfigurationSection itemsSection = getConfig().getConfigurationSection(type.getConfigPath() + ".items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                managedGroups.add(itemsSection.getString(key + ".group", key).toLowerCase());
            }
        }

        lp.getUserManager().modifyUser(target.getUniqueId(), user -> {
            for (String group : managedGroups) {
                user.data().remove(InheritanceNode.builder(group).build());
            }
            user.data().add(InheritanceNode.builder(newGroup.toLowerCase()).build());
        }).thenRun(() -> {
            Bukkit.getScheduler().runTask(this, () -> {
                String keySetMsg = type == MenuType.TIER ? "messages.tier-set" : "messages.rank-set";
                String keyReceivedMsg = type == MenuType.TIER ? "messages.tier-received" : "messages.rank-received";

                tester.sendMessage(msg("messages.prefix") + msg(keySetMsg)
                        .replace("{role}", newGroup.toUpperCase())
                        .replace("{player}", target.getName()));

                if (target.isOnline() && !target.equals(tester)) {
                    target.sendMessage(msg("messages.prefix") + msg(keyReceivedMsg)
                            .replace("{role}", newGroup.toUpperCase()));
                }

                tester.playSound(tester.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                tester.closeInventory();
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(this, () -> {
                tester.sendMessage(msg("messages.prefix") + color("&#f38ba8Error: " + throwable.getMessage()));
            });
            return null;
        });
    }

    private String getCurrentRole(Player player, MenuType type) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user == null) return null;

            ConfigurationSection itemsSection = getConfig().getConfigurationSection(type.getConfigPath() + ".items");
            if (itemsSection == null) return null;

            Set<String> managedGroups = new HashSet<>();
            for (String key : itemsSection.getKeys(false)) {
                managedGroups.add(itemsSection.getString(key + ".group", key).toLowerCase());
            }

            for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
                String group = node.getGroupName().toLowerCase();
                if (managedGroups.contains(group)) {
                    return group;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String color(String message) {
        if (message == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hexCode = matcher.group(1);
            matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + hexCode).toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    private String msg(String path) {
        return color(getConfig().getString(path, ""));
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static class MenuHolder implements InventoryHolder {
        private final Player target;
        private final MenuType type;

        public MenuHolder(Player target, MenuType type) {
            this.target = target;
            this.type = type;
        }

        public Player getTarget() {
            return target;
        }

        public MenuType getType() {
            return type;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
                            }
