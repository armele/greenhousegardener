package com.deathfrog.greenhousegardener.core.items;

import javax.annotation.Nonnull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.core.blocks.BlockClimateControlHub;
import com.deathfrog.greenhousegardener.core.blocks.BlockCucumber;
import com.deathfrog.greenhousegardener.core.blocks.BlockSpinach;
import com.deathfrog.greenhousegardener.core.blocks.ModBlocks;
import com.deathfrog.greenhousegardener.core.blocks.huts.BlockHutGreenhouse;
import com.minecolonies.api.items.ModTags;
import com.minecolonies.api.items.ItemBlockHut;
import com.minecolonies.core.items.ItemCrop;
import com.minecolonies.core.items.ItemFood;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems
{
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GreenhouseGardenerMod.MODID);

    @SuppressWarnings("null")
    public static final @Nonnull DeferredItem<ItemBlockHut> blockHutGreenhouseItem = ITEMS.register(
      BlockHutGreenhouse.HUT_NAME,
      () -> new ItemBlockHut(ModBlocks.blockHutGreenhouse.get(), new Item.Properties()));

    @SuppressWarnings("null")
    public static final @Nonnull DeferredItem<BlockItem> climateControlHubItem = ITEMS.register(
      BlockClimateControlHub.BLOCK_NAME,
      () -> new BlockItem(ModBlocks.climateControlHub.get(), new Item.Properties()));

    @SuppressWarnings("null")
    public static final @Nonnull DeferredItem<ItemCrop> cucumber = ITEMS.register(
      BlockCucumber.BLOCK_NAME,
      () -> new ItemCrop(ModBlocks.cucumber.get(), new Item.Properties(), ModTags.temperateBiomes));

    @SuppressWarnings("null")
    public static final @Nonnull DeferredItem<ItemCrop> spinach = ITEMS.register(
      BlockSpinach.BLOCK_NAME,
      () -> new ItemCrop(ModBlocks.spinach.get(), new Item.Properties(), null));

    @SuppressWarnings("null")
    public static final @Nonnull DeferredItem<Item> appleCiderVinegar = ITEMS.register(
      "apple_cider_vinegar",
      () -> new Item((new Item.Properties())));

    @SuppressWarnings("null")
    public static final @Nonnull DeferredItem<Item> aussieSpread = ITEMS.register(
      "aussie_spread",
      () -> new Item((new Item.Properties())));

    public static final @Nonnull DeferredItem<Item> aussieToast = registerFood("aussie_toast", 1);
    public static final @Nonnull DeferredItem<Item> baconButty = registerFood("bacon_butty", 2);
    public static final @Nonnull DeferredItem<Item> barbecue = registerFood("barbecue", 2);
    public static final @Nonnull DeferredItem<Item> barbecuePlate = registerFood("barbecue_plate", 3);
    public static final @Nonnull DeferredItem<Item> biscuits = registerFood("biscuits", 1);
    public static final @Nonnull DeferredItem<Item> biscuitsAndGravy = registerFood("biscuits_and_gravy", 3);
    public static final @Nonnull DeferredItem<Item> chickenAndWaffles = registerFood("chicken_and_waffles", 3);
    public static final @Nonnull DeferredItem<Item> clubSandwich = registerFood("club_sandwich", 2);
    public static final @Nonnull DeferredItem<Item> clubSandwichPlate = registerFood("club_sandwich_plate", 3);
    public static final @Nonnull DeferredItem<Item> coleslaw = registerFood("coleslaw", 1);
    public static final @Nonnull DeferredItem<Item> cornOil = registerIngredient("corn_oil");
    public static final @Nonnull DeferredItem<Item> doner = registerFood("doner", 3);
    public static final @Nonnull DeferredItem<Item> friedChicken = registerFood("fried_chicken", 2);
    public static final @Nonnull DeferredItem<Item> garlicCheeseGrits = registerFood("garlic_cheese_grits", 2);
    public static final @Nonnull DeferredItem<Item> mayo = registerIngredient("mayo");
    public static final @Nonnull DeferredItem<Item> pickles = registerFood("pickles", 1);
    public static final @Nonnull DeferredItem<Item> popcorn = registerFood("popcorn", 1);
    public static final @Nonnull DeferredItem<Item> potatoChips = registerFood("potato_chips", 1);
    public static final @Nonnull DeferredItem<Item> rouladen = registerFood("rouladen", 2);
    public static final @Nonnull DeferredItem<Item> sausage = registerFood("sausage", 1);
    public static final @Nonnull DeferredItem<Item> sausagePizza = registerFood("sausage_pizza", 3);
    public static final @Nonnull DeferredItem<Item> sourdoughBread = registerFood("sourdough_bread", 1);
    public static final @Nonnull DeferredItem<Item> sourdoughStarter = registerIngredient("sourdough_starter");
    public static final @Nonnull DeferredItem<Item> spanikopita = registerFood("spanikopita", 2);
    public static final @Nonnull DeferredItem<Item> spinachSalad = registerFood("spinach_salad", 1);
    public static final @Nonnull DeferredItem<Item> waffles = registerFood("waffles", 2);

    private ModItems()
    {
        throw new IllegalStateException("Tried to initialize: ModItems but this is a Utility class.");
    }

    public static void register(final IEventBus modEventBus)
    {
        if (modEventBus == null) return;
        
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::modifyComponents);
    }

    @SuppressWarnings("null")
    private static void modifyComponents(final ModifyDefaultComponentsEvent event)
    {
        event.modify(cucumber.get(), builder -> builder.remove(DataComponents.FOOD));
        event.modify(spinach.get(), builder -> builder.remove(DataComponents.FOOD));
    }

    @SuppressWarnings("null")
    private static @Nonnull DeferredItem<Item> registerIngredient(final @Nonnull String name)
    {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    @SuppressWarnings("null")
    private static @Nonnull DeferredItem<Item> registerFood(final String name, final int tier)
    {
        return ITEMS.register(name, () -> new ItemFood(new Item.Properties().food(foodProperties(tier)), tier));
    }

    private static FoodProperties foodProperties(final int tier)
    {
        return switch (tier)
        {
            case 3 -> new FoodProperties.Builder().nutrition(9).saturationModifier(1.2F).build();
            case 2 -> new FoodProperties.Builder().nutrition(7).saturationModifier(1.0F).build();
            default -> new FoodProperties.Builder().nutrition(5).saturationModifier(0.6F).build();
        };
    }
}
