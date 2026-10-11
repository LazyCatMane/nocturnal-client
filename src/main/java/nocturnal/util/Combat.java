package nocturnal.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Targeting and damage math shared by the combat modules. Mirrors the vanilla 1.21.11 server formulas. */
public final class Combat {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final float CRYSTAL_POWER = 6;
    private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private Combat() {}

    public static boolean isHostile(Entity e) {
        return e instanceof Enemy;
    }

    public static boolean isPassive(Entity e) {
        return e instanceof LivingEntity && !(e instanceof Enemy) && !(e instanceof Player) && !(e instanceof ArmorStand);
    }

    /** Alive, attackable and not us. */
    public static boolean isValid(Entity e) {
        return e != mc.player && e.isAlive() && e instanceof LivingEntity && !e.isSpectator()
            && !(e instanceof Player p && p.isCreative());
    }

    /** Distance from our eyes to the nearest point of the box, which is what the server measures. */
    public static double reach(AABB box) {
        return Math.sqrt(box.distanceToSqr(mc.player.getEyePosition()));
    }

    /** The point of the box closest to our eyes, pulled slightly inside so the hit always lands. */
    public static Vec3 aimPoint(AABB box) {
        Vec3 eye = mc.player.getEyePosition();
        AABB in = box.deflate(0.05);
        return new Vec3(Mth.clamp(eye.x, in.minX, in.maxX), Mth.clamp(eye.y, in.minY, in.maxY), Mth.clamp(eye.z, in.minZ, in.maxZ));
    }

    public static float health(LivingEntity e) {
        return e.getHealth() + e.getAbsorptionAmount();
    }

    /** Damage an end crystal exploding at center would deal to the entity after armor, resistance and protection. */
    public static float crystalDamage(LivingEntity target, Vec3 center) {
        float diameter = CRYSTAL_POWER * 2;
        double dist = Math.sqrt(target.distanceToSqr(center)) / diameter;
        if (dist > 1) return 0;
        double impact = (1 - dist) * ServerExplosion.getSeenPercent(center, target);
        float damage = (float) ((impact * impact + impact) / 2 * 7 * diameter + 1);

        if (target instanceof Player) {
            damage = switch (mc.level.getDifficulty()) {
                case PEACEFUL -> 0;
                case EASY -> Math.min(damage / 2 + 1, damage);
                case NORMAL -> damage;
                case HARD -> damage * 1.5F;
            };
        }
        return reduce(target, damage, true);
    }

    /** Damage our current melee hit would deal to the entity, ignoring weapon enchantments. */
    public static float meleeDamage(LivingEntity target, boolean crit) {
        float strength = mc.player.getAttackStrengthScale(0.5F);
        float damage = (float) mc.player.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2F + strength * strength * 0.8F);
        if (crit && strength > 0.9F) damage *= 1.5F;
        return reduce(target, damage, false);
    }

    /** Applies armor, resistance and protection enchantments, the order the server uses. */
    private static float reduce(LivingEntity target, float damage, boolean explosion) {
        damage = CombatRules.getDamageAfterAbsorb(target, damage, mc.level.damageSources().generic(),
            target.getArmorValue(), (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));

        var resistance = target.getEffect(MobEffects.RESISTANCE);
        if (resistance != null) damage = Math.max(damage * (25 - (resistance.getAmplifier() + 1) * 5) / 25F, 0);

        int protection = 0;
        for (EquipmentSlot slot : ARMOR) {
            var stack = target.getItemBySlot(slot);
            protection += EnchantmentHelper.getItemEnchantmentLevel(enchantment(Enchantments.PROTECTION), stack);
            if (explosion) protection += 2 * EnchantmentHelper.getItemEnchantmentLevel(enchantment(Enchantments.BLAST_PROTECTION), stack);
        }
        return CombatRules.getDamageAfterMagicAbsorb(damage, protection);
    }

    private static Holder<Enchantment> enchantment(ResourceKey<Enchantment> key) {
        return mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }
}
