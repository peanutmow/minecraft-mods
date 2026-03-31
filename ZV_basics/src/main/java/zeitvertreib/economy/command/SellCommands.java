package zeitvertreib.economy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import zeitvertreib.economy.currency.CurrencyManager;
import zeitvertreib.economy.sell.ItemSellPriceRegistry;
import zeitvertreib.economy.sell.SellMarketManager;

public final class SellCommands {

    private static final int LIST_PAGE_SIZE = 15;

    private final CurrencyManager currencyManager;
    private final SellMarketManager sellMarketManager;

    public SellCommands(CurrencyManager currencyManager, SellMarketManager sellMarketManager) {
        this.currencyManager = currencyManager;
        this.sellMarketManager = sellMarketManager;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("sell")
            .executes(this::sellOne)
            .then(Commands.literal("all")
                .executes(this::sellAll))
            .then(Commands.literal("list")
                .executes(ctx -> showList(ctx, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> showList(ctx, IntegerArgumentType.getInteger(ctx, "page")))))
            .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                .executes(this::sellAmount)));
    }

    // -------------------------------------------------------------------------
    // Command handlers
    // -------------------------------------------------------------------------

    private int sellOne(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return doSell(context, 1);
    }

    private int sellAmount(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return doSell(context, IntegerArgumentType.getInteger(context, "amount"));
    }

    private int doSell(CommandContext<CommandSourceStack> context, int requested) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();

        if (held.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Hold the item you want to sell in your main hand."));
            return 0;
        }

        Item item = held.getItem();
        if (!sellMarketManager.isSellable(item)) {
            context.getSource().sendFailure(Component.literal("That item cannot be sold."));
            return 0;
        }

        int price = sellMarketManager.getCurrentPrice(item);
        int toSell = Math.min(requested, held.getCount());
        int total = price * toSell;

        held.shrink(toSell);

        MinecraftServer server = context.getSource().getServer();
        currencyManager.addBalance(server, player.getUUID(), total);
        sellMarketManager.recordSale(server, item, toSell);

        String itemName = formatItemName(item);
        int finalPrice = price;
        int finalToSell = toSell;
        context.getSource().sendSuccess(() -> Component.literal("Sold ")
            .append(Component.literal(finalToSell + "x " + itemName).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" for "))
            .append(Component.literal(total + " coins").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" (" + finalPrice + " each).").withStyle(ChatFormatting.GRAY)), false);
        return Command.SINGLE_SUCCESS;
    }

    private int sellAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();

        // item -> [totalQty, totalEarned]
        Map<Item, int[]> sold = new LinkedHashMap<>();
        int grandTotal = 0;

        // Main inventory slots (hotbar + main)
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (!sellMarketManager.isSellable(item)) continue;

            int price = sellMarketManager.getCurrentPrice(item);
            int qty = stack.getCount();
            int earned = price * qty;
            grandTotal += earned;
            sold.computeIfAbsent(item, k -> new int[]{0, 0});
            sold.get(item)[0] += qty;
            sold.get(item)[1] += earned;
            sellMarketManager.recordSale(server, item, qty);
            player.getInventory().setItem(i, ItemStack.EMPTY);
        }

        // Offhand slot
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && sellMarketManager.isSellable(offhand.getItem())) {
            Item item = offhand.getItem();
            int price = sellMarketManager.getCurrentPrice(item);
            int qty = offhand.getCount();
            int earned = price * qty;
            grandTotal += earned;
            sold.computeIfAbsent(item, k -> new int[]{0, 0});
            sold.get(item)[0] += qty;
            sold.get(item)[1] += earned;
            sellMarketManager.recordSale(server, item, qty);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }

        if (grandTotal == 0) {
            context.getSource().sendFailure(Component.literal("You have no sellable items."));
            return 0;
        }

        currencyManager.addBalance(server, player.getUUID(), grandTotal);

        context.getSource().sendSuccess(() -> Component.literal("--- Sold all items ---").withStyle(ChatFormatting.GOLD), false);
        for (Map.Entry<Item, int[]> e : sold.entrySet()) {
            String name = formatItemName(e.getKey());
            int qty = e.getValue()[0];
            int itemTotal = e.getValue()[1];
            context.getSource().sendSuccess(() -> Component.literal("  ")
                .append(Component.literal(qty + "x " + name).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" \u2192 " + itemTotal + " coins").withStyle(ChatFormatting.GOLD)), false);
        }
        int gt = grandTotal;
        context.getSource().sendSuccess(() -> Component.literal("Total earned: ")
            .append(Component.literal(gt + " coins").withStyle(ChatFormatting.GOLD)), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showList(CommandContext<CommandSourceStack> context, int page) {
        List<Map.Entry<Item, Integer>> entries = new ArrayList<>(ItemSellPriceRegistry.BASE_PRICES.entrySet());
        entries.sort((a, b) -> {
            int cmp = Integer.compare(b.getValue(), a.getValue());
            return cmp != 0 ? cmp : formatItemName(a.getKey()).compareTo(formatItemName(b.getKey()));
        });

        final int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / LIST_PAGE_SIZE));
        final int displayPage = Math.min(page, totalPages);
        final int start = (displayPage - 1) * LIST_PAGE_SIZE;
        final int end = Math.min(start + LIST_PAGE_SIZE, entries.size());

        context.getSource().sendSuccess(() -> Component
            .literal("--- Sell Prices (page " + displayPage + "/" + totalPages + ") ---")
            .withStyle(ChatFormatting.GOLD), false);

        for (int i = start; i < end; i++) {
            Item item = entries.get(i).getKey();
            int base = entries.get(i).getValue();
            int current = sellMarketManager.getCurrentPrice(item);
            String name = formatItemName(item);
            MutableComponent line = Component.literal("  " + name + ": ")
                .append(Component.literal(current + " coins").withStyle(ChatFormatting.GOLD));
            if (current < base) {
                line.append(Component.literal(" (base: " + base + ")").withStyle(ChatFormatting.GRAY));
            }
            context.getSource().sendSuccess(() -> line, false);
        }

        if (totalPages > 1) {
            context.getSource().sendSuccess(
                () -> Component.literal("Use /sell list <page> for more.").withStyle(ChatFormatting.GRAY), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String formatItemName(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        String[] parts = id.getPath().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }
}
