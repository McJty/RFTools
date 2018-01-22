package mcjty.rftools.blocks.screens.modules;

import mcjty.lib.varia.BlockPosTools;
import mcjty.rftools.api.screens.IScreenDataHelper;
import mcjty.rftools.api.screens.IScreenModule;
import mcjty.rftools.api.screens.data.IModuleDataBoolean;
import mcjty.rftools.blocks.logic.wireless.RedstoneChannels;
import mcjty.rftools.blocks.screens.ScreenConfiguration;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

public class RedstoneScreenModule implements IScreenModule<IModuleDataBoolean> {
    private int channel = -1;
    private BlockPos coordinate = BlockPosTools.INVALID;
    private int dim = 0;
    private EnumFacing side = null;

    @Override
    public IModuleDataBoolean getData(IScreenDataHelper helper, World worldObj, long millis) {
        if (channel == -1) {
            // If we are monitoring some block then we can use that.
            if (!BlockPosTools.INVALID.equals(coordinate)) {
                World world = DimensionManager.getWorld(dim);
                if (world != null) {
//                    int powerTo = world.isBlockProvidingPowerTo(coordinate.getX(), coordinate.getY(), coordinate.getZ(), side);
                    int powerTo = world.getRedstonePower(coordinate.offset(side), side.getOpposite());
//                    int powerTo = world.getIndirectPowerLevelTo(coordinate.getX(), coordinate.getY(), coordinate.getZ(), side);

                    return helper.createBoolean(powerTo > 0);
                }
            }
            return null;
        }
        RedstoneChannels channels = RedstoneChannels.getChannels();
        if (channels == null) {
            return null;
        }
        RedstoneChannels.RedstoneChannel ch = channels.getChannel(channel);
        if (ch == null) {
            return null;
        }
        return helper.createBoolean(ch.getValue() != 0);
    }

    @Override
    public void setupFromNBT(NBTTagCompound tagCompound, int dim, BlockPos pos) {
        if (tagCompound != null) {
            channel = -1;
            if (tagCompound.hasKey("channel")) {
                channel = tagCompound.getInteger("channel");
            }
            if (tagCompound.hasKey("monitorx")) {
                side = EnumFacing.VALUES[tagCompound.getInteger("monitorside")];
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
    }

    @Override
    public int getRfPerTick() {
        return ScreenConfiguration.REDSTONE_RFPERTICK;
    }

    @Override
    public void mouseClick(World world, int x, int y, boolean clicked, EntityPlayer player) {

    }
}
