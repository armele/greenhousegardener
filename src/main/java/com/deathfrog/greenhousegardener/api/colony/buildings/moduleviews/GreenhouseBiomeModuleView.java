package com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.greenhousegardener.core.client.gui.modules.WindowBiomeModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.HumiditySetting;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.TemperatureSetting;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class GreenhouseBiomeModuleView  extends AbstractBuildingModuleView
{
    private int supportedFieldCount;
    private int ownedFieldCount;
    private int modifiedBiomeLimit;
    private int modifiedBiomeCount;
    private int hotBalance;
    private int coldBalance;
    private int humidBalance;
    private int dryBalance;
    private final List<FieldBiomeView> fields = new ArrayList<>();

    @Override
    public void deserialize(@NotNull RegistryFriendlyByteBuf buf)
    {
        supportedFieldCount = buf.readInt();
        ownedFieldCount = buf.readInt();
        modifiedBiomeLimit = buf.readInt();
        modifiedBiomeCount = buf.readInt();
        hotBalance = buf.readInt();
        coldBalance = buf.readInt();
        humidBalance = buf.readInt();
        dryBalance = buf.readInt();
        fields.clear();

        final int fieldCount = buf.readInt();
        for (int i = 0; i < fieldCount; i++)
        {
            fields.add(new FieldBiomeView(
                buf.readInt(),
                buf.readBlockPos(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                buf.readEnum(TemperatureSetting.class),
                buf.readEnum(HumiditySetting.class),
                buf.readEnum(TemperatureSetting.class),
                buf.readEnum(HumiditySetting.class),
                buf.readBoolean()));
        }
    }

    @Override
    public @Nullable Component getDesc()
    {
        return Component.translatable("com.greenhousegardener.core.gui.modules.biome_settings");
    }

    @Override
    public BOWindow getWindow()
    {
        return new WindowBiomeModule(this);
    }

    /**
     * Get the icon of the module.
     * 
     * @return the icon to show.
     */
    @Override
    public String getIcon()
    {
        return "greenhouse";
    }

    public int getSupportedFieldCount()
    {
        return supportedFieldCount;
    }

    public int getOwnedFieldCount()
    {
        return ownedFieldCount;
    }

    public int getModifiedBiomeLimit()
    {
        return modifiedBiomeLimit;
    }

    public int getModifiedBiomeCount()
    {
        return modifiedBiomeCount;
    }

    public int getHotBalance()
    {
        return hotBalance;
    }

    public int getColdBalance()
    {
        return coldBalance;
    }

    public int getHumidBalance()
    {
        return humidBalance;
    }

    public int getDryBalance()
    {
        return dryBalance;
    }

    public List<FieldBiomeView> getFields()
    {
        return fields;
    }

    /**
     * Update the client-side climate assignment for a visible field row.
     *
     * @param fieldPosition position of the farm field anchor
     * @param temperature selected temperature setting
     * @param humidity selected humidity setting
     */
    public void setFieldAssignment(final BlockPos fieldPosition, final TemperatureSetting temperature, final HumiditySetting humidity)
    {
        for (int i = 0; i < fields.size(); i++)
        {
            final FieldBiomeView field = fields.get(i);
            if (field.position().equals(fieldPosition))
            {
                fields.set(i, new FieldBiomeView(
                    field.fieldIndex(),
                    field.position(),
                    field.seed(),
                    temperature,
                    humidity,
                    field.naturalTemperature(),
                    field.naturalHumidity(),
                    field.owned()));
                return;
            }
        }
    }

    /**
     * Update the client-side ownership state for a visible field row.
     *
     * @param fieldPosition position of the farm field anchor
     * @param owned true when this greenhouse owns the field
     */
    public void setFieldOwned(final BlockPos fieldPosition, final boolean owned)
    {
        for (int i = 0; i < fields.size(); i++)
        {
            final FieldBiomeView field = fields.get(i);
            if (field.position().equals(fieldPosition))
            {
                fields.set(i, new FieldBiomeView(
                    field.fieldIndex(),
                    field.position(),
                    field.seed(),
                    field.temperature(),
                    field.humidity(),
                    field.naturalTemperature(),
                    field.naturalHumidity(),
                    owned));
                ownedFieldCount += owned ? 1 : -1;
                return;
            }
        }
    }

    public record FieldBiomeView(
        int fieldIndex,
        BlockPos position,
        ItemStack seed,
        TemperatureSetting temperature,
        HumiditySetting humidity,
        TemperatureSetting naturalTemperature,
        HumiditySetting naturalHumidity,
        boolean owned)
    {
    }

}
