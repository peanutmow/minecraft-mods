package zeitvertreib.economy;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import zeitvertreib.economy.hud.StatsHudRenderer;
import zeitvertreib.economy.network.StatsPayload;

@Environment(EnvType.CLIENT)
public class ZeitvertreibEconomyClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        StatsHudRenderer.register();
        ClientPlayNetworking.registerGlobalReceiver(StatsPayload.TYPE,
                (payload, context) -> StatsHudRenderer.onReceive(payload));
    }
}
