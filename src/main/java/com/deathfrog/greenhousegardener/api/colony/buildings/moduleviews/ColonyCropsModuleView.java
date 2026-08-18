package com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.greenhousegardener.core.client.gui.modules.WindowColonyCropsModule;
import com.deathfrog.greenhousegardener.core.colony.crops.CropFieldSnapshot;
import com.deathfrog.greenhousegardener.core.colony.crops.CropFieldSnapshotCodec;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** Client-side data for the colony-wide crop-field overview. */
public class ColonyCropsModuleView extends AbstractBuildingModuleView
{
    private List<CropFieldSnapshot> fields = List.of();

    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf)
    {
        fields = CropFieldSnapshotCodec.readList(buf);
    }

    @Override
    public @Nullable Component getDesc()
    {
        return Component.translatable("com.greenhousegardener.core.gui.modules.colony_crops");
    }

    @Override
    public BOWindow getWindow()
    {
        return new WindowColonyCropsModule(this);
    }

    @Override
    public String getIcon()
    {
        return "field";
    }

    public List<CropFieldSnapshot> getFields()
    {
        return List.copyOf(fields);
    }

}
