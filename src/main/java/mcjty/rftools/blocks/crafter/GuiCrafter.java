package mcjty.rftools.blocks.crafter;

import mcjty.lib.base.StyleConfig;
import mcjty.lib.container.GenericGuiContainer;
import mcjty.lib.entity.GenericEnergyStorageTileEntity;
import mcjty.lib.gui.RenderHelper;
import mcjty.lib.gui.Window;
import mcjty.lib.gui.events.DefaultSelectionEvent;
import mcjty.lib.gui.layout.HorizontalAlignment;
import mcjty.lib.gui.layout.HorizontalLayout;
import mcjty.lib.gui.layout.PositionalLayout;
import mcjty.lib.gui.widgets.*;
import mcjty.lib.network.Argument;
import mcjty.lib.varia.BlockTools;
import mcjty.lib.varia.ItemStackList;
import mcjty.lib.varia.RedstoneMode;
import mcjty.rftools.RFTools;
import mcjty.rftools.craftinggrid.CraftingRecipe;
import mcjty.rftools.network.RFToolsMessages;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import java.awt.Rectangle;

public class GuiCrafter extends GenericGuiContainer<CrafterBaseTE> {
    public static final int CRAFTER_WIDTH = 256;
    public static final int CRAFTER_HEIGHT = 238;

    private EnergyBar energyBar;
    private WidgetList recipeList;
    private ChoiceLabel keepItem;
    private ChoiceLabel internalRecipe;
    private Button applyButton;
    private ImageChoiceLabel redstoneMode;
    private ImageChoiceLabel speedMode;

    private static final ResourceLocation iconLocation = new ResourceLocation(RFTools.MODID, "textures/gui/crafter.png");
    private static final ResourceLocation iconGuiElements = new ResourceLocation(RFTools.MODID, "textures/gui/guielements.png");

    private static int lastSelected = -1;

    public GuiCrafter(CrafterBlockTileEntity1 te, CrafterContainer container) {
        super(RFTools.instance, RFToolsMessages.INSTANCE, te, container, RFTools.GUI_MANUAL_MAIN, "crafter");
        GenericEnergyStorageTileEntity.setCurrentRF(te.getEnergyStored());

        xSize = CRAFTER_WIDTH;
        ySize = CRAFTER_HEIGHT;
    }

    public GuiCrafter(CrafterBlockTileEntity2 te, CrafterContainer container) {
        super(RFTools.instance, RFToolsMessages.INSTANCE, te, container, RFTools.GUI_MANUAL_MAIN, "crafter");
        GenericEnergyStorageTileEntity.setCurrentRF(te.getEnergyStored());

        xSize = CRAFTER_WIDTH;
        ySize = CRAFTER_HEIGHT;
    }

    public GuiCrafter(CrafterBlockTileEntity3 te, CrafterContainer container) {
        super(RFTools.instance, RFToolsMessages.INSTANCE, te, container, RFTools.GUI_MANUAL_MAIN, "crafter");
        GenericEnergyStorageTileEntity.setCurrentRF(te.getEnergyStored());

        xSize = CRAFTER_WIDTH;
        ySize = CRAFTER_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();

        int maxEnergyStored = tileEntity.getMaxEnergyStored();
        energyBar = new EnergyBar(mc, this).setVertical().setMaxValue(maxEnergyStored).setLayoutHint(new PositionalLayout.PositionalHint(12, 141, 10, 76)).setShowText(false);
        energyBar.setValue(GenericEnergyStorageTileEntity.getCurrentRF());

        initKeepMode();
        initInternalRecipe();
        Slider listSlider = initRecipeList();

        applyButton = new Button(mc, this).
                setText("Apply").
                setTooltips("Press to apply the", "recipe to the crafter").
                addButtonEvent(parent -> applyRecipe()).
                setEnabled(false).
                setLayoutHint(new PositionalLayout.PositionalHint(212, 65, 34, 16));

        Button rememberButton = new Button(mc, this)
                .setText("R")
                .setTooltips("Remember the current items", "in the internal and", "external buffers")
                .addButtonEvent(widget -> rememberItems())
                .setLayoutHint(new PositionalLayout.PositionalHint(148, 74, 18, 16));
        Button forgetButton = new Button(mc, this)
                .setText("F")
                .setTooltips("Forget the remembered layout")
                .addButtonEvent(widget -> forgetItems())
                .setLayoutHint(new PositionalLayout.PositionalHint(168, 74, 18, 16));

        initRedstoneMode();
        initSpeedMode();

        Panel toplevel = new Panel(mc, this).setBackground(iconLocation).setLayout(new PositionalLayout()).addChild(energyBar).addChild(keepItem).addChild(internalRecipe).
                addChild(recipeList).addChild(listSlider).addChild(applyButton).addChild(redstoneMode).addChild(speedMode).addChild(rememberButton).addChild(forgetButton);
        toplevel.setBounds(new Rectangle(guiLeft, guiTop, xSize, ySize));

        if (lastSelected != -1 && lastSelected < tileEntity.getSizeInventory()) {
            recipeList.setSelected(lastSelected);
        }
//        sendChangeToServer(-1, null, null, false, CraftingRecipe.CraftMode.EXT);

        window = new Window(this, toplevel);
        tileEntity.requestRfFromServer(RFTools.MODID);
    }

    private Slider initRecipeList() {
        recipeList = new WidgetList(mc, this)
                .addSelectionEvent(new DefaultSelectionEvent() {
                    @Override
                    public void select(Widget parent, int index) {
                        lastSelected = recipeList.getSelected();
                    }

                    @Override
                    public void doubleClick(Widget parent, int index) {
                        selectRecipe();
                    }
                })
                .setLayoutHint(new PositionalLayout.PositionalHint(10, 7, 126, 84));
        populateList();

        return new Slider(mc, this).setVertical().setScrollable(recipeList).setLayoutHint(new PositionalLayout.PositionalHint(137, 7, 10, 84));
    }

    private void initInternalRecipe() {
        internalRecipe = new ChoiceLabel(mc, this).
                addChoices("Ext", "Int", "ExtC").
                setTooltips("'Int' will put result of", "crafting operation in", "inventory instead of", "output buffer").
                setEnabled(false).
                setLayoutHint(new PositionalLayout.PositionalHint(148, 24, 41, 14));
        internalRecipe.setChoiceTooltip("Ext", "Result of crafting operation", "will go to output buffer",
                TextFormatting.GREEN + "(press Apply after changing)");
        internalRecipe.setChoiceTooltip("Int", "Result of crafting operation", "will stay in input buffer",
                TextFormatting.GREEN + "(press Apply after changing)");
        internalRecipe.setChoiceTooltip("ExtC", "Result of crafting operation", "will go to output buffer",
                "but remaining items (like", "buckets) will stay in input",
                TextFormatting.GREEN + "(press Apply after changing)");
    }

    private void initKeepMode() {
        keepItem = new ChoiceLabel(mc, this).
                addChoices("All", "Keep").
                setEnabled(false).
                setLayoutHint(new PositionalLayout.PositionalHint(148, 7, 41, 14));
        keepItem.setChoiceTooltip("All", "All items in input slots are consumed",
                TextFormatting.GREEN + "(press Apply after changing)");
        keepItem.setChoiceTooltip("Keep", "Keep one item in every inventory slot",
                TextFormatting.GREEN + "(press Apply after changing)");
    }

    private void initSpeedMode() {
        speedMode = new ImageChoiceLabel(mc, this).
                addChoiceEvent((parent, newChoice) -> changeSpeedMode()).
                addChoice("Slow", "Speed mode:\nSlow", iconGuiElements, 48, 0).
                addChoice("Fast", "Speed mode:\nFast", iconGuiElements, 64, 0);
        speedMode.setLayoutHint(new PositionalLayout.PositionalHint(49, 186, 16, 16));
        speedMode.setCurrentChoice(tileEntity.getSpeedMode());
    }

    private void initRedstoneMode() {
        redstoneMode = new ImageChoiceLabel(mc, this).
                addChoiceEvent((parent, newChoice) -> changeRedstoneMode()).
                addChoice(RedstoneMode.REDSTONE_IGNORED.getDescription(), "Redstone mode:\nIgnored", iconGuiElements, 0, 0).
                addChoice(RedstoneMode.REDSTONE_OFFREQUIRED.getDescription(), "Redstone mode:\nOff to activate", iconGuiElements, 16, 0).
                addChoice(RedstoneMode.REDSTONE_ONREQUIRED.getDescription(), "Redstone mode:\nOn to activate", iconGuiElements, 32, 0);
        redstoneMode.setLayoutHint(new PositionalLayout.PositionalHint(31, 186, 16, 16));
        redstoneMode.setCurrentChoice(tileEntity.getRSMode().ordinal());
    }

    private void changeRedstoneMode() {
        tileEntity.setRSMode(RedstoneMode.values()[redstoneMode.getCurrentChoiceIndex()]);
        sendChangeToServer();
    }

    private void changeSpeedMode() {
        tileEntity.setSpeedMode(speedMode.getCurrentChoiceIndex());
        sendChangeToServer();
    }

    private void rememberItems() {
        sendServerCommand(RFToolsMessages.INSTANCE, CrafterBaseTE.CMD_REMEMBER);
    }

    private void forgetItems() {
        sendServerCommand(RFToolsMessages.INSTANCE, CrafterBaseTE.CMD_FORGET);
    }

    private void sendChangeToServer() {
        sendServerCommand(RFToolsMessages.INSTANCE, CrafterBaseTE.CMD_MODE,
                new Argument("rs", RedstoneMode.values()[redstoneMode.getCurrentChoiceIndex()].getDescription()),
                new Argument("speed", speedMode.getCurrentChoiceIndex()));
    }

    private void populateList() {
        recipeList.removeChildren();
        for (int i = 0 ; i < tileEntity.getSupportedRecipes() ; i++) {
            CraftingRecipe recipe = tileEntity.getRecipe(i);
            addRecipeLine(recipe.getResult());
        }
    }

    private void addRecipeLine(ItemStack craftingResult) {
        String readableName = BlockTools.getReadableName(craftingResult);
        int color = StyleConfig.colorTextInListNormal;
        if (craftingResult.isEmpty()) {
            readableName = "<no recipe>";
            color = 0xFF505050;
        }
        Panel panel = new Panel(mc, this).setLayout(new HorizontalLayout())
                .addChild(new BlockRender(mc, this)
                        .setRenderItem(craftingResult)
                        .setTooltips("Double click to edit this recipe"))
                .addChild(new Label(mc, this)
                        .setColor(color)
                        .setHorizontalAlignment(HorizontalAlignment.ALIGH_LEFT)
                        .setDynamic(true)
                        .setText(readableName)
                        .setTooltips("Double click to edit this recipe"));
        recipeList.addChild(panel);
    }

    private void selectRecipe() {
        int selected = recipeList.getSelected();
        lastSelected = selected;
        if (selected == -1) {
            for (int i = 0 ; i < 10 ; i++) {
                inventorySlots.getSlot(i).putStack(ItemStack.EMPTY);
            }
            keepItem.setChoice("All");
            internalRecipe.setChoice("Ext");
            return;
        }
        CraftingRecipe craftingRecipe = tileEntity.getRecipe(selected);
        InventoryCrafting inv = craftingRecipe.getInventory();
        for (int i = 0 ; i < 9 ; i++) {
            inventorySlots.getSlot(i).putStack(inv.getStackInSlot(i));
        }
        inventorySlots.getSlot(9).putStack(craftingRecipe.getResult());
        keepItem.setChoice(craftingRecipe.isKeepOne() ? "Keep" : "All");
        internalRecipe.setChoice(craftingRecipe.getCraftMode().getDescription());
    }

    private void testRecipe() {
        int selected = recipeList.getSelected();
        if (selected == -1) {
            return;
        }

        InventoryCrafting inv = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer var1) {
                return false;
            }
        }, 3, 3);

        for (int i = 0 ; i < 9 ; i++) {
            inv.setInventorySlotContents(i, inventorySlots.getSlot(i).getStack());
        }

        // Compare current contents to avoid unneeded slot update.
        IRecipe recipe = CraftingRecipe.findRecipe(mc.world, inv);
        ItemStack newResult;
        if (recipe == null) {
            newResult = ItemStack.EMPTY;
        } else {
            newResult = recipe.getCraftingResult(inv);
        }
        inventorySlots.getSlot(9).putStack(newResult);
    }

    private void applyRecipe() {
        int selected = recipeList.getSelected();
        if (selected == -1) {
            return;
        }

        if (selected >= tileEntity.getSupportedRecipes()) {
            recipeList.setSelected(-1);
            return;
        }

        CraftingRecipe craftingRecipe = tileEntity.getRecipe(selected);
        InventoryCrafting inv = craftingRecipe.getInventory();

        for (int i = 0 ; i < 9 ; i++) {
            ItemStack oldStack = inv.getStackInSlot(i);
            ItemStack newStack = inventorySlots.getSlot(i).getStack();
            if (!itemStacksEqual(oldStack, newStack)) {
                inv.setInventorySlotContents(i, newStack);
            }
        }

        // Compare current contents to avoid unneeded slot update.
        IRecipe recipe = CraftingRecipe.findRecipe(mc.world, inv);
        ItemStack newResult;
        if (recipe == null) {
            newResult = ItemStack.EMPTY;
        } else {
            newResult = recipe.getCraftingResult(inv);
        }
        ItemStack oldResult = inventorySlots.getSlot(9).getStack();
        if (!itemStacksEqual(oldResult, newResult)) {
            inventorySlots.getSlot(9).putStack(newResult);
        }

        craftingRecipe.setResult(newResult);
        updateRecipe();
        populateList();
    }

    private void updateRecipe() {
        int selected = recipeList.getSelected();
        if (selected == -1) {
            return;
        }
        CraftingRecipe craftingRecipe = tileEntity.getRecipe(selected);
        boolean keepOne = "Keep".equals(keepItem.getCurrentChoice());
        CraftingRecipe.CraftMode mode;
        if ("Int".equals(internalRecipe.getCurrentChoice())) {
            mode = CraftingRecipe.CraftMode.INT;
        } else if ("Ext".equals(internalRecipe.getCurrentChoice())) {
            mode = CraftingRecipe.CraftMode.EXT;
        } else {
            mode = CraftingRecipe.CraftMode.EXTC;
        }
        craftingRecipe.setKeepOne(keepOne);
        craftingRecipe.setCraftMode(mode);
        sendChangeToServer(selected, craftingRecipe.getInventory(), craftingRecipe.getResult(), keepOne, mode);
    }

    private boolean itemStacksEqual(ItemStack matches, ItemStack oldStack) {
        if (matches.isEmpty()) {
            return oldStack.isEmpty();
        } else {
            return !oldStack.isEmpty() && matches.isItemEqual(oldStack);
        }
    }

    private void sendChangeToServer(int index, InventoryCrafting inv, ItemStack result, boolean keepOne,
                                    CraftingRecipe.CraftMode mode) {

        RFToolsMessages.INSTANCE.sendToServer(new PacketCrafter(tileEntity.getPos(), index, inv,
                result, keepOne, mode));
    }

    /**
     * Draws the screen and all the components in it.
     */
    @Override
    public void drawScreen(int par1, int par2, float par3) {
        updateButtons();
        super.drawScreen(par1, par2, par3);
        testRecipe();
    }

    private void updateButtons() {
        boolean selected = recipeList.getSelected() != -1;
        keepItem.setEnabled(selected);
        internalRecipe.setEnabled(selected);
        applyButton.setEnabled(selected);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float v, int x, int y) {
        drawWindow();
        int currentRF = GenericEnergyStorageTileEntity.getCurrentRF();
        energyBar.setValue(currentRF);
        tileEntity.requestRfFromServer(RFTools.MODID);

        // Draw the ghost slots here
        drawGhostSlots();
    }

    private void drawGhostSlots() {
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.pushMatrix();
        GlStateManager.translate(guiLeft, guiTop, 0.0F);
        GlStateManager.color(1.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.enableRescaleNormal();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (short) 240 / 1.0F, 240.0f);

        ItemStackList ghostSlots = tileEntity.getGhostSlots();
        zLevel = 100.0F;
        itemRender.zLevel = 100.0F;
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();

        for (int i = 0 ; i < ghostSlots.size() ; i++) {
            ItemStack stack = ghostSlots.get(i);
            if (!stack.isEmpty()) {
                int slotIdx;
                if (i < CrafterContainer.BUFFER_SIZE) {
                    slotIdx = i + CrafterContainer.SLOT_BUFFER;
                } else {
                    slotIdx = i + CrafterContainer.SLOT_BUFFEROUT - CrafterContainer.BUFFER_SIZE;
                }
                Slot slot = inventorySlots.getSlot(slotIdx);
                if (!slot.getHasStack()) {
                    itemRender.renderItemAndEffectIntoGUI(stack, slot.xPos, slot.yPos);

                    GlStateManager.disableLighting();
                    GlStateManager.enableBlend();
                    GlStateManager.disableDepth();
                    this.mc.getTextureManager().bindTexture(iconGuiElements);
                    RenderHelper.drawTexturedModalRect(slot.xPos, slot.yPos, 14 * 16, 3 * 16, 16, 16);
                    GlStateManager.enableDepth();
                    GlStateManager.disableBlend();
                    GlStateManager.enableLighting();
                }
            }

        }
        itemRender.zLevel = 0.0F;
        zLevel = 0.0F;

        GlStateManager.popMatrix();
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
    }
}
