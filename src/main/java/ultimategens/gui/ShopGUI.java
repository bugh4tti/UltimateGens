package ultimategens.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.milkbowl.vault.economy.Economy;
import ultimategens.UltimateGens;
import ultimategens.config.GensConfig;
import ultimategens.model.GeneratorType;
import ultimategens.utils.Text;

public class ShopGUI implements Listener {

    private final UltimateGens plugin;

    public ShopGUI(UltimateGens plugin) {
        this.plugin = plugin;
    }

    /** Holder para identificar el menú de la tienda. */
    public static class ShopHolder implements InventoryHolder {

        private final int page;
        private final Map<Integer, String> slots = new HashMap<>();
        private Inventory inventory;

        public ShopHolder(int page) {
            this.page = page;
        }

        public int getPage() {
            return page;
        }

        public Map<Integer, String> getSlots() {
            return slots;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ============================================================
    //  Abrir
    // ============================================================

    public void open(Player player, int page) {
        FileConfiguration c = plugin.getConfig();
        GensConfig cfg = plugin.getGensConfig();

        List<GeneratorType> list = new ArrayList<>(cfg.getTypes().values());
        List<Integer> genSlots = c.getIntegerList("gui.shop.gen-slots");
        int perPage = Math.max(1, genSlots.size());
        int maxPage = Math.max(0, (list.size() - 1) / perPage);
        page = Math.max(0, Math.min(page, maxPage));

        int rows = Math.max(1, Math.min(6, c.getInt("gui.shop.rows", 5)));
        ShopHolder holder = new ShopHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, Text.color(c.getString("gui.shop.title", "Tienda")));
        holder.setInventory(inventory);

        // Relleno
        ItemStack filler = simpleItem(c.getString("gui.shop.filler.material", "GRAY_STAINED_GLASS_PANE"),
                c.getString("gui.shop.filler.name", " "));
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        // Generadores
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < list.size() && i < genSlots.size(); i++) {
            GeneratorType type = list.get(start + i);
            int slot = genSlots.get(i);
            if (slot < 0 || slot >= inventory.getSize()) continue;

            ItemStack item = new ItemStack(type.material());
            ItemMeta meta = item.getItemMeta();
            String name = c.getString("item.name", "%name%").replace("%name%", type.name());
            meta.setDisplayName(Text.color("&r" + name));
            meta.setLore(plugin.getGeneratorManager().buildLore(type, true));
            item.setItemMeta(meta);

            inventory.setItem(slot, item);
            holder.getSlots().put(slot, type.id());
        }

        // Botones de página
        if (page > 0) {
            int slot = c.getInt("gui.shop.previous-page-slot", 39);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, simpleItem(c.getString("gui.shop.previous-page.material", "ARROW"),
                        c.getString("gui.shop.previous-page.name", "&a← Página anterior")));
            }
        }
        if (page < maxPage) {
            int slot = c.getInt("gui.shop.next-page-slot", 41);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, simpleItem(c.getString("gui.shop.next-page.material", "ARROW"),
                        c.getString("gui.shop.next-page.name", "&a→ Siguiente página")));
            }
        }

        player.openInventory(inventory);
    }

    private ItemStack simpleItem(String materialName, String name) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color(name));
        item.setItemMeta(meta);
        return item;
    }

    // ============================================================
    //  Clicks
    // ============================================================

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ShopHolder holder)) return;

        // Nada se puede mover dentro de la tienda
        event.setCancelled(true);

        if (event.getClickedInventory() != top) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        FileConfiguration c = plugin.getConfig();

        if (slot == c.getInt("gui.shop.previous-page-slot", 39) && holder.getPage() > 0) {
            int target = holder.getPage() - 1;
            plugin.getServer().getScheduler().runTask(plugin, () -> open(player, target));
            return;
        }
        if (slot == c.getInt("gui.shop.next-page-slot", 41) && event.getCurrentItem() != null
                && event.getCurrentItem().getType() == Material.matchMaterial(
                        c.getString("gui.shop.next-page.material", "ARROW"))) {
            int target = holder.getPage() + 1;
            plugin.getServer().getScheduler().runTask(plugin, () -> open(player, target));
            return;
        }

        String id = holder.getSlots().get(slot);
        if (id != null) {
            buy(player, id);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopHolder) {
            event.setCancelled(true);
        }
    }

    // ============================================================
    //  Compra
    // ============================================================

    private void buy(Player player, String id) {
        GensConfig cfg = plugin.getGensConfig();
        GeneratorType type = cfg.getType(id);
        if (type == null) return;

        // Solo el generador inicial se puede comprar
        if (!type.id().equals(cfg.getStarterId())) {
            player.sendMessage(cfg.msg("shop.not-buyable"));
            return;
        }

        Economy economy = plugin.getEconomy();
        if (economy == null) {
            player.sendMessage(cfg.msg("economy.no-economy"));
            return;
        }

        double price = type.price();
        if (!economy.has(player, price)) {
            player.sendMessage(cfg.msg("economy.not-enough-money", "%price%", plugin.formatMoney(price)));
            return;
        }

        economy.withdrawPlayer(player, price);
        plugin.getGeneratorManager().giveItem(player, plugin.getGeneratorManager().createItem(type, 1));
        player.sendMessage(cfg.msg("shop.bought",
                "%name%", type.name(),
                "%price%", plugin.formatMoney(price)));
    }
      }
