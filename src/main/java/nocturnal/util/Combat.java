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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
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
        return crystalDamage(target, center, target.getBoundingBox());
    }

    /** Same as above, as if the entity's hitbox were at box. Used to judge positions before moving there. */
    public static float crystalDamage(LivingEntity target, Vec3 center, AABB box) {
        float diameter = CRYSTAL_POWER * 2;
        Vec3 feet = new Vec3((box.minX + box.maxX) / 2, box.minY, (box.minZ + box.maxZ) / 2);
        double dist = feet.distanceTo(center) / diameter;
        if (dist > 1) return 0;
        double impact = (1 - dist) * exposure(center, box, target);
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

    /** Fraction of the box the explosion can see. A copy of ServerExplosion.getSeenPercent that takes any box. */
    private static float exposure(Vec3 center, AABB box, Entity entity) {
        double dx = 1 / ((box.maxX - box.minX) * 2 + 1);
        double dy = 1 / ((box.maxY - box.minY) * 2 + 1);
        double dz = 1 / ((box.maxZ - box.minZ) * 2 + 1);
        double offX = (1 - Math.floor(1 / dx) * dx) / 2;
        double offZ = (1 - Math.floor(1 / dz) * dz) / 2;
        int seen = 0, total = 0;
        for (double x = 0; x <= 1; x += dx) {
            for (double y = 0; y <= 1; y += dy) {
                for (double z = 0; z <= 1; z += dz) {
                    Vec3 point = new Vec3(Mth.lerp(x, box.minX, box.maxX) + offX, Mth.lerp(y, box.minY, box.maxY), Mth.lerp(z, box.minZ, box.maxZ) + offZ);
                    if (mc.level.clip(new ClipContext(point, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS) seen++;
                    total++;
                }
            }
        }
        return total == 0 ? 0 : (float) seen / total;
    }

    public static int enchantLevel(ResourceKey<Enchantment> key, ItemStack stack) {
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment(key), stack);
    }

    private static Holder<Enchantment> enchantment(ResourceKey<Enchantment> key) {
        return mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }
}
