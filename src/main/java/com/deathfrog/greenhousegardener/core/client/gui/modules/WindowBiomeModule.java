package com.deathfrog.greenhousegardener.core.client.gui.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.ModCommands;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseBiomeModuleView;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseBiomeModuleView.FieldBiomeView;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.HumiditySetting;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.TemperatureSetting;
import com.deathfrog.greenhousegardener.core.network.RefreshGreenhouseBiomeModuleMessage;
import com.deathfrog.greenhousegardener.core.network.SaveGreenhouseBiomeFieldsMessage;
import com.deathfrog.greenhousegardener.core.network.SaveGreenhouseBiomeFieldsMessage.FieldChange;
import com.ldtteam.blockui.BOGuiGraphics;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.AbstractTextBuilder;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.CheckBox;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.DropDownList;
import com.ldtteam.blockui.views.ScrollingList;
import com.ldtteam.blockui.views.View;
import com.minecolonies.core.client.gui.AbstractModuleWindow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Window for assigning climate settings to each managed greenhouse field.
 */
public class WindowBiomeModule extends AbstractModuleWindow<GreenhouseBiomeModuleView>
{
    private static final String FIELD_LIST = "fields";
    private static final String IMAGE_HELP = "help";
    private static final String BALANCE_TITLE = "balanceTitle";
    private static final String BALANCE_HOT = "hotBalance";
    private static final String BALANCE_COLD = "coldBalance";
    private static final String BALANCE_HUMID = "humidBalance";
    private static final String BALANCE_DRY = "dryBalance";
    private static final String FIELD_SUMMARY = "fieldSummary";
    private static final String SAVE_CHANGES = "saveBiomeChanges";
    private static final String BIOME_LIMIT_REACHED = "com.greenhousegardener.core.gui.biome.limit_reached";
    private static final String BIOME_CHANGES_SAVED = "com.greenhousegardener.core.gui.biome.changes_saved";
    private static final String FIELD_HIGHLIGHT = "fieldHighlight";
    private static final String FIELD_SEED = "seed";
    private static final String FIELD_OWNED = "owned";
    private static final String FIELD_TEMPERATURE = "temp";
    private static final String FIELD_HUMIDITY = "humidity";
    @SuppressWarnings("null")
    private static final ItemStack UNSET_FIELD_SEED_ICON = new ItemStack(Items.WOODEN_HOE);
    private final ScrollingList fieldList;
    private final Map<BlockPos, DraftField> drafts = new HashMap<>();
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
        registerButton(SAVE_CHANGES, this::saveChanges);
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

        final Text ccu = findPaneOfTypeByID(BALANCE_TITLE, Text.class);
        ccu.setHoverPane(null);
        PaneBuilders.tooltipBuilder()
            .append(Component.translatable("com.greenhousegardener.core.gui.biome.balance.tooltip"))
            .hoverPane(ccu)
            .build();

        updateBalances();
        updateFieldSummary();
        updateFields();
        updateSaveButton();
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
        updateSaveButton();
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
            draftedOwnedFieldCount(),
            moduleView.getSupportedFieldCount(),
            draftedModifiedBiomeCount(),
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
        final DraftField draft = field == null ? null : draftFor(field);
        if (field == null || draft == null || updatingFields || !draft.owned() || draft.temperature() == temperature)
        {
            return;
        }

        setAssignment(field, temperature, draft.humidity());
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
        final DraftField draft = field == null ? null : draftFor(field);
        if (field == null || draft == null || updatingFields || !draft.owned() || draft.humidity() == humidity)
        {
            return;
        }

        setAssignment(field, draft.temperature(), humidity);
    }

    /**
     * Stores a new draft assignment locally and refreshes the visible rows.
     *
     * @param field field row being changed
     * @param temperature selected temperature setting
     * @param humidity selected humidity setting
     */
    private void setAssignment(final FieldBiomeView field, final TemperatureSetting temperature, final HumiditySetting humidity)
    {
        updateDraft(field, temperature, humidity, true);
        updateFieldSummary();
        updateFields();
        updateSaveButton();
    }

    /**
     * Stores a new ownership draft locally and refreshes the visible rows.
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

        final DraftField draft = draftFor(field);
        updateDraft(field, draft.temperature(), draft.humidity(), owned);
        updateFieldSummary();
        updateFields();
        updateSaveButton();
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
            final DraftField draft = draftFor(field);
            final DropDownList tempDropdown = row.findPaneOfTypeByID(FIELD_TEMPERATURE, DropDownList.class);
            final DropDownList humidityDropdown = row.findPaneOfTypeByID(FIELD_HUMIDITY, DropDownList.class);
            tempDropdown.setDataProvider(new TemperatureDataProvider());
            humidityDropdown.setDataProvider(new HumidityDataProvider());
            setSelectedDropdownIndex(tempDropdown, draft.temperature().ordinal());
            setSelectedDropdownIndex(humidityDropdown, draft.humidity().ordinal());
            tempDropdown.setEnabled(draft.owned());
            humidityDropdown.setEnabled(draft.owned());
            tempDropdown.setHandler(dropdown -> setTemperature(fieldIndex, TemperatureSetting.values()[dropdown.getSelectedIndex()]));
            humidityDropdown.setHandler(dropdown -> setHumidity(fieldIndex, HumiditySetting.values()[dropdown.getSelectedIndex()]));

            final CheckBox ownedCheckbox = row.findPaneOfTypeByID(FIELD_OWNED, CheckBox.class);
            ownedCheckbox.setChecked(draft.owned());
            ownedCheckbox.setEnabled(draft.owned() || canClaimMoreFields());
            ownedCheckbox.setHandler(button -> setOwnership(fieldIndex, ownedCheckbox.isChecked()));

            final ItemIcon seedIcon = row.findPaneOfTypeByID(FIELD_SEED, ItemIcon.class);
            seedIcon.setItem(field.seed().isEmpty() ? UNSET_FIELD_SEED_ICON : field.seed());

            final Button highlightButton = ensureFieldHighlightButton(row);
            if (highlightButton != null)
            {
                highlightButton.setHandler(button -> highlightField(field.position()));
                addPositionTooltip(highlightButton, field);
            }

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
     * Adds or reuses an invisible clickable overlay for the field icon in a row.
     *
     * @param row field row pane
     * @return clickable overlay button, or null if the row cannot contain children
     */
    private static Button ensureFieldHighlightButton(final Pane row)
    {
        final Pane existing = row.findPaneByID(FIELD_HIGHLIGHT);
        if (existing instanceof Button button)
        {
            return button;
        }

        if (!(row instanceof View view))
        {
            return null;
        }

        final Button button = new InvisibleButton();
        button.setID(FIELD_HIGHLIGHT);
        button.setPosition(4, 5);
        button.setSize(16, 16);
        view.addChild(button);
        return button;
    }

    /**
     * Ask the server to run the same field highlight command used by chat links.
     *
     * @param fieldPosition field anchor position
     */
    @SuppressWarnings("null")
    private static void highlightField(final BlockPos fieldPosition)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener listener = minecraft.getConnection();

        if (fieldPosition == null || listener == null)
        {
            return;
        }

        final String command = ModCommands.highlightFieldCommand(fieldPosition);
        listener.sendCommand(command.startsWith("/") ? command.substring(1) : command);
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
     * Finds the field view for a field position.
     *
     * @param fieldPosition field position to match
     * @return matching field view, or null when no visible field matches
     */
    private FieldBiomeView getField(final BlockPos fieldPosition)
    {
        if (fieldPosition == null)
        {
            return null;
        }

        for (final FieldBiomeView field : moduleView.getFields())
        {
            if (field.position().equals(fieldPosition))
            {
                return field;
            }
        }
        return null;
    }

    /**
     * Check whether another unowned field can be claimed at this building level.
     *
     * @return true when the client-side owned count is below the supported field cap
     */
    private boolean canClaimMoreFields()
    {
        return draftedOwnedFieldCount() < moduleView.getSupportedFieldCount();
    }

    /**
     * Save all drafted field changes as one server-validated update.
     */
    private void saveChanges()
    {
        if (drafts.isEmpty())
        {
            return;
        }

        if (draftedModifiedBiomeCount() > moduleView.getModifiedBiomeLimit())
        {
            showBiomeLimitMessage();
            return;
        }

        final List<FieldChange> changes = new ArrayList<>();
        for (final Map.Entry<BlockPos, DraftField> entry : drafts.entrySet())
        {
            final DraftField draft = entry.getValue();
            changes.add(new FieldChange(
                entry.getKey(),
                draft.temperature().ordinal(),
                draft.humidity().ordinal(),
                draft.owned()));
        }

        new SaveGreenhouseBiomeFieldsMessage(buildingView.getPosition(), changes).sendToServer();
        applyDraftsToView();
        drafts.clear();
        showActionbarMessage(BIOME_CHANGES_SAVED);
        updateFieldSummary();
        updateFields();
        updateSaveButton();
    }

    /**
     * Update the save button enabled state.
     */
    private void updateSaveButton()
    {
        final Button saveButton = findPaneOfTypeByID(SAVE_CHANGES, Button.class);
        saveButton.setEnabled(!drafts.isEmpty());
    }

    /**
     * Show a middle-bottom actionbar warning when the drafted save would exceed the biome limit.
     */
    private static void showBiomeLimitMessage()
    {
        showActionbarMessage(BIOME_LIMIT_REACHED);
    }

    /**
     * Show a translated middle-bottom actionbar message.
     *
     * @param translationKey message translation key
     */
    @SuppressWarnings("null")
    private static void showActionbarMessage(final String translationKey)
    {
        final LocalPlayer player = Minecraft.getInstance().player;

        if (player != null)
        {
            player.displayClientMessage(Component.translatable(translationKey), true);
        }
    }

    /**
     * Mirror accepted draft state into the local module view while the server processes the save.
     */
    private void applyDraftsToView()
    {
        for (final Map.Entry<BlockPos, DraftField> entry : drafts.entrySet())
        {
            final DraftField draft = entry.getValue();
            final FieldBiomeView field = getField(entry.getKey());
            if (field != null && field.owned() != draft.owned())
            {
                moduleView.setFieldOwned(entry.getKey(), draft.owned());
            }
            if (draft.owned())
            {
                moduleView.setFieldAssignment(entry.getKey(), draft.temperature(), draft.humidity());
            }
        }
    }

    /**
     * Update or clear a draft entry for a field.
     *
     * @param field field row being drafted
     * @param temperature selected temperature setting
     * @param humidity selected humidity setting
     * @param owned selected ownership state
     */
    private void updateDraft(final FieldBiomeView field, final TemperatureSetting temperature, final HumiditySetting humidity, final boolean owned)
    {
        if (field == null)
        {
            return;
        }

        final DraftField draft = new DraftField(temperature, humidity, owned);
        final BlockPos position = field.position();
        if (draft.equals(baseDraft(field)))
        {
            drafts.remove(position);
            return;
        }

        drafts.put(position, draft);
    }

    /**
     * Resolve the visible draft state for a field.
     *
     * @param field field row to inspect
     * @return drafted state or the server-backed base state
     */
    private DraftField draftFor(final FieldBiomeView field)
    {
        return drafts.getOrDefault(field.position(), baseDraft(field));
    }

    /**
     * Build the server-backed base draft for a field.
     *
     * @param field field row to inspect
     * @return current server-backed state
     */
    private static DraftField baseDraft(final FieldBiomeView field)
    {
        return new DraftField(field.temperature(), field.humidity(), field.owned());
    }

    /**
     * Count client-visible owned fields after applying drafts.
     *
     * @return drafted owned field count
     */
    private int draftedOwnedFieldCount()
    {
        int count = 0;
        for (final FieldBiomeView field : moduleView.getFields())
        {
            if (draftFor(field).owned())
            {
                count++;
            }
        }
        return count;
    }

    /**
     * Count distinct modified biome variations after applying drafts.
     *
     * @return drafted modified-biome variation count
     */
    private int draftedModifiedBiomeCount()
    {
        final List<DraftClimate> climates = new ArrayList<>();
        for (final FieldBiomeView field : moduleView.getFields())
        {
            final DraftField draft = draftFor(field);
            if (draft.owned() && isModified(field, draft.temperature(), draft.humidity()))
            {
                final DraftClimate climate = new DraftClimate(draft.temperature(), draft.humidity());
                if (!climates.contains(climate))
                {
                    climates.add(climate);
                }
            }
        }
        return climates.size();
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
     * Client-side unsaved field state.
     */
    private record DraftField(TemperatureSetting temperature, HumiditySetting humidity, boolean owned)
    {
    }

    /**
     * Distinct greenhouse climate pair used for draft limit counting.
     */
    private record DraftClimate(TemperatureSetting temperature, HumiditySetting humidity)
    {
    }

    /**
     * Click target layered over the field item icon without adding any rendered chrome.
     */
    private static final class InvisibleButton extends ButtonImage
    {
        @Override
        public void drawSelf(final BOGuiGraphics graphics, final double mx, final double my)
        {
            // Intentionally invisible; the ItemIcon below provides the visuals.
        }
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
