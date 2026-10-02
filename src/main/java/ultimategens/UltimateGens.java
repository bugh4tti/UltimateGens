package ultimategens;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import net.milkbowl.vault.economy.Economy;
import ultimategens.command.GensCommand;
import ultimategens.config.GensConfig;
import ultimategens.gui.ShopGUI;
import ultimategens.listener.BoosterListener;
import ultimategens.listener.GenListener;
import ultimategens.manager.BoosterManager;
import ultimategens.manager.GeneratorManager;
import ultimategens.task.DropTask;
import ultimategens.utils.Text;

public class UltimateGens extends JavaPlugin {

    private static UltimateGens instance;

    private Economy economy;
    private GensConfig gensConfig;
    private GeneratorManager generatorManager;
    private BoosterManager boosterManager;
    private ShopGUI shopGUI;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        gensConfig = new GensConfig(this);
        generatorManager = new GeneratorManager(this);
        boosterManager = new BoosterManager(this);
        shopGUI = new ShopGUI(this);

        generatorManager.loadData();
        boosterManager.loadData();

        getServer().getPluginManager().registerEvents(new GenListener(this), this);
        getServer().getPluginManager().registerEvents(new BoosterListener(this), this);
        getServer().getPluginManager().registerEvents(shopGUI, this);

        GensCommand command = new GensCommand(this);
        getCommand("gens").setExecutor(command);
        getCommand("gens").setTabCompleter(command);

        new DropTask(this).runTaskTimer(this, 20L, 20L);

        // Autoguardado cada 5 minutos
        getServer().getScheduler().runTaskTimer(this, () -> {
            generatorManager.saveData();
            boosterManager.saveData();
        }, 6000L, 6000L);

        // Verifica la economía cuando todos los plugins ya cargaron
        getServer().getScheduler().runTask(this, () -> {
            if (getEconomy() == null) {
                getLogger().warning("No se encontró un plugin de economía compatible con Vault.");
            }
        });

        getLogger().info("UltimateGens habilitado. Generadores cargados: " + generatorManager.all().size());
    }

    @Override
    public void onDisable() {
        if (generatorManager != null) generatorManager.saveData();
        if (boosterManager != null) boosterManager.saveData();
    }

    public static UltimateGens getInstance() {
        return instance;
    }

    /** Devuelve la economía de Vault (null si no hay ninguna). */
    public Economy getEconomy() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        }
        return economy;
    }

    /** Formatea dinero con abreviaturas (K, M, B, T...) si está activado en la config. */
    public String formatMoney(double value) {
        if (getConfig().getBoolean("settings.number-format.enabled", true)) {
            java.util.List<String> suffixes = getConfig().getStringList("settings.number-format.suffixes");
            if (!suffixes.isEmpty()) {
                return Text.abbreviate(value, suffixes);
            }
        }
        return Text.full(value);
    }

    /** Recarga config.yml, gens.yml y messages.yml. */
    public void reloadAll() {
        gensConfig.reload();
    }

    public GensConfig getGensConfig() {
        return gensConfig;
    }

    public GeneratorManager getGeneratorManager() {
        return generatorManager;
    }

    public BoosterManager getBoosterManager() {
        return boosterManager;
    }

    public ShopGUI getShopGUI() {
        return shopGUI;
    }
  }
