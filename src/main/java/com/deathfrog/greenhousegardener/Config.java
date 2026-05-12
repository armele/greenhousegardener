package com.deathfrog.greenhousegardener;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;


public class Config 
{
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ConfigValue<Integer>  baseConversionCost;
    public static final ConfigValue<Integer>  baseMaintenanceCost;
    public static final ConfigValue<Integer>  maintenanceRevertDays;
    public static final ConfigValue<Integer>  maintenanceOverlayRepairPercent;
    public static final ConfigValue<Integer>  roofPercentage;
    public static final ConfigValue<Boolean>  ambientPoofsEnabled;
    public static final ConfigValue<Integer>  ambientPoofIntervalTicks;

    public static final ConfigValue<Boolean>  fieldReversionWarning;

    static
    {
        BUILDER.push("conversioncost");
        baseConversionCost = BUILDER.comment("Conversion cost (CCU)").defineInRange("baseConversionCost", 40, 1, 100);
        baseMaintenanceCost = BUILDER.comment("Maintenance cost (CCU)").defineInRange("baseMaintenanceCost", 1, 1, 100);
        BUILDER.pop();

        BUILDER.push("notifications");
        fieldReversionWarning = BUILDER.comment("Notify of pending field reversion.").define("fieldReversionWarning", true);
        BUILDER.pop();

        BUILDER.push("maintenance");
        maintenanceRevertDays = BUILDER.comment("Colony days a field can miss maintenance before reverting to its natural biome").defineInRange("maintenanceRevertDays", 5, 1, 30);
        maintenanceOverlayRepairPercent = BUILDER.comment("Maximum percent of loaded greenhouse biome cells that daily maintenance may repair before the field requires full conversion again").defineInRange("maintenanceOverlayRepairPercent", 15, 0, 100);
        BUILDER.pop();

        BUILDER.push("other");
        roofPercentage = BUILDER.comment("What percentage of the roof must be a greenhouse material?").defineInRange("roofPercentage", 75, 50, 100);
        ambientPoofsEnabled = BUILDER.comment("Whether maintained greenhouse fields occasionally emit ambient poof particles").define("ambientPoofsEnabled", true);
        ambientPoofIntervalTicks = BUILDER.comment("Ticks between ambient greenhouse poof pulses").defineInRange("ambientPoofIntervalTicks", 100, 20, 1200);
        BUILDER.pop();

        SPEC = BUILDER.build(); // Last

    }
    /**
     * Register the config with the given mod container.
     * @param modContainer The mod container to register the config with.
     */
    public static void register(ModContainer modContainer) 
    {
        LOGGER.info("Registering Greenhouse Gardener Mod to handle configurations.");
        modContainer.registerConfig(ModConfig.Type.SERVER, SPEC, "greenhousegardener-server.toml");
    }
}
