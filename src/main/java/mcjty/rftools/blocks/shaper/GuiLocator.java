package mcjty.rftools.blocks.shaper;

import mcjty.lib.container.EmptyContainer;
import mcjty.lib.container.GenericGuiContainer;
import mcjty.lib.entity.GenericEnergyStorageTileEntity;
import mcjty.lib.gui.Window;
import mcjty.lib.gui.layout.HorizontalAlignment;
import mcjty.lib.gui.layout.PositionalLayout;
import mcjty.lib.gui.widgets.*;
import mcjty.lib.network.Argument;
import mcjty.lib.network.Arguments;
import mcjty.lib.varia.RedstoneMode;
import mcjty.rftools.CommandHandler;
import mcjty.rftools.RFTools;
import mcjty.rftools.network.RFToolsMessages;
import mcjty.rftools.shapes.BeaconType;
import net.minecraft.util.ResourceLocation;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GuiLocator extends GenericGuiContainer<LocatorTileEntity> {

    private static final int LOCATOR_WIDTH = 256;
    private static final int LOCATOR_HEIGHT = 238;

    private static final ResourceLocation iconLocation = new ResourceLocation(RFTools.MODID, "textures/gui/locator.png");
    private static final ResourceLocation iconGuiElements = new ResourceLocation(RFTools.MODID, "textures/gui/guielements.png");

    private EnergyBar energyBar;
    private ImageChoiceLabel redstoneMode;

    private ColorChoiceLabel hostile;
    private ColorChoiceLabel passive;
    private ColorChoiceLabel player;
    private ToggleButton hostileBeacon;
    private ToggleButton passiveBeacon;
    private ToggleButton playerBeacon;

    private TextField filter;

    private ColorChoiceLabel energy;
    private ToggleButton energyBeacon;
    private TextField minEnergy;
    private TextField maxEnergy;

    private Label energyLabel;

    public static int energyConsumption = 0;

    public GuiLocator(LocatorTileEntity tileEntity, EmptyContainer container) {
        super(RFTools.instance, RFToolsMessages.INSTANCE, tileEntity, container, RFTools.GUI_MANUAL_SHAPE, "locator");

        xSize = LOCATOR_WIDTH;
        ySize = LOCATOR_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();

        Panel toplevel = new Panel(mc, this).setBackground(iconLocation).setLayout(new PositionalLayout());

        int maxEnergyStored = tileEntity.getMaxEnergyStored();
        energyBar = new EnergyBar(mc, this).setHorizontal().setMaxValue(maxEnergyStored).setLayoutHint(new PositionalLayout.PositionalHint(28, 10, 70, 10)).setShowText(false);
        energyBar.setValue(GenericEnergyStorageTileEntity.getCurrentRF());
        toplevel.addChild(energyBar);

        initRedstoneMode();
        toplevel.addChild(redstoneMode);

        hostile = new ColorChoiceLabel(mc, this);
        hostileBeacon = new ToggleButton(mc, this);
        addBeaconSetting(toplevel, hostile, hostileBeacon, 30, "Hostile");
        hostile.setCurrentColor(tileEntity.getHostileType().getColor());
        hostileBeacon.setPressed(tileEntity.isHostileBeacon());

        passive = new ColorChoiceLabel(mc, this);
        passiveBeacon = new ToggleButton(mc, this);
        addBeaconSetting(toplevel, passive, passiveBeacon, 46, "Passive");
        passive.setCurrentColor(tileEntity.getPassiveType().getColor());
        passiveBeacon.setPressed(tileEntity.isPassiveBeacon());

        player = new ColorChoiceLabel(mc, this);
        playerBeacon = new ToggleButton(mc, this);
        addBeaconSetting(toplevel, player, playerBeacon, 62, "Player");
        player.setCurrentColor(tileEntity.getPlayerType().getColor());
        playerBeacon.setPressed(tileEntity.isPlayerBeacon());

        toplevel.addChild(new Label<>(mc, this)
                .setText("Filter")
                .setLayoutHint(new PositionalLayout.PositionalHint(8, 82, 40, 14)));
        filter = new TextField(mc, this);
        filter.setLayoutHint(new PositionalLayout.PositionalHint(50, 82, 90, 14));
        filter.setText(tileEntity.getFilter());
        filter.addTextEvent((parent, newText) -> update());
        toplevel.addChild(filter);

        energy = new ColorChoiceLabel(mc, this);
        energyBeacon = new ToggleButton(mc, this);
        addBeaconSetting(toplevel, energy, energyBeacon, 98, "Energy");
        energy.setCurrentColor(tileEntity.getEnergyType().getColor());
        energyBeacon.setPressed(tileEntity.isEnergyBeacon());

        toplevel.addChild(new Label<>(mc, this).setText("<").setLayoutHint(new PositionalLayout.PositionalHint(153, 98, 10, 14)));
        minEnergy = new TextField(mc, this).setLayoutHint(new PositionalLayout.PositionalHint(162, 98, 25, 14));
        minEnergy.setText(tileEntity.getMinEnergy() == null ? "" : Integer.toString(tileEntity.getMinEnergy()));
        minEnergy.addTextEvent((parent, newText) -> update());
        toplevel.addChild(new Label<>(mc, this).setText("%").setLayoutHint(new PositionalLayout.PositionalHint(187, 98, 10, 14)));
        toplevel.addChild(minEnergy);
        toplevel.addChild(new Label<>(mc, this).setText(">").setLayoutHint(new PositionalLayout.PositionalHint(205, 98, 10, 14)));
        maxEnergy = new TextField(mc, this).setLayoutHint(new PositionalLayout.PositionalHint(214, 98, 25, 14));
        maxEnergy.setText(tileEntity.getMaxEnergy() == null ? "" : Integer.toString(tileEntity.getMaxEnergy()));
        maxEnergy.addTextEvent((parent, newText) -> update());
        toplevel.addChild(maxEnergy);
        toplevel.addChild(new Label<>(mc, this).setText("%").setLayoutHint(new PositionalLayout.PositionalHint(238, 98, 10, 14)));

        toplevel.addChild(new Label<>(mc, this)
                .setColor(0x993300)
                .setHorizontalAlignment(HorizontalAlignment.ALIGH_LEFT)
                .setText("RF per scan (every " + ScannerConfiguration.ticksPerLocatorScan + " ticks):")
                .setLayoutHint(new PositionalLayout.PositionalHint(8, 186, 156, 14)));
        energyLabel = new Label(mc, this).setText("");
        energyLabel.setHorizontalAlignment(HorizontalAlignment.ALIGH_LEFT);
        energyLabel.setLayoutHint(new PositionalLayout.PositionalHint(8, 200, 156, 14));
        toplevel.addChild(energyLabel);

        toplevel.setBounds(new Rectangle(guiLeft, guiTop, xSize, ySize));

        window = new Window(this, toplevel);

        tileEntity.requestRfFromServer(RFTools.MODID);

        energyConsumption = 0;
    }

    private static final Map<Integer, BeaconType> COLOR_TO_TYPE = new HashMap<>();

    private void addBeaconSetting(Panel toplevel, ColorChoiceLabel choice, ToggleButton toggle, int y, String label) {
        toplevel.addChild(new Label<>(mc, this).setText(label).setLayoutHint(new PositionalLayout.PositionalHint(8, y, 40, 14)));
        choice.setLayoutHint(new PositionalLayout.PositionalHint(50, y, 30, 14));
        for (BeaconType type : BeaconType.VALUES) {
            choice.addColors(type.getColor());
            COLOR_TO_TYPE.put(type.getColor(), type);
        }
        choice.addChoiceEvent((parent, newColor) -> {
            update();
        });
        toplevel.addChild(choice);
        toggle.setCheckMarker(true).setText("Beacon");
        toggle.setLayoutHint(new PositionalLayout.PositionalHint(90, y, 60, 14));
        toggle.addButtonEvent(parent -> update());
        toplevel.addChild(toggle);
    }

    private void update() {
        List<Argument> arguments = new ArrayList<>();
        arguments.add(new Argument("hostile", COLOR_TO_TYPE.get(hostile.getCurrentColor()).getCode()));
        arguments.add(new Argument("passive", COLOR_TO_TYPE.get(passive.getCurrentColor()).getCode()));
        arguments.add(new Argument("player", COLOR_TO_TYPE.get(player.getCurrentColor()).getCode()));
        arguments.add(new Argument("energy", COLOR_TO_TYPE.get(energy.getCurrentColor()).getCode()));
        arguments.add(new Argument("hostileBeacon", hostileBeacon.isPressed()));
        arguments.add(new Argument("passiveBeacon", passiveBeacon.isPressed()));
        arguments.add(new Argument("playerBeacon", playerBeacon.isPressed()));
        arguments.add(new Argument("energyBeacon", energyBeacon.isPressed()));
        arguments.add(new Argument("filter", filter.getText()));
        if (!minEnergy.getText().trim().isEmpty()) {
            try {
                arguments.add(new Argument("minEnergy", Integer.parseInt(minEnergy.getText())));
            } catch (NumberFormatException e) {
            }
        }
        if (!maxEnergy.getText().trim().isEmpty()) {
            try {
                arguments.add(new Argument("maxEnergy", Integer.parseInt(maxEnergy.getText())));
            } catch (NumberFormatException e) {
            }
        }

        sendServerCommand(RFToolsMessages.INSTANCE, LocatorTileEntity.CMD_SETTINGS,
                arguments.toArray(new Argument[arguments.size()])
        );
    }

    private void initRedstoneMode() {
        redstoneMode = new ImageChoiceLabel(mc, this).
                addChoiceEvent((parent, newChoice) -> changeRedstoneMode()).
                addChoice(RedstoneMode.REDSTONE_IGNORED.getDescription(), "Redstone mode:\nIgnored", iconGuiElements, 0, 0).
                addChoice(RedstoneMode.REDSTONE_OFFREQUIRED.getDescription(), "Redstone mode:\nOff to activate", iconGuiElements, 16, 0).
                addChoice(RedstoneMode.REDSTONE_ONREQUIRED.getDescription(), "Redstone mode:\nOn to activate", iconGuiElements, 32, 0);
        redstoneMode.setLayoutHint(new PositionalLayout.PositionalHint(8, 10, 16, 16));
        redstoneMode.setCurrentChoice(tileEntity.getRSMode().ordinal());
    }

    private void changeRedstoneMode() {
        tileEntity.setRSMode(RedstoneMode.values()[redstoneMode.getCurrentChoiceIndex()]);
        sendServerCommand(RFToolsMessages.INSTANCE, LocatorTileEntity.CMD_MODE,
                new Argument("rs", RedstoneMode.values()[redstoneMode.getCurrentChoiceIndex()].getDescription()));
    }

    static int cnt = 10;

    @Override
    protected void drawGuiContainerBackgroundLayer(float v, int x, int y) {

        drawWindow();

        int currentRF = GenericEnergyStorageTileEntity.getCurrentRF();
        energyBar.setValue(currentRF);
        tileEntity.requestRfFromServer(RFTools.MODID);
        cnt--;
        if (cnt < 0) {
            cnt = 10;
            sendServerCommand(RFTools.MODID, CommandHandler.CMD_REQUEST_LOCATOR_ENERGY,
                    Arguments.builder().value(tileEntity.getPos()).build());
        }
        energyLabel.setText(energyConsumption + " RF");
    }

}
