package com.deathfrog.greenhousegardener.api.sounds;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.core.colony.buildings.jobs.ModJobs;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.util.Tuple;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.*;
import javax.annotation.Nonnull;

/**
 * Registering of sound events for our colony.
 */
public final class ModSoundEvents
{

    @SuppressWarnings("null")
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
        DeferredRegister.create(Registries.SOUND_EVENT, GreenhouseGardenerMod.MODID);
    public static Map<String, Map<EventType, List<Tuple<SoundEvent, SoundEvent>>>> GG_SOUND_EVENTS = new HashMap<>(); 

    /**
     * Private constructor to hide the implicit public one.
     */
    private ModSoundEvents()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Register the {@link SoundEvent}s. Note that this implementation adds the sound events to the MineColonies list of
     * CITIZEN_SOUND_EVENTS as well. Not preferable, but required.
     *
     * @param registry the registry to register at.
     */
    static
    {
        final List<ResourceLocation> jobList = new ArrayList<>(ModJobs.getJobs());

        registerSoundsForJobs(GreenhouseGardenerMod.MODID, jobList, SOUND_EVENTS, GG_SOUND_EVENTS);
    }

    /**
     * Registers the sound events for the given jobs.
     *
     * @param jobs a list of {@link ResourceLocation}s, which represent the jobs for which the sound events should be registered.
     */
    public static void registerSoundsForJobs(final @Nonnull String modID,
        final List<ResourceLocation> jobs,
        DeferredRegister<SoundEvent> soundEventRegister,
        Map<String, Map<EventType, List<Tuple<SoundEvent, SoundEvent>>>> soundMap)
    {
        for (final ResourceLocation job : jobs)
        {
            final Map<EventType, List<Tuple<SoundEvent, SoundEvent>>> map = new HashMap<>();
            for (final EventType event : EventType.values())
            {
                final List<Tuple<SoundEvent, SoundEvent>> individualSounds = new ArrayList<>();
                for (int i = 1; i <= 4; i++)
                {
                    // MCTradePostMod.LOGGER.info("Registering sound event: " + ModSoundEvents.CITIZEN_SOUND_EVENT_PREFIX +
                    // job.getPath() + ".genderplaceholder." + event.getId());

                    final SoundEvent maleSoundEvent = ModSoundEvents.getSoundID(modID,
                        com.minecolonies.api.sounds.ModSoundEvents.CITIZEN_SOUND_EVENT_PREFIX + job.getPath() + ".male" + i + "." + event.getId());
                    final SoundEvent femaleSoundEvent = ModSoundEvents.getSoundID(modID,
                        com.minecolonies.api.sounds.ModSoundEvents.CITIZEN_SOUND_EVENT_PREFIX + job.getPath() + ".female" + i + "." + event.getId());

                    String maleSoundPath = maleSoundEvent.getLocation().getPath();

                    if (maleSoundPath != null)
                    {
                        soundEventRegister.register(maleSoundPath, () -> maleSoundEvent);
                    }

                    String femaleSoundPath = femaleSoundEvent.getLocation().getPath();

                    if (femaleSoundPath != null)
                    {
                        soundEventRegister.register(femaleSoundPath, () -> femaleSoundEvent);
                    }

                    individualSounds.add(new Tuple<>(maleSoundEvent, femaleSoundEvent));
                }
                map.put(event, individualSounds);
            }
            soundMap.put(job.getPath(), map);
        }
    }

    /**
     * Register a {@link SoundEvent}.
     *
     * @param soundName The SoundEvent's name without the minecolonies prefix
     * @return The SoundEvent
     */
    public static @Nonnull SoundEvent getSoundID(final @Nonnull String modID, final @Nonnull String soundName)
    {
        final ResourceLocation location = ResourceLocation.fromNamespaceAndPath(modID, soundName);

        if (location == null)
        {
            throw new IllegalArgumentException("No resource location found for sound name: " + soundName);
        }

        SoundEvent sound = SoundEvent.createVariableRangeEvent(location);

        if (sound == null)
        {
            throw new IllegalArgumentException("No sound event found for sound: " + location.toString());
        }

        return sound;
    }

    /**
     * Injects the citizen sound events from MCTradePost into MineColonies' CITIZEN_SOUND_EVENTS. This is a temporary solution until
     * sounds in MineColonies have the flexibility to look up sound events from other modpacks.
     */
    public static void injectSounds()
    {
        if (GG_SOUND_EVENTS.isEmpty())
        {
            GreenhouseGardenerMod.LOGGER.info("There are no sounds to inject.");
        }
        else
        {
            int size = GG_SOUND_EVENTS.size();
            GreenhouseGardenerMod.LOGGER.info("Injecting {} sound events.", size);
            com.minecolonies.api.sounds.ModSoundEvents.CITIZEN_SOUND_EVENTS.putAll(GG_SOUND_EVENTS);
        }
    }
}
