package mcjty.rftools.blocks.storage;

import mcjty.lib.container.GenericGuiContainer;
import mcjty.lib.entity.GenericEnergyStorageTileEntity;
import mcjty.lib.gui.Window;
import mcjty.lib.gui.layout.PositionalLayout;
import mcjty.lib.gui.widgets.EnergyBar;
import mcjty.lib.gui.widgets.ImageChoiceLabel;
import mcjty.lib.gui.widgets.Panel;
import mcjty.lib.network.Argument;
import mcjty.rftools.RFTools;
import mcjty.rftools.network.RFToolsMessages;
import net.minecraft.util.ResourceLocation;

import java.awt.*;


public class GuiRemoteStorage extends GenericGuiContainer<RemoteStorageTileEntity> {
    public static final int STORAGE_WIDTH = 180;
    public static final int STORAGE_HEIGHT = 152;

    private static final ResourceLocation iconLocation = new ResourceLocation(RFTools.MODID, "textures/gui/remotestorage.png");
    private static final ResourceLocation guiElements = new ResourceLocation(RFTools.MODID, "textures/gui/guielements.png");

    private EnergyBar energyBar;
    private ImageChoiceLabel global[] = new ImageChoiceLabel[] { null, null, null, null };

    public GuiRemoteStorage(RemoteStorageTileEntity remoteStorageTileEntity, RemoteStorageContainer container) {
        super(RFTools.instance, RFToolsMessages.INSTANCE, remoteStorageTileEntity, container, RFTools.GUI_MANUAL_MAIN, "remotestor");

        xSize = STORAGE_WIDTH;
        ySize = STORAGE_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();

        int maxEnergyStored = tileEntity.getMaxEnergyStored();
        energyBar = new EnergyBar(mc, this).setVertical().setMaxValue(maxEnergyStored).setLayoutHint(new PositionalLayout.PositionalHint(10, 7, 8, 54)).setShowText(false);
        energyBar.setValue(GenericEnergyStorageTileEntity.getCurrentRF());

        Panel toplevel = new Panel(mc, this).setBackground(iconLocation).setLayout(new PositionalLayout()).addChild(energyBar);
        for (int i = 0 ; i < 4 ; i++) {
            global[i] = new ImageChoiceLabel(mc, this);
            final int finalI = i;
            global[i].addChoiceEvent((parent, newChoice) -> changeGlobal(finalI));
            global[i].addChoice("off" + i, "Cross-dimension access disabled", guiElements, 0, 32);
            global[i].addChoice("on" + i, "Cross-dimension access enabled", guiElements, 16, 32);
            global[i].setLayoutHint(new PositionalLayout.PositionalHint(i < 2 ? (43 - 18) : (120 - 18), (i % 2) == 0 ? 9 : 36, 16, 16));
            global[i].setCurrentChoice(tileEntity.isGlobal(i) ? 1 : 0);
            toplevel.addChild(global[i]);
        }

        toplevel.setBounds(new Rectangle(guiLeft, guiTop, xSize, ySize));

        window = new Window(this, toplevel);
    }

    private void changeGlobal(int index) {
        sendServerCommand(RFToolsMessages.INSTANCE, RemoteStorageTileEntity.CMD_SETGLOBAL,
                new Argument("index", index),
                new Argument("global", global[index].getCurrentChoiceIndex() == 1));
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float v, int i, int i2) {
        drawWindow();
        energyBar.setValue(GenericEnergyStorageTileEntity.getCurrentRF());
        tileEntity.requestRfFromServer(RFTools.MODID);
    }
}
