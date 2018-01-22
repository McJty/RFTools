package mcjty.rftools.blocks.screens.modulesclient;

import mcjty.lib.gui.RenderHelper;
import mcjty.rftools.api.screens.*;
import mcjty.rftools.api.screens.data.IModuleData;
import mcjty.rftools.blocks.screens.modules.DumpScreenModule;
import mcjty.rftools.blocks.screens.modulesclient.helper.ScreenTextHelper;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class DumpClientScreenModule implements IClientScreenModule<IModuleData> {
    private String line = "";
    private int color = 0xffffff;
    private ItemStack[] stacks = new ItemStack[DumpScreenModule.COLS * DumpScreenModule.ROWS];
    private ITextRenderHelper buttonCache = new ScreenTextHelper();

    @Override
    public TransformMode getTransformMode() {
        return TransformMode.TEXT;
    }

    @Override
    public int getHeight() {
        return 14;
    }

    @Override
    public void render(IModuleRenderHelper renderHelper, FontRenderer fontRenderer, int currenty, IModuleData screenData, ModuleRenderInfo renderInfo) {
        GlStateManager.disableLighting();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(false);
        int xoffset = 7 + 5;

        RenderHelper.drawBeveledBox(xoffset - 5, currenty, 130 - 7, currenty + 12, 0xffeeeeee, 0xff333333, 0xff448866);
        buttonCache.setup(line, 490, renderInfo);
        buttonCache.renderText(xoffset -10, currenty + 2, color, renderInfo);
    }

    @Override
    public void mouseClick(World world, int x, int y, boolean clicked) {
    }

    @Override
    public void createGui(IModuleGuiBuilder guiBuilder) {
        int index = 0;
        for (int y = 0 ; y < DumpScreenModule.ROWS ; y++) {
            for (int x = 0 ; x < DumpScreenModule.COLS ; x++) {
                guiBuilder.ghostStack("stack" + index);
                index++;
            }
            guiBuilder.nl();
        }
        guiBuilder
                .label("Label:").text("text", "Label text").color("color", "Label color").nl()
                .toggle("oredict", "Ore Dict", "If enabled use ore dictionary", "to match items");
    }


    @Override
    public void setupFromNBT(NBTTagCompound tagCompound, int dim, BlockPos pos) {
        if (tagCompound != null) {
            line = tagCompound.getString("text");
            if (tagCompound.hasKey("color")) {
                color = tagCompound.getInteger("color");
            } else {
                color = 0xffffff;
            }
            for (int i = 0 ; i < stacks.length ; i++) {
                if (tagCompound.hasKey("stack"+i)) {
                    stacks[i] = new ItemStack(tagCompound.getCompoundTag("stack" + i));
                }
            }
        }
    }

    @Override
    public boolean needsServerData() {
        return true;
    }
}
