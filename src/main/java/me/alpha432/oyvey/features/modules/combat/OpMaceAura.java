package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.features.settings.BoolSetting;
import me.alpha432.oyvey.features.settings.IntSetting;
import me.alpha432.oyvey.features.settings.EnumSetting;
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.event.impl.TickEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import java.lang.reflect.Field;
import java.util.ArrayList;

public class OpMaceAura extends Module {
    
    public enum AntiTotemMode {
        TpOffset,
        MaceSwap,
        MultiHit
    }
    
    public enum RangeMode {
        Short,
        Medium,
        Long
    }
    
    // Main settings
    public final Setting<Boolean> enable = new BoolSetting.Builder()
        .name("Enabled")
        .defaultValue(true)
        .build();
    
    public final Setting<RangeMode> rangeMode = new EnumSetting.Builder<RangeMode>()
        .name("Range Mode")
        .defaultValue(RangeMode.Medium)
        .build();
    
    public final Setting<Integer> fallHeight = new IntSetting.Builder()
        .name("Fall Height")
        .description("Simulated fall height for damage")
        .defaultValue(23)
        .min(4)
        .max(300)
        .sliderRange(4, 300)
        .build();
    
    // Anti-totem settings
    public final Setting<Boolean> antiTotem = new BoolSetting.Builder()
        .name("Anti Totem")
        .description("Bypass auto totem")
        .defaultValue(true)
        .build();
    
    public final Setting<AntiTotemMode> antiTotemMode = new EnumSetting.Builder<AntiTotemMode>()
        .name("AntiTotem Mode")
        .description("Method to bypass totems")
        .defaultValue(AntiTotemMode.TpOffset)
        .build();
    
    public final Setting<Integer> fallHeight2 = new IntSetting.Builder()
        .name("Second Hit Offset")
        .description("Fall offset for second hit")
        .defaultValue(10)
        .min(4)
        .max(300)
        .sliderRange(4, 300)
        .visible(() -> antiTotem.getValue())
        .build();
    
    public final Setting<Boolean> thirdHit = new BoolSetting.Builder()
        .name("Third Hit")
        .description("Extra hit for future totems")
        .defaultValue(false)
        .visible(() -> antiTotem.getValue() && antiTotemMode.getValue() == AntiTotemMode.TpOffset)
        .build();
    
    public final Setting<Integer> fallHeight3 = new IntSetting.Builder()
        .name("Third Hit Offset")
        .description("Fall offset for third hit")
        .defaultValue(10)
        .min(4)
        .max(300)
        .sliderRange(4, 300)
        .visible(() -> antiTotem.getValue() && thirdHit.getValue() && antiTotemMode.getValue() == AntiTotemMode.TpOffset)
        .build();
    
    // Safety settings
    public final Setting<Boolean> safetyChecks = new BoolSetting.Builder()
        .name("Safety Checks")
        .description("Prevent self damage")
        .defaultValue(true)
        .build();
    
    public final Setting<Boolean> paperClip = new BoolSetting.Builder()
        .name("PaperClip")
        .description("Alternative teleport method")
        .defaultValue(false)
        .build();
    
    // OP settings
    public final Setting<Boolean> throughWalls = new BoolSetting.Builder()
        .name("Through Walls")
        .description("Hit through blocks")
        .defaultValue(true)
        .build();
    
    public final Setting<Boolean> alwaysCrit = new BoolSetting.Builder()
        .name("Always Crit")
        .description("Force critical hits")
        .defaultValue(true)
        .build();
    
    public final Setting<Integer> attackCount = new IntSetting.Builder()
        .name("Attack Count")
        .description("Number of attacks per tick")
        .defaultValue(3)
        .min(1)
        .max(10)
        .sliderRange(1, 10)
        .build();
    
    public final Setting<Boolean> instantKill = new BoolSetting.Builder()
        .name("Instant Kill")
        .description("Kill low health enemies instantly")
        .defaultValue(true)
        .build();
    
    private LivingEntity target = null;
    private boolean antiTotemActive = false;
    private Vec3 originalPosition = null;
    private int attackTimer = 0;
    
    public OpMaceAura() {
        super("OpMaceAura", "OP mace insta-kill", Category.COMBAT);
    }
    
    private float getRange() {
        return switch (rangeMode.getValue()) {
            case Short -> 3.0f;
            case Medium -> 4.5f;
            case Long -> 6.0f;
        };
    }
    
    private boolean isSafeBlock(BlockPos pos) {
        return mc.level.getBlockState(pos).isAir() && 
               mc.level.getFluidState(pos).isEmpty() && 
               !mc.level.getBlockState(pos).is(Blocks.LAVA);
    }
    
    private int getMaxHeightAbovePlayer(int fallHeight) {
        BlockPos playerPos = mc.player.blockPosition();
        int maxHeight = playerPos.getY() + fallHeight;
        
        for (int i = maxHeight; i > playerPos.getY(); i--) {
            BlockPos up1 = new BlockPos(playerPos.getX(), i, playerPos.getZ());
            BlockPos up2 = up1.above();
            if (isSafeBlock(up1) && isSafeBlock(up2)) {
                return i - playerPos.getY();
            }
        }
        
        return 0;
    }
    
    private void simulateTeleport(int height, Vec3 startPos, boolean usePaperClip) {
        // Teleport simulation for damage
        if (alwaysCrit.getValue()) {
            // Force crit state
            mc.player.setOnGround(false);
            mc.player.setDeltaMovement(0, height * 0.1, 0);
            
            // Send position packets
            for (int i = 0; i < 3; i++) {
                sendPositionPacket(startPos.x, startPos.y + (height / 3.0) * i, startPos.z);
            }
            
            // Send landing packet
            sendPositionPacket(startPos.x, startPos.y, startPos.z);
        }
    }
    
    private void sendPositionPacket(double x, double y, double z) {
        if (mc.player.connection != null) {
            ServerboundMovePlayerPacket.Pos packet = new ServerboundMovePlayerPacket.Pos(
                x, y, z, mc.player.onGround()
            );
            mc.player.connection.send(packet);
        }
    }
    
    private LivingEntity findTarget() {
        float range = getRange();
        
        return mc.level.entitiesForRendering().stream()
            .filter(e -> e instanceof LivingEntity)
            .map(e -> (LivingEntity) e)
            .filter(e -> e != mc.player)
            .filter(e -> e.isAlive())
            .filter(e -> mc.player.distanceTo(e) <= range)
            .filter(e -> throughWalls.getValue() || mc.player.hasLineOfSight(e))
            .filter(e -> !(e instanceof Player) || safetyChecks.getValue() || 
                     (e.getHealth() > 0 && !e.isInvulnerable()))
            .min((a, b) -> Float.compare(
                (float) mc.player.distanceToSqr(a), 
                (float) mc.player.distanceToSqr(b)
            ))
            .orElse(null);
    }
    
    @Subscribe
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled() || !enable.getValue() || mc.player == null) return;
        
        if (event.getPacket() instanceof ServerboundInteractPacket packet) {
            try {
                // Get entity from packet using reflection
                Field entityIdField = ServerboundInteractPacket.class.getDeclaredField("entityId");
                entityIdField.setAccessible(true);
                int entityId = entityIdField.getInt(packet);
                
                LivingEntity target = (LivingEntity) mc.level.getEntity(entityId);
                if (target != null && isHoldingMace()) {
                    // Start anti-totem sequence
                    if (antiTotem.getValue() && !antiTotemActive) {
                        this.target = target;
                        antiTotemActive = true;
                        originalPosition = mc.player.position();
                        
                        // Simulate teleport for damage
                        int height = getMaxHeightAbovePlayer(fallHeight.getValue());
                        simulateTeleport(height, originalPosition, paperClip.getValue());
                    }
                }
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
    }
    
    @Subscribe
    public void onTick(TickEvent.Post event) {
        if (!isEnabled() || !enable.getValue() || mc.player == null) return;
        
        if (attackTimer > 0) attackTimer--;
        
        // Auto find target if not in anti-totem sequence
        if (!antiTotemActive) {
            target = findTarget();
            
            if (target != null && isHoldingMace() && attackTimer == 0) {
                performAttack();
                attackTimer = 2; // Small cooldown
            }
        }
        
        // Handle anti-totem sequence
        if (antiTotemActive && target != null) {
            handleAntiTotemSequence();
            antiTotemActive = false;
            target = null;
            originalPosition = null;
        }
    }
    
    private boolean isHoldingMace() {
        return mc.player.getMainHandItem().getItem() == Items.MACE || 
               mc.player.getOffhandItem().getItem() == Items.MACE;
    }
    
    private ArrayList<Integer> getMaceSlots() {
        ArrayList<Integer> slots = new ArrayList<>();
        
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.MACE) {
                slots.add(i);
            }
        }
        
        return slots;
    }
    
    private void performAttack() {
        if (target == null || !target.isAlive()) return;
        
        // Force crit
        if (alwaysCrit.getValue() && mc.player.onGround()) {
            mc.player.jumpFromGround();
            mc.player.fallDistance = fallHeight.getValue();
        }
        
        // Multiple attacks
        for (int i = 0; i < attackCount.getValue(); i++) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        
        // Instant kill for low health
        if (instantKill.getValue() && target.getHealth() <= 6.0f) {
            target.kill();
        }
    }
    
    private void handleAntiTotemSequence() {
        if (target == null || !target.isAlive()) return;
        
        int h1 = fallHeight.getValue();
        int h2 = h1 + fallHeight2.getValue();
        int h3 = h2 + fallHeight3.getValue();
        
        switch (antiTotemMode.getValue()) {
            case TpOffset:
                // First hit
                mc.gameMode.attack(mc.player, target);
                
                // Second hit with offset
                simulateTeleport(h2, originalPosition != null ? originalPosition : mc.player.position(), paperClip.getValue());
                mc.gameMode.attack(mc.player, target);
                
                // Third hit if enabled
                if (thirdHit.getValue()) {
                    simulateTeleport(h3, originalPosition != null ? originalPosition : mc.player.position(), paperClip.getValue());
                    mc.gameMode.attack(mc.player, target);
                }
                break;
                
            case MaceSwap:
                ArrayList<Integer> slots = getMaceSlots();
                int prevSlot = mc.player.getInventory().selected;
                
                if (!slots.isEmpty()) {
                    // Switch to mace
                    mc.player.getInventory().selected = slots.get(0);
                    mc.gameMode.attack(mc.player, target);
                    
                    // Switch back
                    mc.player.getInventory().selected = prevSlot;
                    mc.gameMode.attack(mc.player, target);
                }
                break;
                
            case MultiHit:
                for (int i = 0; i < 3; i++) {
                    mc.gameMode.attack(mc.player, target);
                }
                break;
        }
        
        // Return to original position
        if (originalPosition != null && paperClip.getValue()) {
            mc.player.setPos(originalPosition);
        }
    }
    
    @Override
    public void onEnable() {
        target = null;
        antiTotemActive = false;
        originalPosition = null;
        attackTimer = 0;
    }
    
    @Override
    public void onDisable() {
        target = null;
        antiTotemActive = false;
        originalPosition = null;
    }
    
    @Override
    public String getDisplayInfo() {
        if (!isEnabled()) return null;
        
        if (target != null) {
            return target.getName().getString();
        }
        
        return "READY";
    }
}
