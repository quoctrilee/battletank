package com.mygame.tank.world;

import com.mygame.tank.entity.Tank;

import java.util.*;

/**
 * Tracks enemy counts per area and manages sequential area progression:
 *   Area A → clear enemies → open Door A → Area B → … → open Boss Door → Boss Room
 *
 * Areas progress in a fixed order. An area is "cleared" when all its registered
 * enemies are dead. Clearing an area opens the door to the next area.
 */
public class AreaManager {

    /** Ordered list of areas before the boss room. */
    private static final List<String> AREA_ORDER = Arrays.asList("A", "B", "C");

    // Enemies registered per area (set once at world creation)
    private final Map<String, List<Tank>> areaEnemies  = new LinkedHashMap<>();
    private final Set<String>             clearedAreas  = new HashSet<>();
    private final Set<String>             openDoors     = new HashSet<>();

    private boolean bossActive   = false;
    private boolean bossDefeated = false;

    /** Register the tank enemies belonging to a specific area. Call once at startup. */
    public void registerEnemies(String areaId, List<Tank> enemies) {
        areaEnemies.put(areaId, new ArrayList<>(enemies));
    }

    /**
     * Must be called every frame after enemy updates.
     * Checks whether each area is cleared and opens the appropriate door.
     */
    public void update() {
        for (String areaId : AREA_ORDER) {
            if (clearedAreas.contains(areaId)) continue;

            List<Tank> enemies = areaEnemies.getOrDefault(areaId, Collections.emptyList());
            if (enemies.isEmpty()) continue;   // don't auto-clear empty areas

            boolean allDead = enemies.stream().noneMatch(Tank::isAlive);
            if (allDead) {
                clearedAreas.add(areaId);

                int idx = AREA_ORDER.indexOf(areaId);
                if (idx < AREA_ORDER.size() - 1) {
                    openDoors.add(areaId);     // open door to next area
                } else {
                    openDoors.add("BOSS");     // final area → open boss door
                }
            }
        }
    }

    /**
     * Whether a door should be passable.
     * Door keys match the Tiled "Doors" layer object names: "A", "B", "BOSS".
     */
    public boolean isDoorOpen(String doorKey) {
        return openDoors.contains(doorKey);
    }

    /** True on the frame the BOSS door opens and boss hasn't been spawned yet. */
    public boolean shouldSpawnBoss() {
        return isDoorOpen("BOSS") && !bossActive && !bossDefeated;
    }

    public boolean isAreaCleared(String areaId) { return clearedAreas.contains(areaId); }

    public void setBossActive(boolean active)     { bossActive = active; }
    public void setBossDefeated(boolean defeated) { bossDefeated = defeated; bossActive = false; }
    public boolean isBossDefeated()              { return bossDefeated; }
    public boolean isBossActive()                { return bossActive; }

    /**
     * Returns whether a given area's door is open so enemies can become active.
     * Area A is always active (player starts there).
     */
    public boolean isAreaActive(String areaId) {
        switch (areaId) {
            case "A":    return true;
            case "B":    return isDoorOpen("A");
            case "C":    return isDoorOpen("B");
            case "BOSS": return isDoorOpen("BOSS");
            default:     return true;
        }
    }
}
