package ultimategens.model;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.block.Block;

public class PlacedGen {

    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final UUID owner;
    private String typeId;
    private long nextDrop;
    private int drops;
    private boolean broken;

    public PlacedGen(String world, int x, int y, int z, UUID owner, String typeId) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.owner = owner;
        this.typeId = typeId;
    }

    public static String key(String world, int x, int y, int z) {
        return world + ";" + x + ";" + y + ";" + z;
    }

    public static String key(Block block) {
        return key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static String key(Location loc) {
        return key(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public String key() {
        return key(world, x, y, z);
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getTypeId() {
        return typeId;
    }

    public void setTypeId(String typeId) {
        this.typeId = typeId;
    }

    public long getNextDrop() {
        return nextDrop;
    }

    public void setNextDrop(long nextDrop) {
        this.nextDrop = nextDrop;
    }

    public int getDrops() {
        return drops;
    }

    public void setDrops(int drops) {
        this.drops = drops;
    }

    public boolean isBroken() {
        return broken;
    }

    public void setBroken(boolean broken) {
        this.broken = broken;
    }
  }
