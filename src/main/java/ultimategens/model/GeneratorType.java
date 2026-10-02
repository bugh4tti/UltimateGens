package ultimategens.model;

import org.bukkit.Material;

public record GeneratorType(
        String id,
        String name,
        Material material,
        double price,
        int interval,
        double upgradeCost,
        double repairCost,
        String next,
        DropInfo normal,
        DropInfo superDrop) {

    public record DropInfo(String name, Material material, double value, double chance) {
    }

    public boolean isMaxLevel() {
        return next == null || next.isEmpty();
    }
}
