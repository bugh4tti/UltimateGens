package ultimategens.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import ultimategens.UltimateGens;

public class GensTabCompleter implements TabCompleter {

    private static final List<String> AMOUNTS = List.of("1", "5", "10", "32", "64");

    private final UltimateGens plugin;

    public GensTabCompleter(UltimateGens plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        boolean admin = sender.hasPermission("ultimategens.admin");
        boolean use = sender.hasPermission("ultimategens.use");
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);

        switch (args.length) {
            case 1 -> {
                if (use) {
                    options.add("shop");
                    options.add("sell");
                }
                if (admin) {
                    options.add("give");
                    options.add("booster");
                    options.add("reload");
                }
            }
            case 2 -> {
                if (admin && isGiveCommand(args[0])) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        options.add(player.getName());
                    }
                }
            }
            case 3 -> {
                if (admin) {
                    if (args[0].equalsIgnoreCase("give")) {
                        options.addAll(plugin.getGensConfig().getTypes().keySet());
                    } else if (args[0].equalsIgnoreCase("booster")) {
                        options.addAll(plugin.getGensConfig().getBoosters().keySet());
                    }
                }
            }
            case 4 -> {
                if (admin && isGiveCommand(args[0])) {
                    options.addAll(AMOUNTS);
                }
            }
            default -> {
                // Sin más sugerencias
            }
        }

        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(current));
        return options;
    }

    private boolean isGiveCommand(String arg) {
        return arg.equalsIgnoreCase("give") || arg.equalsIgnoreCase("booster");
    }
  }
