package com.deathfrog.greenhousegardener.core.client.gui.modules;

import java.util.List;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.ColonyCropsModuleView;
import com.deathfrog.greenhousegardener.core.colony.crops.CropFieldSnapshot;
import com.deathfrog.greenhousegardener.core.blocks.ModBlocks;
import com.deathfrog.greenhousegardener.core.network.RefreshGreenhouseBuildingViewMessage;
import com.ldtteam.blockui.BOGuiGraphics;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.ldtteam.blockui.views.View;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.api.colony.IColonyView;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Read-only Greenhouse tab listing all MineColonies farm fields in the colony. */
public class WindowColonyCropsModule extends AbstractModuleWindow<ColonyCropsModuleView>
{
    @SuppressWarnings("null")
    private static final ItemStack UNSET_SEED = new ItemStack(Items.WOODEN_HOE);

    @SuppressWarnings("null")
    private static final ItemStack CLIMATE_HUB = new ItemStack(ModBlocks.climateControlHub.get());
    
    private final ScrollingList fieldList;
    private List<CropFieldSnapshot> fields = List.of();

    public WindowColonyCropsModule(final ColonyCropsModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(
            GreenhouseGardenerMod.MODID, "gui/layouthuts/layoutcolonycropsmodule.xml"));
        fieldList = window.findPaneOfTypeByID("fields", ScrollingList.class);
    }

    @Override
    public void onOpened()
    {
        super.onOpened();
        new RefreshGreenhouseBuildingViewMessage(buildingView.getPosition()).sendToServer();
        refresh();
    }

    @Override
    public void onUpdate()
    {
        super.onUpdate();
        refresh();
    }

    /** Refresh the summary and bind the latest synchronized fields to the scrolling list. */
    private void refresh()
    {
        fields = moduleView.getFields();
        refresh(this, fieldList, fields, moduleView.getColony());
    }

    /**
     * Populate Colony Crops controls for either the hut module or a portable journal.
     *
     * @param host window containing the Colony Crops controls
     * @param fieldList scrolling list to populate
     * @param fields current synchronized fields
     * @param colony client colony used to resolve live farming phases
     */
    public static void refresh(final AbstractWindowSkeleton host, final ScrollingList fieldList,
        final List<CropFieldSnapshot> fields, final IColonyView colony)
    {
        final long unassigned = fields.stream().filter(field -> !field.assigned()).count();
        host.findPaneOfTypeByID("summary", Text.class).setText(unassigned == 0 && !fields.isEmpty()
            ? Component.translatable("com.greenhousegardener.core.gui.colony_crops.summary.all_assigned", fields.size())
            : Component.translatable("com.greenhousegardener.core.gui.colony_crops.summary", unassigned, fields.size()));
        fieldList.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return fields.size();
            }

            @Override
            public void updateElement(final int index, final Pane row)
            {
                updateRow(fields.get(index), row, colony);
            }
        });
    }

    /**
     * Populate one visible field row and attach its highlight and tooltip behavior.
     *
     * @param field synchronized field data for this row
     * @param row row pane to populate
     */
    @SuppressWarnings("null")
    private static void updateRow(final CropFieldSnapshot field, final Pane row, final IColonyView colony)
    {
        final ItemIcon seed = row.findPaneOfTypeByID("seed", ItemIcon.class);
        seed.setItem(field.seed().isEmpty() ? UNSET_SEED : field.seed());
        final ItemIcon potential = row.findPaneOfTypeByID("potential", ItemIcon.class);
        if (field.hasClimateControlHub())
        {
            potential.setItem(CLIMATE_HUB);
            potential.show();
        }
        else
        {
            potential.hide();
        }

        final Text worker = row.findPaneOfTypeByID("worker", Text.class);
        if (!field.assigned())
        {
            worker.setText(Component.translatable("com.greenhousegardener.core.gui.colony_crops.unassigned")
                .withStyle(ChatFormatting.DARK_RED));
        }
        else if (field.workers().isEmpty())
        {
            worker.setText(Component.translatable("com.greenhousegardener.core.gui.colony_crops.no_worker")
                .withStyle(ChatFormatting.GOLD));
        }
        else
        {
            worker.setText(Component.literal(String.join(", ", field.workers())));
        }

        final Text storedCount = row.findPaneOfTypeByID("storedCount", Text.class);
        storedCount.setText(field.seed().isEmpty()
            ? Component.literal("-")
            : Component.literal(Integer.toString(field.productCount())));
        storedCount.setHoverPane(null);
        PaneBuilders.tooltipBuilder().hoverPane(storedCount).append(storageTooltip(field)).build();

        final Button highlight = ensureHighlightButton(row);
        if (highlight != null)
        {
            highlight.setHandler(button -> WindowBiomeModule.highlightField(field.position()));
            tooltip(highlight, field, colony);
        }
        tooltip(seed, field, colony);
        tooltip(worker, field, colony);
        if (field.hasClimateControlHub())
        {
            tooltip(potential, field, colony);
        }
    }

    /**
     * Attach the complete field tooltip to a row control.
     *
     * @param pane control that should display the tooltip
     * @param field field described by the tooltip
     */
    private static void tooltip(final Pane pane, final CropFieldSnapshot field, final IColonyView colony)
    {
        pane.setHoverPane(null);
        PaneBuilders.tooltipBuilder().hoverPane(pane).append(fieldTooltip(field, colony)).build();
    }

    /**
     * Build the full field tooltip, including assignment, biome, and climate-control state.
     *
     * @param field field to describe
     * @return composed tooltip component
     */
    @SuppressWarnings("null")
    private static Component fieldTooltip(final CropFieldSnapshot field, final IColonyView colony)
    {
        final BlockPos pos = field.position();
        Component result = Component.translatable("com.greenhousegardener.core.gui.colony_crops.position",
            pos.getX(), pos.getY(), pos.getZ());
        result = result.copy().append("\n").append(Component.translatable(
            "com.greenhousegardener.core.gui.colony_crops.crop",
            !field.product().isEmpty()
                ? field.product().getHoverName()
                : field.seed().isEmpty()
                    ? Component.translatable("com.greenhousegardener.core.gui.colony_crops.none")
                    : field.seed().getHoverName()));
        if (!field.product().isEmpty())
        {
            result = result.copy().append("\n").append(storageAmountTooltip(field));
        }
        result = result.copy().append("\n").append(assignmentTooltip(field));
        final Component farmingPhase = farmingPhaseTooltip(field, colony);
        if (farmingPhase != null)
        {
            result = result.copy().append("\n").append(farmingPhase);
        }
        if (field.farmPosition() != null)
        {
            result = result.copy().append("\n").append(Component.translatable(
                "com.greenhousegardener.core.gui.colony_crops.farm_position",
                field.farmPosition().getX(), field.farmPosition().getY(), field.farmPosition().getZ()));
        }
        result = result.copy().append("\n").append(Component.translatable(
            "com.greenhousegardener.core.gui.colony_crops.natural_biome", biomeName(field.naturalBiome())));
        if (!field.naturalBiome().equals(field.effectiveBiome()))
        {
            result = result.copy().append("\n").append(Component.translatable(
                "com.greenhousegardener.core.gui.colony_crops.effective_biome", biomeName(field.effectiveBiome())));
        }
        return result.copy().append("\n").append(climateTooltip(field));
    }

    /**
     * Resolve and describe the current MineColonies farming phase for an assigned field.
     *
     * @param field field whose live extension state should be inspected
     * @return translated farming phase, or {@code null} for unassigned or unavailable fields
     */
    private static Component farmingPhaseTooltip(final CropFieldSnapshot field, final IColonyView colony)
    {
        if (!field.assigned() || colony == null)
        {
            return null;
        }

        return colony.getClientBuildingManager()
            .getBuildingExtensions(extension -> extension instanceof FarmField
                && field.position().equals(extension.getPosition()))
            .stream()
            .map(FarmField.class::cast)
            .findFirst()
            .<Component>map(farmField -> Component.translatable(
                "com.greenhousegardener.core.gui.colony_crops.farming_phase",
                farmField.getFieldStage().getStageText()))
            .orElse(null);
    }

    /**
     * Describe the mapped crop product and its raw quantity across colony warehouses.
     *
     * @param field field whose stored product should be described
     * @return translated warehouse count component
     */
    private static Component storageTooltip(final CropFieldSnapshot field)
    {
        if (field.product().isEmpty())
        {
            return Component.translatable("com.greenhousegardener.core.gui.colony_crops.storage.none");
        }
        return Component.translatable(
            "com.greenhousegardener.core.gui.colony_crops.storage.count",
            field.product().getHoverName(),
            field.productCount());
    }

    /**
     * Describe the stored crop quantity compactly within the complete field tooltip.
     *
     * @param field field whose stored product quantity should be described
     * @return translated compact warehouse count component
     */
    private static Component storageAmountTooltip(final CropFieldSnapshot field)
    {
        return Component.translatable(
            "com.greenhousegardener.core.gui.colony_crops.storage.amount",
            field.productCount());
    }

    /**
     * Describe the field's farm and farmer assignment state.
     *
     * @param field field whose assignment should be described
     * @return translated assignment component
     */
    private static Component assignmentTooltip(final CropFieldSnapshot field)
    {
        if (!field.assigned())
        {
            return Component.translatable("com.greenhousegardener.core.gui.colony_crops.assignment.none");
        }
        if (field.workers().isEmpty())
        {
            return Component.translatable("com.greenhousegardener.core.gui.colony_crops.assignment.no_worker");
        }
        return Component.translatable("com.greenhousegardener.core.gui.colony_crops.assignment.worker", String.join(", ", field.workers()));
    }

    /**
     * Describe the field's climate-control potential, ownership, and local capacity state.
     *
     * @param field field whose climate-control state should be described
     * @return translated climate-control component
     */
    private static Component climateTooltip(final CropFieldSnapshot field)
    {
        if (!field.hasClimateControlHub())
        {
            return Component.translatable("com.greenhousegardener.core.gui.colony_crops.climate.no_hub");
        }
        if (field.ownedByAnotherGreenhouse())
        {
            return Component.translatable("com.greenhousegardener.core.gui.colony_crops.climate.other");
        }
        if (field.ownedByThisGreenhouse())
        {
            return Component.translatable("com.greenhousegardener.core.gui.colony_crops.climate.this");
        }
        if (!field.thisGreenhouseHasCapacity())
        {
            return Component.translatable("com.greenhousegardener.core.gui.colony_crops.climate.no_capacity");
        }
        return Component.translatable("com.greenhousegardener.core.gui.colony_crops.climate.potential");
    }

    /**
     * Convert a biome identifier into its localized display component.
     *
     * @param biome biome identifier to localize
     * @return translated biome name
     */
    @SuppressWarnings("null")
    private static Component biomeName(final ResourceLocation biome)
    {
        return Component.translatable(Util.makeDescriptionId("biome", biome));
    }

    /**
     * Find or create the invisible button layered over a row's crop icon.
     *
     * @param row row containing the crop icon
     * @return highlight button, or {@code null} when the row cannot contain children
     */
    private static Button ensureHighlightButton(final Pane row)
    {
        final Pane existing = row.findPaneByID("fieldHighlight");
        if (existing instanceof Button button)
        {
            return button;
        }
        if (!(row instanceof View view))
        {
            return null;
        }
        final Button button = new InvisibleButton();
        button.setID("fieldHighlight");
        button.setPosition(4, 5);
        button.setSize(16, 16);
        view.addChild(button);
        return button;
    }

    private static final class InvisibleButton extends Button
    {
        @Override
        public void drawSelf(final BOGuiGraphics target, final double mx, final double my)
        {
            // The ItemIcon below this button supplies the visuals.
        }
    }
}
