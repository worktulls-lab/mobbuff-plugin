package com.example.mobbuff;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MobBuffPlugin extends JavaPlugin {

    private boolean buffEnabled = false;

    // Динамическая сложность: счётчик убийств враждебных мобов игроками
    private int killCount = 0;
    private long lastKillMillis = System.currentTimeMillis();

    // Кому уже прилетели фантомы этой ночью (сбрасывается с рассветом)
    private final Set<UUID> phantomHandledTonight = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        buffEnabled = getConfig().getBoolean("enabled", false);
        killCount = getConfig().getInt("killCount", 0);

        getCommand("mobbuff").setExecutor(new MobBuffCommand(this));
        getServer().getPluginManager().registerEvents(new MobBuffListener(this), this);

        if (buffEnabled) {
            applyToAllExistingMobs(true);
        }

        startDecayTask();
        startPhantomTask();
        startWaterSinkTask();
        startInfectedWaterTask();
        startDragonFireballTask();
        startCreeperNoFearTask();
        startSkeletonHorseTrapTask();
        startSkeletonHorseTrapCheckTask();

        getLogger().info("MobBuff запущен. Баф мобов сейчас: " + (buffEnabled ? "ВКЛЮЧЁН" : "ВЫКЛЮЧЕН"));
    }

    @Override
    public void onDisable() {
        getConfig().set("enabled", buffEnabled);
        getConfig().set("killCount", killCount);
        saveConfig();
    }

    public boolean isBuffEnabled() {
        return buffEnabled;
    }

    public void setBuffEnabled(boolean enabled) {
        this.buffEnabled = enabled;
        getConfig().set("enabled", enabled);
        saveConfig();
        applyToAllExistingMobs(enabled);
        if (!enabled) {
            killCount = 0;
            phantomHandledTonight.clear();
        }
    }

    public void applyToAllExistingMobs(boolean enable) {
        getServer().getWorlds().forEach(world ->
                world.getLivingEntities().forEach(entity -> {
                    if (!(entity instanceof Player)) {
                        if (enable) {
                            MobBuffUtil.applyBuff(entity, this);
                        } else {
                            MobBuffUtil.removeBuff(entity);
                        }
                    }
                })
        );
    }

    // --- Динамическая сложность (фарм мобов постепенно её поднимает и сама остывает) ---

    public void registerHostileKill() {
        killCount = Math.min(MobBuffUtil.MAX_KILL_COUNT, killCount + 1);
        lastKillMillis = System.currentTimeMillis();
    }

    public int getKillCount() {
        return killCount;
    }

    public double getCurrentDamageMultiplier() {
        return Math.min(MobBuffUtil.DAMAGE_MAX_MULTIPLIER,
                MobBuffUtil.BASE_MULTIPLIER + MobBuffUtil.rawBonus(killCount, MobBuffUtil.DAMAGE_MAX_MULTIPLIER));
    }

    public double getCurrentHealthMultiplier() {
        return Math.min(MobBuffUtil.HEALTH_MAX_MULTIPLIER,
                MobBuffUtil.BASE_MULTIPLIER + MobBuffUtil.rawBonus(killCount, MobBuffUtil.HEALTH_MAX_MULTIPLIER));
    }

    private void startDecayTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled || killCount <= 0) {
                    return;
                }
                // Спад начинается через минуту без убийств, дальше -1 каждые ~15 секунд простоя
                if (System.currentTimeMillis() - lastKillMillis > 60_000L) {
                    killCount = Math.max(0, killCount - 1);
                }
            }
        }.runTaskTimer(this, 300L, 300L);
    }

    // --- Фантомы каждую ночь, у каждого игрока по отдельности ---

    private void startPhantomTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) {
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    long time = player.getWorld().getTime();
                    boolean isNight = time >= 13000 && time < 23000;
                    if (isNight) {
                        if (!phantomHandledTonight.contains(player.getUniqueId())) {
                            MobBuffUtil.spawnNightPhantoms(player);
                            phantomHandledTonight.add(player.getUniqueId());
                        }
                    } else {
                        phantomHandledTonight.remove(player.getUniqueId());
                    }
                }
            }
        }.runTaskTimer(this, 100L, 100L);
    }

    // --- Мобы в воде тонут быстрее ---

    private void startWaterSinkTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) {
                    return;
                }
                getServer().getWorlds().forEach(world ->
                        world.getLivingEntities().forEach(entity -> {
                            if (!(entity instanceof Player) && MobBuffUtil.isBuffed(entity)) {
                                MobBuffUtil.applyWaterSink(entity);
                            }
                        })
                );
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    // --- Заражённая вода: если рядом с игроком в воде толпа утопленников (3+), вода "заражена" ---

    private void startInfectedWaterTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) {
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.isInWater()) {
                        continue;
                    }
                    long nearbyDrowned = player.getNearbyEntities(12, 6, 12).stream()
                            .filter(e -> e instanceof Drowned)
                            .count();
                    if (nearbyDrowned >= MobBuffUtil.INFECTED_WATER_DROWNED_THRESHOLD) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 0));
                    }
                }
            }
        }.runTaskTimer(this, 60L, 60L); // проверка каждые 3 секунды
    }

    // --- Дракон дополнительно чаще плюётся файрболами ---

    private void startDragonFireballTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) {
                    return;
                }
                getServer().getWorlds().forEach(world ->
                        world.getEntitiesByClass(EnderDragon.class).forEach(dragon -> {
                            Player nearest = null;
                            double bestDist = Double.MAX_VALUE;
                            for (Player player : world.getPlayers()) {
                                double dist = player.getLocation().distanceSquared(dragon.getLocation());
                                if (dist < bestDist && dist < 60 * 60) {
                                    bestDist = dist;
                                    nearest = player;
                                }
                            }
                            if (nearest != null) {
                                Location from = dragon.getLocation().add(0, 2, 0);
                                Vector direction = nearest.getLocation().toVector()
                                        .subtract(from.toVector()).normalize();
                                DragonFireball fireball = world.spawn(from, DragonFireball.class);
                                fireball.setVelocity(direction.multiply(1.2));
                            }
                        })
                );
            }
        }.runTaskTimer(this, 140L, 140L); // доп. плевок каждые ~7 секунд (усилено)
    }

    // --- Криперы не боятся кошек (постоянно гасим попытку сбежать, пока кот рядом) ---

    private void startCreeperNoFearTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) {
                    return;
                }
                getServer().getWorlds().forEach(world ->
                        world.getEntitiesByClass(Creeper.class).forEach(MobBuffUtil::suppressCreeperFear)
                );
            }
        }.runTaskTimer(this, 5L, 5L);
    }

    // --- Ловушки со скелетами-лошадьми: спавнятся чаще, и толпа при срабатывании больше ---

    private void startSkeletonHorseTrapTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) {
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    MobBuffUtil.trySpawnSkeletonHorseTrap(player);
                }
            }
        }.runTaskTimer(this, 6000L, 6000L); // раз в ~5 минут пробуем заспавнить рядом с каждым игроком
    }

    private void startSkeletonHorseTrapCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) {
                    return;
                }
                getServer().getWorlds().forEach(world ->
                        world.getEntitiesByClass(SkeletonHorse.class).forEach(horse -> {
                            if (!MobBuffUtil.isTrapHorse(horse)) {
                                return;
                            }
                            boolean playerClose = horse.getNearbyEntities(
                                            MobBuffUtil.SKELETON_HORSE_TRAP_RADIUS,
                                            MobBuffUtil.SKELETON_HORSE_TRAP_RADIUS,
                                            MobBuffUtil.SKELETON_HORSE_TRAP_RADIUS)
                                    .stream().anyMatch(e -> e instanceof Player);
                            if (playerClose) {
                                MobBuffUtil.triggerSkeletonHorseTrap(horse);
                            }
                        })
                );
            }
        }.runTaskTimer(this, 20L, 20L); // проверка раз в секунду
    }
}
