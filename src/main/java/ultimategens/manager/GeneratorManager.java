package ultimategens.manager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import ultimategens.UltimateGens;
import ultimategens.model.BoosterType;
import ultimategens.model.GeneratorType;
import ultimategens.model.PlacedGen;
import ultimategens.utils.Text;

public class GeneratorManager {

    private final UltimateGens plugin;
    private final Map<String, PlacedGen> placed = new HashMap<>();
    private final NamespacedKey genKey;
    private final NamespacedKey dropKey;
    private final File dataFile;
    private final Random random = new Random();

    public GeneratorManager(UltimateGens plugin) {
        this.plugin = plugin;
        this.genKey = new NamespacedKey(plugin, "gen-id");
        this.dropKey = new NamespacedKey(plugin, "drop-value");
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    // ============================================================
    //  Generadores colocados
    // ============================================================

    public Collection<PlacedGen> all() {
        return placed.values();
    }

    public PlacedGen get(Block block) {
        return placed.get(PlacedGen.key(block));
    }

    public void add(PlacedGen gen) {
        placed.put(gen.key(), gen);
    }

    public void remove(PlacedGen gen) {
        placed.remove(gen.key());
    }

    public int count(UUID owner) {
        int total = 0;
        for (PlacedGen gen : placed.values()) {
            if (gen.getOwner().equals(owner)) total++;
        }
        return total;
    }

    public int getLimit(Player player) {
        int limit = plugin.getConfig().getInt("settings.default-limit", 10);
        String prefix = plugin.getConfig().getString("settings.limit-permission-prefix", "ultimategens.limit.");
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String perm = info.getPermission().toLowerCase();
            if (!perm.startsWith(prefix)) continue;
            String rest = perm.substring(prefix.length());
            if (rest.equals("*")) return Integer.MAX_VALUE;
            try {
                limit = Math.max(limit, Integer.parseInt(rest));
            } catch (NumberFormatException ignored) {
                // permiso con formato inválido
            }
        }
        return limit;
    }

    // ============================================================
    //  Ítems
    // ============================================================

    public ItemStack createItem(GeneratorType type, int amount) {
        ItemStack item = new ItemStack(type.material(), amount);
        ItemMeta meta = item.getItemMeta();
        String name = Text.replace(plugin.getConfig().getString("item.name", "%name%"), "%name%", type.name());
        meta.setDisplayName(Text.color("&r" + name));
        meta.setLore(buildLore(type, false));
        meta.getPersistentDataContainer().set(genKey, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    /** Devuelve el id del generador si el ítem es un generador, o null. */
    public String getGenId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(genKey, PersistentDataType.STRING);
    }

    /**
     * Lore del generador. En la tienda (shop = true) el generador inicial muestra
     * requisitos y "¡Click para comprar!"; el resto muestra "¡Colócalo en el suelo!".
     */
    public List<String> buildLore(GeneratorType type, boolean shop) {
        FileConfiguration c = plugin.getConfig();
        boolean buyable = shop && type.id().equals(plugin.getGensConfig().getStarterId());
        List<String> out = new ArrayList<>();

        for (String line : c.getStringList("item.lore")) {
            if (line.contains("%drops%")) {
                addDropLines(out, type);
                continue;
            }
            out.add(fill(line, type));
        }

        if (buyable) {
            for (String line : c.getStringList("item.requirements-lore")) {
                out.add(fill(line, type));
            }
            out.add(c.getString("item.click-buy", ""));
        } else {
            out.add(c.getString("item.click-place", ""));
        }

        List<String> colored = new ArrayList<>();
        for (String line : out) {
            colored.add(Text.color("&r" + line));
        }
        return colored;
    }

    private void addDropLines(List<String> out, GeneratorType type) {
        String dropLine = plugin.getConfig().getString("item.drop-line", "%drop% $%value%");
        if (type.normal() != null) {
            out.add(fillDrop(dropLine, type.normal()));
        }
        if (type.superDrop() != null) {
            out.add(fillDrop(dropLine, type.superDrop()));
        }
    }

    private String fillDrop(String line, GeneratorType.DropInfo drop) {
        return line.replace("%drop%", drop.name())
                .replace("$%value%", "$" + Text.full(drop.value()));
    }

    private String fill(String line, GeneratorType type) {
        String upgrade = type.isMaxLevel() ? "Máximo" : "$" + Text.full(type.upgradeCost());
        return line.replace("$%upgrade%", upgrade)
                .replace("$%repair%", "$" + Text.full(type.repairCost()))
                .replace("$%price%", "$" + Text.full(type.price()))
                .replace("%time%", String.valueOf(type.interval()))
                .replace("%name%", type.name());
    }

    /** Entrega un ítem al jugador; si no hay espacio lo tira a sus pies. */
    public void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(item);
        for (ItemStack rest : left.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    // ============================================================
    //  Drops
    // ============================================================

    public ItemStack createDrop(GeneratorType.DropInfo drop, int amount, boolean isSuper) {
        ItemStack item = new ItemStack(drop.material(), amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color(isSuper ? "&r&e✦ &f" + drop.name() : "&r&f" + drop.name()));

        List<String> lore = new ArrayList<>();
        lore.add(Text.color("&r&7Valor: &a$" + Text.full(drop.value())));
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(dropKey, PersistentDataType.DOUBLE, drop.value());

        if (isSuper) {
            Enchantment glow = Enchantment.getByKey(NamespacedKey.minecraft("luck_of_the_sea"));
            if (glow != null) {
                meta.addEnchant(glow, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    /** Valor de venta de un ítem dropeado por un generador (0 si no lo es). */
    public double getDropValue(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Double value = item.getItemMeta().getPersistentDataContainer().get(dropKey, PersistentDataType.DOUBLE);
        return value == null ? 0 : value;
    }

    /** Se ejecuta cada segundo desde DropTask. */
    public void tick(long now) {
        BoosterManager boosters = plugin.getBoosterManager();
        int breakAfter = plugin.getConfig().getInt("settings.breaks-after-drops", 0);

        Iterator<PlacedGen> iterator = placed.values().iterator();
        while (iterator.hasNext()) {
            PlacedGen gen = iterator.next();

            World world = Bukkit.getWorld(gen.getWorld());
            if (world == null) continue;
            if (!world.isChunkLoaded(gen.getX() >> 4, gen.getZ() >> 4)) continue;

            GeneratorType type = plugin.getGensConfig().getType(gen.getTypeId());
            if (type == null) continue;

            Block block = world.getBlockAt(gen.getX(), gen.getY(), gen.getZ());
            if (block.getType() != type.material()) {
                // El bloque ya no existe: se limpia el registro
                iterator.remove();
                continue;
            }

            if (gen.isBroken() || now < gen.getNextDrop()) continue;

            double speed = boosters.getMultiplier(gen.getOwner(), BoosterType.Kind.SPEED);
            double dropMultiplier = boosters.getMultiplier(gen.getOwner(), BoosterType.Kind.DROP);

            spawnDrop(world, gen, type, dropMultiplier);

            long interval = (long) (type.interval() * 1000L / Math.max(0.1, speed));
            gen.setNextDrop(now + interval);

            if (breakAfter > 0) {
                gen.setDrops(gen.getDrops() + 1);
                if (gen.getDrops() >= breakAfter) {
                    gen.setBroken(true);
                    Player owner = Bukkit.getPlayer(gen.getOwner());
                    if (owner != null) {
                        owner.sendMessage(plugin.getGensConfig().msg("generator.broken", "%name%", type.name()));
                    }
                }
            }
        }
    }

    private void spawnDrop(World world, PlacedGen gen, GeneratorType type, double multiplier) {
        GeneratorType.DropInfo chosen = type.normal();
        boolean isSuper = false;

        GeneratorType.DropInfo superDrop = type.superDrop();
        if (superDrop != null && random.nextDouble() * 100.0 < superDrop.chance()) {
            chosen = superDrop;
            isSuper = true;
        }
        if (chosen == null) return;

        int amount = (int) Math.floor(multiplier);
        if (random.nextDouble() < multiplier - amount) amount++;
        amount = Math.max(1, amount);

        Location location = new Location(world, gen.getX() + 0.5, gen.getY() + 1.1, gen.getZ() + 0.5);
        org.bukkit.entity.Item dropped = world.dropItem(location, createDrop(chosen, amount, isSuper));
        dropped.setVelocity(new Vector(0, 0.1, 0));
    }

    // ============================================================
    //  Persistencia (data.yml)
    // ============================================================

    public void loadData() {
        placed.clear();
        if (!dataFile.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = yaml.getConfigurationSection("gens");
        if (section == null) return;

        long now = System.currentTimeMillis();
        for (String key : section.getKeys(false)) {
            ConfigurationSection c = section.getConfigurationSection(key);
            if (c == null) continue;
            try {
                PlacedGen gen = new PlacedGen(
                        c.getString("world"),
                        c.getInt("x"),
                        c.getInt("y"),
                        c.getInt("z"),
                        UUID.fromString(c.getString("owner")),
                        c.getString("type"));
                gen.setDrops(c.getInt("drops", 0));
                gen.setBroken(c.getBoolean("broken", false));
                gen.setNextDrop(now + 1000L);
                placed.put(gen.key(), gen);
            } catch (Exception e) {
                plugin.getLogger().warning("Generador inválido en data.yml (" + key + ").");
            }
        }
    }

    public void saveData() {
        YamlConfiguration yaml = new YamlConfiguration();
        int index = 0;
        for (PlacedGen gen : placed.values()) {
            String path = "gens." + (index++) + ".";
            yaml.set(path + "world", gen.getWorld());
            yaml.set(path + "x", gen.getX());
            yaml.set(path + "y", gen.getY());
            yaml.set(path + "z", gen.getZ());
            yaml.set(path + "owner", gen.getOwner().toString());
            yaml.set(path + "type", gen.getTypeId());
            yaml.set(path + "drops", gen.getDrops());
            yaml.set(path + "broken", gen.isBroken());
        }
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar data.yml: " + e.getMessage());
        }
    }
  }
