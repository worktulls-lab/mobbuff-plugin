package com.example.mobbuff;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;

public final class MobBuffListener implements Listener {

    private final MobBuffPlugin plugin;
    private static final Random RANDOM = new Random();
    private static final EntityType[] HORDE_TYPES = {
            EntityType.ZOMBIE,
            EntityType.SKELETON,
            EntityType.SPIDER
    };

    public MobBuffListener(MobBuffPlugin plugin) {
        this.plugin = plugin;
    }

    /** Применяет баф каждому новому мобу. Обычные пауки ночью иногда прячутся невидимками до атаки. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!plugin.isBuffEnabled()) return;
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;
        MobBuffUtil.applyBuff(entity, plugin);

        if (entity instanceof Spider && !(entity instanceof CaveSpider)
                && MobBuffUtil.isDark(entity.getLocation())
                && RANDOM.nextDouble() < MobBuffUtil.SPIDER_AMBUSH_CHANCE) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        }
    }

    /** Урон мобов: динамика для обычных, фиксированный x2 для боссов; яд от пауков; заражение от зомби; вода. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.isBuffEnabled()) return;
        if (!(event.getDamager() instanceof LivingEntity damager) || damager instanceof Player) return;

        double multiplier = MobBuffUtil.isDragon(damager)
                ? MobBuffUtil.DRAGON_MULTIPLIER
                : (MobBuffUtil.isBoss(damager)
                        ? MobBuffUtil.BOSS_MULTIPLIER
                        : MobBuffUtil.damageMultiplier(damager.getLocation(), plugin));
        if (damager.isInWater()) {
            multiplier *= MobBuffUtil.WATER_ATTACK_BONUS;
        }
        event.setDamage(event.getDamage() * multiplier);

        // Невидимый паук-засадник раскрывается после первой атаки
        if (damager instanceof Spider && damager.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            damager.removePotionEffect(PotionEffectType.INVISIBILITY);
        }

        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        // Обычные пауки — 50% шанс отравить, как пещерные
        if (damager instanceof Spider && !(damager instanceof CaveSpider)
                && RANDOM.nextDouble() < MobBuffUtil.SPIDER_POISON_CHANCE) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0));
        }

        // Укус зомби — лёгкое заражение (недолгая слабость)
        if (damager instanceof Zombie && RANDOM.nextDouble() < MobBuffUtil.ZOMBIE_INFECT_CHANCE) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
        }
    }

    /** Зомби и скелеты не горят, пока стоят в воде. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        if (!plugin.isBuffEnabled()) return;
        if (event.getEntity() instanceof LivingEntity le && (le instanceof Zombie || le instanceof Skeleton)) {
            if (le.isInWater()) {
                event.setCancelled(true);
            }
        }
    }

    /** Скелеты стреляют точнее и быстрее летящими стрелами. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!plugin.isBuffEnabled()) return;
        if (!(event.getEntity() instanceof Skeleton skeleton)) return;

        Projectile projectile = event.getProjectile();
        LivingEntity target = skeleton.getTarget();

        if (target != null) {
            Vector direction = target.getEyeLocation()
                    .subtract(skeleton.getEyeLocation())
                    .toVector()
                    .normalize();
            double speed = projectile.getVelocity().length() * 1.3;
            projectile.setVelocity(direction.multiply(speed));
        } else {
            projectile.setVelocity(projectile.getVelocity().multiply(1.3));
        }
    }

    /** Считает килы для динамической сложности и урезает опыт с мобов. */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!plugin.isBuffEnabled()) return;
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;

        if (entity.getKiller() != null) {
            plugin.registerHostileKill();
        }

        event.setDroppedExp((int) Math.round(event.getDroppedExp() * (1.0 - MobBuffUtil.XP_REDUCTION)));
    }

    /** Через 5-10 секунд после смерти игрока рядом (не на самом месте) появляется небольшая орда. Без сообщений в чат. */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.isBuffEnabled()) return;
        Location deathLoc = event.getEntity().getLocation().clone();
        World world = deathLoc.getWorld();
        if (world == null) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                int amount = 3 + RANDOM.nextInt(3); // 3-5 мобов
                for (int i = 0; i < amount; i++) {
                    int dx = (5 + RANDOM.nextInt(6)) * (RANDOM.nextBoolean() ? 1 : -1);
                    int dz = (5 + RANDOM.nextInt(6)) * (RANDOM.nextBoolean() ? 1 : -1);
                    Location spawnLoc = deathLoc.clone().add(dx, 0, dz);
                    EntityType type = HORDE_TYPES[RANDOM.nextInt(HORDE_TYPES.length)];
                    world.spawnEntity(spawnLoc, type);
                }
            }
        }.runTaskLater(plugin, 100L + RANDOM.nextInt(100)); // 5-10 секунд
    }

    /** Небольшой шанс ловушки (заражённый камень) в новых пещерных чанках. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.isBuffEnabled() || !event.isNewChunk()) return;
        MobBuffUtil.tryPlaceCaveTrap(event.getChunk());
    }
}
