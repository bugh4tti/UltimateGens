package ultimategens.config;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import ultimategens.UltimateGens;
import ultimategens.model.BoosterType;
import ultimategens.model.GeneratorType;
import ultimategens.utils.Text;

public class GensConfig {

    private final UltimateGens plugin;
    private FileConfiguration gensFile;
    private FileConfiguration messagesFile;
    private final Map<String, GeneratorType> types = new LinkedHashMap<>();
    private final Map<String, BoosterType> boosters = new LinkedHashMap<>();

    public GensConfig(UltimateGens plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        gensFile = load("gens.yml");
        messagesFile = load("messages.yml");
        loadGenerators();
        loadBoosters();
    }

    private FileConfiguration load(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void loadGenerators() {
        types.clear();
        ConfigurationSection root = gensFile.getConfigurationSection("generators");
        if (root == null) {
            plugin.getLogger().warning("gens.yml no tiene la sección 'generators'.");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;

            Material material = Material.matchMaterial(s.getString("material", "STONE"));
            if (material == null) {
                plugin.getLogger().warning("Material inválido en el generador '" + id + "'.");
                continue;
            }

            GeneratorType.DropInfo normal = readDrop(s.getConfigurationSection("drops.normal"), 100.0);
            GeneratorType.DropInfo superDrop = readDrop(s.getConfigurationSection("drops.super"), 0.0);
            String next = s.getString("next", null);

            types.put(id.toLowerCase(), new GeneratorType(
                    id.toLowerCase(),
                    s.getString("name", id),
                    material,
                    s.getDouble("price", 0),
                    Math.max(1, s.getInt("interval", 20)),
                    s.getDouble("upgrade-cost", 0),
                    s.getDouble("repair-cost", 0),
                    next == null ? null : next.toLowerCase(),
                    normal,
                    superDrop));
        }
    }

    private GeneratorType.DropInfo readDrop(ConfigurationSection s, double defaultChance) {
        if (s == null) return null;
        Material material = Material.matchMaterial(s.getString("material", "STONE"));
        if (material == null) material = Material.STONE;
        return new GeneratorType.DropInfo(
                s.getString("name", "Drop"),
                material,
                s.getDouble("value", 0),
                s.getDouble("chance", defaultChance));
    }

    private void loadBoosters() {
        boosters.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("boosters");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;

            BoosterType.Kind kind;
            try {
                kind = BoosterType.Kind.valueOf(s.getString("type", "DROP").toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Tipo de booster inválido en '" + id + "'.");
                continue;
            }

            Material material = Material.matchMaterial(s.getString("material", "PAPER"));
            if (material == null) material = Material.PAPER;

            boosters.put(id.toLowerCase(), new BoosterType(
                    id.toLowerCase(),
                    kind,
                    s.getDouble("multiplier", 2.0),
                    s.getInt("duration", 600),
                    s.getDouble("chance", 100.0),
                    material,
                    s.getString("name", id),
                    s.getStringList("lore")));
        }
    }

    // ---------- Generadores ----------

    public GeneratorType getType(String id) {
        return id == null ? null : types.get(id.toLowerCase());
    }

    public Map<String, GeneratorType> getTypes() {
        return types;
    }

    public String getStarterId() {
        return plugin.getConfig().getString("settings.starter-generator", "trigo").toLowerCase();
    }

    public GeneratorType getStarter() {
        return getType(getStarterId());
    }

    // ---------- Boosters ----------

    public BoosterType getBooster(String id) {
        return id == null ? null : boosters.get(id.toLowerCase());
    }

    public Map<String, BoosterType> getBoosters() {
        return boosters;
    }

    // ---------- Mensajes ----------

    /** Mensaje con prefijo y colores. Placeholders en pares. */
    public String msg(String path, String... pairs) {
        String prefix = messagesFile.getString("prefix", "");
        String text = messagesFile.getString(path, "&cMensaje faltante: " + path);
        return Text.color(prefix + Text.replace(text, pairs));
    }

    /** Mensaje sin prefijo. */
    public String plain(String path, String... pairs) {
        String text = messagesFile.getString(path, "&cMensaje faltante: " + path);
        return Text.color(Text.replace(text, pairs));
    }

    public List<String> list(String path) {
        return Text.color(messagesFile.getStringList(path));
    }
  }
