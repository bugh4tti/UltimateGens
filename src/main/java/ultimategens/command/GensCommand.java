package ultimategens.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.milkbowl.vault.economy.Economy;
import ultimategens.UltimateGens;
import ultimategens.config.GensConfig;
import ultimategens.manager.BoosterManager;
import ultimategens.manager.GeneratorManager;
import ultimategens.model.BoosterType;
import ultimategens.model.GeneratorType;

public class GensCommand implements CommandExecutor, TabCompleter {

    private final UltimateGens plugin;

    public GensCommand(UltimateGens plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        GensConfig cfg = plugin.getGensConfig();

        if (args.length == 0) {
            if (sender instanceof Player player) {
                openShop(player);
            } else {
                sendHelp(sender);
            }
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "shop" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(cfg.msg("only-players"));
                    return true;
                }
                openShop(player);
            }
            case "sell" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(cfg.msg("only-players"));
                    return true;
                }
                sell(player);
            }
            case "give" -> giveGen(sender, args);
            case "booster" -> giveBooster(sender, args);
            case "reload" -> {
                if (!sender.hasPermission("ultimategens.admin")) {
                    sender.sendMessage(cfg.msg("no-permission"));
                    return true;
                }
                plugin.reloadAll();
                sender.sendMessage(cfg.msg("reloaded"));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    // ============================================================
    //  Subcomandos
    // ============================================================

    private void openShop(Player player) {
        if (!player.hasPermission("ultimategens.use")) {
            player.sendMessage(plugin.getGensConfig().msg("no-permission"));
            return;
        }
        plugin.getShopGUI().open(player, 0);
    }

    private void sell(Player player) {
        GensConfig cfg = plugin.getGensConfig();
        if (!player.hasPermission("ultimategens.use")) {
            player.sendMessage(cfg.msg("no-permission"));
            return;
        }

        Economy economy = plugin.getEconomy();
        if (economy == null) {
            player.sendMessage(cfg.msg("economy.no-economy"));
            return;
        }

        GeneratorManager manager = plugin.getGeneratorManager();
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();

        double base = 0;
        int amount = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            double value = manager.getDropValue(item);
            if (value <= 0) continue;
            base += value * item.getAmount();
            amount += item.getAmount();
            inventory.setItem(i, null);
        }

        if (amount == 0) {
            player.sendMessage(cfg.msg("sell.nothing"));
            return;
        }

        double global = plugin.getConfig().getDouble("settings.global-multiplier", 1.0);
        double boost = plugin.getBoosterManager().getMultiplier(player.getUniqueId(), BoosterType.Kind.SELL);
        double multiplier = global * boost;
        double money = base * multiplier;

        economy.depositPlayer(player, money);
        player.sendMessage(cfg.msg("sell.sold",
                "%amount%", String.valueOf(amount),
                "%money%", plugin.formatMoney(money),
                "%multiplier%", BoosterManager.fmt(multiplier)));
    }

    private void giveGen(CommandSender sender, String[] args) {
        GensConfig cfg = plugin.getGensConfig();
        if (!sender.hasPermission("ultimategens.admin")) {
            sender.sendMessage(cfg.msg("no-permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(cfg.msg("help-give"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(cfg.msg("player-not-found"));
            return;
        }

        GeneratorType type = cfg.getType(args[2]);
        if (type == null) {
            sender.sendMessage(cfg.msg("unknown-generator"));
            return;
        }

        int amount = parseAmount(args, 3);
        GeneratorManager manager = plugin.getGeneratorManager();
        manager.giveItem(target, manager.createItem(type, amount));
        sender.sendMessage(cfg.msg("generator.given",
                "%amount%", String.valueOf(amount),
                "%name%", type.name(),
                "%player%", target.getName()));
    }

    private void giveBooster(CommandSender sender, String[] args) {
        GensConfig cfg = plugin.getGensConfig();
        if (!sender.hasPermission("ultimategens.admin")) {
            sender.sendMessage(cfg.msg("no-permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(cfg.msg("help-booster"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(cfg.msg("player-not-found"));
            return;
        }

        BoosterType type = cfg.getBooster(args[2]);
        if (type == null) {
            sender.sendMessage(cfg.msg("booster.invalid"));
            return;
        }

        int amount = parseAmount(args, 3);
        plugin.getGeneratorManager().giveItem(target, plugin.getBoosterManager().createItem(type, amount));
        sender.sendMessage(cfg.msg("booster.given",
                "%booster%", type.id(),
                "%player%", target.getName()));
    }

    private int parseAmount(String[] args, int index) {
        if (args.length <= index) return 1;
        try {
            return Math.max(1, Math.min(64, Integer.parseInt(args[index])));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void sendHelp(CommandSender sender) {
        for (String line : plugin.getGensConfig().list("help")) {
            sender.sendMessage(line);
        }
    }

    // ============================================================
    //  Tab completion
    // ============================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("ultimategens.admin");
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);

        if (args.length == 1) {
            out.add("shop");
            out.add("sell");
            if (admin) {
                out.add("give");
                out.add("booster");
                out.add("reload");
            }
        } else if (admin && args.length == 2
                && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("booster"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                out.add(player.getName());
            }
        } else if (admin && args.length == 3) {
            if (args[0].equalsIgnoreCase("give")) {
                out.addAll(plugin.getGensConfig().getTypes().keySet());
            } else if (args[0].equalsIgnoreCase("booster")) {
                out.addAll(plugin.getGensConfig().getBoosters().keySet());
            }
        }

        out.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(current));
        return out;
    }
                    }
