package mcjty.rftools.blocks.screens.modulesclient;

import mcjty.lib.varia.BlockPosTools;
import mcjty.rftools.api.screens.*;
import mcjty.rftools.api.screens.data.IModuleDataContents;
import mcjty.rftools.blocks.screens.modulesclient.helper.ScreenLevelHelper;
import mcjty.rftools.blocks.screens.modulesclient.helper.ScreenTextHelper;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class EnergyBarClientScreenModule implements IClientScreenModule<IModuleDataContents> {

    private String line = "";
    private int color = 0xffffff;
    protected int dim = 0;
    protected BlockPos coordinate = BlockPosTools.INVALID;

    private ITextRenderHelper labelCache = new ScreenTextHelper();
    private ILevelRenderHelper rfRenderer = new ScreenLevelHelper().gradient(0xffff0000, 0xff333300);

    @Override
    public TransformMode getTransformMode() {
        return TransformMode.TEXT;
    }

    @Override
    public int getHeight() {
        return 10;
    }

    @Override
    public void render(IModuleRenderHelper renderHelper, FontRenderer fontRenderer, int currenty, IModuleDataContents screenData, ModuleRenderInfo renderInfo) {
        GlStateManager.disableLighting();

        int xoffset;
        if (!line.isEmpty()) {
            labelCache.setup(line, 160, renderInfo);
            labelCache.renderText(0, currenty, color, renderInfo);
            xoffset = 7 + 40;
        } else {
            xoffset = 7;
        }

        if (!BlockPosTools.INVALID.equals(coordinate)) {
            rfRenderer.render(xoffset, currenty, screenData, renderInfo);
        } else {
            renderHelper.renderText(xoffset, currenty, 0xff0000, renderInfo, "<invalid>");
        }
    }

    @Override
    public void mouseClick(World world, int x, int y, boolean clicked) {

    }

    @Override
    public void createGui(IModuleGuiBuilder guiBuilder) {
        guiBuilder
                .label("Label:").text("text", "Label text").color("color", "Color for the label").nl()
                .label("RF+:").color("rfcolor", "Color for the RF text").label("RF-:").color("rfcolor_neg", "Color for the negative", "RF/tick ratio").nl()
                .toggleNegative("hidebar", "Bar", "Toggle visibility of the", "energy bar").mode("RF").format("format").nl()
                .choices("align", "Label alignment", "Left", "Center", "Right").nl()
                .label("Block:").block("monitor").nl();
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
            int rfcolor;
            if (tagCompound.hasKey("rfcolor")) {
                rfcolor = tagCompound.getInteger("rfcolor");
            } else {
                rfcolor = 0xffffff;
            }
            int rfcolorNeg;
            if (tagCompound.hasKey("rfcolor_neg")) {
                rfcolorNeg = tagCompound.getInteger("rfcolor_neg");
            } else {
                rfcolorNeg = 0xffffff;
            }
            rfRenderer.color(rfcolor, rfcolorNeg);

            if (tagCompound.hasKey("align")) {
                String alignment = tagCompound.getString("align");
                labelCache.align(TextAlign.get(alignment));
            } else {
                labelCache.align(TextAlign.ALIGN_LEFT);
            }

            boolean hidebar = tagCompound.getBoolean("hidebar");
            boolean hidetext = tagCompound.getBoolean("hidetext");
            boolean showdiff = tagCompound.getBoolean("showdiff");
            boolean showpct = tagCompound.getBoolean("showpct");
            rfRenderer.settings(hidebar, hidetext, showpct, showdiff);

            rfRenderer.format(FormatStyle.values()[tagCompound.getInteger("format")]);

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
