package ultimategens.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import ultimategens.UltimateGens;
import ultimategens.manager.BoosterManager;
import ultimategens.model.BoosterType;

public class BoosterListener implements Listener {

    private final UltimateGens plugin;

    public BoosterListener(UltimateGens plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        BoosterManager boosters = plugin.getBoosterManager();
        String id = boosters.getBoosterId(event.getItem());
        if (id == null) return;

        // Es un booster: se evita que el ítem haga su acción normal
        event.setCancelled(true);

        Player player = event.getPlayer();
        BoosterType type = plugin.getGensConfig().getBooster(id);
        if (type == null) {
            player.sendMessage(plugin.getGensConfig().msg("booster.invalid"));
            return;
        }

        EquipmentSlot hand = event.getHand();
        if (hand == null) return;

        if (boosters.activate(player, type)) {
            consume(player, hand);
        }
    }

    private void consume(Player player, EquipmentSlot hand) {
        PlayerInventory inventory = player.getInventory();
        boolean offHand = hand == EquipmentSlot.OFF_HAND;
        ItemStack held = offHand ? inventory.getItemInOffHand() : inventory.getItemInMainHand();

        ItemStack result;
        if (held.getAmount() <= 1) {
            result = new ItemStack(Material.AIR);
        } else {
            result = held.clone();
            result.setAmount(held.getAmount() - 1);
        }

        if (offHand) {
            inventory.setItemInOffHand(result);
        } else {
            inventory.setItemInMainHand(result);
        }
    }
    }
