package com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.greenhousegardener.core.client.gui.modules.WindowBiomeModule;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

public class GreenhouseBiomeModuleView  extends AbstractBuildingModuleView
{


    @Override
    public void deserialize(@NotNull RegistryFriendlyByteBuf arg0)
    {
        // Authoritative workshop settings are delivered per-player after the window opens.
    }

    @Override
    public @Nullable Component getDesc()
    {
        return Component.translatable("com.greenhousegardener.core.gui.modules.workshop");
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

}
