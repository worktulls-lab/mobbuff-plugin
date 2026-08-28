package com.example.mobbuff;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Random;

public final class MobBuffUtil {

    // Базовая сложность
    public static final double BASE_MULTIPLIER = 2.3D;
    // Раздельные потолки: урон может расти сильнее, здоровье — мягче, чтобы бои не тянулись вечно
    public static final double DAMAGE_MAX_MULTIPLIER = 3.5D;
    public static final double HEALTH_MAX_MULTIPLIER = 3.0D;
    // Бонус за темноту (пещера без факела ИЛИ ночь — это одно и то же с точки зрения освещённости)
    public static final double DARKNESS_BONUS = 0.5D;
    // Сколько прибавляет одно убийство к множителю
    public static final double KILL_BONUS_PER_KILL = 0.02D;
    // После этого числа убийств бонус уже упирается в потолок
    public static final int MAX_KILL_COUNT = 100;

    public static final double SPEED_MULTIPLIER = 1.2D;
    public static final double EXPLOSION_MULTIPLIER = 2.0D;
    public static final double CREEPER_FUSE_MULTIPLIER = 0.7D; // взрывается быстрее после активации
    public static final double FOLLOW_RANGE_MULTIPLIER = 1.3D;
    public static final double WATER_ATTACK_BONUS = 1.3D;

    // Боссы масштабируются отдельно, фиксированно — не зависят от фарма/темноты
    public static final double BOSS_MULTIPLIER = 2.0D;      // Иссушитель
    public static final double BOSS_EXTRA_SPEED = 1.15D;
    public static final double DRAGON_MULTIPLIER = 3.0D;    // Дракон усилен сильнее Иссушителя
    public static final double DRAGON_EXTRA_SPEED = 1.25D;

    // Обычные мобы дополнительно крепче в ближнем бою
    public static final double KNOCKBACK_RESISTANCE_BONUS = 0.25D;

    // Криперы: шанс заспавниться сразу заряженными (без молнии)
    public static final double CHARGED_CREEPER_CHANCE = 0.08D;

    // Ловушки со скелетами-лошадьми
    public static final double SKELETON_HORSE_TRAP_SPAWN_CHANCE = 0.25D; // шанс на попытку раз в интервал
    public static final int SKELETON_HORSE_TRAP_RADIUS = 6;              // на каком расстоянии срабатывает
    public static final int SKELETON_HORSE_TRAP_HORDE_MIN = 5;
    public static final int SKELETON_HORSE_TRAP_HORDE_MAX = 7;

    // Новые механики
    public static final double SPIDER_POISON_CHANCE = 0.5D;      // обычные пауки шанс отравить как пещерные
    public static final double ZOMBIE_INFECT_CHANCE = 0.3D;      // шанс лёгкого дебаффа от укуса зомби
    public static final double SPIDER_AMBUSH_CHANCE = 0.1D;      // шанс пауку заспавниться невидимым ночью
    public static final double CAVE_TRAP_CHANCE = 0.05D;         // шанс ловушки на новый чанк пещеры
    public static final double XP_REDUCTION = 0.3D;              // опыт с мобов урезан на 30%
    public static final int INFECTED_WATER_DROWNED_THRESHOLD = 3; // сколько утопленников рядом, чтобы вода "заразилась"

    private static final String NS = "mobbuff";
    private static final Random RANDOM = new Random();

    private MobBuffUtil() {
    }

    // === Аттрибуты с учётом разных версий API ===

    private static Attribute maxHealthAttribute() {
        try {
            return Attribute.valueOf("MAX_HEALTH");
        } catch (IllegalArgumentException ex) {
            return Attribute.valueOf("GENERIC_MAX_HEALTH");
        }
    }

    private static Attribute movementSpeedAttribute() {
        try {
            return Attribute.valueOf("MOVEMENT_SPEED");
        } catch (IllegalArgumentException ex) {
            return Attribute.valueOf("GENERIC_MOVEMENT_SPEED");
        }
    }

    private static Attribute followRangeAttribute() {
        try {
            return Attribute.valueOf("FOLLOW_RANGE");
        } catch (IllegalArgumentException ex) {
            return Attribute.valueOf("GENERIC_FOLLOW_RANGE");
        }
    }

    private static Attribute knockbackResistanceAttribute() {
        try {
            return Attribute.valueOf("KNOCKBACK_RESISTANCE");
        } catch (IllegalArgumentException ex) {
            return Attribute.valueOf("GENERIC_KNOCKBACK_RESISTANCE");
        }
    }

    // === Динамическая сложность ===

    /** true, если в точке темно — покрывает и пещеру без факела, и ночь (оба снижают уровень света). */
    public static boolean isDark(Location loc) {
        return loc.getBlock().getLightLevel() < 7;
    }

    /** Иссушитель и Эндер-дракон — масштабируются отдельно от обычной динамической сложности. */
    public static boolean isBoss(LivingEntity entity) {
        return entity instanceof Wither || entity instanceof EnderDragon;
    }

    public static boolean isDragon(LivingEntity entity) {
        return entity instanceof EnderDragon;
    }

    public static double rawBonus(int killCount, double cap) {
        return Math.min(cap - BASE_MULTIPLIER, killCount * KILL_BONUS_PER_KILL);
    }

    /** Множитель ЗДОРОВЬЯ: база x2, растёт от фарма и темноты, потолок x3.0 — чтобы бои не тянулись вечно. */
    public static double healthMultiplier(Location loc, MobBuffPlugin plugin) {
        double value = BASE_MULTIPLIER + rawBonus(plugin.getKillCount(), HEALTH_MAX_MULTIPLIER);
        if (isDark(loc)) {
            value += DARKNESS_BONUS;
        }
        return Math.min(HEALTH_MAX_MULTIPLIER, value);
    }

    /** Множитель УРОНА: база x2, растёт от фарма и темноты, потолок x3.5 — самый опасный параметр. */
    public static double damageMultiplier(Location loc, MobBuffPlugin plugin) {
        double value = BASE_MULTIPLIER + rawBonus(plugin.getKillCount(), DAMAGE_MAX_MULTIPLIER);
        if (isDark(loc)) {
            value += DARKNESS_BONUS;
        }
        return Math.min(DAMAGE_MAX_MULTIPLIER, value);
    }

    // === Применение бафа к мобу при спавне ===

    public static void applyBuff(LivingEntity entity, MobBuffPlugin plugin) {
        if (isBuffed(entity)) {
            return;
        }
        AttributeInstance healthAttr = entity.getAttribute(maxHealthAttribute());
        if (healthAttr == null) {
            return;
        }

        boolean boss = isBoss(entity);
        boolean dragon = isDragon(entity);
        double multiplier = dragon ? DRAGON_MULTIPLIER : (boss ? BOSS_MULTIPLIER : healthMultiplier(entity.getLocation(), plugin));
        double newMax = healthAttr.getBaseValue() * multiplier;
        healthAttr.setBaseValue(newMax);
        entity.setHealth(Math.min(newMax, healthAttr.getValue()));

        AttributeInstance speedAttr = entity.getAttribute(movementSpeedAttribute());
        double bossSpeedFactor = dragon ? DRAGON_EXTRA_SPEED : (boss ? BOSS_EXTRA_SPEED : 1.0);
        double appliedSpeedMult = SPEED_MULTIPLIER * bossSpeedFactor;
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * appliedSpeedMult);
        }

        AttributeInstance knockbackAttr = entity.getAttribute(knockbackResistanceAttribute());
        if (knockbackAttr != null) {
            knockbackAttr.setBaseValue(Math.min(1.0, knockbackAttr.getBaseValue() + KNOCKBACK_RESISTANCE_BONUS));
        }

        AttributeInstance followAttr = entity.getAttribute(followRangeAttribute());
        if (followAttr != null) {
            followAttr.setBaseValue(followAttr.getBaseValue() * FOLLOW_RANGE_MULTIPLIER);
        }

        if (entity instanceof Creeper creeper) {
            creeper.setExplosionRadius((int) Math.round(creeper.getExplosionRadius() * EXPLOSION_MULTIPLIER));
            creeper.setMaxFuseTicks((int) Math.round(creeper.getMaxFuseTicks() * CREEPER_FUSE_MULTIPLIER));
            if (!creeper.isPowered() && RANDOM.nextDouble() < CHARGED_CREEPER_CHANCE) {
                creeper.setPowered(true);
            }
        }

        mark(entity, multiplier, appliedSpeedMult);
    }

    public static void removeBuff(LivingEntity entity) {
        Double appliedMultiplier = getAppliedMultiplier(entity);
        if (appliedMultiplier == null) {
            return;
        }
        Double appliedSpeedMult = getAppliedSpeedMultiplier(entity);
        if (appliedSpeedMult == null) {
            appliedSpeedMult = SPEED_MULTIPLIER;
        }

        AttributeInstance healthAttr = entity.getAttribute(maxHealthAttribute());
        if (healthAttr != null) {
            double restoredMax = healthAttr.getBaseValue() / appliedMultiplier;
            healthAttr.setBaseValue(restoredMax);
            entity.setHealth(Math.min(restoredMax, entity.getHealth()));
        }

        AttributeInstance speedAttr = entity.getAttribute(movementSpeedAttribute());
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() / appliedSpeedMult);
        }

        AttributeInstance followAttr = entity.getAttribute(followRangeAttribute());
        if (followAttr != null) {
            followAttr.setBaseValue(followAttr.getBaseValue() / FOLLOW_RANGE_MULTIPLIER);
        }

        AttributeInstance knockbackAttr = entity.getAttribute(knockbackResistanceAttribute());
        if (knockbackAttr != null) {
            knockbackAttr.setBaseValue(Math.max(0.0, knockbackAttr.getBaseValue() - KNOCKBACK_RESISTANCE_BONUS));
        }

        if (entity instanceof Creeper creeper) {
            creeper.setExplosionRadius((int) Math.round(creeper.getExplosionRadius() / EXPLOSION_MULTIPLIER));
            creeper.setMaxFuseTicks((int) Math.round(creeper.getMaxFuseTicks() / CREEPER_FUSE_MULTIPLIER));
        }

        unmark(entity);
    }

    public static boolean isBuffed(LivingEntity entity) {
        return entity.getPersistentDataContainer()
                .has(new org.bukkit.NamespacedKey(NS, "applied_mult"), PersistentDataType.DOUBLE);
    }

    private static Double getAppliedMultiplier(LivingEntity entity) {
        var key = new org.bukkit.NamespacedKey(NS, "applied_mult");
        var pdc = entity.getPersistentDataContainer();
        if (!pdc.has(key, PersistentDataType.DOUBLE)) {
            return null;
        }
        return pdc.get(key, PersistentDataType.DOUBLE);
    }

    private static Double getAppliedSpeedMultiplier(LivingEntity entity) {
        var key = new org.bukkit.NamespacedKey(NS, "applied_speed_mult");
        var pdc = entity.getPersistentDataContainer();
        if (!pdc.has(key, PersistentDataType.DOUBLE)) {
            return null;
        }
        return pdc.get(key, PersistentDataType.DOUBLE);
    }

    private static void mark(LivingEntity entity, double multiplier, double speedMultiplier) {
        var key = new org.bukkit.NamespacedKey(NS, "applied_mult");
        var speedKey = new org.bukkit.NamespacedKey(NS, "applied_speed_mult");
        entity.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, multiplier);
        entity.getPersistentDataContainer().set(speedKey, PersistentDataType.DOUBLE, speedMultiplier);
    }

    private static void unmark(LivingEntity entity) {
        var key = new org.bukkit.NamespacedKey(NS, "applied_mult");
        var speedKey = new org.bukkit.NamespacedKey(NS, "applied_speed_mult");
        entity.getPersistentDataContainer().remove(key);
        entity.getPersistentDataContainer().remove(speedKey);
    }

    // === Фантомы каждую ночь ===

    public static void spawnNightPhantoms(Player player) {
        int amount = 2 + RANDOM.nextInt(3); // 2-4
        for (int i = 0; i < amount; i++) {
            Location loc = player.getLocation().clone().add(
                    RANDOM.nextInt(11) - 5,
                    15 + RANDOM.nextInt(5),
                    RANDOM.nextInt(11) - 5
            );
            player.getWorld().spawn(loc, Phantom.class);
        }
    }

    // === Вода: тонут быстрее ===

    public static void applyWaterSink(LivingEntity entity) {
        if (entity.isInWater() && !entity.isOnGround()) {
            Vector vel = entity.getVelocity();
            entity.setVelocity(vel.add(new Vector(0, -0.05, 0)));
        }
    }

    // === Ловушки в пещерах (заражённый камень) ===

    public static void tryPlaceCaveTrap(org.bukkit.Chunk chunk) {
        if (RANDOM.nextDouble() > CAVE_TRAP_CHANCE) {
            return;
        }
        org.bukkit.World world = chunk.getWorld();
        for (int attempt = 0; attempt < 6; attempt++) {
            int x = chunk.getX() * 16 + RANDOM.nextInt(16);
            int z = chunk.getZ() * 16 + RANDOM.nextInt(16);
            int y = 5 + RANDOM.nextInt(45); // случайная пещерная высота
            org.bukkit.block.Block block = world.getBlockAt(x, y, z);
            if (block.getType() == org.bukkit.Material.STONE) {
                org.bukkit.block.Block below = block.getRelative(org.bukkit.block.BlockFace.DOWN);
                org.bukkit.block.Block above = block.getRelative(org.bukkit.block.BlockFace.UP);
                if (above.getType() == org.bukkit.Material.CAVE_AIR || below.getType() == org.bukkit.Material.CAVE_AIR) {
                    block.setType(org.bukkit.Material.INFESTED_STONE);
                    return;
                }
            }
        }
    }

    // === Криперы не боятся кошек: гасим попытку сбежать при обнаружении кота рядом ===

    public static void suppressCreeperFear(Creeper creeper) {
        boolean catNearby = creeper.getNearbyEntities(10, 6, 10).stream()
                .anyMatch(e -> e instanceof org.bukkit.entity.Cat || e instanceof org.bukkit.entity.Ocelot);
        if (catNearby) {
            Vector vel = creeper.getVelocity();
            if (Math.abs(vel.getX()) > 0.02 || Math.abs(vel.getZ()) > 0.02) {
                creeper.setVelocity(new Vector(0, vel.getY(), 0));
            }
        }
    }

    // === Ловушки со скелетами-лошадьми ===

    private static final String TRAP_HORSE_KEY = "trap_horse";

    public static void trySpawnSkeletonHorseTrap(Player player) {
        if (RANDOM.nextDouble() > SKELETON_HORSE_TRAP_SPAWN_CHANCE) {
            return;
        }
        Location base = player.getLocation().clone().add(
                RANDOM.nextInt(31) - 15,
                0,
                RANDOM.nextInt(31) - 15
        );
        org.bukkit.World world = base.getWorld();
        if (world == null) return;
        int highestY = world.getHighestBlockYAt(base.getBlockX(), base.getBlockZ());
        Location spawnLoc = new Location(world, base.getX(), highestY + 1, base.getZ());

        var horse = world.spawn(spawnLoc, org.bukkit.entity.SkeletonHorse.class);
        horse.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(NS, TRAP_HORSE_KEY), PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isTrapHorse(org.bukkit.entity.SkeletonHorse horse) {
        return horse.getPersistentDataContainer()
                .has(new org.bukkit.NamespacedKey(NS, TRAP_HORSE_KEY), PersistentDataType.BYTE);
    }

    /** Срабатывает, когда игрок подходит слишком близко: большая толпа скелетов-всадников вокруг. */
    public static void triggerSkeletonHorseTrap(org.bukkit.entity.SkeletonHorse horse) {
        horse.getPersistentDataContainer().remove(new org.bukkit.NamespacedKey(NS, TRAP_HORSE_KEY));
        org.bukkit.World world = horse.getWorld();
        Location loc = horse.getLocation();
        world.strikeLightningEffect(loc);

        int amount = SKELETON_HORSE_TRAP_HORDE_MIN
                + RANDOM.nextInt(SKELETON_HORSE_TRAP_HORDE_MAX - SKELETON_HORSE_TRAP_HORDE_MIN + 1);
        for (int i = 0; i < amount; i++) {
            Location skelLoc = loc.clone().add(RANDOM.nextInt(5) - 2, 0, RANDOM.nextInt(5) - 2);
            world.spawn(skelLoc, org.bukkit.entity.Skeleton.class);
        }
    }
}
