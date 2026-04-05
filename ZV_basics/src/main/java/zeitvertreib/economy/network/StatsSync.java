package zeitvertreib.economy.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import zeitvertreib.economy.currency.CurrencyManager;
import zeitvertreib.economy.team.TeamData;
import zeitvertreib.economy.team.TeamManager;

public final class StatsSync {

    private static final java.util.Map<java.util.UUID, Boolean> HUD_ENABLED = new java.util.HashMap<>();

    private StatsSync() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(StatsPayload.TYPE, StatsPayload.CODEC);
    }

    public static void setHudEnabled(ServerPlayer player, boolean enabled) {
        HUD_ENABLED.put(player.getUUID(), enabled);
    }

    public static boolean isHudEnabled(ServerPlayer player) {
        return HUD_ENABLED.getOrDefault(player.getUUID(), true);
    }

    public static void sendToPlayer(MinecraftServer server, ServerPlayer player,
            CurrencyManager currency, TeamManager teams) {
        int balance = currency.getBalance(server, player.getUUID());
        TeamData team = teams.getTeamForPlayer(server, player.getUUID());
        String teamName = team != null ? team.name() : "";
        int teamLevel = team != null ? team.level() : 0;
        int bankBalance = team != null ? team.bankBalance() : 0;
        boolean hudEnabled = isHudEnabled(player);

        if (ServerPlayNetworking.canSend(player, StatsPayload.TYPE.id())) {
            // Client has the mod: send the custom packet for the HUD overlay
            ServerPlayNetworking.send(player, new StatsPayload(balance, teamName, teamLevel, bankBalance, hudEnabled));
        } else if (hudEnabled) {
            // Vanilla client fallback: show stats in the action bar
            net.minecraft.network.chat.MutableComponent msg = net.minecraft.network.chat.Component
                .literal("\u2732 ")
                .withStyle(net.minecraft.ChatFormatting.GOLD)
                .append(net.minecraft.network.chat.Component.literal(balance + " coins").withStyle(net.minecraft.ChatFormatting.YELLOW));
            if (!teamName.isEmpty()) {
                msg.append(net.minecraft.network.chat.Component.literal(" | ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
                   .append(net.minecraft.network.chat.Component.literal(teamName + " Lv." + teamLevel).withStyle(net.minecraft.ChatFormatting.AQUA))
                   .append(net.minecraft.network.chat.Component.literal(" | Bank: " + bankBalance).withStyle(net.minecraft.ChatFormatting.GREEN));
            }
            player.displayClientMessage(msg, true);
        }
    }

    public static void sendToAll(MinecraftServer server, CurrencyManager currency, TeamManager teams) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendToPlayer(server, player, currency, teams);
        }
    }
}
