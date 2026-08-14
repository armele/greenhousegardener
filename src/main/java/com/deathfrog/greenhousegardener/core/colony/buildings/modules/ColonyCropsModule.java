package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.deathfrog.greenhousegardener.core.datalistener.GreenhouseCropProductListener;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.ColonyCropsModuleView;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.ColonyCropsModuleView.CropFieldView;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;

/** Read-only, colony-wide crop-field overview for the Greenhouse hut. */
public class ColonyCropsModule extends AbstractBuildingModule
{
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        ColonyCropsModuleView.writeFields(buf, snapshot());
    }

    /**
     * Build the server-authoritative crop snapshot used by both the hut tab and crop journal.
     *
     * @return immutable, unassigned-first field snapshot
     */
    @SuppressWarnings("null")
    public List<CropFieldView> snapshot()
    {
        final List<FarmField> fields = allFarmFields();
        final GreenhouseBiomeModule biomeModule = building.getModule(GreenhouseBiomeModule.class, ignored -> true);
        final Map<Item, Integer> productCounts = new HashMap<>();

        fields.sort(Comparator
            .comparing(FarmField::isTaken)
            .thenComparingInt(field -> field.getPosition().distManhattan(building.getID())));

        final List<CropFieldView> snapshot = new ArrayList<>(fields.size());
        for (final FarmField field : fields)
        {
            final BlockPos position = field.getPosition().immutable();
            final IBuilding farm = field.isTaken()
                ? building.getColony().getServerBuildingManager().getBuilding(field.getBuildingId())
                : null;
            final List<String> workers = farm == null ? List.of() : farm.getAllAssignedCitizen().stream()
                .map(ICitizenData::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
            final ResourceLocation effectiveBiome = biomeId(building.getColony().getWorld().getBiome(position));
            final ItemStack product = field.getSeed().isEmpty()
                ? ItemStack.EMPTY
                : new ItemStack(GreenhouseCropProductListener.INSTANCE.productFor(field.getSeed()));
            final int productCount = product.isEmpty()
                ? 0
                : productCounts.computeIfAbsent(product.getItem(), this::warehouseProductCount);

            snapshot.add(new CropFieldView(position, field.getSeed().copy(), product, productCount, field.isTaken(),
                farm == null ? null : farm.getID(), workers,
                biomeModule.hasClimateControlHub(position), biomeModule.isOwned(position),
                biomeModule.isOwnedByAnotherGreenhouse(position), biomeModule.isOwned(position)
                    || biomeModule.getOwnedFieldCount() < biomeModule.getSupportedFieldCount(),
                effectiveBiome, biomeModule.getNaturalBiomeId(building.getColony().getWorld(), position)));
        }
        return List.copyOf(snapshot);
    }

    /**
     * Collect all MineColonies farm-field extensions registered to this colony.
     *
     * @return mutable list containing every registered farm field
     */
    private List<FarmField> allFarmFields()
    {
        final List<FarmField> fields = new ArrayList<>();
        building.getColony().getServerBuildingManager()
            .getBuildingExtensions(extension -> extension instanceof FarmField)
            .forEach(extension -> fields.add((FarmField) extension));
        return fields;
    }

    /**
     * Count one crop product across every warehouse registered to this colony.
     *
     * @param product product item to count
     * @return raw physical quantity stored in loaded warehouse rack containers
     */
    private int warehouseProductCount(final @Nonnull Item product)
    {
        final ItemStorage matcher = new ItemStorage(new ItemStack(product), true, true);
        int count = 0;
        for (final IWareHouse warehouse : building.getColony().getServerBuildingManager().getWareHouses())
        {
            count += InventoryUtils.getCountFromBuilding(warehouse, matcher);
        }
        return count;
    }

    /**
     * Resolve a biome holder to its registered identifier.
     *
     * @param biome biome holder to resolve
     * @return registered biome identifier, or a stable unknown fallback
     */
    @SuppressWarnings("null")
    private static ResourceLocation biomeId(final Holder<Biome> biome)
    {
        return biome.unwrapKey().map(ResourceKey::location)
            .orElse(ResourceLocation.fromNamespaceAndPath("minecraft", "unknown"));
    }
}
