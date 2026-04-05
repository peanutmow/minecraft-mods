package zeitvertreib.economy.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import zeitvertreib.economy.network.StatsPayload;

@Environment(EnvType.CLIENT)
public final class StatsHudRenderer {

    // Coin/gold: §6 = 0xFFAA00, Aqua: §b = 0x55FFFF, Green: §a = 0x55FF55, Gray: §7 = 0xAAAAAA
    private static final int COLOR_GOLD  = 0xFFFFAA00;
    private static final int COLOR_AQUA  = 0xFF55FFFF;
    private static final int COLOR_GREEN = 0xFF55FF55;
    private static final int COLOR_GRAY  = 0xFFAAAAAA;
    private static final int BG_COLOR    = 0x60000000; // 38% opaque black

    private static int balance = 0;
    private static String teamName = "";
    private static int teamLevel = 0;
    private static int bankBalance = 0;
    private static boolean hudEnabled = true;
    private static boolean hasData = false;

    private StatsHudRenderer() {}

    public static void onReceive(StatsPayload payload) {
        balance = payload.balance();
        teamName = payload.teamName();
        teamLevel = payload.teamLevel();
        bankBalance = payload.bankBalance();
        hudEnabled = payload.hudEnabled();
        hasData = true;
    }

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("zeitvertreib-economy", "stats_hud"),
                StatsHudRenderer::renderHud);
    }

    private static void renderHud(GuiGraphics graphics, DeltaTracker delta) {
        if (!hasData || !hudEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (mc.screen != null) return;

        Font font = mc.font;
        int lineHeight = font.lineHeight + 2;
        int padding = 4;

        String[] lines = buildLines();
        int[] colors = buildColors();

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, font.width(line));
        }

        int panelW = maxWidth + padding * 2;
        int panelH = lines.length * lineHeight + padding * 2 - 2;
        int margin = 4;
        int x = mc.getWindow().getGuiScaledWidth() - panelW - margin;
        int y = margin;

        graphics.fill(x - 1, y - 1, x + panelW + 1, y + panelH + 1, BG_COLOR);

        for (int i = 0; i < lines.length; i++) {
            graphics.drawString(font, lines[i], x + padding, y + padding + i * lineHeight, colors[i], false);
        }
    }

    private static String[] buildLines() {
        String balanceLine = "Coins: " + fmt(balance);
        if (teamName.isEmpty()) {
            return new String[] { balanceLine, "Team: None" };
        }
        return new String[] {
            balanceLine,
            "Team: " + teamName + " (Lv." + teamLevel + ")",
            "Bank: " + fmt(bankBalance)
        };
    }

    private static int[] buildColors() {
        if (teamName.isEmpty()) {
            return new int[] { COLOR_GOLD, COLOR_GRAY };
        }
        return new int[] { COLOR_GOLD, COLOR_AQUA, COLOR_GREEN };
    }

    private static String fmt(int n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000)     return String.format("%.1fk", n / 1_000.0);
        return String.valueOf(n);
    }
}
