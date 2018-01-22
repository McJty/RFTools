package mcjty.rftools.blocks.shaper;

import mcjty.lib.container.GenericGuiContainer;
import mcjty.lib.gui.Window;
import mcjty.lib.gui.WindowManager;
import mcjty.lib.gui.layout.PositionalLayout;
import mcjty.lib.gui.widgets.*;
import mcjty.rftools.RFTools;
import mcjty.rftools.items.builder.ShapeCardItem;
import mcjty.rftools.network.RFToolsMessages;
import mcjty.rftools.shapes.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Mouse;

import java.awt.Rectangle;
import java.io.IOException;

public class GuiComposer extends GenericGuiContainer<ComposerTileEntity> implements IShapeParentGui {
    public static final int SIDEWIDTH = 80;
    public static final int SHAPER_WIDTH = 256;
    public static final int SHAPER_HEIGHT = 238;

    private static final ResourceLocation sideBackground = new ResourceLocation(RFTools.MODID, "textures/gui/sidegui.png");
    private static final ResourceLocation iconLocation = new ResourceLocation(RFTools.MODID, "textures/gui/composer.png");
    private static final ResourceLocation guiElements = new ResourceLocation(RFTools.MODID, "textures/gui/guielements.png");

    private ChoiceLabel operationLabels[] = new ChoiceLabel[ComposerContainer.SLOT_COUNT];
    private ChoiceLabel rotationLabels[] = new ChoiceLabel[ComposerContainer.SLOT_COUNT];
    private ToggleButton flipButtons[] = new ToggleButton[ComposerContainer.SLOT_COUNT];
    private Button configButton[] = new Button[ComposerContainer.SLOT_COUNT];
    private Button outConfigButton;

    private ToggleButton showAxis;
    private ToggleButton showOuter;
    private ToggleButton showScan;

    // For GuiShapeCard: the current card to edit
    public static BlockPos shaperBlock = null;
    public static int shaperStackSlot = 0;

    private ShapeRenderer shapeRenderer = null;

    private Window sideWindow;


    public GuiComposer(ComposerTileEntity composerTileEntity, ComposerContainer container) {
        super(RFTools.instance, RFToolsMessages.INSTANCE, composerTileEntity, container, RFTools.GUI_MANUAL_SHAPE, "shaper");

        xSize = SHAPER_WIDTH;
        ySize = SHAPER_HEIGHT;
    }

    private ShapeRenderer getShapeRenderer() {
        if (shapeRenderer == null) {
            shapeRenderer = new ShapeRenderer(getShapeID());
        }
        return shapeRenderer;
    }

    private ShapeID getShapeID() {
        Slot slot = inventorySlots.getSlot(ComposerContainer.SLOT_OUT);
        ItemStack stack = slot.getHasStack() ? slot.getStack() : ItemStack.EMPTY;

        return new ShapeID(tileEntity.getWorld().provider.getDimension(), tileEntity.getPos(), ShapeCardItem.getScanId(stack), false, ShapeCardItem.isSolid(stack));
    }


    @Override
    public void initGui() {
        super.initGui();

        Panel toplevel = new Panel(mc, this).setBackground(iconLocation).setLayout(new PositionalLayout());

        getShapeRenderer().initView(getPreviewLeft(), guiTop+100);

        ShapeModifier[] modifiers = tileEntity.getModifiers();

        operationLabels[0] = null;
        for (int i = 0; i < ComposerContainer.SLOT_COUNT ; i++) {
            operationLabels[i] = new ChoiceLabel(mc, this).addChoices(
                    ShapeOperation.UNION.getCode(),
                    ShapeOperation.SUBTRACT.getCode(),
                    ShapeOperation.INTERSECT.getCode());
            for (ShapeOperation operation : ShapeOperation.values()) {
                operationLabels[i].setChoiceTooltip(operation.getCode(), operation.getDescription());
            }
            operationLabels[i].setLayoutHint(new PositionalLayout.PositionalHint(55, 7 + i*18, 26, 16));
            operationLabels[i].setChoice(modifiers[i].getOperation().getCode());
            operationLabels[i].addChoiceEvent((parent, newChoice) -> update());
            toplevel.addChild(operationLabels[i]);
        }
        operationLabels[0].setEnabled(false);

        for (int i = 0; i < ComposerContainer.SLOT_COUNT ; i++) {
            configButton[i] = new Button(mc, this).setText("?");
            configButton[i].setLayoutHint(new PositionalLayout.PositionalHint(3, 7 + i*18+2, 13, 12));
            int finalI = i;
            configButton[i].addButtonEvent(parent -> openCardGui(finalI));
            configButton[i].setTooltips("Click to open the card gui");
            toplevel.addChild(configButton[i]);
        }
        outConfigButton = new Button(mc, this).setText("?");
        outConfigButton.setLayoutHint(new PositionalLayout.PositionalHint(3, 200+2, 13, 12));
        outConfigButton.addButtonEvent(parent -> openCardGui(-1));
        outConfigButton.setTooltips("Click to open the card gui");
        toplevel.addChild(outConfigButton);

        showAxis = ShapeGuiTools.createAxisButton(this, toplevel, 5, 176);
        showOuter = ShapeGuiTools.createBoxButton(this, toplevel, 31, 176);
        showScan = ShapeGuiTools.createScanButton(this, toplevel, 57, 176);

        toplevel.setBounds(new Rectangle(guiLeft, guiTop, xSize, ySize));

        Panel sidePanel = new Panel(mc, this).setLayout(new PositionalLayout()).setBackground(sideBackground);
        String[] tt = {
                "Drag left mouse button to rotate",
                "Shift drag left mouse to pan",
                "Use mouse wheel to zoom in/out",
                "Use middle click to reset rotation" };
        sidePanel.addChild(new Label<>(mc, this).setText("E").setColor(0xffff0000).setTooltips(tt).setLayoutHint(new PositionalLayout.PositionalHint(5, 175, 15, 15)));
        sidePanel.addChild(new Label<>(mc, this).setText("W").setColor(0xffff0000).setTooltips(tt).setLayoutHint(new PositionalLayout.PositionalHint(40, 175, 15, 15)));
        sidePanel.addChild(new Label<>(mc, this).setText("U").setColor(0xff00bb00).setTooltips(tt).setLayoutHint(new PositionalLayout.PositionalHint(5, 190, 15, 15)));
        sidePanel.addChild(new Label<>(mc, this).setText("D").setColor(0xff00bb00).setTooltips(tt).setLayoutHint(new PositionalLayout.PositionalHint(40, 190, 15, 15)));
        sidePanel.addChild(new Label<>(mc, this).setText("N").setColor(0xff0000ff).setTooltips(tt).setLayoutHint(new PositionalLayout.PositionalHint(5, 205, 15, 15)));
        sidePanel.addChild(new Label<>(mc, this).setText("S").setColor(0xff0000ff).setTooltips(tt).setLayoutHint(new PositionalLayout.PositionalHint(40, 205, 15, 15)));

        for (int i = 0; i < ComposerContainer.SLOT_COUNT ; i++) {
            ToggleButton flip = new ToggleButton(mc, this).setText("Flip").setCheckMarker(true).setLayoutHint(new PositionalLayout.PositionalHint(6, 7 + i*18, 35, 16));
            flip.setPressed(modifiers[i].isFlipY());
            flip.addButtonEvent(parent -> update());
            sidePanel.addChild(flip);
            flipButtons[i] = flip;
            ChoiceLabel rot = new ChoiceLabel(mc, this).addChoices("None", "X", "Y", "Z").setChoice("None").setLayoutHint(new PositionalLayout.PositionalHint(45, 7 + i*18, 35, 16));
            rot.setChoice(modifiers[i].getRotation().getCode());
            rot.addChoiceEvent((parent, newChoice) -> update());
            sidePanel.addChild(rot);
            rotationLabels[i] = rot;
        }

        sidePanel.setBounds(new Rectangle(guiLeft-SIDEWIDTH, guiTop, SIDEWIDTH, ySize));
        sideWindow = new Window(this, sidePanel);

        window = new Window(this, toplevel);
    }


    @Override
    protected void registerWindows(WindowManager mgr) {
        super.registerWindows(mgr);
        mgr.addWindow(sideWindow);
    }

    @Override
    public int getPreviewLeft() {
        return getGuiLeft();
    }

    @Override
    public int getPreviewTop() {
        return getGuiTop();
    }

    private void openCardGui(int i) {
        int slot;
        if (i == -1) {
            slot = ComposerContainer.SLOT_OUT;
        } else {
            slot = ComposerContainer.SLOT_TABS+i;
        }
        ItemStack cardStack = inventorySlots.getSlot(slot).getStack();
        if (!cardStack.isEmpty()) {
            EntityPlayerSP player = Minecraft.getMinecraft().player;
            shaperBlock = tileEntity.getPos();
            shaperStackSlot = slot;
            player.openGui(RFTools.instance, RFTools.GUI_SHAPECARD_COMPOSER, player.getEntityWorld(), (int) player.posX, (int) player.posY, (int) player.posZ);
        }
    }

    private void update() {
        ShapeModifier[] modifiers = new ShapeModifier[ComposerContainer.SLOT_COUNT];
        for (int i = 0; i < ComposerContainer.SLOT_COUNT ; i++) {
            ShapeOperation op = ShapeOperation.getByName(operationLabels[i].getCurrentChoice());
            ShapeRotation rot = ShapeRotation.getByName(rotationLabels[i].getCurrentChoice());
            modifiers[i] = new ShapeModifier(op, flipButtons[i].isPressed(), rot);
        }
        network.sendToServer(new PacketSendComposerData(tileEntity.getPos(), modifiers));
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        x -= guiLeft;
        y -= guiTop;

        getShapeRenderer().handleShapeDragging(x, y);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float v, int x, int y) {

        getShapeRenderer().handleMouseWheel();

        drawWindow();

        Slot slot = inventorySlots.getSlot(ComposerContainer.SLOT_OUT);
        if (slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                getShapeRenderer().setShapeID(getShapeID());
                getShapeRenderer().renderShape(this, stack, guiLeft, guiTop, showAxis.isPressed(), showOuter.isPressed(), showScan.isPressed(), true);
            }
        }
    }

}
