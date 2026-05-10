package com.deathfrog.greenhousegardener.core.client.gui.modules;

import javax.annotation.Nonnull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseBiomeModuleView;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseBiomeModuleView.FieldBiomeView;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.HumiditySetting;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.TemperatureSetting;
import com.deathfrog.greenhousegardener.core.network.RefreshGreenhouseBiomeModuleMessage;
import com.deathfrog.greenhousegardener.core.network.SetGreenhouseBiomeFieldMessage;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.AbstractTextBuilder;
import com.ldtteam.blockui.controls.CheckBox;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.DropDownList;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Window for assigning climate settings to each managed greenhouse field.
 */
public class WindowBiomeModule extends AbstractModuleWindow<GreenhouseBiomeModuleView>
{
    private static final String FIELD_LIST = "fields";
    private static final String IMAGE_HELP = "help";
    private static final String BALANCE_HOT = "hotBalance";
    private static final String BALANCE_COLD = "coldBalance";
    private static final String BALANCE_HUMID = "humidBalance";
    private static final String BALANCE_DRY = "dryBalance";
    private static final String FIELD_SUMMARY = "fieldSummary";
    private static final String FIELD_SEED = "seed";
    private static final String FIELD_OWNED = "owned";
    private static final String FIELD_TEMPERATURE = "temp";
    private static final String FIELD_HUMIDITY = "humidity";
    private final ScrollingList fieldList;
    private boolean updatingFields = false;

    /**
     * Creates the biome settings module window.
     *
     * @param moduleView client-side module view containing greenhouse field assignments
     */
    public WindowBiomeModule(final GreenhouseBiomeModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, "gui/layouthuts/layoutbiomemodule.xml"));
        this.fieldList = window.findPaneOfTypeByID(FIELD_LIST, ScrollingList.class);
    }

    /**
     * Initializes dropdown controls and populates field rows when the window opens.
     */
    @Override
    public void onOpened()
    {
        super.onOpened();
        new RefreshGreenhouseBiomeModuleMessage(buildingView.getPosition()).sendToServer();

        final Image help = findPaneOfTypeByID(IMAGE_HELP, Image.class);
        final AbstractTextBuilder.TooltipBuilder helpTipBuilder = PaneBuilders.tooltipBuilder().hoverPane(help);
        helpTipBuilder.append(Component.translatable("com.greenhousegardener.biomesettings.help"));
        helpTipBuilder.build();

        updateBalances();
        updateFieldSummary();
        updateFields();
    }

    /**
     * Keeps the window synchronized with the latest module view data while open.
     */
    @Override
    public void onUpdate()
    {
        super.onUpdate();
        updateBalances();
        updateFieldSummary();
        updateFields();
    }

    /**
     * Refreshes the climate modification balances shown above the field table.
     */
    @SuppressWarnings("null")
    private void updateBalances()
    {
        findPaneOfTypeByID(BALANCE_HOT, Text.class).setText(Component.literal(String.valueOf(moduleView.getHotBalance())));
        findPaneOfTypeByID(BALANCE_COLD, Text.class).setText(Component.literal(String.valueOf(moduleView.getColdBalance())));
        findPaneOfTypeByID(BALANCE_HUMID, Text.class).setText(Component.literal(String.valueOf(moduleView.getHumidBalance())));
        findPaneOfTypeByID(BALANCE_DRY, Text.class).setText(Component.literal(String.valueOf(moduleView.getDryBalance())));
    }

    /**
     * Refreshes the field and biome-slot summary above the field table.
     */
    private void updateFieldSummary()
    {
        findPaneOfTypeByID(FIELD_SUMMARY, Text.class).setText(Component.translatable(
            "com.greenhousegardener.core.gui.biome.field_summary",
            moduleView.getOwnedFieldCount(),
            moduleView.getSupportedFieldCount(),
            moduleView.getModifiedBiomeCount(),
            moduleView.getModifiedBiomeLimit()));
    }

    /**
     * Applies a temperature selection for a field row.
     *
     * @param fieldIndex zero-based field row index
     * @param temperature selected temperature setting
     */
    private void setTemperature(final int fieldIndex, final TemperatureSetting temperature)
    {
        final FieldBiomeView field = getField(fieldIndex);
        if (field == null || updatingFields || !field.owned() || field.temperature() == temperature)
        {
            return;
        }

        setAssignment(field, temperature, field.humidity());
    }

    /**
     * Applies a humidity selection for a field row.
     *
     * @param fieldIndex zero-based field row index
     * @param humidity selected humidity setting
     */
    private void setHumidity(final int fieldIndex, final HumiditySetting humidity)
    {
        final FieldBiomeView field = getField(fieldIndex);
        if (field == null || updatingFields || !field.owned() || field.humidity() == humidity)
        {
            return;
        }

        setAssignment(field, field.temperature(), humidity);
    }

    /**
     * Stores a new assignment locally, sends it to the server, and refreshes the visible rows.
     *
     * @param field field row being changed
     * @param temperature selected temperature setting
     * @param humidity selected humidity setting
     */
    private void setAssignment(final FieldBiomeView field, final TemperatureSetting temperature, final HumiditySetting humidity)
    {
        if (!canUseModifiedBiomeSlot(field, temperature, humidity))
        {
            updateFields();
            return;
        }

        moduleView.setFieldAssignment(field.position(), temperature, humidity);
        new SetGreenhouseBiomeFieldMessage(buildingView.getPosition(), field.position(), temperature.ordinal(), humidity.ordinal(), field.owned()).sendToServer();
        updateFields();
    }

    /**
     * Stores a new ownership value locally, sends it to the server, and refreshes the visible rows.
     *
     * @param fieldIndex zero-based field row index
     * @param owned true when this greenhouse should claim the field
     */
    private void setOwnership(final int fieldIndex, final boolean owned)
    {
        final FieldBiomeView field = getField(fieldIndex);
        if (field == null || updatingFields || field.owned() == owned)
        {
            return;
        }
        if (owned && !canClaimMoreFields())
        {
            updateFields();
            return;
        }

        moduleView.setFieldOwned(field.position(), owned);
        new SetGreenhouseBiomeFieldMessage(buildingView.getPosition(), field.position(), field.temperature().ordinal(), field.humidity().ordinal(), owned).sendToServer();
        updateFields();
    }

    /**
     * Refreshes field rows, seed icons, dropdown selections, and field-position tooltips.
     */
    private void updateFields()
    {
        updatingFields = true;
        fieldList.enable();
        fieldList.show();
        fieldList.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return moduleView.getFields().size();
            }

            @Override
            public void updateElement(final int index, final Pane rowPane)
            {
                updateFieldRow(index, rowPane);
            }
        });
        updatingFields = false;
    }

    /**
     * Refresh a single field row in the scrollable list.
     *
     * @param fieldIndex zero-based field row index
     * @param row row pane to update
     */
    private void updateFieldRow(final int fieldIndex, final Pane row)
    {
        final boolean wasUpdatingFields = updatingFields;
        updatingFields = true;
        final FieldBiomeView field = getField(fieldIndex);
        try
        {
            if (field == null)
            {
                row.hide();
                return;
            }

            row.show();
            final DropDownList tempDropdown = row.findPaneOfTypeByID(FIELD_TEMPERATURE, DropDownList.class);
            final DropDownList humidityDropdown = row.findPaneOfTypeByID(FIELD_HUMIDITY, DropDownList.class);
            tempDropdown.setDataProvider(new TemperatureDataProvider());
            humidityDropdown.setDataProvider(new HumidityDataProvider());
            setSelectedDropdownIndex(tempDropdown, field.temperature().ordinal());
            setSelectedDropdownIndex(humidityDropdown, field.humidity().ordinal());
            tempDropdown.setEnabled(field.owned());
            humidityDropdown.setEnabled(field.owned());
            tempDropdown.setHandler(dropdown -> setTemperature(fieldIndex, TemperatureSetting.values()[dropdown.getSelectedIndex()]));
            humidityDropdown.setHandler(dropdown -> setHumidity(fieldIndex, HumiditySetting.values()[dropdown.getSelectedIndex()]));

            final CheckBox ownedCheckbox = row.findPaneOfTypeByID(FIELD_OWNED, CheckBox.class);
            ownedCheckbox.setChecked(field.owned());
            ownedCheckbox.setEnabled(field.owned() || canClaimMoreFields());
            ownedCheckbox.setHandler(button -> setOwnership(fieldIndex, ownedCheckbox.isChecked()));

            final ItemIcon seedIcon = row.findPaneOfTypeByID(FIELD_SEED, ItemIcon.class);
            seedIcon.setItem(field.seed().isEmpty() ? ItemStack.EMPTY : field.seed());

            addPositionTooltip(tempDropdown, field);
            addPositionTooltip(humidityDropdown, field);
            addPositionTooltip(seedIcon, field);
            addPositionTooltip(ownedCheckbox, field);
        }
        finally
        {
            updatingFields = wasUpdatingFields;
        }
    }

    /**
     * Selects the current dropdown value without needlessly re-firing the dropdown handler.
     *
     * @param dropdown dropdown to update
     * @param index selected option index
     */
    private static void setSelectedDropdownIndex(final DropDownList dropdown, final int index)
    {
        if (dropdown.getSelectedIndex() != index)
        {
            dropdown.setSelectedIndex(index);
        }
    }

    /**
     * Finds the field view for a row index.
     *
     * @param fieldIndex zero-based field row index
     * @return matching field view, or null when the row has no field
     */
    private FieldBiomeView getField(final int fieldIndex)
    {
        if (fieldIndex < 0 || fieldIndex >= moduleView.getFields().size())
        {
            return null;
        }

        return moduleView.getFields().get(fieldIndex);
    }

    /**
     * Check whether another unowned field can be claimed at this building level.
     *
     * @return true when the client-side owned count is below the supported field cap
     */
    private boolean canClaimMoreFields()
    {
        return moduleView.getOwnedFieldCount() < moduleView.getSupportedFieldCount();
    }

    /**
     * Check whether the client can optimistically send an assignment that consumes a new modified-biome slot.
     *
     * @param field field row being changed
     * @param temperature selected temperature setting
     * @param humidity selected humidity setting
     * @return true when the change should be allowed client-side
     */
    private boolean canUseModifiedBiomeSlot(final FieldBiomeView field, final TemperatureSetting temperature, final HumiditySetting humidity)
    {
        if (field == null)
        {
            return false;
        }

        return isModified(field) || !isModified(field, temperature, humidity) || modifiedBiomeCount() < moduleView.getModifiedBiomeLimit();
    }

    /**
     * Check whether a view row consumes a modified-biome slot.
     *
     * @param field field row to inspect
     * @return true when the visible assignment differs from its natural climate
     */
    private static boolean isModified(final FieldBiomeView field)
    {
        return isModified(field, field.temperature(), field.humidity());
    }

    /**
     * Check whether a proposed assignment differs from the server-provided natural climate.
     *
     * @param field field row containing natural climate data
     * @param temperature selected temperature setting
     * @param humidity selected humidity setting
     * @return true when the assignment differs from the field's natural climate
     */
    private static boolean isModified(final FieldBiomeView field, final TemperatureSetting temperature, final HumiditySetting humidity)
    {
        return temperature != field.naturalTemperature() || humidity != field.naturalHumidity();
    }

    /**
     * Count client-visible owned fields that currently consume modified-biome slots.
     *
     * @return current client-side modified-biome slot usage
     */
    private int modifiedBiomeCount()
    {
        int count = 0;
        for (final FieldBiomeView field : moduleView.getFields())
        {
            if (field.owned() && isModified(field))
            {
                count++;
            }
        }
        return count;
    }

    /**
     * Builds the translated label for a temperature option.
     *
     * @param temperature temperature option
     * @return translated dropdown label
     */
    private static MutableComponent temperatureLabel(final TemperatureSetting temperature)
    {
        return Component.translatable("com.greenhousegardener.core.gui.biome.temperature." + temperature.getSerializedName());
    }

    /**
     * Builds the translated label for a humidity option.
     *
     * @param humidity humidity option
     * @return translated dropdown label
     */
    private static MutableComponent humidityLabel(final HumiditySetting humidity)
    {
        return Component.translatable("com.greenhousegardener.core.gui.biome.humidity." + humidity.getSerializedName());
    }

    /**
     * Dropdown data provider for greenhouse temperature options.
     */
    private static final class TemperatureDataProvider implements DropDownList.DataProvider
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public int getElementCount()
        {
            return TemperatureSetting.values().length;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MutableComponent getLabel(final int index)
        {
            return temperatureLabel(TemperatureSetting.values()[index]);
        }
    }

    /**
     * Dropdown data provider for greenhouse humidity options.
     */
    private static final class HumidityDataProvider implements DropDownList.DataProvider
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public int getElementCount()
        {
            return HumiditySetting.values().length;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MutableComponent getLabel(final int index)
        {
            return humidityLabel(HumiditySetting.values()[index]);
        }
    }

    /**
     * Adds a tooltip showing the world position and maintenance history of a field.
     *
     * @param pane pane receiving the tooltip
     * @param field field displayed by the row
     */
    private static void addPositionTooltip(final Pane pane, final FieldBiomeView field)
    {
        final BlockPos position = field.position();
        final MutableComponent tooltip = Component.translatable(
            "com.greenhousegardener.core.gui.biome.field_position",
            position.getX(),
            position.getY(),
            position.getZ());

        if (field.owned() && field.daysSinceLastMaintenance() >= 0)
        {
            tooltip.append("\n").append(maintenanceTooltipLine(field.daysSinceLastMaintenance()));
        }

        pane.setHoverPane(null);
        PaneBuilders.tooltipBuilder()
            .append(tooltip)
            .hoverPane(pane)
            .build();
    }

    /**
     * Build the translated maintenance-history tooltip line.
     *
     * @param daysSinceLastMaintenance zero for today or a positive day count
     * @return translated maintenance-history line
     */
    @SuppressWarnings("null")
    private static @Nonnull MutableComponent maintenanceTooltipLine(final int daysSinceLastMaintenance)
    {
        if (daysSinceLastMaintenance == 0)
        {
            return Component.translatable("com.greenhousegardener.core.gui.biome.field_last_maintained.today");
        }
        if (daysSinceLastMaintenance == 1)
        {
            return Component.translatable("com.greenhousegardener.core.gui.biome.field_last_maintained.day_ago");
        }
        if (daysSinceLastMaintenance > 0)
        {
            return Component.translatable(
                "com.greenhousegardener.core.gui.biome.field_last_maintained.days_ago",
                daysSinceLastMaintenance);
        }

        return Component.translatable("com.greenhousegardener.core.gui.biome.field_last_maintained.today");
    }
}
