package com.deathfrog.greenhousegardener.core.client.gui.modules;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.RanchHerdListModuleView;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.RanchHerdListModuleView.HerdView;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

import java.util.Comparator;
import java.util.List;

/**
 * Read-only Ranch hut tab showing managed herds inside the building bounds.
 */
public class WindowRanchHerdListModule extends AbstractModuleWindow<RanchHerdListModuleView>
{
    @SuppressWarnings("null")
    private static final ItemStack BOWL_ICON = new ItemStack(Items.BOWL);
    @SuppressWarnings("null")
    private static final ItemStack BUCKET_ICON = new ItemStack(Items.BUCKET);
    @SuppressWarnings("null")
    private static final ItemStack SHEARS_ICON = new ItemStack(Items.SHEARS);
    @SuppressWarnings("null")
    private static final ItemStack CANNOT_BREED_ICON = new ItemStack(Items.BARRIER);

    private final ScrollingList herdList;
    private List<HerdView> sortedHerds = List.of();

    /**
     * Creates a Ranch herd-list window backed by synchronized module data.
     *
     * @param moduleView herd-list module view
     */
    public WindowRanchHerdListModule(final RanchHerdListModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(
            GreenhouseGardenerMod.MODID, "gui/layouthuts/layoutranchherdlistmodule.xml"));
        herdList = window.findPaneOfTypeByID("herds", ScrollingList.class);
    }

    /**
     * Populates the capacity header and herd rows when the tab opens.
     */
    @Override
    public void onOpened()
    {
        super.onOpened();
        refresh();
    }

    /**
     * Refreshes the displayed snapshot when module-view data changes.
     */
    @Override
    public void onUpdate()
    {
        super.onUpdate();
        refresh();
    }

    /**
     * Sorts the synchronized rows by localized animal name and binds them to
     * the scrolling list.
     */
    private void refresh()
    {
        findPaneOfTypeByID("capacity", Text.class).setText(Component.translatable(
            "com.greenhousegardener.core.gui.ranch_herds.capacity", moduleView.getHerdCapacity()));
        findPaneOfTypeByID("typeCapacity", Text.class).setText(Component.translatable(
            "com.greenhousegardener.core.gui.ranch_herds.type_capacity", moduleView.getSupportedTypeCapacity()));
        sortedHerds = moduleView.getHerds().stream()
            .sorted(Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(herd -> herd.entityType().toString()))
            .toList();
        herdList.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return sortedHerds.size();
            }

            @SuppressWarnings("null")
            @Override
            public void updateElement(final int index, final Pane rowPane)
            {
                final HerdView herd = sortedHerds.get(index);
                final Text animal = rowPane.findPaneOfTypeByID("animal", Text.class);
                animal.setText(herd.supported()
                    ? animalName(herd)
                    : animalName(herd).copy().withStyle(ChatFormatting.RED));
                animal.setHoverPane(null);
                PaneBuilders.tooltipBuilder()
                    .hoverPane(animal)
                    .append(modTooltip(herd))
                    .append(herd.supported()
                        ? Component.empty()
                        : Component.literal("\n").append(Component.translatable(
                            "com.greenhousegardener.core.gui.ranch_herds.unsupported").withStyle(ChatFormatting.RED)))
                    .build();
                rowPane.findPaneOfTypeByID("count", Text.class)
                    .setText(Component.literal(Integer.toString(herd.count())));
                updateCapabilityIcon(rowPane, "breedingFood", herd.breedingFood(), !herd.breedingFood().isEmpty());
                updateCapabilityIcon(rowPane, "cannotBreed", CANNOT_BREED_ICON, herd.breedingFood().isEmpty());
                updateCapabilityIcon(rowPane, "bowl", BOWL_ICON, herd.isBowlMilkable());
                updateCapabilityIcon(rowPane, "bucket", BUCKET_ICON, herd.isBucketMilkable());
                updateCapabilityIcon(rowPane, "shears", SHEARS_ICON, herd.isShearable());
            }
        });
    }

    /**
     * Shows and populates a product-capability icon when supported, or hides
     * its reserved row position otherwise.
     *
     * @param rowPane herd row containing the icon
     * @param id icon pane identifier
     * @param stack item used to represent the capability
     * @param visible whether the herd has that capability
     */
    private static void updateCapabilityIcon(
        final Pane rowPane,
        final String id,
        final ItemStack stack,
        final boolean visible)
    {
        final ItemIcon icon = rowPane.findPaneOfTypeByID(id, ItemIcon.class);
        if (visible)
        {
            icon.setItem(stack);
            icon.show();
        }
        else
        {
            icon.hide();
        }
    }

    /**
     * Resolves a herd's localized name for client-side sorting.
     *
     * @param herd herd row
     * @return localized display text
     */
    private String displayName(final HerdView herd)
    {
        return animalName(herd).getString();
    }

    /**
     * Resolves the entity type's translated name, falling back to its registry
     * identifier when the client does not know that entity type.
     *
     * @param herd herd row
     * @return animal display component
     */
    @SuppressWarnings("null")
    private Component animalName(final HerdView herd)
    {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(herd.entityType())
            .<Component>map(EntityType::getDescription)
            .orElseGet(() -> Component.literal(herd.entityType().toString()));
    }

    /**
     * Builds a tooltip identifying the mod that registered an animal type.
     *
     * @param herd herd row whose namespace is resolved
     * @return mod display name followed by the full entity-type identifier
     */
    private Component modTooltip(final HerdView herd)
    {
        final String namespace = herd.entityType().getNamespace();
        final String modName = ModList.get().getModContainerById(namespace)
            .map(container -> container.getModInfo().getDisplayName())
            .orElse(namespace);
        return Component.literal(modName + "\n" + herd.entityType());
    }
}
