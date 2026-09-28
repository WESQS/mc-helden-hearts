package de.wesqs.heldenhearts;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.nbt.NbtIo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class HeldenHearts implements ModInitializer {
    private static final int MAX_HEARTS = 2;
    private static final long BAN_TIME = 24L * 60 * 60 * 1000;
    private static final String FILE = "helden-hearts.dat";
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private MinecraftServer server;
    private long lastSave;

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> { server = s; load(); });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { save(); server = null; });

        // This event runs before the inventory is dropped, so the complete loot can be saved.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, damage) -> {
            if (entity instanceof ServerPlayerEntity player) onDeath(player);
            return true;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            PlayerData data = data(handler.player.getUuid());
            if (data.banUntil > System.currentTimeMillis()) {
                handler.disconnect(Text.literal("Du bist noch " + remaining(data.banUntil) + " gebannt."));
                return;
            }
            giveSavedLoot(handler.player, data);
            showHearts(handler.player, data);
        });

        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (System.currentTimeMillis() - lastSave > 5000) save();
            for (ServerPlayerEntity player : s.getPlayerManager().getPlayerList()) showHearts(player, data(player.getUuid()));
        });
    }

    private void onDeath(ServerPlayerEntity player) {
        PlayerData data = data(player.getUuid());
        data.hearts = Math.max(0, data.hearts - 1);
        data.loot.clear();
        RegistryWrapper.WrapperLookup lookup = player.getRegistryManager();
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack stack = inv.getStack(slot);
            if (!stack.isEmpty()) data.loot.add(stack.copy());
        }
        if (data.hearts == 0) {
            data.banUntil = System.currentTimeMillis() + BAN_TIME;
            player.networkHandler.disconnect(Text.literal("Alle Heldenherzen verloren. 24 Stunden gebannt."));
        }
        showHearts(player, data);
        save();
    }

    private void giveSavedLoot(ServerPlayerEntity player, PlayerData data) {
        if (data.loot.isEmpty()) return;
        Random random = Random.create();
        int amount = Math.max(1, (int) Math.ceil(data.loot.size() * 0.20));
        List<ItemStack> copy = new ArrayList<>(data.loot);
        Collections.shuffle(copy, new RandomAdapter(random));
        for (int i = 0; i < amount && i < copy.size(); i++) player.getInventory().offerOrDrop(copy.get(i).copy());
        data.loot.clear();
        player.sendMessage(Text.literal("Du erhältst 20% deines vorherigen Loots zurück."), false);
        save();
    }

    private void showHearts(ServerPlayerEntity player, PlayerData data) {
        player.sendMessage(Text.literal("§c❤ ".repeat(data.hearts) + "§7♡ ".repeat(MAX_HEARTS - data.hearts) + "§f  Heldenherzen"), true);
    }

    private PlayerData data(UUID uuid) { return players.computeIfAbsent(uuid, ignored -> new PlayerData()); }
    private String remaining(long until) { long h = Math.max(1, (until - System.currentTimeMillis()) / 3600000); return h + " Stunden"; }

    private Path file() { return server.getSavePath(WorldSavePath.ROOT).resolve(FILE); }
    private void load() {
        try {
            if (!Files.exists(file())) return;
            NbtCompound root = NbtIo.readCompressed(file());
            for (String key : root.getKeys()) players.put(UUID.fromString(key), PlayerData.read(root.getCompound(key), server.getRegistryManager()));
        } catch (Exception e) { e.printStackTrace(); }
    }
    private void save() {
        if (server == null) return;
        try {
            NbtCompound root = new NbtCompound();
            for (var entry : players.entrySet()) root.put(entry.getKey().toString(), entry.getValue().write(server.getRegistryManager()));
            NbtIo.writeCompressed(root, file()); lastSave = System.currentTimeMillis();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static class PlayerData {
        int hearts = MAX_HEARTS; long banUntil; List<ItemStack> loot = new ArrayList<>();
        NbtCompound write(RegistryWrapper.WrapperLookup lookup) {
            NbtCompound n = new NbtCompound(); n.putInt("Hearts", hearts); n.putLong("BanUntil", banUntil);
            NbtList list = new NbtList(); for (ItemStack stack : loot) list.add(stack.encode(lookup)); n.put("Loot", list); return n;
        }
        static PlayerData read(NbtCompound n, RegistryWrapper.WrapperLookup lookup) {
            PlayerData d = new PlayerData(); d.hearts = n.getInt("Hearts"); d.banUntil = n.getLong("BanUntil");
            NbtList list = n.getList("Loot", 10); for (int i=0;i<list.size();i++) ItemStack.fromNbt(lookup, list.getCompound(i)).ifPresent(d.loot::add); return d;
        }
    }

    private static class RandomAdapter extends java.util.Random {
        private final Random r; RandomAdapter(Random r) { this.r = r; }
        @Override public int nextInt(int bound) { return r.nextInt(bound); }
    }
}
