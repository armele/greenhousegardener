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

    public static final ConfigValue<String>  coldDry;
    public static final ConfigValue<String>  coldNormal;
    public static final ConfigValue<String>  coldHumid;
    public static final ConfigValue<String>  temperateDry;
    public static final ConfigValue<String>  temperateNormal;
    public static final ConfigValue<String>  temperateHumid;
    public static final ConfigValue<String>  hotDry;
    public static final ConfigValue<String>  hotNormal;
    public static final ConfigValue<String>  hotHumid;

    public static final ConfigValue<Integer>  baseConversionCost;
    public static final ConfigValue<Integer>  baseBiomeCount;

    static
    {
            // Notifications
        BUILDER.push("reference biomes");
        coldDry =       BUILDER.comment("Cold and dry").define("coldDry", "minecraft:snowy_slopes");
        coldNormal =    BUILDER.comment("Cold normal").define("coldNormal", "minecraft:snowy_plains");
        coldHumid =     BUILDER.comment("Cold and humid").define("coldHumid", "minecraft:old_growth_pine_taiga");
        temperateDry =       BUILDER.comment("Temperate and dry").define("temperateDry", "minecraft:savanna");
        temperateNormal =    BUILDER.comment("Temperate normal").define("temperateNormal", "minecraft:plains");
        temperateHumid =     BUILDER.comment("Temperate and humid").define("temperateHumid", "minecraft:swamp");
        hotDry =       BUILDER.comment("Hot and dry").define("hotDry", "minecraft:desert");
        hotNormal =    BUILDER.comment("Hot normal").define("hotNormal", "minecraft:sparse_jungle");
        hotHumid =     BUILDER.comment("Hot and humid").define("hotHumid", "minecraft:jungle");
        BUILDER.pop();

        BUILDER.push("balance");
        baseConversionCost = BUILDER.comment("Conversion cost").defineInRange("baseConversionCost", 1, 1, 10);
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
