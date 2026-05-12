package com.deathfrog.greenhousegardener.core.client.gui.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.ldtteam.blockui.Color;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import static com.minecolonies.api.util.constant.WindowConstants.BUTTON_SELECT;
import static com.minecolonies.api.util.constant.WindowConstants.NAME_LABEL;

/**
 * Selector dialog for choosing climate control items from data-pack driven values.
 */
public class WindowSelectClimateItems extends AbstractWindowSkeleton
{
    private static final String BUTTON_DONE = "done";
    private static final String BUTTON_CANCEL = "cancel";
    private static final int DEFAULT_PROTECTED_ITEMS = 0;
    private static final String PROTECTED_QUANTITY_TOOLTIP = "com.greenhousegardener.core.gui.climate.quantity.protected.tooltip";
    private static final String RESOURCE_CCU = "resourceCcu";
    private static final int WHITE = Color.getByName("white", 0);

    private final List<ItemStack> allItems = new ArrayList<>();
    private final ScrollingList resourceList;
    private final Predicate<ItemStack> test;
    private final ToIntFunction<ItemStack> valueResolver;
    private final BiConsumer<ItemStack, Integer> consumer;

    private String filter = "";
    private int tick;

    /**
     * Create a selector window for value-filtered greenhouse climate items.
     *
     * @param origin window that opened this selector
     * @param test predicate used to filter selectable items
     * @param valueResolver resolver for the CCU value displayed beside each item
     * @param consumer callback receiving the selected item and protected quantity
     */
    public WindowSelectClimateItems(
        final BOWindow origin,
        final Predicate<ItemStack> test,
        final ToIntFunction<ItemStack> valueResolver,
        final BiConsumer<ItemStack, Integer> consumer)
    {
        super(origin, ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, "gui/windowselectclimateitems.xml"));
        this.resourceList = findPaneOfTypeByID("resources", ScrollingList.class);
        this.test = test;
        this.valueResolver = valueResolver;
        this.consumer = consumer;

        registerButton(BUTTON_DONE, this::doneClicked);
        registerButton(BUTTON_CANCEL, this::cancelClicked);
        registerButton(BUTTON_SELECT, this::selectClicked);

        final TextField quantityInput = findPaneOfTypeByID("quantity", TextField.class);
        quantityInput.setText(String.valueOf(DEFAULT_PROTECTED_ITEMS));
        PaneBuilders.tooltipBuilder().hoverPane(quantityInput).build().setText(Component.translatable(PROTECTED_QUANTITY_TOOLTIP));

        findPaneOfTypeByID("resourceIcon", ItemIcon.class).setItem(ItemStack.EMPTY);
        findPaneOfTypeByID("resourceName", Text.class).setText(ItemStack.EMPTY.getHoverName());

        window.findPaneOfTypeByID(NAME_LABEL, TextField.class).setHandler(input -> {
            final String newFilter = input.getText();
            if (!newFilter.equals(filter))
            {
                filter = newFilter;
                tick = 10;
            }
        });
    }

    /**
     * Populate the selectable item list when the selector opens.
     */
    @Override
    public void onOpened()
    {
        updateResources();
    }

    /**
     * Refresh the item list after the search filter changes.
     */
    @Override
    public void onUpdate()
    {
        super.onUpdate();
        if (tick > 0 && --tick == 0)
        {
            updateResources();
        }
    }

    /**
     * Select an item from the visible resource row.
     *
     * @param button select button clicked in the row
     */
    private void selectClicked(final Button button)
    {
        final int row = resourceList.getListElementIndexByPane(button);
        if (row < 0 || row >= allItems.size())
        {
            return;
        }

        final ItemStack selected = allItems.get(row);
        findPaneOfTypeByID("resourceIcon", ItemIcon.class).setItem(selected);
        findPaneOfTypeByID("resourceName", Text.class).setText(selected.getHoverName());
    }

    /**
     * Close the selector without changing the parent module.
     */
    private void cancelClicked()
    {
        close();
    }

    /**
     * Confirm the selected item and send it to the parent callback.
     */
    private void doneClicked()
    {
        final ItemStack selected = findPaneOfTypeByID("resourceIcon", ItemIcon.class).getItem();
        if (!selected.isEmpty())
        {
            consumer.accept(normalizedSelectedStack(selected), getProtectedQuantity());
        }
        close();
    }

    /**
     * Read the protected quantity from the input field.
     *
     * @return parsed protected item count, or zero when the input is invalid
     */
    private int getProtectedQuantity()
    {
        try
        {
            return Math.max(0, Integer.parseInt(findPaneOfTypeByID("quantity", TextField.class).getText()));
        }
        catch (final NumberFormatException ex)
        {
            Log.getLogger().warn("Invalid greenhouse climate item protected quantity, defaulting to 0.");
            return DEFAULT_PROTECTED_ITEMS;
        }
    }

    /**
     * Keep the selected stack as an item identity while the protected amount travels separately.
     *
     * @param selected stack selected in the UI
     * @return count-normalized selected stack
     */
    private static ItemStack normalizedSelectedStack(final ItemStack selected)
    {
        final ItemStack normalized = selected.copy();
        normalized.setCount(1);
        return normalized;
    }

    /**
     * Rebuild the selectable item list from all items and the player's inventory.
     */
    @SuppressWarnings("deprecation")
    private void updateResources()
    {
        allItems.clear();
        for (final ItemStack stack : ItemStackUtils.allItemsPlusInventory(Minecraft.getInstance().player))
        {
            if (test.test(stack) && matchesFilter(stack))
            {
                allItems.add(stack);
            }
        }

        allItems.sort(Comparator.comparingInt(stack -> StringUtils.getLevenshteinDistance(stack.getHoverName().getString(), filter)));
        updateResourceList();
    }

    /**
     * Check whether an item matches the current search filter.
     *
     * @param stack item stack to test
     * @return true when the item matches the filter
     */
    private boolean matchesFilter(final ItemStack stack)
    {
        return filter.isEmpty()
            || stack.getDescriptionId().toLowerCase(Locale.US).contains(filter.toLowerCase(Locale.US))
            || stack.getHoverName().getString().toLowerCase(Locale.US).contains(filter.toLowerCase(Locale.US));
    }

    /**
     * Render the current selectable item list into the scrolling list control.
     */
    private void updateResourceList()
    {
        resourceList.enable();
        resourceList.show();
        final List<ItemStack> visibleItems = new ArrayList<>(allItems);
        resourceList.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return visibleItems.size();
            }

            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane)
            {
                final ItemStack stack = visibleItems.get(index);
                final Text resourceLabel = rowPane.findPaneOfTypeByID("resourceName", Text.class);
                resourceLabel.setText(stack.getHoverName());
                resourceLabel.setColors(WHITE);
                rowPane.findPaneOfTypeByID(RESOURCE_CCU, Text.class).setText(Component.literal(String.valueOf(valueResolver.applyAsInt(stack)) + ""));
                rowPane.findPaneOfTypeByID("resourceIcon", ItemIcon.class).setItem(stack);
            }
        });
    }
}
