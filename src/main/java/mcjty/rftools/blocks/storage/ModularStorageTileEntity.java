package mcjty.rftools.blocks.storage;

import mcjty.lib.container.DefaultSidedInventory;
import mcjty.lib.container.InventoryHelper;
import mcjty.lib.entity.GenericTileEntity;
import mcjty.lib.network.Argument;
import mcjty.lib.varia.ItemStackList;
import mcjty.lib.varia.NullSidedInvWrapper;
import mcjty.rftools.ClientInfo;
import mcjty.rftools.api.general.IInventoryTracker;
import mcjty.rftools.craftinggrid.*;
import mcjty.rftools.items.storage.StorageFilterCache;
import mcjty.rftools.items.storage.StorageFilterItem;
import mcjty.rftools.items.storage.StorageModuleItem;
import mcjty.rftools.items.storage.StorageTypeItem;
import mcjty.rftools.jei.JEIRecipeAcceptor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public class ModularStorageTileEntity extends GenericTileEntity implements ITickable, DefaultSidedInventory, IInventoryTracker,
        CraftingGridProvider, JEIRecipeAcceptor {

    public static final String CMD_SETTINGS = "settings";
    public static final String CMD_COMPACT = "compact";
    public static final String CMD_CYCLE = "cycle";
    public static final String CMD_CLEARGRID = "clearGrid";

    private int[] accessible = null;
    private int maxSize = 0;
    private int version = 0;

    private StorageFilterCache filterCache = null;

    private InventoryHelper inventoryHelper = new InventoryHelper(this, ModularStorageContainer.factory, ModularStorageContainer.SLOT_STORAGE + ModularStorageContainer.MAXSIZE_STORAGE);

    private CraftingGrid craftingGrid = new CraftingGrid();

    private String sortMode = "";
    private String viewMode = "";
    private boolean groupMode = false;
    private String filter = "";

    private int numStacks = -1;       // -1 means no storage cell.
    private int remoteId = 0;

    private int prevLevel = -3;     // -3 means to check, -2 means invalid
    private int timer = 10;

    private RemoteStorageTileEntity cachedRemoteStorage;
    private int cachedRemoteStorageId;

    @Override
    /// We don't use the standard McJtyLib inventory wrapper but our own
    protected boolean needsCustomInvWrapper() {
        return false;
    }

    @Override
    public void update() {
        if (!getWorld().isRemote) {
            checkStateServer();
        }
    }

    private void checkStateServer() {
        timer--;
        if (timer > 0) {
            return;
        }
        timer = 10;
        cachedRemoteStorage = null;
        cachedRemoteStorageId = -1;

        if (isRemote()) {
            // Only if we have a remote storage module do we have to do anything.
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            int si = -1;
            if (storageTileEntity != null) {
                si = storageTileEntity.findRemoteIndex(remoteId);
            }
            if (si == -1) {
                if (prevLevel != -2) {
                    prevLevel = -2;
                    clearInventory();
                }
                return;
            }

            numStacks = storageTileEntity.getCount(si);

            int newMaxSize = storageTileEntity.getMaxStacks(si);
            if (newMaxSize != maxSize) {
                setMaxSize(newMaxSize);
            }
            int level = getRenderLevel();
            if (level != prevLevel) {
                prevLevel = level;
                markDirtyClient();
            }
        }
    }

    private void clearInventory() {
        setMaxSize(0);
        numStacks = -1;
        for (int i = ModularStorageContainer.SLOT_STORAGE; i < ModularStorageContainer.MAXSIZE_STORAGE ; i++) {
            inventoryHelper.setInventorySlotContents(getInventoryStackLimit(), i, ItemStack.EMPTY);
        }
    }

    @Override
    public InventoryHelper getInventoryHelper() {
        return inventoryHelper;
    }

    @Override
    public void setRecipe(int index, ItemStack[] stacks) {
        craftingGrid.setRecipe(index, stacks);
        markDirty();
    }

    @Override
    public void storeRecipe(int index) {
        getCraftingGrid().storeRecipe(index);
    }

    @Override
    public void setGridContents(List<ItemStack> stacks) {
        for (int i = 0 ; i < stacks.size() ; i++) {
            craftingGrid.getCraftingGridInventory().setInventorySlotContents(i, stacks.get(i));
        }
        markDirty();
    }

    @Override
    public CraftingGrid getCraftingGrid() {
        return craftingGrid;
    }

    @Override
    public void markInventoryDirty() {
        markDirty();
    }

    @Override
    @Nonnull
    public int[] craft(EntityPlayer player, int n, boolean test) {
        InventoriesItemSource itemSource = new InventoriesItemSource()
                .add(player.inventory, 0).add(this, ModularStorageContainer.SLOT_STORAGE);

        if (test) {
            return StorageCraftingTools.testCraftItems(player, n, craftingGrid.getActiveRecipe(), itemSource);
        } else {
            StorageCraftingTools.craftItems(player, n, craftingGrid.getActiveRecipe(), itemSource);
            return new int[0];
        }
    }

    @Override
    public int[] getSlotsForFace(EnumFacing side) {
        if (accessible == null) {
            accessible = new int[maxSize];
            for (int i = 0 ; i < maxSize ; i++) {
                accessible[i] = ModularStorageContainer.SLOT_STORAGE + i;
            }
        }
        return accessible;
    }

    @Override
    public boolean canInsertItem(int index, ItemStack stack, EnumFacing direction) {
        return index >= ModularStorageContainer.SLOT_STORAGE && isItemValidForSlot(index, stack);
    }

    @Override
    public boolean canExtractItem(int index, ItemStack stack, EnumFacing direction) {
        return index >= ModularStorageContainer.SLOT_STORAGE && isItemValidForSlot(index, stack);
    }

    public boolean isGroupMode() {
        return groupMode;
    }

    public void setGroupMode(boolean groupMode) {
        this.groupMode = groupMode;
        markDirty();
    }

    public String getSortMode() {
        return sortMode;
    }

    public void setSortMode(String sortMode) {
        this.sortMode = sortMode;
        markDirty();
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
        markDirty();
    }

    public String getViewMode() {
        return viewMode;
    }

    public void setViewMode(String viewMode) {
        this.viewMode = viewMode;
        markDirty();
    }

    public int getMaxSize() {
        return maxSize;
    }

    @Override
    public int getSizeInventory() {
        return ModularStorageContainer.SLOT_STORAGE + maxSize;
    }

    private boolean containsItem(int index) {
        if (isStorageAvailableRemotely(index)) {
            index -= ModularStorageContainer.SLOT_STORAGE;
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return false;
            }
            ItemStackList slots = storageTileEntity.findStacksForId(remoteId);
            if (index >= slots.size()) {
                return false;
            }
            return !slots.get(index).isEmpty();
        } else {
            return inventoryHelper.containsItem(index);
        }
    }

    // On server, and if we have a remote storage module and if we're accessing a remote slot we check the remote storage.
    private boolean isStorageAvailableRemotely(int index) {
        return isServer() && isRemote() && index >= ModularStorageContainer.SLOT_STORAGE;
    }

    private boolean isRemote() {
        return remoteId != 0;
    }

    public int getRemoteId() {
        return remoteId;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        if (index >= getSizeInventory()) {
            return ItemStack.EMPTY;
        }
        if (isStorageAvailableRemotely(index)) {
            index -= ModularStorageContainer.SLOT_STORAGE;
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return ItemStack.EMPTY;
            }
            ItemStackList slots = storageTileEntity.findStacksForId(remoteId);
            if (index >= slots.size()) {
                return ItemStack.EMPTY;
            }
            return slots.get(index);
        }
        return inventoryHelper.getStackInSlot(index);
    }

    private void handleNewAmount(boolean s1, int index) {
        if (index < ModularStorageContainer.SLOT_STORAGE) {
            return;
        }
        boolean s2 = containsItem(index);
        if (s1 == s2) {
            return;
        }

        int rlold = getRenderLevel();

        if (s1) {
            numStacks--;
        } else {
            numStacks++;
        }
        StorageModuleItem.updateStackSize(getStackInSlot(ModularStorageContainer.SLOT_STORAGE_MODULE), numStacks);

        int rlnew = getRenderLevel();
        if (rlold != rlnew) {
            markDirtyClient();
        }
    }

    public int getRenderLevel() {
        if (numStacks == -1 || maxSize == 0) {
            return -1;
        }
        return (numStacks+6) * 7 / maxSize;
    }

    public int getNumStacks() {
        return numStacks;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        version++;
        if (isStorageAvailableRemotely(index)) {
            index -= ModularStorageContainer.SLOT_STORAGE;
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return ItemStack.EMPTY;
            }

            int si = storageTileEntity.findRemoteIndex(remoteId);
            if (si == -1) {
                return ItemStack.EMPTY;
            }
            storageTileEntity.updateVersion();
            return storageTileEntity.removeStackFromSlotRemote(si, index);

        } else {
            return inventoryHelper.removeStackFromSlot(index);
        }
    }

    private ItemStack decrStackSizeHelper(int index, int amount) {
        if (isStorageAvailableRemotely(index)) {
            index -= ModularStorageContainer.SLOT_STORAGE;
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return ItemStack.EMPTY;
            }

            int si = storageTileEntity.findRemoteIndex(remoteId);
            if (si == -1) {
                return ItemStack.EMPTY;
            }
            storageTileEntity.updateVersion();
            return storageTileEntity.decrStackSizeRemote(si, index, amount);
        } else {
            return inventoryHelper.decrStackSize(index, amount);
        }
    }

    @Override
    public ItemStack decrStackSize(int index, int amount) {
        version++;
        if (index == ModularStorageContainer.SLOT_STORAGE_MODULE) {
            if (!getWorld().isRemote) {
                copyToModule();
            }
        }

        boolean s1 = containsItem(index);
        ItemStack itemStack = decrStackSizeHelper(index, amount);
        handleNewAmount(s1, index);

        if (index == ModularStorageContainer.SLOT_STORAGE_MODULE) {
            ItemStack stackInSlot = inventoryHelper.getStackInSlot(ModularStorageContainer.SLOT_STORAGE_MODULE);
            // Will probably be null here. Just to be safe
            copyFromModule(stackInSlot);
        }
        return itemStack;
    }

    private void setInventorySlotContentsHelper(int limit, int index, ItemStack stack) {
        if (isStorageAvailableRemotely(index)) {
            index -= ModularStorageContainer.SLOT_STORAGE;
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return;
            }

            int si = storageTileEntity.findRemoteIndex(remoteId);
            if (si == -1) {
                return;
            }
            storageTileEntity.updateVersion();
            storageTileEntity.updateRemoteSlot(si, limit, index, stack);
        } else {
            inventoryHelper.setInventorySlotContents(getInventoryStackLimit(), index, stack);
        }
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        version++;
        if (index == ModularStorageContainer.SLOT_STORAGE_MODULE) {
            if (isServer()) {
                copyToModule();
            }
        } else if (index == ModularStorageContainer.SLOT_TYPE_MODULE) {
            // Make sure front side is updated.
            IBlockState state = getWorld().getBlockState(getPos());
            getWorld().notifyBlockUpdate(getPos(), state, state, 3);
        } else if (index == ModularStorageContainer.SLOT_FILTER_MODULE) {
            filterCache = null;
        }
        boolean s1 = containsItem(index);

        setInventorySlotContentsHelper(getInventoryStackLimit(), index, stack);

        if (index == ModularStorageContainer.SLOT_STORAGE_MODULE) {
//            if ((!isRemote()) || isServer()) {
            if (isServer()) {
                copyFromModule(stack);
            }
        }

        handleNewAmount(s1, index);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return canPlayerAccess(player);
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        if (index >= getSizeInventory()) {
            return false;
        }

        switch (index) {
            case ModularStorageContainer.SLOT_STORAGE_MODULE:
                return !stack.isEmpty() && ModularStorageSetup.storageModuleItem == stack.getItem();
            case ModularStorageContainer.SLOT_FILTER_MODULE:
                return !stack.isEmpty() && stack.getItem() instanceof StorageFilterItem;
            case ModularStorageContainer.SLOT_TYPE_MODULE:
                return !stack.isEmpty() && stack.getItem() instanceof StorageTypeItem;
        }

        if (index < ModularStorageContainer.SLOT_STORAGE) {
            return true;
        }

        if (isStorageAvailableRemotely(index)) {
            index -= ModularStorageContainer.SLOT_STORAGE;
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return false;
            }

            ItemStackList stacks = storageTileEntity.findStacksForId(remoteId);
            if (index >= stacks.size()) {
                return false;
            }
        }

        if (inventoryHelper.containsItem(ModularStorageContainer.SLOT_FILTER_MODULE)) {
            getFilterCache();
            if (filterCache != null) {
                return filterCache.match(stack);
            }
        }

        return true;
    }

    private void getFilterCache() {
        if (filterCache == null) {
            filterCache = StorageFilterItem.getCache(inventoryHelper.getStackInSlot(ModularStorageContainer.SLOT_FILTER_MODULE));
        }
    }

    public void copyToModule() {
        ItemStack stack = inventoryHelper.getStackInSlot(ModularStorageContainer.SLOT_STORAGE_MODULE);
        if (stack.isEmpty()) {
            // Should be impossible.
            return;
        }

        if (stack.getItemDamage() == StorageModuleItem.STORAGE_REMOTE) {
            remoteId = 0;
            return;
        }
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
            stack.setTagCompound(tagCompound);
        }
        int cnt = writeBufferToItemNBT(tagCompound);
        tagCompound.setInteger("count", cnt);
    }

    public void copyFromModule(ItemStack stack) {
        for (int i = ModularStorageContainer.SLOT_STORAGE ; i < inventoryHelper.getCount() ; i++) {
            inventoryHelper.setInventorySlotContents(0, i, ItemStack.EMPTY);
        }

        if (stack.isEmpty()) {
            clearInventory();
            return;
        }

        remoteId = 0;
        if (stack.getItemDamage() == StorageModuleItem.STORAGE_REMOTE) {
            NBTTagCompound tagCompound = stack.getTagCompound();
            if (tagCompound == null || !tagCompound.hasKey("id")) {
                clearInventory();
                return;
            }
            remoteId = tagCompound.getInteger("id");
            RemoteStorageTileEntity remoteStorageTileEntity = getRemoteStorage(remoteId);
            if (remoteStorageTileEntity == null) {
                clearInventory();
                return;
            }
            ItemStack storageStack = remoteStorageTileEntity.findStorageWithId(remoteId);
            if (storageStack.isEmpty()) {
                clearInventory();
                return;
            }

            setMaxSize(StorageModuleItem.MAXSIZE[storageStack.getItemDamage()]);
        } else {
            setMaxSize(StorageModuleItem.MAXSIZE[stack.getItemDamage()]);
            NBTTagCompound tagCompound = stack.getTagCompound();
            if (tagCompound != null) {
                readBufferFromItemNBT(tagCompound);
            }
        }

        updateStackCount();
    }

    private RemoteStorageTileEntity getRemoteStorage(int id) {
        if (id != cachedRemoteStorageId) {
            cachedRemoteStorage = null;
        }
        if (cachedRemoteStorage != null) {
            return cachedRemoteStorage;
        }

        World world = getWorldSafe();
        cachedRemoteStorage = RemoteStorageIdRegistry.getRemoteStorage(world, id);
        if (cachedRemoteStorage != null) {
            cachedRemoteStorageId = id;
        } else {
            cachedRemoteStorageId = -1;
        }

        return cachedRemoteStorage;
    }

    private void updateStackCount() {
        numStacks = 0;
        if (isServer() && isRemote()) {
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return;
            }
            int si = storageTileEntity.findRemoteIndex(remoteId);
            if (si == -1) {
                return;
            }
            ItemStackList stacks = storageTileEntity.getRemoteStacks(si);
            for (int i = 0 ; i < Math.min(maxSize, stacks.size()) ; i++) {
                if (!stacks.get(i).isEmpty()) {
                    numStacks++;
                }
            }
            storageTileEntity.updateCount(si, numStacks);
        } else {
            for (int i = ModularStorageContainer.SLOT_STORAGE; i < ModularStorageContainer.SLOT_STORAGE + maxSize; i++) {
                if (inventoryHelper.containsItem(i)) {
                    numStacks++;
                }
            }
        }
        StorageModuleItem.updateStackSize(getStackInSlot(ModularStorageContainer.SLOT_STORAGE_MODULE), numStacks);
    }

    private boolean isServer() {
        if (getWorld() != null) {
            return !getWorld().isRemote;
        } else {
            return FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER;
        }
    }

    private World getWorldSafe() {
        World world = getWorld();
        if (world == null) {
            if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
                world = ClientInfo.getWorld();
            } else {
                world = DimensionManager.getWorld(0);
            }
        }
        return world;
    }



    private void setMaxSize(int ms) {
        maxSize = ms;
        inventoryHelper.setNewCount(ModularStorageContainer.SLOT_STORAGE + maxSize);
        accessible = null;

        markDirtyClient();
    }

    @Override
    public void readClientDataFromNBT(NBTTagCompound tagCompound) {
        numStacks = tagCompound.getInteger("numStacks");
        maxSize = tagCompound.getInteger("maxSize");
        remoteId = tagCompound.getInteger("remoteId");
        inventoryHelper.setNewCount(ModularStorageContainer.SLOT_STORAGE + maxSize);
    }



    @Override
    public void writeClientDataToNBT(NBTTagCompound tagCompound) {
        tagCompound.setInteger("numStacks", numStacks);
        tagCompound.setInteger("maxSize", maxSize);
        tagCompound.setInteger("remoteId", remoteId);
    }

    /**
     * Called from the container (detectAndSendChanges) and executed on the client.
     */
    public void syncInventoryFromServer(int maxSize, int numStacks, String sortMode, String viewMode, boolean groupMode, String filter) {
        this.sortMode = sortMode;
        this.viewMode = viewMode;
        this.groupMode = groupMode;
        this.filter = filter;
        this.numStacks = numStacks;
        this.maxSize = maxSize;
        int newcount = ModularStorageContainer.SLOT_STORAGE + maxSize;
        if (newcount != inventoryHelper.getCount()) {
            inventoryHelper.setNewCount(newcount);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);
    }

    @Override
    public void readRestorableFromNBT(NBTTagCompound tagCompound) {
        super.readRestorableFromNBT(tagCompound);
        numStacks = tagCompound.getInteger("numStacks");
        maxSize = tagCompound.getInteger("maxSize");
        remoteId = tagCompound.getInteger("remoteId");
        sortMode = tagCompound.getString("sortMode");
        viewMode = tagCompound.getString("viewMode");
        groupMode = tagCompound.getBoolean("groupMode");
        version = tagCompound.getInteger("version");
        filter = tagCompound.getString("filter");
        inventoryHelper.setNewCount(ModularStorageContainer.SLOT_STORAGE + maxSize);
        accessible = null;
        readBufferFromNBT(tagCompound);
        craftingGrid.readFromNBT(tagCompound.getCompoundTag("grid"));

        if (isServer()) {
            updateStackCount();
        }
    }

    private void readBufferFromItemNBT(NBTTagCompound tagCompound) {
        NBTTagList bufferTagList = tagCompound.getTagList("Items", Constants.NBT.TAG_COMPOUND);
        for (int i = 0 ; i < bufferTagList.tagCount() ; i++) {
            NBTTagCompound nbtTagCompound = bufferTagList.getCompoundTagAt(i);
            inventoryHelper.setStackInSlot(i+ModularStorageContainer.SLOT_STORAGE, new ItemStack(nbtTagCompound));
        }
    }

    private void readBufferFromNBT(NBTTagCompound tagCompound) {
        NBTTagList bufferTagList = tagCompound.getTagList("Items", Constants.NBT.TAG_COMPOUND);
        if (tagCompound.hasKey("SlotStorage")) {
            // This is a new TE with separate NBT tags for the three special slots.
            for (int i = 0 ; i < bufferTagList.tagCount() ; i++) {
                NBTTagCompound nbtTagCompound = bufferTagList.getCompoundTagAt(i);
                inventoryHelper.setStackInSlot(i+ModularStorageContainer.SLOT_STORAGE, new ItemStack(nbtTagCompound));
            }
            inventoryHelper.setStackInSlot(ModularStorageContainer.SLOT_STORAGE_MODULE, new ItemStack(tagCompound.getCompoundTag("SlotStorage")));
            inventoryHelper.setStackInSlot(ModularStorageContainer.SLOT_TYPE_MODULE, new ItemStack(tagCompound.getCompoundTag("SlotType")));
            inventoryHelper.setStackInSlot(ModularStorageContainer.SLOT_FILTER_MODULE, new ItemStack(tagCompound.getCompoundTag("SlotFilter")));
        } else {
            // This is an old TE so we have to convert this to the new format.
            int index = 0;
            for (int i = 0 ; i < bufferTagList.tagCount() ; i++) {
                NBTTagCompound nbtTagCompound = bufferTagList.getCompoundTagAt(i);
                inventoryHelper.setStackInSlot(index, new ItemStack(nbtTagCompound));
                index++;
                if (index == ModularStorageContainer.SLOT_FILTER_MODULE) {
                    index++;    // Skip this slot since this TE will not have that.
                }
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
        super.writeToNBT(tagCompound);
        return tagCompound;
    }

    @Override
    public void writeRestorableToNBT(NBTTagCompound tagCompound) {
        super.writeRestorableToNBT(tagCompound);
        writeBufferToNBT(tagCompound);
        writeSlot(tagCompound, ModularStorageContainer.SLOT_STORAGE_MODULE, "SlotStorage");
        writeSlot(tagCompound, ModularStorageContainer.SLOT_TYPE_MODULE, "SlotType");
        writeSlot(tagCompound, ModularStorageContainer.SLOT_FILTER_MODULE, "SlotFilter");
        tagCompound.setInteger("numStacks", numStacks);
        tagCompound.setInteger("maxSize", maxSize);
        tagCompound.setInteger("remoteId", remoteId);
        tagCompound.setString("sortMode", sortMode);
        tagCompound.setString("viewMode", viewMode);
        tagCompound.setBoolean("groupMode", groupMode);
        tagCompound.setString("filter", filter);
        tagCompound.setInteger("version", version);
        tagCompound.setTag("grid", craftingGrid.writeToNBT());
    }

    private void writeSlot(NBTTagCompound tagCompound, int index, String name) {
        NBTTagCompound nbtTagCompound = new NBTTagCompound();
        ItemStack stack = inventoryHelper.getStackInSlot(index);
        if (!stack.isEmpty()) {
            stack.writeToNBT(nbtTagCompound);
        }
        tagCompound.setTag(name, nbtTagCompound);
    }

    private void writeBufferToNBT(NBTTagCompound tagCompound) {
        // If sendToClient is true we have to send dummy information to the client
        // so that it can remotely open gui's.
        boolean sendToClient = isServer() && isRemote();

        NBTTagList bufferTagList = new NBTTagList();
        if (sendToClient) {
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity != null) {
                ItemStackList slots = storageTileEntity.findStacksForId(remoteId);
                for (ItemStack stack : slots) {
                    NBTTagCompound nbtTagCompound = new NBTTagCompound();
                    if (!stack.isEmpty()) {
                        stack.writeToNBT(nbtTagCompound);
                    }
                    bufferTagList.appendTag(nbtTagCompound);
                }
            }
        } else {
            for (int i = ModularStorageContainer.SLOT_STORAGE; i < inventoryHelper.getCount(); i++) {
                ItemStack stack = inventoryHelper.getStackInSlot(i);
                NBTTagCompound nbtTagCompound = new NBTTagCompound();
                if (!stack.isEmpty()) {
                    stack.writeToNBT(nbtTagCompound);
                }
                bufferTagList.appendTag(nbtTagCompound);
            }
        }
        tagCompound.setTag("Items", bufferTagList);
    }

    private int writeBufferToItemNBT(NBTTagCompound tagCompound) {
        int cnt = 0;
        NBTTagList bufferTagList = new NBTTagList();
        for (int i = ModularStorageContainer.SLOT_STORAGE; i < inventoryHelper.getCount(); i++) {
            ItemStack stack = inventoryHelper.getStackInSlot(i);
            NBTTagCompound nbtTagCompound = new NBTTagCompound();
            if (!stack.isEmpty()) {
                stack.writeToNBT(nbtTagCompound);
                // @todo check?
                if (stack.getCount() > 0) {
                    cnt++;
                }
            }
            bufferTagList.appendTag(nbtTagCompound);
        }
        tagCompound.setTag("Items", bufferTagList);
        return cnt;
    }

    @Override
    public boolean execute(EntityPlayerMP playerMP, String command, Map<String, Argument> args) {
        boolean rc = super.execute(playerMP, command, args);
        if (rc) {
            return true;
        }
        if (CMD_SETTINGS.equals(command)) {
            setFilter(args.get("filter").getString());
            setViewMode(args.get("viewMode").getString());
            setSortMode(args.get("sortMode").getString());
            setGroupMode(args.get("groupMode").getBoolean());
            markDirtyClient();
            return true;
        } else if (CMD_COMPACT.equals(command)) {
            compact();
            return true;
        } else if (CMD_CYCLE.equals(command)) {
            cycle();
            return true;
        } else if (CMD_CLEARGRID.equals(command)) {
            clearGrid();
            return true;
        }
        return false;
    }

    private void clearGrid() {
        CraftingGridInventory inventory = craftingGrid.getCraftingGridInventory();
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            inventory.setInventorySlotContents(i, ItemStack.EMPTY);
        }
        markDirty();
    }

    private void cycle() {
        if (isRemote()) {
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return;
            }
            remoteId = storageTileEntity.cycle(remoteId);
            getStackInSlot(ModularStorageContainer.SLOT_STORAGE_MODULE).getTagCompound().setInteger("id", remoteId);
            markDirtyClient();
        }
    }

    private void compact() {
        if (isRemote()) {
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return;
            }
            storageTileEntity.compact(remoteId);
        } else {
            InventoryHelper.compactStacks(inventoryHelper, ModularStorageContainer.SLOT_STORAGE, maxSize);
        }

        updateStackCount();
        markDirtyClient();
    }

    @Override
    public int getVersion() {
        if (isRemote()) {
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return version;
            }
            return storageTileEntity.getVersion();
        } else {
            return version;
        }
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            // We always use NullSidedInvWrapper because we don't want automation
            // to access the storage slots
            if (invHandlerNull == null) {
                invHandlerNull = new NullSidedInvWrapper(this);
            }
            return (T) invHandlerNull;
        }
        return super.getCapability(capability, facing);
    }

}
