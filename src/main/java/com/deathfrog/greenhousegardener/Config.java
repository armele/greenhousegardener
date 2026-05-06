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
    public static final ConfigValue<Integer>  baseBiomeCount;

    public static final ConfigValue<Integer>  climateControlUnitsLow;
    public static final ConfigValue<Integer>  climateControlUnitsMedium;
    public static final ConfigValue<Integer>  climateControlUnitsHigh;

    static
    {
        BUILDER.push("balance");
        climateControlUnitsLow = BUILDER.comment("Climate Control Unit Value (CCU) - Low ").defineInRange("climateControlUnitsLow", 1, 1, 55);
        climateControlUnitsMedium = BUILDER.comment("Climate Control Unit Value (CCU) - Medium ").defineInRange("climateControlUnitsMedium", 3, 1, 55);
        climateControlUnitsHigh = BUILDER.comment("Climate Control Unit Value (CCU) - High ").defineInRange("climateControlUnitsHigh", 7, 1, 55);
        baseConversionCost = BUILDER.comment("Conversion cost (CCU)").defineInRange("baseConversionCost", 6, 1, 10);
        baseMaintenanceCost = BUILDER.comment("Maintenance cost (CCU)").defineInRange("baseMaintenanceCost", 2, 1, 10);
        maintenanceRevertDays = BUILDER.comment("Colony days a field can miss maintenance before reverting to its natural biome").defineInRange("maintenanceRevertDays", 3, 1, 30);
        baseBiomeCount = BUILDER.comment("Base custom biomes").defineInRange("baseBiomeCount", 2, 1, 4);
        BUILDER.pop();

        SPEC = BUILDER.build(); // Last

    }
    /**
     * Register the config with the given mod container.
     * @param modContainer The mod container to register the config with.
     */
    public static void register(ModContainer modContainer) {
        LOGGER.info("Registering Salvation Mod to handle configurations.");
        modContainer.registerConfig(ModConfig.Type.SERVER, SPEC, "salvation-server.toml");
    }
}
