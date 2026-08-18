package com.deathfrog.greenhousegardener.core.client.gui.modules;

import java.util.List;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.core.colony.crops.CropFieldSnapshot;
import com.deathfrog.greenhousegardener.core.network.RequestCropJournalMessage;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

/** Standalone Colony Crops window opened from a bound crop journal. */
public class WindowCropJournal extends AbstractWindowSkeleton
{
    private static final int REFRESH_TICKS = 100;
    private static InteractionHand pendingHand = InteractionHand.MAIN_HAND;
    private static WindowCropJournal active;
    private static boolean awaitingOpen;

    private final ColonyId colonyId;
    private final BlockPos greenhousePosition;
    private final InteractionHand hand;
    private final IColonyView colony;
    private final ScrollingList fieldList;
    private List<CropFieldSnapshot> fields;
    private int refreshTicks;

    private WindowCropJournal(final ColonyId colonyId, final BlockPos greenhousePosition,
        final InteractionHand hand, final List<CropFieldSnapshot> fields)
    {
        super(ResourceLocation.fromNamespaceAndPath(
            GreenhouseGardenerMod.MODID, "gui/layouthuts/layoutcolonycropsmodule.xml"));
        this.colonyId = colonyId;
        this.greenhousePosition = greenhousePosition;
        this.hand = hand;
        this.fields = List.copyOf(fields);
        this.colony = IColonyManager.getInstance().getColonyView(colonyId.id(), colonyId.dimension());
        this.fieldList = findPaneOfTypeByID("fields", ScrollingList.class);
        findPaneOfTypeByID("desc", Text.class).setText(
            Component.translatable("com.greenhousegardener.core.gui.modules.colony_crops"));
    }

    /**
     * Send the initial request while remembering which hand holds the journal.
     *
     * @param hand hand holding the journal
     * @param greenhousePosition bound Greenhouse position
     */
    public static void request(final InteractionHand hand, final BlockPos greenhousePosition)
    {
        pendingHand = hand;
        awaitingOpen = true;
        new RequestCropJournalMessage(hand, greenhousePosition).sendToServer();
    }

    /**
     * Open a journal window or update the matching one with a newer snapshot.
     *
     * @param colonyId bound colony identity
     * @param greenhousePosition bound Greenhouse position
     * @param fields synchronized crop fields
     */
    public static void acceptSnapshot(final ColonyId colonyId, final BlockPos greenhousePosition,
        final List<CropFieldSnapshot> fields)
    {
        if (active != null && active.colonyId.equals(colonyId)
            && active.greenhousePosition.equals(greenhousePosition))
        {
            active.fields = List.copyOf(fields);
            active.refresh();
            return;
        }
        if (!awaitingOpen)
        {
            return;
        }
        awaitingOpen = false;
        active = new WindowCropJournal(colonyId, greenhousePosition, pendingHand, fields);
        active.open();
    }

    @Override
    public void onOpened()
    {
        super.onOpened();
        refresh();
    }

    @Override
    public void onUpdate()
    {
        super.onUpdate();
        if (++refreshTicks >= REFRESH_TICKS)
        {
            refreshTicks = 0;
            new RequestCropJournalMessage(hand, greenhousePosition).sendToServer();
        }
    }

    @Override
    public void onClosed()
    {
        super.onClosed();
        if (active == this)
        {
            active = null;
        }
        awaitingOpen = false;
    }

    /** Rebind the shared Colony Crops renderer to the latest journal snapshot. */
    private void refresh()
    {
        WindowColonyCropsModule.refresh(this, fieldList, fields, colony);
    }
}
