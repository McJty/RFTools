package mcjty.rftools.blocks.monitor;

import mcjty.lib.base.StyleConfig;
import mcjty.lib.container.EmptyContainer;
import mcjty.lib.container.GenericGuiContainer;
import mcjty.lib.gui.Window;
import mcjty.lib.gui.events.DefaultSelectionEvent;
import mcjty.lib.gui.layout.HorizontalAlignment;
import mcjty.lib.gui.layout.HorizontalLayout;
import mcjty.lib.gui.layout.VerticalLayout;
import mcjty.lib.gui.widgets.*;
import mcjty.lib.varia.BlockPosTools;
import mcjty.lib.varia.BlockTools;
import mcjty.rftools.RFTools;
import mcjty.rftools.network.RFToolsMessages;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

public class GuiRFMonitor extends GenericGuiContainer<RFMonitorBlockTileEntity> {
    private WidgetList list;
    private ChoiceLabel alarmModeChoiceLabel;
    private ScrollableLabel alarmLabel;
    private int listDirty;

    public static final int TEXT_COLOR_SELECTED = 0xFFFFFF;

    // A copy of the adjacent blocks we're currently showing
    private List<BlockPos> adjacentBlocks = null;

    // From server.
    public static List<BlockPos> fromServer_clientAdjacentBlocks = null;


    public GuiRFMonitor(RFMonitorBlockTileEntity monitorBlockTileEntity, EmptyContainer container) {
        super(RFTools.instance, RFToolsMessages.INSTANCE, monitorBlockTileEntity, container, RFTools.GUI_MANUAL_MAIN, "monitor");

        xSize = 256;
        ySize = 180;
    }

    @Override
    public void initGui() {
        super.initGui();

        list = new WidgetList(mc, this).addSelectionEvent(new DefaultSelectionEvent() {
            @Override
            public void select(Widget parent, int index) {
                setSelectedBlock(index);
            }
        });
        listDirty = 0;
        Slider listSlider = new Slider(mc, this).setDesiredWidth(10).setVertical().setScrollable(list);
        Panel listPanel = new Panel(mc, this).setLayout(new HorizontalLayout().setHorizontalMargin(3).setSpacing(1)).addChild(list).addChild(listSlider);

        alarmModeChoiceLabel = new ChoiceLabel(mc, this).addChoices(
                RFMonitorMode.MODE_OFF.getDescription(), RFMonitorMode.MODE_LESS.getDescription(), RFMonitorMode.MODE_MORE.getDescription()).
                setDesiredWidth(60).setDesiredHeight(15).
                setTooltips("Control when a redstone", "signal should be sent").
                addChoiceEvent((parent, newChoice) -> changeAlarmMode(RFMonitorMode.getModeFromDescription(newChoice)));
        alarmModeChoiceLabel.setChoice(tileEntity.getAlarmMode().getDescription());

        alarmLabel = new ScrollableLabel(mc, this).setSuffix("%").setDesiredWidth(30).setRealMinimum(0).setRealMaximum(100).
                setRealValue(tileEntity.getAlarmLevel()).
                addValueEvent((parent, newValue) -> changeAlarmValue(newValue));
        Slider alarmSlider = new Slider(mc, this).
                setDesiredHeight(15).
                setHorizontal().
                setMinimumKnobSize(15).
                setTooltips("Alarm level").
                setScrollable(alarmLabel);
        Panel alarmPanel = new Panel(mc, this).setLayout(new HorizontalLayout()).addChild(alarmModeChoiceLabel).addChild(alarmSlider).addChild(alarmLabel).setDesiredHeight(20);

        Panel toplevel = new Panel(mc, this).setFilledRectThickness(2).setLayout(new VerticalLayout()).addChild(listPanel).addChild(alarmPanel);
        toplevel.setBounds(new Rectangle(guiLeft, guiTop, xSize, ySize));
        window = new Window(this, toplevel);

        fromServer_clientAdjacentBlocks = new ArrayList<>();
        RFToolsMessages.INSTANCE.sendToServer(new PacketGetAdjacentBlocks(tileEntity.getPos()));
    }

    private void changeAlarmMode(RFMonitorMode mode) {
        int alarmLevel = alarmLabel.getRealValue();
        tileEntity.setAlarm(mode, alarmLevel);
        RFToolsMessages.INSTANCE.sendToServer(new PacketContentsMonitor(tileEntity.getPos(), mode, alarmLevel));
    }

    private void changeAlarmValue(int newValue) {
        RFMonitorMode mode = RFMonitorMode.getModeFromDescription(alarmModeChoiceLabel.getCurrentChoice());
        tileEntity.setAlarm(mode, newValue);
        RFToolsMessages.INSTANCE.sendToServer(new PacketContentsMonitor(tileEntity.getPos(), mode, newValue));
    }

    private void refreshList() {
    }

    private void setSelectedBlock(int index) {
        if (index != -1) {
            BlockPos c = adjacentBlocks.get(index);
            tileEntity.setMonitor(c);
            RFToolsMessages.INSTANCE.sendToServer(new PacketContentsMonitor(tileEntity.getPos(), c));
        } else {
            tileEntity.setInvalid();
            RFToolsMessages.INSTANCE.sendToServer(new PacketContentsMonitor(tileEntity.getPos(), BlockPosTools.INVALID));
        }
    }

    private void populateList() {
        List<BlockPos> newAdjacentBlocks = fromServer_clientAdjacentBlocks;
        if (newAdjacentBlocks == null) {
            return;
        }
        if (newAdjacentBlocks.equals(adjacentBlocks)) {
            refreshList();
            return;
        }


        adjacentBlocks = new ArrayList<>(newAdjacentBlocks);
        list.removeChildren();

        int index = 0;
        int sel = -1;
        for (BlockPos coordinate : adjacentBlocks) {
            IBlockState state = mc.world.getBlockState(coordinate);
            Block block = state.getBlock();

            int color = StyleConfig.colorTextInListNormal;

            String displayName = BlockTools.getReadableName(mc.world, coordinate);

            if (coordinate.equals(tileEntity.getMonitor())) {
                sel = index;
                color = TEXT_COLOR_SELECTED;
            }

            Panel panel = new Panel(mc, this).setLayout(new HorizontalLayout());
            panel.addChild(new BlockRender(mc, this).setRenderItem(block));
            panel.addChild(new Label(mc, this).setText(displayName).setColor(color).setHorizontalAlignment(HorizontalAlignment.ALIGH_LEFT).setDesiredWidth(90));
            panel.addChild(new Label(mc, this).setDynamic(true).setText(BlockPosTools.toString(coordinate)).setColor(color));
            list.addChild(panel);

            index++;
        }

        list.setSelected(sel);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float v, int i, int i2) {
        listDirty--;
        if (listDirty <= 0) {
            populateList();
            listDirty = 5;
        }

        drawWindow();
    }
}
