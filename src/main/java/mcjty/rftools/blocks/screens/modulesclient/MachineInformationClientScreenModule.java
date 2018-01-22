package mcjty.rftools.blocks.screens.modulesclient;

import mcjty.lib.api.MachineInformation;
import mcjty.lib.gui.layout.HorizontalAlignment;
import mcjty.lib.gui.layout.HorizontalLayout;
import mcjty.lib.gui.layout.VerticalLayout;
import mcjty.lib.gui.widgets.*;
import mcjty.lib.varia.BlockPosTools;
import mcjty.rftools.api.screens.*;
import mcjty.rftools.api.screens.data.IModuleDataString;
import mcjty.rftools.blocks.screens.IModuleGuiChanged;
import mcjty.rftools.blocks.screens.modulesclient.helper.ScreenModuleGuiBuilder;
import mcjty.rftools.blocks.screens.modulesclient.helper.ScreenTextHelper;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public class    MachineInformationClientScreenModule implements IClientScreenModule<IModuleDataString> {

    private String line = "";
    private int labcolor = 0xffffff;
    private int txtcolor = 0xffffff;
    protected int dim = 0;
    protected BlockPos coordinate = BlockPosTools.INVALID;

    private ITextRenderHelper labelCache = new ScreenTextHelper();

    @Override
    public TransformMode getTransformMode() {
        return TransformMode.TEXT;
    }

    @Override
    public int getHeight() {
        return 10;
    }

    @Override
    public void render(IModuleRenderHelper renderHelper, FontRenderer fontRenderer, int currenty, IModuleDataString screenData, ModuleRenderInfo renderInfo) {
        GlStateManager.disableLighting();
        int xoffset;
        if (!line.isEmpty()) {
            labelCache.setup(line, 160, renderInfo);
            labelCache.renderText(0, currenty, labcolor, renderInfo);
            xoffset = 7 + 40;
        } else {
            xoffset = 7;
        }

        if ((!BlockPosTools.INVALID.equals(coordinate)) && screenData != null) {
            renderHelper.renderText(xoffset, currenty, txtcolor, renderInfo, screenData.get());
        } else {
            renderHelper.renderText(xoffset, currenty, 0xff0000, renderInfo, "<invalid>");
        }
    }

    @Override
    public void mouseClick(World world, int x, int y, boolean clicked) {

    }

    @Override
    public void createGui(IModuleGuiBuilder guiBuilder) {
        // @todo Hacky, solve this better
        ScreenModuleGuiBuilder screenModuleGuiBuilder = (ScreenModuleGuiBuilder) guiBuilder;
        Minecraft mc = Minecraft.getMinecraft();
        Gui gui = screenModuleGuiBuilder.getGui();
        NBTTagCompound currentData = screenModuleGuiBuilder.getCurrentData();
        IModuleGuiChanged moduleGuiChanged = screenModuleGuiBuilder.getModuleGuiChanged();

        Panel panel = new Panel(mc, gui).setLayout(new VerticalLayout());
        TextField textField = new TextField(mc, gui).setDesiredHeight(16).setTooltips("Text to use as label").addTextEvent((parent, newText) -> {
            currentData.setString("text", newText);
            moduleGuiChanged.updateData();
        });
        panel.addChild(textField);
        addColorPanel(mc, gui, currentData, moduleGuiChanged, panel);
        addOptionPanel(mc, gui, currentData, moduleGuiChanged, panel);
        addMonitorPanel(mc, gui, currentData, panel);

        if (currentData != null) {
            textField.setText(currentData.getString("text"));
        }

        screenModuleGuiBuilder.overridePanel(panel);
    }

    private void addOptionPanel(Minecraft mc, Gui gui, final NBTTagCompound currentData, final IModuleGuiChanged moduleGuiChanged, Panel panel) {
        Panel optionPanel = new Panel(mc, gui).setLayout(new HorizontalLayout()).setDesiredHeight(16);

        final Map<String,Integer> choiceToIndex = new HashMap<>();
        final ChoiceLabel tagButton = new ChoiceLabel(mc, gui).setDesiredHeight(16).setDesiredWidth(80);
        optionPanel.addChild(tagButton);

//        int dim = currentData.getInteger("monitordim");
        int x = currentData.getInteger("monitorx");
        int y = currentData.getInteger("monitory");
        int z = currentData.getInteger("monitorz");
        TileEntity tileEntity = mc.world.getTileEntity(new BlockPos(x, y, z));

        if (tileEntity instanceof MachineInformation) {
            int current = currentData.getInteger("monitorTag");
            MachineInformation information = (MachineInformation) tileEntity;
            String currentTag = null;
            for (int i = 0 ; i < information.getTagCount() ; i++) {
                String tag = information.getTagName(i);
                choiceToIndex.put(tag, i);
                tagButton.addChoices(tag);
                tagButton.setChoiceTooltip(tag, information.getTagDescription(i));
                if (current == i) {
                    currentTag = tag;
                }
            }
            if (currentTag != null) {
                tagButton.setChoice(currentTag);
            }
        }

        tagButton.addChoiceEvent((parent, newChoice) -> {
            String choice = tagButton.getCurrentChoice();
            Integer index = choiceToIndex.get(choice);
            if (index != null) {
                currentData.setInteger("monitorTag", index);
            }
            moduleGuiChanged.updateData();
        });


        panel.addChild(optionPanel);
    }

    private void addMonitorPanel(Minecraft mc, Gui gui, final NBTTagCompound currentData, Panel panel) {
        Panel monitorPanel = new Panel(mc, gui).setLayout(new HorizontalLayout()).
                setDesiredHeight(16);
        String monitoring;
        if (currentData.hasKey("monitorx")) {
            if (currentData.hasKey("monitordim")) {
                this.dim = currentData.getInteger("monitordim");
            } else {
                // Compatibility reasons
                this.dim = currentData.getInteger("dim");
            }
            World world = mc.player.getEntityWorld();
            if (dim == world.provider.getDimension()) {
                int x = currentData.getInteger("monitorx");
                int y = currentData.getInteger("monitory");
                int z = currentData.getInteger("monitorz");
                monitoring = currentData.getString("monitorname");
                Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
                monitorPanel.addChild(new BlockRender(mc, gui).setRenderItem(block)).setDesiredWidth(20);
                monitorPanel.addChild(new Label(mc, gui).setText(x + "," + y + "," + z).setHorizontalAlignment(HorizontalAlignment.ALIGH_LEFT).setDesiredWidth(150));
            } else {
                monitoring = "<unreachable>";
            }
        } else {
            monitoring = "<not set>";
        }
        panel.addChild(monitorPanel);
        panel.addChild(new Label(mc, gui).setText(monitoring));
    }

    private void addColorPanel(Minecraft mc, Gui gui, final NBTTagCompound currentData, final IModuleGuiChanged moduleGuiChanged, Panel panel) {
        ColorChoiceLabel labelColorSelector = addColorSelector(mc, gui, currentData, moduleGuiChanged, "color").setTooltips("Color for the label");
        ColorChoiceLabel txtColorSelector = addColorSelector(mc, gui, currentData, moduleGuiChanged, "txtcolor").setTooltips("Color for the text");
        Panel colorPanel = new Panel(mc, gui).setLayout(new HorizontalLayout()).
                addChild(new Label(mc, gui).setText("L:")).addChild(labelColorSelector).
                addChild(new Label(mc, gui).setText("Txt:")).addChild(txtColorSelector).
                setDesiredHeight(12);
        panel.addChild(colorPanel);
    }


    private ColorChoiceLabel addColorSelector(Minecraft mc, Gui gui, final NBTTagCompound currentData, final IModuleGuiChanged moduleGuiChanged, final String tagName) {
        ColorChoiceLabel colorChoiceLabel = new ColorChoiceLabel(mc, gui).addColors(0xffffff, 0xff0000, 0x00ff00, 0x0000ff, 0xffff00, 0xff00ff, 0x00ffff).setDesiredWidth(26).setDesiredHeight(14).addChoiceEvent((parent, newColor) -> {
            currentData.setInteger(tagName, newColor);
            moduleGuiChanged.updateData();
        });
        if (currentData != null) {
            int currentColor = currentData.getInteger(tagName);
            if (currentColor != 0) {
                colorChoiceLabel.setCurrentColor(currentColor);
            }
        }
        return colorChoiceLabel;
    }

    @Override
    public void setupFromNBT(NBTTagCompound tagCompound, int dim, BlockPos pos) {
        if (tagCompound != null) {
            line = tagCompound.getString("text");
            if (tagCompound.hasKey("color")) {
                labcolor = tagCompound.getInteger("color");
            } else {
                labcolor = 0xffffff;
            }
            if (tagCompound.hasKey("txtcolor")) {
                txtcolor = tagCompound.getInteger("txtcolor");
            } else {
                txtcolor = 0xffffff;
            }

            setupCoordinateFromNBT(tagCompound, dim, pos);
        }
    }

    protected void setupCoordinateFromNBT(NBTTagCompound tagCompound, int dim, BlockPos pos) {
        coordinate = BlockPosTools.INVALID;
        if (tagCompound.hasKey("monitorx")) {
            if (tagCompound.hasKey("monitordim")) {
                this.dim = tagCompound.getInteger("monitordim");
            } else {
                // Compatibility reasons
                this.dim = tagCompound.getInteger("dim");
            }
            if (dim == this.dim) {
                BlockPos c = new BlockPos(tagCompound.getInteger("monitorx"), tagCompound.getInteger("monitory"), tagCompound.getInteger("monitorz"));
                int dx = Math.abs(c.getX() - pos.getX());
                int dy = Math.abs(c.getY() - pos.getY());
                int dz = Math.abs(c.getZ() - pos.getZ());
                if (dx <= 64 && dy <= 64 && dz <= 64) {
                    coordinate = c;
                }
            }
        }
    }

    @Override
    public boolean needsServerData() {
        return true;
    }
}
