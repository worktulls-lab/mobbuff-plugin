package com.example.mobbuff;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class MobBuffListener implements Listener {

    private final MobBuffPlugin plugin;

    public MobBuffListener(MobBuffPlugin plugin) {
        this.plugin = plugin;
    }

    /** Удваивает здоровье каждому новому мобу, если баф включён. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!plugin.isBuffEnabled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        MobBuffUtil.applyBuff(entity);
    }

    /** Удваивает урон, наносимый мобами (по игрокам или другим сущностям). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.isBuffEnabled()) {
            return;
        }
        // Урон удваивается, только если наносит его моб (не игрок).
        if (event.getDamager() instanceof LivingEntity damager && !(damager instanceof Player)) {
            event.setDamage(event.getDamage() * MobBuffUtil.DAMAGE_MULTIPLIER);
        }
    }
}
