package com.deathfrog.greenhousegardener.core.client.gui.modules;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseBiomeModuleView;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseBiomeModuleView.FieldBiomeView;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.HumiditySetting;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.TemperatureSetting;
import com.deathfrog.greenhousegardener.core.network.SetGreenhouseBiomeFieldMessage;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.DropDownList;
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
    private static final int MAX_FIELD_ROWS = 4;
    private boolean updatingFields = false;

    /**
     * Creates the biome settings module window.
     *
     * @param moduleView client-side module view containing greenhouse field assignments
     */
    public WindowBiomeModule(final GreenhouseBiomeModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, "gui/layouthuts/layoutbiomemodule.xml"));
    }

    /**
     * Initializes dropdown controls and populates field rows when the window opens.
     */
    @Override
    public void onOpened()
    {
        super.onOpened();
        configureDropdowns();
        updateFields();
    }

    /**
     * Connects each row's temperature and humidity dropdown to its data provider and change handler.
     */
    private void configureDropdowns()
    {
        for (int fieldIndex = 0; fieldIndex < MAX_FIELD_ROWS; fieldIndex++)
        {
            final int index = fieldIndex;
            final DropDownList tempDropdown = window.findPaneOfTypeByID("temp" + fieldIndex, DropDownList.class);
            tempDropdown.setDataProvider(new TemperatureDataProvider());
            tempDropdown.setHandler(dropdown -> setTemperature(index, TemperatureSetting.values()[dropdown.getSelectedIndex()]));

            final DropDownList humidityDropdown = window.findPaneOfTypeByID("humidity" + fieldIndex, DropDownList.class);
            humidityDropdown.setDataProvider(new HumidityDataProvider());
            humidityDropdown.setHandler(dropdown -> setHumidity(index, HumiditySetting.values()[dropdown.getSelectedIndex()]));
        }
    }

    /**
     * Keeps the window synchronized with the latest module view data while open.
     */
    @Override
    public void onUpdate()
    {
        super.onUpdate();
        updateFields();
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
        if (field == null || updatingFields || field.temperature() == temperature)
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
        if (field == null || updatingFields || field.humidity() == humidity)
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
        moduleView.setFieldAssignment(field.fieldIndex(), temperature, humidity);
        new SetGreenhouseBiomeFieldMessage(buildingView.getPosition(), field.fieldIndex(), temperature.ordinal(), humidity.ordinal()).sendToServer();
        updateFields();
    }

    /**
     * Refreshes field rows, seed icons, dropdown selections, and field-position tooltips.
     */
    @SuppressWarnings("null")
    private void updateFields()
    {
        updatingFields = true;
        for (int fieldIndex = 0; fieldIndex < MAX_FIELD_ROWS; fieldIndex++)
        {
            final Pane row = window.findPaneByID("field" + fieldIndex);
            if (row == null)
            {
                continue;
            }

            final FieldBiomeView field = getField(fieldIndex);
            if (fieldIndex >= moduleView.getSupportedFieldCount() || field == null)
            {
                row.hide();
                continue;
            }

            row.show();
            final Text fieldName = row.findPaneOfTypeByID("fieldName" + fieldIndex, Text.class);
            fieldName.setText(Component.literal(String.valueOf(fieldIndex + 1)));
            setSelectedDropdownIndex(row.findPaneOfTypeByID("temp" + fieldIndex, DropDownList.class), field.temperature().ordinal());
            setSelectedDropdownIndex(row.findPaneOfTypeByID("humidity" + fieldIndex, DropDownList.class), field.humidity().ordinal());

            final ItemIcon seedIcon = row.findPaneOfTypeByID("seed" + fieldIndex, ItemIcon.class);
            seedIcon.setItem(field.seed().isEmpty() ? ItemStack.EMPTY : field.seed());

            addPositionTooltip(fieldName, field.position());
            addPositionTooltip(row.findPaneOfTypeByID("temp" + fieldIndex, DropDownList.class), field.position());
            addPositionTooltip(row.findPaneOfTypeByID("humidity" + fieldIndex, DropDownList.class), field.position());
            addPositionTooltip(seedIcon, field.position());
        }
        updatingFields = false;
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
        return moduleView.getFields().stream()
            .filter(field -> field.fieldIndex() == fieldIndex)
            .findFirst()
            .orElse(null);
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
     * Adds a tooltip showing the world position of a field.
     *
     * @param pane pane receiving the tooltip
     * @param position field block position
     */
    private static void addPositionTooltip(final Pane pane, final BlockPos position)
    {
        pane.setHoverPane(null);
        PaneBuilders.tooltipBuilder()
            .append(Component.translatable("com.greenhousegardener.core.gui.biome.field_position", position.getX(), position.getY(), position.getZ()))
            .hoverPane(pane)
            .build();
    }
}
