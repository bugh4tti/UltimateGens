package ultimategens.task;

import org.bukkit.scheduler.BukkitRunnable;

import ultimategens.UltimateGens;

public class DropTask extends BukkitRunnable {

    private final UltimateGens plugin;

    public DropTask(UltimateGens plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getGeneratorManager().tick(System.currentTimeMillis());
    }
}
