package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.event.impl.UpdateEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class AutoTotem extends Module {
    
    public Setting<Boolean> enable = new Setting<>("Enabled", true);
    public Setting<Mode> mode = new Setting<>("Mode", Mode.SMART);
    public Setting<Integer> health = new Setting<>("Health", 10, 1, 36);
    public Setting<Boolean> fallProtect = new Setting<>("FallProtect", true);
    public Setting<Integer> fallDistance = new Setting<>("FallDistance", 8, 3, 20);
    public Setting<Boolean> fireProtect = new Setting<>("FireProtect", true);
    public Setting<Boolean> openInv = new Setting<>("OpenInventory", false);
    public Setting<Integer> delay = new Setting<>("Delay", 150, 50, 1000);
    public Setting<Boolean> sound = new Setting<>("Sound", false);
    public Setting<Boolean> shieldBackup = new Setting<>("ShieldBackup", true);
    public Setting<Boolean> hotbarOnly = new Setting<>("HotbarOnly", false);
    public Setting<Boolean> showStatus = new Setting<>("ShowStatus", true);
    
    private int totemCount = 0;
    private boolean hasTotem = false;
    private boolean hasShield = false;
    private int switchCooldown = 0;
    private long lastAction = 0;
    private int lastHealth = 0;
    
    public AutoTotem() {
        super("AutoTotem", "Automatically manages totems", Category.COMBAT);
    }
    
    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || !enable.getValue() || mc.player == null || mc.level == null) return;
        
        if (switchCooldown > 0) switchCooldown--;
        
        countItems();
        
        boolean needTotem = checkNeedTotem();
        boolean canTotem = totemCount > 0 && !hasTotem;
        
        if (needTotem && canTotem && switchCooldown == 0) {
            switchToTotem();
        } else if (hasTotem && !needTotem && shieldBackup.getValue() && hasShield) {
            if (System.currentTimeMillis() - lastAction > 2000) {
                switchToShield();
            }
        }
        
        lastHealth = (int) mc.player.getHealth();
    }
    
    private void countItems() {
        totemCount = 0;
        Inventory inv = mc.player.getInventory();
        
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                totemCount += stack.getCount();
            }
        }
        
        hasTotem = mc.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING;
        hasShield = mc.player.getOffhandItem().getItem() instanceof ShieldItem;
    }
    
    private boolean checkNeedTotem() {
        if (mode.getValue() == Mode.ALWAYS) return true;
        if (mode.getValue() == Mode.SMART) {
            if (mc.player.getHealth() <= health.getValue()) return true;
            if (fallProtect.getValue() && mc.player.fallDistance > fallDistance.getValue()) return true;
            if (fireProtect.getValue() && mc.player.isOnFire()) return true;
            if (mc.player.hurtTime > 0 && mc.player.getHealth() <= 15) return true;
            return false;
        }
        return false;
    }
    
    private void switchToTotem() {
        int totemSlot = -1;
        Inventory inv = mc.player.getInventory();
        
        if (hotbarOnly.getValue()) {
            for (int i = 0; i < 9; i++) {
                if (inv.getItem(i).getItem() == Items.TOTEM_OF_UNDYING) {
                    totemSlot = i;
                    break;
                }
            }
        } else {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).getItem() == Items.TOTEM_OF_UNDYING) {
                    totemSlot = i;
                    break;
                }
            }
        }
        
        if (totemSlot == -1) return;
        
        int offhandSlot = 40;
        ItemStack totem = inv.getItem(totemSlot).copy();
        ItemStack current = inv.getItem(offhandSlot).copy();
        
        inv.setItem(totemSlot, current);
        inv.setItem(offhandSlot, totem);
        
        if (sound.getValue()) {
            mc.level.playSound(mc.player, mc.player.blockPosition(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3f, 1.0f);
        }
        
        switchCooldown = 3;
        lastAction = System.currentTimeMillis();
    }
    
    private void switchToShield() {
        int shieldSlot = -1;
        Inventory inv = mc.player.getInventory();
        
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof ShieldItem) {
                shieldSlot = i;
                break;
            }
        }
        
        if (shieldSlot == -1) return;
        
        int offhandSlot = 40;
        ItemStack shield = inv.getItem(shieldSlot).copy();
        ItemStack current = inv.getItem(offhandSlot).copy();
        
        inv.setItem(shieldSlot, current);
        inv.setItem(offhandSlot, shield);
        
        if (sound.getValue()) {
            mc.level.playSound(mc.player, mc.player.blockPosition(),
                SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.5f, 1.0f);
        }
        
        lastAction = System.currentTimeMillis();
    }
    
    @Override
    public void onEnable() {
        totemCount = 0;
        hasTotem = false;
        hasShield = false;
        switchCooldown = 0;
        lastAction = 0;
        lastHealth = mc.player != null ? (int) mc.player.getHealth() : 0;
    }
    
    @Override
    public void onDisable() {
        // Nothing
    }
    
    @Override
    public String getDisplayInfo() {
        if (!isEnabled() || !showStatus.getValue()) return null;
        
        if (hasTotem) {
            return "✓" + (totemCount > 1 ? "(" + (totemCount - 1) + ")" : "");
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
