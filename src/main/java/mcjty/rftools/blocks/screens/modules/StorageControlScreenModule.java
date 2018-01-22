package mcjty.rftools.blocks.screens.modules;

import io.netty.buffer.ByteBuf;
import mcjty.lib.varia.BlockPosTools;
import mcjty.lib.varia.ItemStackList;
import mcjty.lib.varia.SoundTools;
import mcjty.rftools.RFTools;
import mcjty.rftools.api.screens.IScreenDataHelper;
import mcjty.rftools.api.screens.IScreenModule;
import mcjty.rftools.api.screens.IScreenModuleUpdater;
import mcjty.rftools.api.screens.ITooltipInfo;
import mcjty.rftools.api.screens.data.IModuleData;
import mcjty.rftools.blocks.screens.ScreenConfiguration;
import mcjty.rftools.blocks.storagemonitor.StorageScannerTileEntity;
import mcjty.rftools.varia.RFToolsTools;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

public class StorageControlScreenModule implements IScreenModule<StorageControlScreenModule.ModuleDataStacks>, ITooltipInfo,
        IScreenModuleUpdater {
    private ItemStackList stacks = ItemStackList.create(9);

    protected int dim = 0;
    protected BlockPos coordinate = BlockPosTools.INVALID;
    private boolean starred = false;
    private boolean oredict = false;
    private int dirty = -1;

    public static class ModuleDataStacks implements IModuleData {

        public static final String ID = RFTools.MODID + ":storage";

        private int[] amounts = null;

        @Override
        public String getId() {
            return ID;
        }

        public ModuleDataStacks(int... amountsIn) {
            amounts = amountsIn;
        }

        public ModuleDataStacks(ByteBuf buf) {
            int s = buf.readInt();
            amounts = new int[s];
            for (int i = 0; i < s; i++) {
                amounts[i] = buf.readInt();
            }
        }

        public int getAmount(int idx) {
            return amounts[idx];
        }

        @Override
        public void writeToBuf(ByteBuf buf) {
            buf.writeInt(amounts.length);
            for (int i : amounts) {
                buf.writeInt(i);
            }

        }
    }

    @Override
    public ModuleDataStacks getData(IScreenDataHelper helper, World worldObj, long millis) {
        StorageScannerTileEntity scannerTileEntity = getStorageScanner(dim, coordinate);
        if (scannerTileEntity == null) {
            return null;
        }
        int[] amounts = new int[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) {
            amounts[i] = scannerTileEntity.countItems(stacks.get(i), starred, oredict);
        }
        return new ModuleDataStacks(amounts);
    }

    public static StorageScannerTileEntity getStorageScanner(int dim, BlockPos coordinate) {
        World world = DimensionManager.getWorld(dim);
        if (world == null) {
            return null;
        }

        if (!RFToolsTools.chunkLoaded(world, coordinate)) {
            return null;
        }

        TileEntity te = world.getTileEntity(coordinate);
        if (te == null) {
            return null;
        }

        if (!(te instanceof StorageScannerTileEntity)) {
            return null;
        }

        return (StorageScannerTileEntity) te;
    }

    @Override
    public void setupFromNBT(NBTTagCompound tagCompound, int dim, BlockPos pos) {
        if (tagCompound != null) {
            setupCoordinateFromNBT(tagCompound, dim, pos);
            for (int i = 0; i < stacks.size(); i++) {
                if (tagCompound.hasKey("stack" + i)) {
                    stacks.set(i, new ItemStack(tagCompound.getCompoundTag("stack" + i)));
                }
            }
        }
        StorageScannerTileEntity te = getStorageScanner(dim, coordinate);
        if (te != null) {
            te.clearCachedCounts();
        }
    }

    private int getHighlightedStack(int hitx, int hity) {
        int i = 0;
        for (int yy = 0; yy < 3; yy++) {
            int y = 7 + yy * 35;
            for (int xx = 0; xx < 3; xx++) {
                int x = xx * 40;

                boolean hilighted = hitx >= x + 8 && hitx <= x + 38 && hity >= y - 7 && hity <= y + 22;
                if (hilighted) {
                    return i;
                }
                i++;
            }
        }
        return -1;
    }

    @Override
    public String[] getInfo(World world, int x, int y, EntityPlayer player) {
        StorageScannerTileEntity te = getStorageScanner(dim, coordinate);
        if (te != null) {
            int i = getHighlightedStack(x, y);
            if (i != -1 && !stacks.get(i).isEmpty()) {
                return new String[]{
                        TextFormatting.GREEN + "Item: " + TextFormatting.WHITE + stacks.get(i).getDisplayName()};
            }
        }
        return new String[0];
    }

    protected void setupCoordinateFromNBT(NBTTagCompound tagCompound, int dim, BlockPos pos) {
        coordinate = BlockPosTools.INVALID;
        starred = tagCompound.getBoolean("starred");
        oredict = tagCompound.getBoolean("oredict");
        if (tagCompound.hasKey("monitorx")) {
            if (tagCompound.hasKey("monitordim")) {
                this.dim = tagCompound.getInteger("monitordim");
            } else {
                // Compatibility reasons
                this.dim = tagCompound.getInteger("dim");
            }
            BlockPos c = new BlockPos(tagCompound.getInteger("monitorx"), tagCompound.getInteger("monitory"), tagCompound.getInteger("monitorz"));
            int dx = Math.abs(c.getX() - pos.getX());
            int dy = Math.abs(c.getY() - pos.getY());
            int dz = Math.abs(c.getZ() - pos.getZ());
            coordinate = c;
        }
    }

    @Override
    public int getRfPerTick() {
        return ScreenConfiguration.STORAGE_CONTROL_RFPERTICK;
    }


    private boolean isShown(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (ItemStack s : stacks) {
            if (StorageScannerTileEntity.isItemEqual(stack, s, oredict)) {
                return true;
            }
        }
        return false;
    }


    @Override
    public NBTTagCompound update(NBTTagCompound tagCompound, World world, EntityPlayer player) {
        if (dirty >= 0) {
            NBTTagCompound newCompound = tagCompound.copy();
            NBTTagCompound tc = new NBTTagCompound();
            stacks.get(dirty).writeToNBT(tc);
            newCompound.setTag("stack" + dirty, tc);
            if (player != null) {
                SoundTools.playSound(player.getEntityWorld(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                        player.getPosition().getX(), player.getPosition().getY(), player.getPosition().getZ(), 1.0f, 1.0f);
            }
            dirty = -1;
            return newCompound;
        }
        return null;
    }

    @Override
    public void mouseClick(World world, int hitx, int hity, boolean clicked, EntityPlayer player) {
        if ((!clicked) || player == null) {
            return;
        }
        if (BlockPosTools.INVALID.equals(coordinate)) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.RED + "Module is not linked to storage scanner!"), false);
            return;
        }
        StorageScannerTileEntity scannerTileEntity = getStorageScanner(dim, coordinate);
        if (scannerTileEntity == null) {
            return;
        }
        if (hitx >= 0) {
            boolean insertStackActive = hitx >= 0 && hitx < 60 && hity > 98;
            if (insertStackActive) {
                if (isShown(player.getHeldItem(EnumHand.MAIN_HAND))) {
                    ItemStack stack = scannerTileEntity.injectStackFromScreen(player.getHeldItem(EnumHand.MAIN_HAND), player);
                    player.setHeldItem(EnumHand.MAIN_HAND, stack);
                }
                player.openContainer.detectAndSendChanges();
                return;
            }

            boolean insertAllActive = hitx >= 60 && hity > 98;
            if (insertAllActive) {
                for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                    if (isShown(player.inventory.getStackInSlot(i))) {
                        ItemStack stack = scannerTileEntity.injectStackFromScreen(player.inventory.getStackInSlot(i), player);
                        player.inventory.setInventorySlotContents(i, stack);
                    }
                }
                player.openContainer.detectAndSendChanges();
                return;
            }

            int i = getHighlightedStack(hitx, hity);
            if (i != -1) {
                if (stacks.get(i).isEmpty()) {
                    ItemStack heldItem = player.getHeldItemMainhand();
                    if (!heldItem.isEmpty()) {
                        ItemStack stack = heldItem.copy();
                        stack.setCount(1);
                        stacks.set(i, stack);
                        dirty = i;
                    }
                } else {
                    scannerTileEntity.giveToPlayerFromScreen(stacks.get(i), player.isSneaking(), player, oredict);
                }
            }
        }
    }
}
