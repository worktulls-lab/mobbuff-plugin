package com.example.mobbuff;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

public final class MobBuffUtil {

    public static final double HEALTH_MULTIPLIER = 2.0D;
    public static final double DAMAGE_MULTIPLIER = 2.0D;
    private static final String MARKER_KEY = "mobbuff_applied";

    private MobBuffUtil() {
    }

    /** Достаёт атрибут максимального здоровья независимо от версии сервера. */
    private static Attribute maxHealthAttribute() {
        try {
            // Актуальное имя в новых версиях (без префикса GENERIC_)
            return Attribute.valueOf("MAX_HEALTH");
        } catch (IllegalArgumentException ex) {
            // Старое имя в версиях до переименования
            return Attribute.valueOf("GENERIC_MAX_HEALTH");
        }
    }

    /** Удваивает макс. здоровье моба (один раз, помечая его через PersistentDataContainer). */
    public static void applyBuff(LivingEntity entity) {
        if (isAlreadyBuffed(entity)) {
            return;
        }
        AttributeInstance attr = entity.getAttribute(maxHealthAttribute());
        if (attr == null) {
            return;
        }
        double newMax = attr.getBaseValue() * HEALTH_MULTIPLIER;
        attr.setBaseValue(newMax);
        entity.setHealth(Math.min(newMax, attr.getValue()));
        mark(entity, true);
    }

    /** Возвращает моба к обычному максимальному здоровью. */
    public static void removeBuff(LivingEntity entity) {
        if (!isAlreadyBuffed(entity)) {
            return;
        }
        AttributeInstance attr = entity.getAttribute(maxHealthAttribute());
        if (attr != null) {
            double restoredMax = attr.getBaseValue() / HEALTH_MULTIPLIER;
            attr.setBaseValue(restoredMax);
            entity.setHealth(Math.min(restoredMax, entity.getHealth()));
        }
        mark(entity, false);
    }

    private static boolean isAlreadyBuffed(LivingEntity entity) {
        return entity.getPersistentDataContainer()
                .has(new org.bukkit.NamespacedKey("mobbuff", MARKER_KEY), org.bukkit.persistence.PersistentDataType.BYTE);
    }

    private static void mark(LivingEntity entity, boolean value) {
        var key = new org.bukkit.NamespacedKey("mobbuff", MARKER_KEY);
        if (value) {
            entity.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        } else {
            entity.getPersistentDataContainer().remove(key);
        }
    }
}
