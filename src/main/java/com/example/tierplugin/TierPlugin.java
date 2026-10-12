package com.example.tierplugin;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TierPlugin extends JavaPlugin implements CommandExecutor, Listener {

    // Danh sách 6 tier theo thứ tự từ trái sang phải
    private final List<String> TIERS = Arrays.asList("lt5", "ht5", "lt4", "ht4", "lt3", "ht3");

    // Các vị trí ô (slots) ở hàng thứ 2 trong chest 27 ô (slot 10 đến 15)
    private final int[] TIER_SLOTS = {10, 11, 12, 13, 14, 15};

    // Màu len tương ứng cho từng tier
    private final Material[] TIER_MATERIALS = {
            Material.GRAY_WOOL,        // LT5
            Material.WHITE_WOOL,       // HT5
            Material.LIME_WOOL,        // LT4
            Material.GREEN_WOOL,       // HT4
            Material.LIGHT_BLUE_WOOL,  // LT3
            Material.BLUE_WOOL         // HT3
    };

    @Override
    public void onEnable() {
        getCommand("tier").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("TierPlugin đã sẵn sàng hoạt động!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Lệnh này chỉ dùng được trong game!");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("tier.use")) {
            player.sendMessage(ChatColor.RED + "Bạn không có quyền dùng lệnh này!");
            return true;
        }

        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Không tìm thấy người chơi '" + args[0] + "' đang online!");
                return true;
            }
        } else {
            target = player;
        }

        openTierGUI(player, target);
        return true;
    }

    private void openTierGUI(Player viewer, Player target) {
        // Tạo Inventory 27 ô với Holder tùy chỉnh chứa target
        TierHolder holder = new TierHolder(target);
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.DARK_GRAY + "Chọn Tier - " + target.getName());

        // Lấp nền bằng kính xám
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, glass);
        }

        // Ô giữa trên cùng (Slot 4): Đầu người chơi
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        if (headMeta != null) {
            // Tự động tải skin nếu có; nếu offline/crack sẽ hiển thị đầu mặc định (Steve/Alex)
            headMeta.setOwningPlayer(target);
            headMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + target.getName());

            String currentTier = getCurrentTier(target);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Tier hiện tại: " + (currentTier != null ? ChatColor.YELLOW + currentTier.toUpperCase() : ChatColor.RED + "No Tier"));
            headMeta.setLore(lore);
            head.setItemMeta(headMeta);
        }
        inv.setItem(4, head);

        // Hàng dưới (Slot 10 -> 15): Các ô chọn tier từ lt5 đến ht3
        for (int i = 0; i < TIERS.size(); i++) {
            String tier = TIERS.get(i);
            Material mat = TIER_MATERIALS[i];
            int slot = TIER_SLOTS[i];

            ItemStack tierItem = new ItemStack(mat);
            ItemMeta meta = tierItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + tier.toUpperCase());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Nhấp để set rank: " + ChatColor.YELLOW + tier.toUpperCase());
                lore.add(ChatColor.DARK_GRAY + "(Tự xoá tier cũ & add group " + tier + ")");
                meta.setLore(lore);
                tierItem.setItemMeta(meta);
            }
            inv.setItem(slot, tierItem);
        }

        viewer.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TierHolder)) {
            return;
        }

        event.setCancelled(true); // Ngăn không cho kéo/lấy item ra ngoài

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        Player tester = (Player) event.getWhoClicked();
        TierHolder holder = (TierHolder) event.getInventory().getHolder();
        Player target = holder.getTarget();

        int selectedIndex = -1;
        for (int i = 0; i < TIER_SLOTS.length; i++) {
            if (TIER_SLOTS[i] == slot) {
                selectedIndex = i;
                break;
            }
        }

        if (selectedIndex == -1) {
            return; // Click vào ô kính hoặc đầu người chơi
        }

        String newTier = TIERS.get(selectedIndex);
        setPlayerTier(tester, target, newTier);
    }

    private void setPlayerTier(Player tester, Player target, String newTier) {
        LuckPerms lp;
        try {
            lp = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            tester.sendMessage(ChatColor.RED + "Lỗi: Không kết nối được với LuckPerms!");
            return;
        }

        // Dùng modifyUser của LuckPerms: Tự động chạy thread safe và lưu dữ liệu
        lp.getUserManager().modifyUser(target.getUniqueId(), user -> {
            // 1. Xóa tất cả các group tier cũ
            for (String t : TIERS) {
                user.data().remove(InheritanceNode.builder(t).build());
            }
            // 2. Thêm group tier mới được chọn
            user.data().add(InheritanceNode.builder(newTier).build());
        }).thenRun(() -> {
            Bukkit.getScheduler().runTask(this, () -> {
                tester.sendMessage(ChatColor.GREEN + "✔ Đã set tier " + ChatColor.GOLD + newTier.toUpperCase() + ChatColor.GREEN + " cho " + target.getName() + "!");
                if (target.isOnline() && !target.equals(tester)) {
                    target.sendMessage(ChatColor.GREEN + "✦ Tier của bạn đã được cập nhật thành: " + ChatColor.GOLD + newTier.toUpperCase());
                }
                tester.playSound(tester.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                tester.closeInventory();
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(this, () -> {
                tester.sendMessage(ChatColor.RED + "Có lỗi khi lưu vào LuckPerms: " + throwable.getMessage());
            });
            return null;
        });
    }

    private String getCurrentTier(Player player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
                    String group = node.getGroupName().toLowerCase();
                    if (TIERS.contains(group)) {
                        return group;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    // Class đại diện Holder để định danh GUI an toàn tuyệt đối
    public static class TierHolder implements InventoryHolder {
        private final Player target;

        public TierHolder(Player target) {
            this.target = target;
        }

        public Player getTarget() {
            return target;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
