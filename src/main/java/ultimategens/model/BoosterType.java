package ultimategens.model;

import java.util.List;

import org.bukkit.Material;

public record BoosterType(
        String id,
        Kind kind,
        double multiplier,
        int duration,
        double chance,
        Material material,
        String name,
        List<String> lore) {

    public enum Kind {
        DROP, SPEED, SELL
    }
}
