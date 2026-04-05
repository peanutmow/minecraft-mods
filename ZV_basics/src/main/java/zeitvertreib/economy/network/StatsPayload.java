package zeitvertreib.economy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record StatsPayload(int balance, String teamName, int teamLevel, int bankBalance, boolean hudEnabled)
        implements CustomPacketPayload {

    public static final Type<StatsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("zeitvertreib-economy", "stats_hud"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StatsPayload> CODEC = new StreamCodec<>() {
        @Override
        public StatsPayload decode(RegistryFriendlyByteBuf buf) {
            return new StatsPayload(buf.readInt(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, StatsPayload payload) {
            buf.writeInt(payload.balance());
            buf.writeUtf(payload.teamName());
            buf.writeInt(payload.teamLevel());
            buf.writeInt(payload.bankBalance());
            buf.writeBoolean(payload.hudEnabled());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
