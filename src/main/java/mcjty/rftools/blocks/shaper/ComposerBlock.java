package mcjty.rftools.blocks.shaper;

import mcjty.rftools.RFTools;
import mcjty.rftools.blocks.GenericRFToolsBlock;
import net.minecraft.block.material.Material;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class ComposerBlock extends GenericRFToolsBlock<ComposerTileEntity, ComposerContainer> /*, IRedstoneConnectable */ {

    public ComposerBlock() {
        super(Material.IRON, ComposerTileEntity.class, ComposerContainer.class, "composer", true);
    }


    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack itemStack, World player, List<String> list, ITooltipFlag whatIsThis) {
        super.addInformation(itemStack, player, list, whatIsThis);
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            list.add(TextFormatting.WHITE + "This block can construct more complex");
            list.add(TextFormatting.WHITE + "shape cards for the Builder or Shield");
            list.add(TextFormatting.WHITE + "by creating combinations of other shape");
            list.add(TextFormatting.WHITE + "cards");
        } else {
            list.add(TextFormatting.WHITE + RFTools.SHIFT_MESSAGE);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Class<GuiComposer> getGuiClass() {
        return GuiComposer.class;
    }

    @Override
    public int getGuiID() {
        return RFTools.GUI_COMPOSER;
    }
}
