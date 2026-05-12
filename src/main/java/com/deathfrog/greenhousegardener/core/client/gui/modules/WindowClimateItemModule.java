package com.deathfrog.greenhousegardener.core.client.gui.modules;

import java.util.List;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseClimateItemModuleView;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseClimateItemModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseClimateItemModule.ClimateItemList;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseClimateItemModule.ClimateModificationType;
import com.deathfrog.greenhousegardener.core.datalistener.GreenhouseClimateItemValueListener;
import com.deathfrog.greenhousegardener.core.network.SetGreenhouseClimateItemMessage;
import com.deathfrog.greenhousegardener.core.network.SetGreenhouseClimateItemMessage.ClimateItemAction;
import com.deathfrog.greenhousegardener.core.network.SetGreenhouseClimateItemMessage.ClimateModuleType;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.client.gui.AbstractModuleWindow;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Shared window for greenhouse modules that manage two climate item lists.
 *
 * @param <T> concrete climate item module view type
 */
public abstract class WindowClimateItemModule<T extends GreenhouseClimateItemModuleView> extends AbstractModuleWindow<T>
{
    private static final String ITEM_NAME = "itemName";
    private static final String ITEM_ICON = "itemIcon";
    private static final String ITEM_CLIMATE_MODIFICATION_UNIT = "itemClimateModificationUnit";

    private final ClimateModuleType moduleType;
    private final ClimateModificationType increaseType;
    private final ClimateModificationType decreaseType;
    private final ScrollingList increaseList;
    private final ScrollingList decreaseList;

    /**
     * Create a shared climate item module window with top and bottom item lists.
     *
     * @param moduleView module view backing the window
     * @param moduleType temperature or humidity module type
     * @param increaseType climate value type used by the top selector
     * @param decreaseType climate value type used by the bottom selector
     */
    protected WindowClimateItemModule(
        final T moduleView,
        final ClimateModuleType moduleType,
        final ClimateModificationType increaseType,
        final ClimateModificationType decreaseType)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, "gui/layouthuts/layoutclimateitemmodule.xml"));
        this.moduleType = moduleType;
        this.increaseType = increaseType;
        this.decreaseType = decreaseType;
        this.increaseList = window.findPaneOfTypeByID("increaseList", ScrollingList.class);
        this.decreaseList = window.findPaneOfTypeByID("decreaseList", ScrollingList.class);

        registerButton("addIncrease", () -> addItem(ClimateItemList.INCREASE));
        registerButton("addDecrease", () -> addItem(ClimateItemList.DECREASE));
        registerButton("removeIncrease", button -> removeItem(button, ClimateItemList.INCREASE));
        registerButton("removeDecrease", button -> removeItem(button, ClimateItemList.DECREASE));
    }

    /**
     * Refresh the selected climate item lists when the module opens.
     */
    @Override
    public void onOpened()
    {
        super.onOpened();
        updateLists();
    }

    /**
     * Set the module window title.
     *
     * @param title title text to display
     */
    protected void setTitle(final Component title)
    {
        window.findPaneOfTypeByID("desc", Text.class).setText(title);
    }

    /**
     * Set the title for the top item list.
     *
     * @param title title text to display
     */
    protected void setIncreaseTitle(final Component title)
    {
        window.findPaneOfTypeByID("increaseTitle", Text.class).setText(title);
    }

    /**
     * Set the title for the bottom item list.
     *
     * @param title title text to display
     */
    protected void setDecreaseTitle(final Component title)
    {
        window.findPaneOfTypeByID("decreaseTitle", Text.class).setText(title);
    }

    /**
     * Open the tag-filtered item selector for one climate item list.
     *
     * @param list the target increase or decrease list
     */
    private void addItem(final ClimateItemList list)
    {
        final ClimateModificationType type = modificationType(list);
        new WindowSelectClimateItems(
            this,
            stack -> GreenhouseClimateItemValueListener.INSTANCE.hasValue(type, stack),
            stack -> GreenhouseClimateItemModule.climateModificationUnit(type, stack),
            (stack, quantity) -> {
                new SetGreenhouseClimateItemMessage(buildingView.getPosition(), moduleType.ordinal(), list.ordinal(), ClimateItemAction.ADD.ordinal(), stack, quantity).sendToServer();
            }).open();
        updateLists();
    }

    /**
     * Remove an item row from the selected climate item list and notify the server.
     *
     * @param button remove button clicked in the row
     * @param list the target increase or decrease list
     */
    private void removeItem(final Button button, final ClimateItemList list)
    {
        final ScrollingList scrollingList = list == ClimateItemList.INCREASE ? increaseList : decreaseList;
        final List<ItemStorage> items = list == ClimateItemList.INCREASE ? moduleView.getIncreaseItems() : moduleView.getDecreaseItems();
        final int row = scrollingList.getListElementIndexByPane(button);
        if (row < 0 || row >= items.size())
        {
            return;
        }

        final ItemStorage item = items.remove(row);
        new SetGreenhouseClimateItemMessage(buildingView.getPosition(), moduleType.ordinal(), list.ordinal(), ClimateItemAction.REMOVE.ordinal(), item.getItemStack(), item.getAmount()).sendToServer();
        updateLists();
    }

    /**
     * Refresh both visible item lists.
     */
    private void updateLists()
    {
        updateList(increaseList, moduleView.getIncreaseItems(), increaseType);
        updateList(decreaseList, moduleView.getDecreaseItems(), decreaseType);
    }

    /**
     * Populate a scrolling list with selected items.
     *
     * @param scrollingList list control to update
     * @param items selected items to display
     * @param type climate modification type displayed in this list
     */
    private static void updateList(final ScrollingList scrollingList, final List<ItemStorage> items, final ClimateModificationType type)
    {
        scrollingList.enable();
        scrollingList.show();
        scrollingList.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return items.size();
            }

            @Override
            public void updateElement(final int index, final Pane rowPane)
            {
                final ItemStack stack = items.get(index).getItemStack().copy();
                String cmuLabel = String.valueOf(GreenhouseClimateItemModule.climateModificationUnit(type, stack));
                stack.setCount(1);
                rowPane.findPaneOfTypeByID(ITEM_NAME, Text.class).setText(stack.getHoverName());
                rowPane.findPaneOfTypeByID(ITEM_CLIMATE_MODIFICATION_UNIT, Text.class).setText(Component.literal(cmuLabel == null ? "None" : cmuLabel));
                rowPane.findPaneOfTypeByID(ITEM_ICON, ItemIcon.class).setItem(stack);
            }
        });
    }

    private ClimateModificationType modificationType(final ClimateItemList list)
    {
        return list == ClimateItemList.INCREASE ? increaseType : decreaseType;
    }

}
