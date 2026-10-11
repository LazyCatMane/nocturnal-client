package nocturnal.features.misc;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.features.combat.Criticals;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.module.Modules;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.StringSetting;
import nocturnal.util.Combat;
import nocturnal.util.Packets;

import java.util.UUID;

/** A client-side copy of you to test combat modules on. Only you can see it; the server never knows. */
public class FakePlayer extends Module {
    private static final int ENTITY_ID = -2_000_000;
    private static final float CRYSTAL_RADIUS = 6;
    /** Vanilla ignores new damage for this long after a hit unless it's bigger than the last one. */
    private static final int INVULNERABLE_TICKS = 10;
    /** Totem effects: Regeneration II heals 1 health every 25 ticks for 45 seconds, Absorption II gives 8. */
    private static final int REGEN_TICKS = 900, REGEN_INTERVAL = 25;
    private static final float TOTEM_ABSORPTION = 8;

    private final StringSetting name = add(new StringSetting("Name", "Name shown above the dummy", "FakePlayer"));
    private final BoolSetting damage = add(new BoolSetting("Damage", "Takes simulated crystal and melee damage", true));
    private final BoolSetting totems = add(new BoolSetting("Totems", "Pops a totem instead of dying (unlimited)", true));

    private RemotePlayer fake;
    private int pops, invulnerable, regen;
    private float lastHurt;

    public FakePlayer() {
        super("FakePlayer", "Spawns a client-side copy of you to test combat modules on", Category.MISC);
    }

    @Override
    public String info() {
        return pops > 0 ? pops + " pops" : null;
    }

    @Override
    protected void onDisable() {
        if (fake != null && mc.level != null) mc.level.removeEntity(ENTITY_ID, Entity.RemovalReason.DISCARDED);
        fake = null;
        pops = 0;
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (!inGame()) return;
        if (fake == null || fake.isRemoved() || fake.level() != mc.level) spawn();
        if (invulnerable > 0) invulnerable--;
        if (regen > 0 && regen-- % REGEN_INTERVAL == 0) fake.setHealth(Math.min(fake.getHealth() + 1, fake.getMaxHealth()));
    }

    @Subscribe
    private void onReceive(Events.PacketReceive e) {
        if (e.packet instanceof ClientboundExplodePacket p && p.radius() == CRYSTAL_RADIUS) {
            mc.execute(() -> {
                if (fake != null) hurt(Combat.crystalDamage(fake, p.center()));
            });
        }
    }

    @Subscribe
    private void onSend(Events.PacketSend e) {
        if (fake == null || !(e.packet instanceof ServerboundInteractPacket p) || Packets.attackTarget(p) != ENTITY_ID) return;
        e.cancel();
        boolean crit = (!mc.player.onGround() && mc.player.fallDistance > 0) || Modules.get(Criticals.class).isEnabled();
        hurt(Combat.meleeDamage(fake, crit));
    }

    private void spawn() {
        fake = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), name.get()));
        fake.setId(ENTITY_ID);
        fake.copyPosition(mc.player);
        fake.yHeadRot = fake.yBodyRot = mc.player.getYRot();
        for (EquipmentSlot slot : EquipmentSlot.values()) fake.setItemSlot(slot, mc.player.getItemBySlot(slot).copy());
        fake.getAttributes().assignAllValues(mc.player.getAttributes());
        fake.setHealth(fake.getMaxHealth());
        mc.level.addEntity(fake);
        invulnerable = regen = 0;
    }

    private void hurt(float amount) {
        if (!damage.get() || amount <= 0) return;
        if (invulnerable > 0) {
            if (amount <= lastHurt) return;
            float extra = amount - lastHurt;
            lastHurt = amount;
            amount = extra;
        } else {
            lastHurt = amount;
            invulnerable = INVULNERABLE_TICKS;
        }

        float absorption = fake.getAbsorptionAmount();
        float left = Math.max(amount - absorption, 0);
        fake.setAbsorptionAmount(absorption - (amount - left));
        fake.animateHurt(0);
        float health = fake.getHealth() - left;
        if (health > 0) {
            fake.setHealth(health);
        } else if (totems.get()) {
            pop();
        } else {
            fake.setHealth(fake.getMaxHealth());
        }
    }

    private void pop() {
        pops++;
        fake.setHealth(1);
        fake.setAbsorptionAmount(TOTEM_ABSORPTION);
        regen = REGEN_TICKS;
        mc.particleEngine.createTrackingEmitter(fake, ParticleTypes.TOTEM_OF_UNDYING, 30);
        mc.level.playLocalSound(fake.getX(), fake.getY(), fake.getZ(), SoundEvents.TOTEM_USE, fake.getSoundSource(), 1, 1, false);
    }
}
