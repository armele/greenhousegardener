package com.deathfrog.greenhousegardener.core.client.gui.modules;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseBiomeModuleView;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.resources.ResourceLocation;

/**
 * Workshop module window styled after the MineColonies recipe teaching UI.
 */
public class WindowBiomeModule extends AbstractModuleWindow<GreenhouseBiomeModuleView>
{


    public WindowBiomeModule(final GreenhouseBiomeModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, "gui/layouthuts/layoutbiomemodule.xml"));
    }
}
