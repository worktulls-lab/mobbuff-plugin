package com.example.mobbuff;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class MobBuffPlugin extends JavaPlugin {

    private boolean buffEnabled = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        buffEnabled = getConfig().getBoolean("enabled", false);

        getCommand("mobbuff").setExecutor(new MobBuffCommand(this));
        getServer().getPluginManager().registerEvents(new MobBuffListener(this), this);

        // Если баф был включён на момент прошлого выключения сервера — применяем его
        // ко всем уже существующим мобам в загруженных мирах.
        if (buffEnabled) {
            applyToAllExistingMobs(true);
        }

        getLogger().info("MobBuff запущен. Баф мобов сейчас: " + (buffEnabled ? "ВКЛЮЧЁН" : "ВЫКЛЮЧЕН"));
    }

    @Override
    public void onDisable() {
        // Состояние (вкл/выкл) сохраняется в config.yml, поэтому переживает рестарт сервера.
        getConfig().set("enabled", buffEnabled);
        saveConfig();
    }

    public boolean isBuffEnabled() {
        return buffEnabled;
    }

    /**
     * Переключает баф, сохраняет состояние в конфиг и сразу применяет/снимает
     * его со всех уже существующих в мирах мобов.
     */
    public void setBuffEnabled(boolean enabled) {
        this.buffEnabled = enabled;
        getConfig().set("enabled", enabled);
        saveConfig();
        applyToAllExistingMobs(enabled);
    }

    /**
     * Проходит по всем загруженным мирам и применяет/снимает баф со всех
     * живых существ, кроме игроков.
     */
    public void applyToAllExistingMobs(boolean enable) {
        getServer().getWorlds().forEach(world ->
                world.getLivingEntities().forEach(entity -> {
                    if (!(entity instanceof Player)) {
                        if (enable) {
                            MobBuffUtil.applyBuff(entity);
                        } else {
                            MobBuffUtil.removeBuff(entity);
                        }
                    }
                })
        );
    }
}
