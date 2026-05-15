package dev.propulsionteam.computed.content;

import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.blocks.ComputerBlock;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.content.PeripheralConnectorItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import dev.propulsionteam.computed.menu.ComputerPeripheralMenu;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ComputedRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Computed.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Computed.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Computed.MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, Computed.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Computed.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<?>> COMPUTER_PERIPHERAL_MENU =
            MENU_TYPES.register(
                    "computer_peripheral",
                    () -> {
                        @SuppressWarnings("unchecked")
                        final MenuType<ComputerPeripheralMenu>[] holder = new MenuType[1];
                        holder[0] = IMenuTypeExtension.create((windowId, inv, buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            BlockEntity be = inv.player.level().getBlockEntity(pos);
                            if (!(be instanceof ComputerBlockEntity computer)) {
                                throw new IllegalStateException("No computer block entity at " + pos);
                            }
                            return new ComputerPeripheralMenu(holder[0], windowId, inv, computer);
                        });
                        return holder[0];
                    });

    public static final DeferredBlock<ComputerBlock> COMPUTER_BLOCK =
            BLOCKS.register("computer", props -> new ComputerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredItem<BlockItem> COMPUTER_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("computer", COMPUTER_BLOCK);
    public static final DeferredItem<Item> REDSTONE_LINK = ITEMS.registerItem(
            "redstone_link",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<Item> REDSTONE_EMITTER = ITEMS.registerItem(
            "redstone_emitter",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<PeripheralConnectorItem> PERIPHERAL_CONNECTOR =
            ITEMS.registerItem(
                    "peripheral_connector",
                    PeripheralConnectorItem::new,
                    new Item.Properties().durability(238));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ComputerBlockEntity>> COMPUTER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "computer",
                    () -> BlockEntityType.Builder.of(ComputerBlockEntity::new, COMPUTER_BLOCK.get()).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register(
                    "main",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.computed"))
                            .withTabsBefore(CreativeModeTabs.REDSTONE_BLOCKS)
                            .icon(() -> COMPUTER_BLOCK_ITEM.get().getDefaultInstance())
                            .displayItems((parameters, output) -> {
                                output.accept(COMPUTER_BLOCK_ITEM.get());
                                output.accept(REDSTONE_LINK.get());
                                output.accept(REDSTONE_EMITTER.get());
                                output.accept(PERIPHERAL_CONNECTOR.get());
                            })
                            .build());

    private ComputedRegistries() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
