package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.event.impl.UpdateEvent;
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.glfw.GLFW;
import java.util.Random;

public class DoubleHand extends Module {
    
    public Setting<Boolean> enable = new Setting<>("Enabled", true);
    public Setting<Mode> mode = new Setting<>("Mode", Mode.SMART);
    public Setting<Boolean> attackBoth = new Setting<>("AttackBoth", true);
    public Setting<Boolean> useBoth = new Setting<>("UseBoth", false);
    public Setting<Boolean> weaponsOnly = new Setting<>("WeaponsOnly", true);
    public Setting<Boolean> noShield = new Setting<>("NoShield", true);
    public Setting<Integer> delay = new Setting<>("Delay", 2, 0, 10);
    public Setting<Boolean> sound = new Setting<>("Sound", false);
    public Setting<Boolean> combatOnly = new Setting<>("CombatOnly", true);
    public Setting<Boolean> randomSkip = new Setting<>("RandomSkip", true);
    public Setting<Integer> skipChance = new Setting<>("SkipChance", 15, 0, 100);
    public Setting<Boolean> showActive = new Setting<>("ShowActive", true);
    
    private boolean active = false;
    private int cooldown = 0;
    private int lastHand = 0;
    private final Random rand = new Random();
    private boolean keyHeld = false;
    
    public DoubleHand() {
        super("DoubleHand", "Dual wielding helper", Category.COMBAT);
    }
    
    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || !enable.getValue() || mc.player == null) return;
        
        if (cooldown > 0) cooldown--;
        
        keyHeld = GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == 1;
        
        if (combatOnly.getValue()) {
            boolean inCombat = mc.player.hurtTime > 0 || mc.options.keyAttack.isDown();
            if (!inCombat) {
                active = false;
                return;
            }
        }
        
        active = checkActive();
        
        if (!active || cooldown > 0) return;
        
        if (randomSkip.getValue() && rand.nextInt(100) < skipChance.getValue()) {
            return;
        }
        
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        
        if (!canDualWield(main, off)) return;
        
        boolean attacking = mc.options.keyAttack.isDown();
        boolean using = keyHeld;
        
        if ((attacking && attackBoth.getValue()) || (using && useBoth.getValue())) {
            useOtherHand(attacking);
            
            lastHand = (lastHand + 1) % 2;
            cooldown = delay.getValue();
            
            if (sound.getValue()) {
                playSound(attacking);
            }
        }
    }
    
    private boolean checkActive() {
        if (mode.getValue() == Mode.SMART) {
            ItemStack main = mc.player.getMainHandItem();
            ItemStack off = mc.player.getOffhandItem();
            return canDualWield(main, off) && (mc.options.keyAttack.isDown() || keyHeld);
        } else if (mode.getValue() == Mode.ALWAYS) {
            return true;
        } else if (mode.getValue() == Mode.AGGRESSIVE) {
            return mc.options.keyAttack.isDown();
        }
        return false;
    }
    
    private boolean canDualWield(ItemStack main, ItemStack off) {
        if (main.isEmpty() || off.isEmpty()) return false;
        
        if (noShield.getValue() && (main.getItem() instanceof ShieldItem || off.getItem() instanceof ShieldItem)) {
            return false;
        }
        
        if (weaponsOnly.getValue()) {
            boolean mainWeapon = main.getItem() instanceof SwordItem || main.getItem() instanceof AxeItem;
            boolean offWeapon = off.getItem() instanceof SwordItem || off.getItem() instanceof AxeItem;
            return mainWeapon && offWeapon;
        }
        
        return true;
    }
    
    private void useOtherHand(boolean isAttack) {
        InteractionHand otherHand = lastHand == 0 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        
        if (isAttack) {
            doOtherAttack(otherHand);
        } else {
            doOtherUse(otherHand);
        }
    }
    
    private void doOtherAttack(InteractionHand hand) {
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) return;
        
        EntityHitResult entityHit = (EntityHitResult) hit;
        Entity target = entityHit.getEntity();
        
        if (target instanceof LivingEntity living && living.isAlive()) {
            mc.gameMode.attack(mc.player, living);
            mc.player.swing(hand);
        }
    }
    
    private void doOtherUse(InteractionHand hand) {
        ItemStack inHand = mc.player.getItemInHand(hand);
        if (inHand.isEmpty()) return;
        
        if (inHand.getItem() instanceof SwordItem || inHand.getItem() instanceof AxeItem) {
            return;
        }
        
        ServerboundUseItemPacket packet = new ServerboundUseItemPacket(hand, 0);
        mc.player.connection.send(packet);
        mc.player.swing(hand);
    }
    
    @Subscribe
    public void onPacketSend(PacketEvent.Send event) {
        if (!active || mc.player == null) return;
        
        if (event.getPacket() instanceof ServerboundUseItemPacket packet) {
            mirrorAction(packet.getHand(), false);
        } else if (event.getPacket() instanceof ServerboundSwingPacket packet) {
            mirrorAction(packet.getHand(), true);
        }
    }
    
    private void mirrorAction(InteractionHand usedHand, boolean isAttack) {
        if (cooldown > 0) return;
        
        InteractionHand otherHand = usedHand == InteractionHand.MAIN_HAND ? 
                                  InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        
        ItemStack otherItem = mc.player.getItemInHand(otherHand);
        if (otherItem.isEmpty()) return;
        
        int wait = rand.nextInt(1, 3);
        
        new Thread(() -> {
            try {
                Thread.sleep(wait * 50L);
                if (mc.player != null && active) {
                    mc.execute(() -> {
                        if (isAttack) {
                            HitResult hit = mc.hitResult;
                            if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
                                EntityHitResult entityHit = (EntityHitResult) hit;
                                if (entityHit.getEntity() instanceof LivingEntity living) {
                                    mc.gameMode.attack(mc.player, living);
                                    mc.player.swing(otherHand);
                                }
                            }
                        } else {
                            ServerboundUseItemPacket mirror = new ServerboundUseItemPacket(otherHand, 0);
                            mc.player.connection.send(mirror);
                            mc.player.swing(otherHand);
                        }
                        
                        cooldown = delay.getValue();
                        lastHand = otherHand == InteractionHand.MAIN_HAND ? 0 : 1;
                    });
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }).start();
    }
    
    private void playSound(boolean isAttack) {
        if (mc.level == null) return;
        
        if (isAttack) {
            mc.level.playSound(mc.player, mc.player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 1.0f);
        } else {
            mc.level.playSound(mc.player, mc.player.blockPosition(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.2f);
        }
    }
    
    @Override
    public void onEnable() {
        active = false;
        cooldown = 0;
        lastHand = 0;
        keyHeld = false;
    }
    
    @Override
    public void onDisable() {
        active = false;
        keyHeld = false;
    }
    
    @Override
    public String getDisplayInfo() {
        if (!isEnabled() || !showActive.getValue()) return null;
        
        if (active) {
            return "ON";
        }
        
        return null;
    }
    
    public enum Mode {
        SMART("Smart"),
        ALWAYS("Always"),
        AGGRESSIVE("Aggro");
        
        private final String name;
        
        Mode(String name) {
            this.name = name;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
}
