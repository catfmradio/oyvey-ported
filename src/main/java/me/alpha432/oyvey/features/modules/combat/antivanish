package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.event.impl.UpdateEvent;
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import java.util.*;

public class AntiVanish extends Module {
    
    public Setting<Boolean> enable = new Setting<>("Enabled", true);
    public Setting<Boolean> alert = new Setting<>("Alert", true);
    public Setting<Boolean> trackMovement = new Setting<>("TrackMovement", false);
    public Setting<Boolean> showCount = new Setting<>("ShowCount", true);
    public Setting<Integer> renderDistance = new Setting<>("RenderDistance", 64, 16, 256);
    public Setting<Boolean> logToChat = new Setting<>("LogToChat", true);
    public Setting<Boolean> checkTab = new Setting<>("CheckTab", true);
    public Setting<Boolean> checkPackets = new Setting<>("CheckPackets", true);
    public Setting<Integer> timeout = new Setting<>("Timeout", 1200, 100, 3600);
    
    private final Map<UUID, VanishData> vanished = new HashMap<>();
    private final Set<UUID> recentlyDetected = new HashSet<>();
    private int tickCounter = 0;
    
    public AntiVanish() {
        super("AntiVanish", "Detects vanished players", Category.COMBAT);
    }
    
    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || !enable.getValue() || mc.player == null || mc.level == null) return;
        
        tickCounter++;
        
        if (tickCounter % 100 == 0) {
            cleanupOldData();
        }
        
        checkReappeared();
        
        if (checkTab.getValue()) {
            checkTabList();
        }
        
        if (tickCounter % 200 == 0) {
            recentlyDetected.clear();
        }
    }
    
    @Subscribe
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || !enable.getValue() || mc.level == null) return;
        
        if (checkPackets.getValue() && event.getPacket() instanceof ClientboundRemoveEntitiesPacket packet) {
            for (int id : packet.getEntityIds()) {
                Player player = getPlayerByID(id);
                if (player != null && player != mc.player) {
                    handleVanishDetection(player);
                }
            }
        }
        
        if (event.getPacket() instanceof ClientboundPlayerInfoUpdatePacket packet) {
            packet.entries().forEach(entry -> {
                Player player = mc.level.getPlayerByUUID(entry.profileId());
                if (player != null && vanished.containsKey(entry.profileId())) {
                    updateVanishedPlayer(entry.profileId(), player.position());
                }
            });
        }
    }
    
    private void handleVanishDetection(Player player) {
        UUID uuid = player.getUUID();
        
        if (recentlyDetected.contains(uuid)) return;
        
        VanishData data = vanished.get(uuid);
        if (data == null) {
            data = new VanishData(player, mc.level.getGameTime());
            vanished.put(uuid, data);
            
            if (alert.getValue()) {
                sendMessage("§c[AntiVanish] §f" + player.getName().getString() + " §7may have vanished!");
            }
        } else {
            data.lastSeen = mc.level.getGameTime();
            data.lastPosition = player.position();
        }
        
        recentlyDetected.add(uuid);
    }
    
    private void updateVanishedPlayer(UUID uuid, net.minecraft.world.phys.Vec3 position) {
        VanishData data = vanished.get(uuid);
        if (data != null) {
            data.lastPosition = position;
            data.lastSeen = mc.level.getGameTime();
        }
    }
    
    private void cleanupOldData() {
        long currentTime = mc.level != null ? mc.level.getGameTime() : 0;
        Iterator<Map.Entry<UUID, VanishData>> it = vanished.entrySet().iterator();
        
        while (it.hasNext()) {
            Map.Entry<UUID, VanishData> entry = it.next();
            VanishData data = entry.getValue();
            
            if (currentTime - data.lastSeen > timeout.getValue()) {
                it.remove();
            }
        }
    }
    
    private void checkReappeared() {
        Iterator<Map.Entry<UUID, VanishData>> it = vanished.entrySet().iterator();
        
        while (it.hasNext()) {
            Map.Entry<UUID, VanishData> entry = it.next();
            Player player = mc.level.getPlayerByUUID(entry.getKey());
            
            if (player != null && player.isAlive()) {
                if (alert.getValue()) {
                    sendMessage("§a[AntiVanish] §f" + player.getName().getString() + " §7reappeared");
                }
                it.remove();
            }
        }
    }
    
    private void checkTabList() {
        // Check for players in tab list but not in world
    }
    
    private Player getPlayerByID(int id) {
        if (mc.level == null) return null;
        
        net.minecraft.world.entity.Entity entity = mc.level.getEntity(id);
        return entity instanceof Player ? (Player) entity : null;
    }
    
    private void sendMessage(String message) {
        if (logToChat.getValue() && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }
    
    @Override
    public void onEnable() {
        vanished.clear();
        recentlyDetected.clear();
        tickCounter = 0;
        
        if (alert.getValue() && mc.player != null) {
            sendMessage("§a[AntiVanish] §fEnabled");
        }
    }
    
    @Override
    public void onDisable() {
        if (alert.getValue() && !vanished.isEmpty() && mc.player != null) {
            sendMessage("§c[AntiVanish] §fDisabled - " + vanished.size() + " vanished players cleared");
        }
        vanished.clear();
    }
    
    @Override
    public String getDisplayInfo() {
        if (!isEnabled() || !showCount.getValue()) return null;
        
        return vanished.isEmpty() ? null : vanished.size() + "";
    }
    
    private static class VanishData {
        public final String playerName;
        public final long vanishTime;
        public long lastSeen;
        public net.minecraft.world.phys.Vec3 lastPosition;
        
        public VanishData(Player player, long vanishTime) {
            this.playerName = player.getName().getString();
            this.vanishTime = vanishTime;
            this.lastSeen = vanishTime;
            this.lastPosition = player.position();
        }
    }
    
    public boolean isPlayerVanished(UUID uuid) {
        return vanished.containsKey(uuid);
    }
    
    public Set<UUID> getVanishedPlayers() {
        return new HashSet<>(vanished.keySet());
    }
    
    public int getVanishedCount() {
        return vanished.size();
    }
}
