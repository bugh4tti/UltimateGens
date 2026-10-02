package ultimategens.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import net.milkbowl.vault.economy.Economy;
import ultimategens.UltimateGens;
import ultimategens.config.GensConfig;
import ultimategens.manager.GeneratorManager;
import ultimategens.model.GeneratorType;
import ultimategens.model.PlacedGen;

public class GenListener implements Listener {

    private final UltimateGens plugin;

    public GenListener(UltimateGens plugin) {
        this.plugin = plugin;
    }

    // ============================================================
    //  Colocar
    // ============================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        GeneratorManager manager = plugin.getGeneratorManager();
        GensConfig cfg = plugin.getGensConfig();

        String id = manager.getGenId(event.getItemInHand());
        if (id == null) return;

        Player player = event.getPlayer();
        GeneratorType type = cfg.getType(id);
        if (type == null) {
            event.setCancelled(true);
            player.sendMessage(cfg.msg("unknown-generator"));
            return;
        }

        int current = manager.count(player.getUniqueId());
        int limit = manager.getLimit(player);
        if (current >= limit) {
            event.setCancelled(true);
            player.sendMessage(cfg.msg("generator.limit-reached",
                    "%current%", String.valueOf(current),
                    "%limit%", limitText(limit)));
            return;
        }

        Block block = event.getBlockPlaced();
        PlacedGen gen = new PlacedGen(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ(),
                player.getUniqueId(),
                type.id());
        gen.setNextDrop(System.currentTimeMillis() + type.interval() * 1000L);
        manager.add(gen);

        player.sendMessage(cfg.msg("generator.placed",
                "%name%", type.name(),
                "%current%", String.valueOf(current + 1),
                "%limit%", limitText(limit)));
    }

    // ============================================================
    //  Mejorar / Reparar / Quitar
    // ============================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        PlacedGen gen = plugin.getGeneratorManager().get(block);
        if (gen == null) return;

        Player player = event.getPlayer();
        String upgradeClick = plugin.getConfig().getString("settings.upgrade-click", "SNEAK_RIGHT");
        String removeClick = plugin.getConfig().getString("settings.remove-click", "SNEAK_LEFT");

        if (matches(player, action, upgradeClick)) {
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            if (canManage(player, gen)) {
                upgrade(player, block, gen);
            }
        } else if (matches(player, action, removeClick)) {
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            if (canManage(player, gen)) {
                remove(player, block, gen);
            }
        }
    }

    private boolean matches(Player player, Action action, String mode) {
        boolean sneak = player.isSneaking();
        return switch (mode.toUpperCase()) {
            case "SNEAK_RIGHT" -> sneak && action == Action.RIGHT_CLICK_BLOCK;
            case "SNEAK_LEFT" -> sneak && action == Action.LEFT_CLICK_BLOCK;
            case "RIGHT" -> action == Action.RIGHT_CLICK_BLOCK;
            case "LEFT" -> action == Action.LEFT_CLICK_BLOCK;
            default -> false;
        };
    }

    private boolean canManage(Player player, PlacedGen gen) {
        if (gen.getOwner().equals(player.getUniqueId())) return true;
        if (player.hasPermission("ultimategens.admin")) return true;
        player.sendMessage(plugin.getGensConfig().msg("generator.not-owner"));
        return false;
    }

    private void upgrade(Player player, Block block, PlacedGen gen) {
        GensConfig cfg = plugin.getGensConfig();
        GeneratorType type = cfg.getType(gen.getTypeId());
        if (type == null) return;

        // Si está roto, la misma acción lo repara
        if (gen.isBroken()) {
            repair(player, gen, type);
            return;
        }

        GeneratorType next = type.isMaxLevel() ? null : cfg.getType(type.next());
        if (next == null) {
            player.sendMessage(cfg.msg("generator.max-level"));
            return;
        }

        Economy economy = plugin.getEconomy();
        if (economy == null) {
            player.sendMessage(cfg.msg("economy.no-economy"));
            return;
        }

        double cost = type.upgradeCost();
        if (!economy.has(player, cost)) {
            player.sendMessage(cfg.msg("generator.upgrade-no-money", "%price%", plugin.formatMoney(cost)));
            return;
        }

        economy.withdrawPlayer(player, cost);
        block.setType(next.material());
        gen.setTypeId(next.id());
        gen.setNextDrop(System.currentTimeMillis() + next.interval() * 1000L);

        player.sendMessage(cfg.msg("generator.upgraded",
                "%name%", next.name(),
                "%price%", plugin.formatMoney(cost)));
    }

    private void repair(Player player, PlacedGen gen, GeneratorType type) {
        GensConfig cfg = plugin.getGensConfig();

        Economy economy = plugin.getEconomy();
        if (economy == null) {
            player.sendMessage(cfg.msg("economy.no-economy"));
            return;
        }

        double cost = type.repairCost();
        if (!economy.has(player, cost)) {
            player.sendMessage(cfg.msg("generator.repair-no-money", "%price%", plugin.formatMoney(cost)));
            return;
        }

        economy.withdrawPlayer(player, cost);
        gen.setBroken(false);
        gen.setDrops(0);
        gen.setNextDrop(System.currentTimeMillis() + type.interval() * 1000L);

        player.sendMessage(cfg.msg("generator.repaired",
                "%name%", type.name(),
                "%price%", plugin.formatMoney(cost)));
    }

    private void remove(Player player, Block block, PlacedGen gen) {
        GeneratorManager manager = plugin.getGeneratorManager();
        GensConfig cfg = plugin.getGensConfig();
        GeneratorType type = cfg.getType(gen.getTypeId());

        block.setType(Material.AIR);
        manager.remove(gen);

        if (type != null) {
            if (plugin.getConfig().getBoolean("settings.give-back-on-remove", true)) {
                manager.giveItem(player, manager.createItem(type, 1));
            }
            player.sendMessage(cfg.msg("generator.removed", "%name%", type.name()));
        }
    }

    // ============================================================
    //  Protecciones
    // ============================================================

    /** Los generadores no se rompen a mano: se quitan con agachado + click izquierdo. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.getGeneratorManager().get(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.getGeneratorManager().get(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.getGeneratorManager().get(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (plugin.getGeneratorManager().get(block) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (plugin.getGeneratorManager().get(block) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private String limitText(int limit) {
        return limit == Integer.MAX_VALUE ? "∞" : String.valueOf(limit);
    }
  }
