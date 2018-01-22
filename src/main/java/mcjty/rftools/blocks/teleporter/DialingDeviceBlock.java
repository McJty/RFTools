package mcjty.rftools.blocks.teleporter;

import mcjty.lib.api.Infusable;
import mcjty.lib.container.EmptyContainer;
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

public class DialingDeviceBlock extends GenericRFToolsBlock<DialingDeviceTileEntity, EmptyContainer> implements Infusable {

    public DialingDeviceBlock() {
        super(Material.IRON, DialingDeviceTileEntity.class, EmptyContainer.class, "dialing_device", false);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Class<GuiDialingDevice> getGuiClass() {
        return GuiDialingDevice.class;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack itemStack, World player, List<String> list, ITooltipFlag whatIsThis) {
        super.addInformation(itemStack, player, list, whatIsThis);

        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            list.add(TextFormatting.WHITE + "With the dialing device you can 'dial-up' any");
            list.add(TextFormatting.WHITE + "nearby matter transmitter to any matter receiver");
            list.add(TextFormatting.WHITE + "in the Minecraft universe. This requires power.");
            list.add(TextFormatting.WHITE + "If a Destination Analyzer is adjacent to this block");
            list.add(TextFormatting.WHITE + "you will also be able to check if the destination");
            list.add(TextFormatting.WHITE + "has enough power to be safe.");
            list.add(TextFormatting.YELLOW + "Infusing bonus: reduced power consumption.");
        } else {
            list.add(TextFormatting.WHITE + RFTools.SHIFT_MESSAGE);
        }
    }


    @Override
    public int getGuiID() {
        return RFTools.GUI_DIALING_DEVICE;
    }
}
