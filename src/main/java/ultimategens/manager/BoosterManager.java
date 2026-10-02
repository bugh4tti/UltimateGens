package ultimategens.manager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import ultimategens.UltimateGens;
import ultimategens.model.BoosterType;
import ultimategens.utils.Text;

public class BoosterManager {

    private record Active(double multiplier, long end) {
    }

    private final UltimateGens plugin;
    private final NamespacedKey boosterKey;
    private final File dataFile;
    private final Random random = new Random();
    private final Map<UUID, Map<BoosterType.Kind, Active>> active = new HashMap<>();

    public BoosterManager(UltimateGens plugin) {
        this.plugin = plugin;
        this.boosterKey = new NamespacedKey(plugin, "booster-id");
        this.dataFile = new File(plugin.getDataFolder(), "boosters-data.yml");
        startExpirationTask();
    }

    // ============================================================
    //  Multiplicadores
    // ============================================================

    /** Multiplicador activo del jugador para ese tipo (1.0 si no tiene ninguno). */
    public double getMultiplier(UUID uuid, BoosterType.Kind kind) {
        Map<BoosterType.Kind, Active> map = active.get(uuid);
        if (map == null) return 1.0;
        Active current = map.get(kind);
        if (current == null || current.end() <= System.currentTimeMillis()) return 1.0;
        return current.multiplier();
    }

    /** Segundos restantes del booster activo (0 si no hay). */
    public long getRemaining(UUID uuid, BoosterType.Kind kind) {
        Map<BoosterType.Kind, Active> map = active.get(uuid);
        if (map == null) return 0;
        Active current = map.get(kind);
        if (current == null) return 0;
        return Math.max(0, (current.end() - System.currentTimeMillis()) / 1000L);
    }

    // ============================================================
    //  Ítems
    // ============================================================

    public ItemStack createItem(BoosterType type, int amount) {
        ItemStack item = new ItemStack(type.material(), amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color("&r" + type.name()));

        List<String> lore = new java.util.ArrayList<>();
        for (String line : type.lore()) {
            lore.add(Text.color("&r" + fill(line, type)));
        }
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(boosterKey, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    /** Devuelve el id del booster si el ítem es un booster, o null. */
    public String getBoosterId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(boosterKey, PersistentDataType.STRING);
    }

    // ============================================================
    //  Activación
    // ============================================================

    /**
     * Intenta activar el booster. Devuelve true si el ítem se debe consumir
     * (se haya activado o fallado la probabilidad) y false si no.
     */
    public boolean activate(Player player, BoosterType type) {
        Map<BoosterType.Kind, Active> map = active.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        long now = System.currentTimeMillis();

        Active current = map.get(type.kind());
        if (current != null && current.end() > now) {
            player.sendMessage(plugin.getGensConfig().msg("booster.already-active", "%type%", typeName(type.kind())));
            return false;
        }

        if (random.nextDouble() * 100.0 < type.chance()) {
            map.put(type.kind(), new Active(type.multiplier(), now + type.duration() * 1000L));
            sendTitle(player, "booster-titles.activated",
                    "%multiplier%", fmt(type.multiplier()),
                    "%time%", Text.time(type.duration()),
                    "%type%", typeName(type.kind()),
                    "%chance%", fmt(type.chance()));
        } else {
            sendTitle(player, "booster-titles.failed",
                    "%multiplier%", fmt(type.multiplier()),
                    "%time%", Text.time(type.duration()),
                    "%type%", typeName(type.kind()),
                    "%chance%", fmt(type.chance()));
        }
        return true;
    }

    private void sendTitle(Player player, String path, String... pairs) {
        FileConfiguration c = plugin.getConfig();
        String title = Text.color(Text.replace(c.getString(path + ".title", ""), pairs));
        String subtitle = Text.color(Text.replace(c.getString(path + ".subtitle", ""), pairs));
        player.sendTitle(title, subtitle,
                c.getInt(path + ".fade-in", 10),
                c.getInt(path + ".stay", 40),
                c.getInt(path + ".fade-out", 10));
    }

    private void startExpirationTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<UUID, Map<BoosterType.Kind, Active>>> outer = active.entrySet().iterator();
            while (outer.hasNext()) {
                Map.Entry<UUID, Map<BoosterType.Kind, Active>> entry = outer.next();
                Iterator<Map.Entry<BoosterType.Kind, Active>> inner = entry.getValue().entrySet().iterator();
                while (inner.hasNext()) {
                    Map.Entry<BoosterType.Kind, Active> booster = inner.next();
                    if (booster.getValue().end() > now) continue;
                    inner.remove();

                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null) {
                        String type = typeName(booster.getKey());
                        sendTitle(player, "booster-titles.expired", "%type%", type);
                        player.sendMessage(plugin.getGensConfig().msg("booster.expired", "%type%", type));
                    }
                }
                if (entry.getValue().isEmpty()) outer.remove();
            }
        }, 20L, 20L);
    }

    // ============================================================
    //  Utilidades
    // ============================================================

    public String typeName(BoosterType.Kind kind) {
        return plugin.getGensConfig().plain("booster.types." + kind.name());
    }

    private String fill(String line, BoosterType type) {
        return Text.replace(line,
                "%multiplier%", fmt(type.multiplier()),
                "%time%", Text.time(type.duration()),
                "%chance%", fmt(type.chance()),
                "%type%", typeName(type.kind()));
    }

    /** 2.0 -> "2", 2.5 -> "2.5", 1.25 -> "1.25" */
    public static String fmt(double value) {
        String s = String.format(Locale.US, "%.2f", value);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    // ============================================================
    //  Persistencia (boosters-data.yml)
    // ============================================================

    public void loadData() {
        active.clear();
        if (!dataFile.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection root = yaml.getConfigurationSection("boosters");
        if (root == null) return;

        long now = System.currentTimeMillis();
        for (String uuidText : root.getKeys(false)) {
            ConfigurationSection playerSection = root.getConfigurationSection(uuidText);
            if (playerSection == null) continue;
            try {
                UUID uuid = UUID.fromString(uuidText);
                for (String kindText : playerSection.getKeys(false)) {
                    long end = playerSection.getLong(kindText + ".end");
                    if (end <= now) continue;
                    BoosterType.Kind kind = BoosterType.Kind.valueOf(kindText);
                    double multiplier = playerSection.getDouble(kindText + ".multiplier", 1.0);
                    active.computeIfAbsent(uuid, k -> new HashMap<>()).put(kind, new Active(multiplier, end));
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Booster inválido en boosters-data.yml (" + uuidText + ").");
            }
        }
    }

    public void saveData() {
        YamlConfiguration yaml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<BoosterType.Kind, Active>> entry : active.entrySet()) {
            for (Map.Entry<BoosterType.Kind, Active> booster : entry.getValue().entrySet()) {
                if (booster.getValue().end() <= now) continue;
                String path = "boosters." + entry.getKey() + "." + booster.getKey().name() + ".";
                yaml.set(path + "multiplier", booster.getValue().multiplier());
                yaml.set(path + "end", booster.getValue().end());
            }
        }
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar boosters-data.yml: " + e.getMessage());
        }
    }
            }
