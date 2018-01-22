package mcjty.rftools.blocks.teleporter;

import mcjty.lib.base.StyleConfig;
import mcjty.lib.container.EmptyContainer;
import mcjty.lib.container.GenericGuiContainer;
import mcjty.lib.entity.GenericEnergyStorageTileEntity;
import mcjty.lib.gui.Window;
import mcjty.lib.gui.layout.HorizontalAlignment;
import mcjty.lib.gui.layout.HorizontalLayout;
import mcjty.lib.gui.layout.VerticalLayout;
import mcjty.lib.gui.widgets.Button;
import mcjty.lib.gui.widgets.*;
import mcjty.lib.gui.widgets.Label;
import mcjty.lib.gui.widgets.Panel;
import mcjty.lib.gui.widgets.TextField;
import mcjty.lib.network.Argument;
import mcjty.rftools.RFTools;
import mcjty.rftools.network.PacketGetPlayers;
import mcjty.rftools.network.RFToolsMessages;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.*;
import java.util.List;

public class GuiMatterReceiver extends GenericGuiContainer<MatterReceiverTileEntity> {
    public static final int MATTER_WIDTH = 180;
    public static final int MATTER_HEIGHT = 160;
    public static final String ACCESS_PRIVATE = "Private";
    public static final String ACCESS_PUBLIC = "Public";

    private EnergyBar energyBar;
    private ChoiceLabel privateSetting;
    private WidgetList allowedPlayers;
    private Button addButton;
    private Button delButton;
    private TextField nameField;

    // A copy of the players we're currently showing.
    private List<String> players = null;
    private int listDirty = 0;

    private static Set<String> fromServer_allowedPlayers = new HashSet<>();
    public static void storeAllowedPlayersForClient(List<String> players) {
        fromServer_allowedPlayers = new HashSet<>(players);
    }


    public GuiMatterReceiver(MatterReceiverTileEntity matterReceiverTileEntity, EmptyContainer container) {
        super(RFTools.instance, RFToolsMessages.INSTANCE, matterReceiverTileEntity, container, RFTools.GUI_MANUAL_MAIN, "tpreceiver");
        GenericEnergyStorageTileEntity.setCurrentRF(matterReceiverTileEntity.getEnergyStored());

        xSize = MATTER_WIDTH;
        ySize = MATTER_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();

        int maxEnergyStored = tileEntity.getMaxEnergyStored();
        energyBar = new EnergyBar(mc, this).setFilledRectThickness(1).setHorizontal().setDesiredHeight(12).setDesiredWidth(80).setMaxValue(maxEnergyStored).setShowText(false);
        energyBar.setValue(GenericEnergyStorageTileEntity.getCurrentRF());

        TextField textField = new TextField(mc, this).setTooltips("Use this name to", "identify this receiver", "in the dialer").addTextEvent((parent, newText) -> setReceiverName(newText));
        textField.setText(tileEntity.getName());
        Panel namePanel = new Panel(mc, this).setLayout(new HorizontalLayout()).addChild(new Label(mc, this).setText("Name:")).addChild(textField).setDesiredHeight(16);

        privateSetting = new ChoiceLabel(mc, this).addChoices(ACCESS_PUBLIC, ACCESS_PRIVATE).setDesiredHeight(14).setDesiredWidth(60).
                setChoiceTooltip(ACCESS_PUBLIC, "Everyone can dial to this receiver").
                setChoiceTooltip(ACCESS_PRIVATE, "Only people in the access list below", "can dial to this receiver").
                addChoiceEvent((parent, newChoice) -> changeAccessMode(newChoice));
        if (tileEntity.isPrivateAccess()) {
            privateSetting.setChoice(ACCESS_PRIVATE);
        } else {
            privateSetting.setChoice(ACCESS_PUBLIC);
        }
        Panel privatePanel = new Panel(mc, this).setLayout(new HorizontalLayout()).addChild(new Label(mc, this).setText("Access:")).addChild(privateSetting).setDesiredHeight(16);

        allowedPlayers = new WidgetList(mc, this);
        Slider allowedPlayerSlider = new Slider(mc, this).setDesiredWidth(10).setVertical().setScrollable(allowedPlayers);
        Panel allowedPlayersPanel = new Panel(mc, this).setLayout(new HorizontalLayout().setHorizontalMargin(3).setSpacing(1)).addChild(allowedPlayers).addChild(allowedPlayerSlider)
                .setFilledBackground(0xff9e9e9e);

        nameField = new TextField(mc, this);
        addButton = new Button(mc, this).setText("Add").setDesiredHeight(13).setDesiredWidth(34).setTooltips("Add a player to the access list").
                addButtonEvent(parent -> addPlayer());
        delButton = new Button(mc, this).setText("Del").setDesiredHeight(13).setDesiredWidth(34).setTooltips("Remove the selected player", "from the access list").
                addButtonEvent(parent -> delPlayer());
        Panel buttonPanel = new Panel(mc, this).setLayout(new HorizontalLayout()).addChild(nameField).addChild(addButton).addChild(delButton).setDesiredHeight(16);

        Panel toplevel = new Panel(mc, this).setFilledRectThickness(2).setLayout(new VerticalLayout().setHorizontalMargin(3).setVerticalMargin(3).setSpacing(1)).
                addChild(energyBar).addChild(namePanel).addChild(privatePanel).addChild(allowedPlayersPanel).addChild(buttonPanel);
        toplevel.setBounds(new Rectangle(guiLeft, guiTop, MATTER_WIDTH, MATTER_HEIGHT));
        window = new Window(this, toplevel);
        Keyboard.enableRepeatEvents(true);

        listDirty = 0;
        requestPlayers();
        tileEntity.requestRfFromServer(RFTools.MODID);
    }

    private void setReceiverName(String text) {
        sendServerCommand(RFToolsMessages.INSTANCE, MatterReceiverTileEntity.CMD_SETNAME, new Argument("name", text));
    }

    private void changeAccessMode(String newAccess) {
        sendServerCommand(RFToolsMessages.INSTANCE, MatterReceiverTileEntity.CMD_SETPRIVATE, new Argument("private", ACCESS_PRIVATE.equals(newAccess)));
    }

    private void addPlayer() {
        sendServerCommand(RFToolsMessages.INSTANCE, MatterReceiverTileEntity.CMD_ADDPLAYER, new Argument("player", nameField.getText()));
        listDirty = 0;
    }

    private void delPlayer() {
        sendServerCommand(RFToolsMessages.INSTANCE, MatterReceiverTileEntity.CMD_DELPLAYER, new Argument("player", players.get(allowedPlayers.getSelected())));
        listDirty = 0;
    }


    private void requestPlayers() {
        RFToolsMessages.INSTANCE.sendToServer(new PacketGetPlayers(tileEntity.getPos(), MatterReceiverTileEntity.CMD_GETPLAYERS, MatterReceiverTileEntity.CLIENTCMD_GETPLAYERS));
    }

    private void populatePlayers() {
        List<String> newPlayers = new ArrayList<>(fromServer_allowedPlayers);
        Collections.sort(newPlayers);
        if (newPlayers.equals(players)) {
            return;
        }

        players = new ArrayList<>(newPlayers);
        allowedPlayers.removeChildren();
        for (String player : players) {
            allowedPlayers.addChild(new Label(mc, this).setColor(StyleConfig.colorTextInListNormal).setText(player).setHorizontalAlignment(HorizontalAlignment.ALIGH_LEFT));
        }
    }

    private void requestListsIfNeeded() {
        listDirty--;
        if (listDirty <= 0) {
            requestPlayers();
            listDirty = 20;
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float v, int i, int i2) {
        requestListsIfNeeded();
        populatePlayers();
        enableButtons();

        drawWindow();
        int currentRF = GenericEnergyStorageTileEntity.getCurrentRF();
        energyBar.setValue(currentRF);
        tileEntity.requestRfFromServer(RFTools.MODID);
    }

    private void enableButtons() {
        boolean isPrivate = ACCESS_PRIVATE.equals(privateSetting.getCurrentChoice());
        allowedPlayers.setEnabled(isPrivate);
        nameField.setEnabled(isPrivate);

        int isPlayerSelected = allowedPlayers.getSelected();
        delButton.setEnabled(isPrivate && (isPlayerSelected != -1));
        String name = nameField.getText();
        addButton.setEnabled(isPrivate && name != null && !name.isEmpty());
    }
}
