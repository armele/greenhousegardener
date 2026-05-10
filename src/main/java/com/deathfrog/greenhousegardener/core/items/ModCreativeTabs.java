package com.deathfrog.greenhousegardener.core.items;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs
{
    @SuppressWarnings("null")
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GreenhouseGardenerMod.MODID);

    @SuppressWarnings("null")
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GREENHOUSE_GARDENER = CREATIVE_TABS.register(
        GreenhouseGardenerMod.MODID,
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.greenhousegardener"))
            .icon(() -> ModItems.blockHutGreenhouseItem.get().getDefaultInstance())
            .displayItems((parameters, output) -> ModItems.ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    private ModCreativeTabs()
    {
        throw new IllegalStateException("Tried to initialize: ModCreativeTabs but this is a Utility class.");
    }

    public static void register(final IEventBus modEventBus)
    {
        if (modEventBus == null) return;

        CREATIVE_TABS.register(modEventBus);
    }
}
