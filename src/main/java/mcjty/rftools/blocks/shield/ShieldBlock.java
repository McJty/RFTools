package mcjty.rftools.blocks.shield;

import mcjty.lib.api.IModuleSupport;
import mcjty.lib.api.Infusable;
import mcjty.lib.crafting.INBTPreservingIngredient;
import mcjty.lib.varia.GlobalCoordinate;
import mcjty.lib.varia.Logging;
import mcjty.lib.varia.ModuleSupport;
import mcjty.rftools.RFTools;
import mcjty.rftools.blocks.GenericRFToolsBlock;
import mcjty.rftools.blocks.builder.BuilderSetup;
import mcjty.rftools.items.smartwrench.SmartWrenchItem;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.List;

//@Optional.InterfaceList({
//        @Optional.Interface(iface = "crazypants.enderio.api.redstone.IRedstoneConnectable", modid = "EnderIO")})
public class ShieldBlock extends GenericRFToolsBlock<ShieldTEBase, ShieldContainer> implements Infusable, INBTPreservingIngredient
        /*, IRedstoneConnectable*/ {

    private final int max;

    public ShieldBlock(String blockName, Class<? extends ShieldTEBase> clazz, int max) {
        super(Material.IRON, clazz, ShieldContainer.class, blockName, true);
        this.max = max;
    }

    @Override
    public boolean needsRedstoneCheck() {
        return true;
    }

    @Override
    public RotationType getRotationType() {
        return RotationType.NONE;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Class<GuiShield> getGuiClass() {
        return GuiShield.class;
    }

    @Override
    public int getGuiID() {
        return RFTools.GUI_SHIELD;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack itemStack, World player, List<String> list, ITooltipFlag whatIsThis) {
        super.addInformation(itemStack, player, list, whatIsThis);

        list.add(TextFormatting.GREEN + "Supports " + max + " blocks");

        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            list.add(TextFormatting.WHITE + "This machine forms a shield out of adjacent");
            list.add(TextFormatting.WHITE + "template blocks. It can filter based on type of");
            list.add(TextFormatting.WHITE + "mob and do various things (damage, solid, ...)");
            list.add(TextFormatting.WHITE + "Use the Smart Wrench to add sections to the shield");
            list.add(TextFormatting.RED + "Note: block mimic is not implemented yet!");
            list.add(TextFormatting.YELLOW + "Infusing bonus: reduced power consumption and");
            list.add(TextFormatting.YELLOW + "increased damage.");
        } else {
            list.add(TextFormatting.WHITE + RFTools.SHIFT_MESSAGE);
        }
    }

    @Override
    protected IModuleSupport getModuleSupport() {
        return new ModuleSupport(ShieldContainer.SLOT_SHAPE) {
            @Override
            public boolean isModule(ItemStack itemStack) {
                return itemStack.getItem() == BuilderSetup.shapeCardItem;
            }
        };
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        restoreBlockFromNBT(world, pos, stack);
        setOwner(world, pos, placer);
    }

    @Override
    public void onBlockClicked(World world, BlockPos pos, EntityPlayer playerIn) {
        if (!world.isRemote) {
            composeDecomposeShield(world, pos, true);
            // @todo achievements
//            Achievements.trigger(playerIn, Achievements.shieldSafety);
        }
    }

    @Override
    protected boolean wrenchUse(World world, BlockPos pos, EnumFacing side, EntityPlayer player) {
        composeDecomposeShield(world, pos, false);
        // @todo achievements
//        Achievements.trigger(player, Achievements.shieldSafety);
        return true;
    }

    @Override
    protected boolean wrenchSneakSelect(World world, BlockPos pos, EntityPlayer player) {
        if (!world.isRemote) {
            GlobalCoordinate currentBlock = SmartWrenchItem.getCurrentBlock(player.getHeldItem(EnumHand.MAIN_HAND));
            if (currentBlock == null) {
                SmartWrenchItem.setCurrentBlock(player.getHeldItem(EnumHand.MAIN_HAND), new GlobalCoordinate(pos, world.provider.getDimension()));
                Logging.message(player, TextFormatting.YELLOW + "Selected block");
            } else {
                SmartWrenchItem.setCurrentBlock(player.getHeldItem(EnumHand.MAIN_HAND), null);
                Logging.message(player, TextFormatting.YELLOW + "Cleared selected block");
            }
        }
        return true;
    }

    private void composeDecomposeShield(World world, BlockPos pos, boolean ctrl) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof ShieldTEBase) {
                ((ShieldTEBase)te).composeDecomposeShield(ctrl);
            }
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof ShieldTEBase) {
            if (!world.isRemote) {
                ShieldTEBase shieldTileEntity = (ShieldTEBase) te;
                if (shieldTileEntity.isShieldComposed()) {
                    shieldTileEntity.decomposeShield();
                }
            }
        }

        super.breakBlock(world, pos, state);
    }

//
//    @Override
//    public boolean shouldRedstoneConduitConnect(World world, int x, int y, int z, EnumFacing from) {
//        return true;
//    }
}
