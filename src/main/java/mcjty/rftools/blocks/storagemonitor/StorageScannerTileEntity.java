package mcjty.rftools.blocks.storagemonitor;

import com.google.common.base.Function;
import mcjty.lib.bindings.DefaultAction;
import mcjty.lib.bindings.DefaultValue;
import mcjty.lib.bindings.IAction;
import mcjty.lib.bindings.IValue;
import mcjty.lib.container.DefaultSidedInventory;
import mcjty.lib.container.InventoryHelper;
import mcjty.lib.tileentity.GenericEnergyReceiverTileEntity;
import mcjty.lib.typed.Key;
import mcjty.lib.typed.Type;
import mcjty.lib.typed.TypedMap;
import mcjty.lib.varia.*;
import mcjty.rftools.RFTools;
import mcjty.rftools.api.general.IInventoryTracker;
import mcjty.rftools.api.storage.IStorageScanner;
import mcjty.rftools.craftinggrid.*;
import mcjty.rftools.compat.jei.JEIRecipeAcceptor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class StorageScannerTileEntity extends GenericEnergyReceiverTileEntity implements DefaultSidedInventory, ITickable,
        CraftingGridProvider, JEIRecipeAcceptor, IStorageScanner {

    public static final String CMD_SCANNER_INFO = "getScannerInfo";
    public static final Key<Long> PARAM_ENERGY = new Key<>("energy", Type.LONG);
    public static final Key<Boolean> PARAM_EXPORT = new Key<>("export", Type.BOOLEAN);

    public static final String CMD_UP = "scanner.up";
    public static final String CMD_TOP = "scanner.top";
    public static final String CMD_DOWN = "scanner.down";
    public static final String CMD_BOTTOM = "scanner.bottom";
    public static final String CMD_REMOVE = "scanner.remove";
    public static final String CMD_TOGGLEROUTABLE = "scanner.toggleRoutable";
    public static final String CMD_SETVIEW = "scanner.setView";
    public static final String CMD_UPDATESORTMODE = "scanner.updateSortMode";

    public static final Key<Integer> PARAM_INDEX = new Key<>("index", Type.INTEGER);
    public static final Key<BlockPos> PARAM_POS = new Key<>("pos", Type.BLOCKPOS);
    public static final Key<Boolean> PARAM_VIEW = new Key<>("view", Type.BOOLEAN);
    public static final Key<String> PARAM_SORTMODE = new Key<>("sortmode", Type.STRING);

    public static final String ACTION_CLEARGRID = "clearGrid";

    public static final Key<Boolean> VALUE_EXPORT = new Key<>("export", Type.BOOLEAN);
    public static final Key<Integer> VALUE_RADIUS = new Key<>("radius", Type.INTEGER);

    // Client side data returned by CMD_SCANNER_INFO
    public static long rfReceived = 0;
    public static boolean exportToCurrentReceived = false;
    
    private String sortMode = "";

    @Override
    public IAction[] getActions() {
        return new IAction[] {
                new DefaultAction(ACTION_CLEARGRID, this::clearGrid),
        };
    }

    @Override
    public IValue<?>[] getValues() {
        return new IValue[] {
                new DefaultValue<>(VALUE_EXPORT, this::isExportToCurrent, this::setExportToCurrent),
                new DefaultValue<>(VALUE_RADIUS, this::getRadius, this::setRadius),
        };
    }

    private static final int[] SLOTS = new int[]{0, 1, 2};

    public static final int XNETDELAY = 40;

    private List<BlockPos> inventories = new ArrayList<>();
    private Set<BlockPos> inventoriesFromXNet = new HashSet<>();

    // This data is fed directly by the storage channel system (XNet) and is
    // cleared automatically if that system stops or is disabled
    private Map<BlockPos, InventoryAccessSettings> xnetAccess = Collections.emptyMap();
    private int xnetDelay = XNETDELAY;      // Timer to control when to clear the above

    private Map<CachedItemKey, CachedItemCount> cachedCounts = new HashMap<>();
    private Set<BlockPos> routable = new HashSet<>();
    private int radius = 1;

    // This is set on a client-side dummy tile entity for a tablet
    private Integer monitorDim;

    private boolean exportToCurrent = false;
    private BlockPos lastSelectedInventory = null;

    // Indicates if for this storage scanner the inventories should be shown wide
    private boolean openWideView = true;

    private InventoryHelper inventoryHelper = new InventoryHelper(this, StorageScannerContainer.factory, 3);    // 1 extra slot for automation is at index 2
    private CraftingGrid craftingGrid = new CraftingGrid();

    public StorageScannerTileEntity() {
        super(StorageScannerConfiguration.MAXENERGY.get(), StorageScannerConfiguration.RECEIVEPERTICK.get());
        monitorDim = null;
        radius = (StorageScannerConfiguration.xnetRequired.get() && RFTools.setup.xnet) ? 0 : 1;
    }

    // This constructor is used for constructing a dummy client-side tile entity when
    // accessing the storage scanner remotely
    public StorageScannerTileEntity(EntityPlayer entityPlayer, int monitordim) {
        super(StorageScannerConfiguration.MAXENERGY.get(), StorageScannerConfiguration.RECEIVEPERTICK.get());
//        this.entityPlayer = entityPlayer;
        this.monitorDim = monitordim;
    }

    @Override
    protected boolean needsCustomInvWrapper() {
        return true;
    }

    @Override
    public void storeRecipe(int index) {
        getCraftingGrid().storeRecipe(index);
    }

    @Override
    public void setRecipe(int index, ItemStack[] stacks) {
        craftingGrid.setRecipe(index, stacks);
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
        CraftingRecipe activeRecipe = craftingGrid.getActiveRecipe();
        return craft(player, n, test, activeRecipe);
    }

    @Nonnull
    public int[] craft(EntityPlayer player, int n, boolean test, CraftingRecipe activeRecipe) {
        TileEntityItemSource itemSource = new TileEntityItemSource()
                .addInventory(player.inventory, 0);
        inventories.stream()
                .filter(p -> isOutputFromGui(p) && isRoutable(p))
                .forEachOrdered(p -> {
                    TileEntity tileEntity = getWorld().getTileEntity(p);
                    if (!(tileEntity instanceof StorageScannerTileEntity)) {
                        itemSource.add(tileEntity, 0);
                    }
                });

        if (test) {
            return StorageCraftingTools.testCraftItems(player, n, activeRecipe, itemSource);
        } else {
            StorageCraftingTools.craftItems(player, n, activeRecipe, itemSource);
            return new int[0];
        }
    }

    @Override
    public void setGridContents(List<ItemStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            craftingGrid.getCraftingGridInventory().setInventorySlotContents(i, stacks.get(i));
        }
        markDirty();
    }

    @Override
    public void update() {
        if (!getWorld().isRemote) {
            xnetDelay--;
            if (xnetDelay < 0) {
                // If there was no update from XNet for a while then we assume we no longer have information
                xnetAccess = Collections.emptyMap();
                xnetDelay = XNETDELAY;
            }

            if (inventoryHelper.containsItem(StorageScannerContainer.SLOT_IN)) {
                if (getStoredPower() < StorageScannerConfiguration.rfPerInsert.get()) {
                    return;
                }

                ItemStack stack = inventoryHelper.getStackInSlot(StorageScannerContainer.SLOT_IN);
                stack = injectStackInternal(stack, exportToCurrent, this::isInputFromGui);
                inventoryHelper.setInventorySlotContents(64, StorageScannerContainer.SLOT_IN, stack);

                consumeEnergy(StorageScannerConfiguration.rfPerInsert.get());
            }
            if (inventoryHelper.containsItem(StorageScannerContainer.SLOT_IN_AUTO)) {
                if (getStoredPower() < StorageScannerConfiguration.rfPerInsert.get()) {
                    return;
                }

                ItemStack stack = inventoryHelper.getStackInSlot(StorageScannerContainer.SLOT_IN_AUTO);
                stack = injectStackInternal(stack, false, this::isInputFromAuto);
                inventoryHelper.setInventorySlotContents(64, StorageScannerContainer.SLOT_IN_AUTO, stack);

                consumeEnergy(StorageScannerConfiguration.rfPerInsert.get());
            }
        }
    }


    public ItemStack injectStackFromScreen(ItemStack stack, EntityPlayer player) {
        if (getStoredPower() < StorageScannerConfiguration.rfPerInsert.get()) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.RED + "Not enough power to insert items!"), false);
            return stack;
        }
        if (!checkForRoutableInventories()) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.RED + "There are no routable inventories!"), false);
            return stack;
        }
        stack = injectStackInternal(stack, false, this::isInputFromScreen);
        if (stack.isEmpty()) {
            consumeEnergy(StorageScannerConfiguration.rfPerInsert.get());
            SoundTools.playSound(getWorld(), SoundEvents.ENTITY_ITEM_PICKUP, getPos().getX(), getPos().getY(), getPos().getZ(), 1.0f, 3.0f);
        }
        return stack;
    }

    private boolean checkForRoutableInventories() {
        return inventories.stream()
                .filter(p -> isValid(p) && (!p.equals(getPos()) && isRoutable(p)) && WorldTools.chunkLoaded(getWorld(), p))
                .anyMatch(p -> getWorld().getTileEntity(p) != null);
    }

    private ItemStack injectStackInternal(ItemStack stack, boolean toSelected, @Nonnull Function<BlockPos, Boolean> testAccess) {
        if (toSelected && lastSelectedInventory != null && lastSelectedInventory.getY() != -1) {
            // Try to insert into the selected inventory
            TileEntity te = getWorld().getTileEntity(lastSelectedInventory);
            if (te != null && !(te instanceof StorageScannerTileEntity)) {
                if (testAccess.apply(lastSelectedInventory) && getInputMatcher(lastSelectedInventory).test(stack)) {
                    stack = InventoryHelper.insertItem(getWorld(), lastSelectedInventory, null, stack);
                    if (stack.isEmpty()) {
                        return stack;
                    }
                }
            }
            return stack;
        }
        final ItemStack finalStack = stack;
        Iterator<TileEntity> iterator = inventories.stream()
                .filter(p -> testAccess.apply(p) && !p.equals(getPos()) && isRoutable(p) && WorldTools.chunkLoaded(getWorld(), p) && getInputMatcher(p).test(finalStack))
                .map(getWorld()::getTileEntity)
                .filter(te -> te != null && !(te instanceof StorageScannerTileEntity))
                .iterator();
        while (!stack.isEmpty() && iterator.hasNext()) {
            TileEntity te = iterator.next();
            stack = InventoryHelper.insertItem(getWorld(), te.getPos(), null, stack);
        }
        return stack;
    }

    /**
     * Give a stack matching the input stack to the player containing either a single
     * item or else a full stack
     *
     * @param stack
     * @param single
     * @param player
     */
    public void giveToPlayerFromScreen(ItemStack stack, boolean single, EntityPlayer player, boolean oredict) {
        if (stack.isEmpty()) {
            return;
        }
        if (getStoredPower() < StorageScannerConfiguration.rfPerRequest.get()) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.RED + "Not enough power to request items!"), false);
            return;
        }

        Set<Integer> oredictMatches = getOredictMatchers(stack, oredict);
        final int[] cnt = {single ? 1 : stack.getMaxStackSize()};
        int orig = cnt[0];
        inventories.stream()
                .filter(this::isOutputFromScreen)
                .map(this::getItemHandlerAt)
                .filter(Objects::nonNull)
                .forEachOrdered(handler -> {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack itemStack = handler.getStackInSlot(i);
                        if (isItemEqual(stack, itemStack, oredictMatches)) {
                            ItemStack received = handler.extractItem(i, cnt[0], false);
                            giveItemToPlayer(player, cnt, received);
                        }
                    }
                });
        if (orig != cnt[0]) {
            consumeEnergy(StorageScannerConfiguration.rfPerRequest.get());
            SoundTools.playSound(getWorld(), SoundEvents.ENTITY_ITEM_PICKUP, getPos().getX(), getPos().getY(), getPos().getZ(), 1.0f, 1.0f);
        }
    }

    private boolean giveItemToPlayer(EntityPlayer player, int[] cnt, ItemStack received) {
        if (!received.isEmpty() && cnt[0] > 0) {
            cnt[0] -= received.getCount();
            giveToPlayer(received, player);
            return true;
        }
        return false;
    }

    private boolean giveToPlayer(ItemStack stack, EntityPlayer player) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!player.inventory.addItemStackToInventory(stack)) {
            player.entityDropItem(stack, 1.05f);
        }
        return true;
    }

    @Override
    public int countItems(Predicate<ItemStack> matcher, boolean starred, @Nullable Integer maxneeded) {
        final int[] cc = {0};
        inventories.stream()
                .filter(p -> isValid(p) && ((!starred) || isRoutable(p)) && WorldTools.chunkLoaded(getWorld(), p))
                .map(getWorld()::getTileEntity)
                .filter(te -> te != null && !(te instanceof StorageScannerTileEntity))
                .allMatch(te -> {
                    InventoryHelper.getItems(te, matcher)
                            .forEach(s -> cc[0] += s.getCount());
                    if (maxneeded != null && cc[0] >= maxneeded) {
                        return false;
                    }
                    return true;
                });
        return cc[0];
    }


    @Override
    public int countItems(ItemStack match, boolean routable, boolean oredict) {
        return countItems(match, routable, oredict, null);
    }

    @Override
    public int countItems(ItemStack stack, boolean starred, boolean oredict, @Nullable Integer maxneeded) {
        if (stack.isEmpty()) {
            return 0;
        }
        Set<Integer> oredictMatches = getOredictMatchers(stack, oredict);
        Iterator<TileEntity> iterator = inventories.stream()
                .filter(p -> isValid(p) && ((!starred) || isRoutable(p)) && WorldTools.chunkLoaded(getWorld(), p))
                .map(getWorld()::getTileEntity)
                .filter(te -> te != null && !(te instanceof StorageScannerTileEntity))
                .iterator();

        int cnt = 0;
        while (iterator.hasNext()) {
            TileEntity te = iterator.next();
            Integer cachedCount = null;
            if (te instanceof IInventoryTracker) {
                IInventoryTracker tracker = (IInventoryTracker) te;
                CachedItemCount itemCount = cachedCounts.get(new CachedItemKey(te.getPos(), stack.getItem(), stack.getMetadata()));
                if (itemCount != null) {
                    Integer oldVersion = itemCount.getVersion();
                    if (oldVersion == tracker.getVersion()) {
                        cachedCount = itemCount.getCount();
                    }
                }
            }
            if (cachedCount != null) {
                cnt += cachedCount;
            } else {
                final int[] cc = {0};
                InventoryHelper.getItems(te, s -> isItemEqual(stack, s, oredictMatches))
                        .forEach(s -> cc[0] += s.getCount());
                if (te instanceof IInventoryTracker) {
                    IInventoryTracker tracker = (IInventoryTracker) te;
                    cachedCounts.put(new CachedItemKey(te.getPos(), stack.getItem(), stack.getMetadata()), new CachedItemCount(tracker.getVersion(), cc[0]));
                }
                cnt += cc[0];
            }
            if (maxneeded != null && cnt >= maxneeded) {
                break;
            }
        }

        return cnt;
    }

    private static Set<Integer> getOredictMatchers(ItemStack stack, boolean oredict) {
        Set<Integer> oredictMatches = new HashSet<>();
        if (oredict) {
            for (int id : OreDictionary.getOreIDs(stack)) {
                oredictMatches.add(id);
            }
        }
        return oredictMatches;
    }

    public static boolean isItemEqual(ItemStack thisItem, ItemStack other, boolean oredict) {
        return isItemEqual(thisItem, other, getOredictMatchers(thisItem, oredict));
    }

    public static boolean isItemEqual(ItemStack thisItem, ItemStack other, Set<Integer> oreDictMatchers) {
        if (other.isEmpty()) {
            return false;
        }
        if (oreDictMatchers.isEmpty()) {
            return thisItem.isItemEqual(other);
        } else {
            int[] oreIDs = OreDictionary.getOreIDs(other);
            for (int id : oreIDs) {
                if (oreDictMatchers.contains(id)) {
                    return true;
                }
            }
        }
        return false;
    }


    public Set<BlockPos> performSearch(String search) {

        Predicate<ItemStack> matcher = getMatcher(search);

        Set<BlockPos> output = new HashSet<>();
        Predicate<ItemStack> finalMatcher = matcher;
        inventories.stream()
                .filter(this::isValid)
                .map(getWorld()::getTileEntity)
                .filter(te -> te != null && !(te instanceof StorageScannerTileEntity))
                .forEach(te -> InventoryHelper.getItems(te, finalMatcher).forEach(s -> output.add(te.getPos())));
        return output;
    }

    public static Predicate<ItemStack> getMatcher(String search) {
        Predicate<ItemStack> matcher = null;
        search = search.toLowerCase();

        String[] splitted = StringUtils.split(search);
        for (String split : splitted) {
            if (matcher == null) {
                matcher = makeSearchPredicate(split);
            } else {
                matcher = matcher.and(makeSearchPredicate(split));
            }
        }
        if (matcher == null) {
            matcher = s -> true;
        }
        return matcher;
    }

    private static Predicate<ItemStack> makeSearchPredicate(String split) {
        if (split.startsWith("@")) {
            return s -> BlockTools.getModid(s).toLowerCase().startsWith(split.substring(1));
        } else {
            return s -> s.getDisplayName().toLowerCase().contains(split);
        }
    }
    
    public int getRadius() {
        return radius;
    }

    public void setRadius(int v) {
        radius = v;
        if (StorageScannerConfiguration.xnetRequired.get() && RFTools.setup.xnet) {
            radius = 0;
        }
        markDirtyClient();
    }

    public boolean isOpenWideView() {
        return openWideView;
    }

    public void setOpenWideView(boolean openWideView) {
        this.openWideView = openWideView;
        markDirtyClient();
    }

    public boolean isExportToCurrent() {
        return exportToCurrent;
    }

    public void setExportToCurrent(boolean exportToCurrent) {
        this.exportToCurrent = exportToCurrent;
        markDirtyClient();
    }

    private void toggleExportRoutable() {
        exportToCurrent = !exportToCurrent;
        markDirtyClient();
    }

    public boolean isRoutable(BlockPos p) {
        return routable.contains(p);
    }

    public boolean isValid(BlockPos p) {
        if (xnetAccess.containsKey(p)) {
            return true;
        }
        return !inventoriesFromXNet.contains(p);
    }

    public boolean isOutputFromGui(BlockPos p) {
        InventoryAccessSettings settings = xnetAccess.get(p);
        if (settings != null) {
            return !settings.isBlockOutputGui();
        }
        return !inventoriesFromXNet.contains(p);
    }

    public boolean isOutputFromScreen(BlockPos p) {
        InventoryAccessSettings settings = xnetAccess.get(p);
        if (settings != null) {
            return !settings.isBlockOutputScreen();
        }
        return !inventoriesFromXNet.contains(p);
    }

    public boolean isOutputFromAuto(BlockPos p) {
        InventoryAccessSettings settings = xnetAccess.get(p);
        if (settings != null) {
            return !settings.isBlockOutputAuto();
        }
        return !inventoriesFromXNet.contains(p);
    }

    public Predicate<ItemStack> getInputMatcher(BlockPos p) {
        InventoryAccessSettings settings = xnetAccess.get(p);
        if (settings != null) {
            return settings.getMatcher();
        }
        return stack -> true;
    }

    public boolean isInputFromGui(BlockPos p) {
        InventoryAccessSettings settings = xnetAccess.get(p);
        if (settings != null) {
            return !settings.isBlockInputGui();
        }
        return !inventoriesFromXNet.contains(p);
    }

    public boolean isInputFromScreen(BlockPos p) {
        InventoryAccessSettings settings = xnetAccess.get(p);
        if (settings != null) {
            return !settings.isBlockInputScreen();
        }
        return !inventoriesFromXNet.contains(p);
    }

    public boolean isInputFromAuto(BlockPos p) {
        InventoryAccessSettings settings = xnetAccess.get(p);
        if (settings != null) {
            return !settings.isBlockInputAuto();
        }
        return !inventoriesFromXNet.contains(p);
    }

    public void toggleRoutable(BlockPos p) {
        if (routable.contains(p)) {
            routable.remove(p);
        } else {
            routable.add(p);
        }
        markDirtyClient();
    }

    public void register(Map<BlockPos, InventoryAccessSettings> access) {
        xnetAccess = access;
        xnetDelay = XNETDELAY;
    }

    private void moveUp(int index) {
        if (index <= 0) {
            return;
        }
        if (index >= inventories.size()) {
            return;
        }
        BlockPos p1 = inventories.get(index - 1);
        BlockPos p2 = inventories.get(index);
        inventories.set(index - 1, p2);
        inventories.set(index, p1);
        markDirty();
    }

    private void moveTop(int index) {
        if (index <= 0) {
            return;
        }
        if (index >= inventories.size()) {
            return;
        }
        BlockPos p = inventories.get(index);
        inventories.remove(index);
        inventories.add(0, p);
        markDirty();
    }

    private void moveDown(int index) {
        if (index < 0) {
            return;
        }
        if (index >= inventories.size() - 1) {
            return;
        }
        BlockPos p1 = inventories.get(index);
        BlockPos p2 = inventories.get(index + 1);
        inventories.set(index, p2);
        inventories.set(index + 1, p1);
        markDirty();
    }

    private void moveBottom(int index) {
        if (index < 0) {
            return;
        }
        if (index >= inventories.size() - 1) {
            return;
        }
        BlockPos p = inventories.get(index);
        inventories.remove(index);
        inventories.add(p);
        markDirty();
    }

    private void removeInventory(int index) {
        if (index < 0) {
            return;
        }
        if (index >= inventories.size()) {
            return;
        }
        BlockPos p = inventories.get(index);
        if (inventoriesFromXNet.contains(p)) {
            // Cannot remove inventories from xnet
            return;
        }
        inventories.remove(index);
        markDirty();
    }

    public void clearCachedCounts() {
        cachedCounts.clear();
    }

    public Stream<BlockPos> findInventories() {
        if (RFTools.setup.xnet && StorageScannerConfiguration.xnetRequired.get()) {
            radius = 0;
        }

        // Clear the caches
        cachedCounts.clear();
        inventoriesFromXNet.clear();

        // First remove all inventories that are either out of range or no longer an inventory:
        List<BlockPos> old = inventories;
        Set<BlockPos> oldAdded = new HashSet<>();
        Set<IItemHandler> seenItemHandlers = new HashSet<>();
        inventories = new ArrayList<>();

        for (BlockPos p : old) {
            if (xnetAccess.containsKey(p) || inRange(p)) {
                TileEntity te = getWorld().getTileEntity(p);
                if (InventoryHelper.isInventory(te) && !(te instanceof StorageScannerTileEntity)) {
                    IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                    if (handler == null || seenItemHandlers.add(handler)) {
                        inventories.add(p);
                        oldAdded.add(p);
                    }
                }
            }
        }

        // Now append all inventories that are new.
        for (int x = getPos().getX() - radius; x <= getPos().getX() + radius; x++) {
            for (int z = getPos().getZ() - radius; z <= getPos().getZ() + radius; z++) {
                for (int y = getPos().getY() - radius; y <= getPos().getY() + radius; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    inventoryAddNew(oldAdded, seenItemHandlers, p);
                }
            }
        }
        for (BlockPos p : xnetAccess.keySet()) {
            inventoryAddNew(oldAdded, seenItemHandlers, p);
            inventoriesFromXNet.add(p);
        }

        return getAllInventories();
    }

    public Stream<BlockPos> getAllInventories() {
        return inventories.stream()
                .filter(this::isValid);
    }

    private void inventoryAddNew(Set<BlockPos> oldAdded, Set<IItemHandler> seenItemHandlers, BlockPos p) {
        if (!oldAdded.contains(p)) {
            TileEntity te = getWorld().getTileEntity(p);
            if (InventoryHelper.isInventory(te) && !(te instanceof StorageScannerTileEntity)) {
                if (!inventories.contains(p)) {
                    IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                    if (handler == null || seenItemHandlers.add(handler)) {
                        inventories.add(p);
                    }
                }
            }
        }
    }

    private boolean inRange(BlockPos p) {
        return p.getX() >= getPos().getX() - radius && p.getX() <= getPos().getX() + radius && p.getY() >= getPos().getY() - radius && p.getY() <= getPos().getY() + radius && p.getZ() >= getPos().getZ() - radius && p.getZ() <= getPos().getZ() + radius;
    }

    @Override
    public ItemStack requestItem(Predicate<ItemStack> matcher, boolean simulate, int amount, boolean doRoutable) {
        if (getStoredPower() < StorageScannerConfiguration.rfPerRequest.get()) {
            return ItemStack.EMPTY;
        }
        return inventories.stream()
                .filter(p -> isOutputFromAuto(p) && ((!doRoutable) || isRoutable(p)))
                .map(this::getItemHandlerAt)
                .filter(Objects::nonNull)
                .map(handler -> {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack itemStack = handler.getStackInSlot(i);
                        if (matcher.test(itemStack)) {
                            ItemStack received = handler.extractItem(i, amount, simulate);
                            if (!received.isEmpty()) {
                                return received.copy();
                            }
                        }
                    }
                    return null;        // Null itemstack is ok here! needed for findFirst.
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(ItemStack.EMPTY);
    }

    @Override
    public ItemStack requestItem(ItemStack match, int amount, boolean doRoutable, boolean oredict) {
        if (match.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (getStoredPower() < StorageScannerConfiguration.rfPerRequest.get()) {
            return ItemStack.EMPTY;
        }

        Set<Integer> oredictMatches = getOredictMatchers(match, oredict);
        final ItemStack[] result = {ItemStack.EMPTY};
        final int[] cnt = {match.getMaxStackSize() < amount ? match.getMaxStackSize() : amount};
        inventories.stream()
                .filter(p -> isOutputFromAuto(p) && (!doRoutable) || isRoutable(p))
                .map(this::getItemHandlerAt)
                .filter(Objects::nonNull)
                .allMatch(handler -> {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack itemStack = handler.getStackInSlot(i);
                        if (isItemEqual(match, itemStack, oredictMatches)) {
                            ItemStack received = handler.extractItem(i, cnt[0], false);
                            if (!received.isEmpty()) {
                                if (result[0].isEmpty()) {
                                    result[0] = received;
                                } else {
                                    result[0].grow(received.getCount());
                                }
                                cnt[0] -= received.getCount();
                            }
                        }
                    }
                    return cnt[0] > 0;
                });
        if (!result[0].isEmpty()) {
            consumeEnergy(StorageScannerConfiguration.rfPerRequest.get());
        }
        return result[0];
    }

    @Nullable
    private IItemHandler getItemHandlerAt(BlockPos p) {
        if (!WorldTools.chunkLoaded(getWorld(), p)) {
            return null;
        }
        TileEntity te = getWorld().getTileEntity(p);
        if (te == null || te instanceof StorageScannerTileEntity) {
            return null;
        }
        return getItemHandlerAt(te, null);
    }

    // @todo move to McJtyLib
    @Nullable
    private static IItemHandler getItemHandlerAt(@Nullable TileEntity te, EnumFacing intSide) {
        if (te != null && te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, intSide)) {
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, intSide);
            if (handler != null) {
                return handler;
            }
        } else if (te instanceof ISidedInventory) {
            // Support for old inventory
            ISidedInventory sidedInventory = (ISidedInventory) te;
            return new SidedInvWrapper(sidedInventory, intSide);
        } else if (te instanceof IInventory) {
            // Support for old inventory
            IInventory inventory = (IInventory) te;
            return new InvWrapper(inventory);
        }
        return null;
    }

    @Override
    public int insertItem(ItemStack stack) {
        ItemStack s = insertItem(stack, false);
        return s.getCount();
    }

    @Override
    public ItemStack insertItem(ItemStack stack, boolean simulate) {
        if (getStoredPower() < StorageScannerConfiguration.rfPerInsert.get()) {
            return stack;
        }

        ItemStack toInsert = stack.copy();

        Iterator<IItemHandler> iterator = inventories.stream()
                .filter(p -> isInputFromAuto(p) && (!p.equals(getPos()) && isRoutable(p) && getInputMatcher(p).test(stack)))
                .map(this::getItemHandlerAt)
                .filter(Objects::nonNull)
                .iterator();

        while (!toInsert.isEmpty() && iterator.hasNext()) {
            IItemHandler handler = iterator.next();
            toInsert = ItemHandlerHelper.insertItem(handler, toInsert, simulate);
        }

        consumeEnergy(StorageScannerConfiguration.rfPerInsert.get());
        return toInsert;
    }

    private ItemStack requestStackFromInv(BlockPos invPos, ItemStack requested, Integer[] todo, ItemStack outSlot) {
        TileEntity tileEntity = getWorld().getTileEntity(invPos);
        if (tileEntity instanceof StorageScannerTileEntity) {
            return outSlot;
        }

        int size = InventoryHelper.getInventorySize(tileEntity);

        for (int i = 0; i < size; i++) {
            ItemStack stack = ItemStackTools.getStack(tileEntity, i);
            if (ItemHandlerHelper.canItemStacksStack(requested, stack)) {
                ItemStack extracted = ItemStackTools.extractItem(tileEntity, i, todo[0]);
                todo[0] -= extracted.getCount();
                if (outSlot.isEmpty()) {
                    outSlot = extracted;
                } else {
                    outSlot.grow(extracted.getCount());
                }
                if (todo[0] == 0) {
                    break;
                }
            }
        }
        return outSlot;
    }

    // Meant to be used from the gui
    public void requestStack(BlockPos invPos, ItemStack requested, int amount, EntityPlayer player) {
        int rf = StorageScannerConfiguration.rfPerRequest.get();
        if (amount >= 0) {
            rf /= 10;       // Less RF usage for requesting less items
        }
        if (amount == -1) {
            amount = requested.getMaxStackSize();
        }
        if (getStoredPower() < rf) {
            return;
        }

        Integer[] todo = new Integer[]{amount};

        ItemStack outSlot = inventoryHelper.getStackInSlot(StorageScannerContainer.SLOT_OUT);
        if (!outSlot.isEmpty()) {
            // Check if the items are the same and there is room
            if (!ItemHandlerHelper.canItemStacksStack(outSlot, requested)) {
                return;
            }
            if (outSlot.getCount() >= requested.getMaxStackSize()) {
                return;
            }
            todo[0] = Math.min(todo[0], requested.getMaxStackSize() - outSlot.getCount());
        }

        if (invPos.getY() == -1) {
            Iterator<BlockPos> iterator = inventories.stream()
                    .filter(p -> isOutputFromGui(p) && isRoutable(p))
                    .iterator();
            while (iterator.hasNext()) {
                BlockPos blockPos = iterator.next();
                outSlot = requestStackFromInv(blockPos, requested, todo, outSlot);
                if (todo[0] == 0) {
                    break;
                }

            }
        } else {
            if (isOutputFromGui(invPos)) {
                outSlot = requestStackFromInv(invPos, requested, todo, outSlot);
            }
        }

        if (todo[0] == amount) {
            // Nothing happened
            return;
        }

        consumeEnergy(rf);
        setInventorySlotContents(StorageScannerContainer.SLOT_OUT, outSlot);

        if (StorageScannerConfiguration.requestStraightToInventory.get()) {
            if (player.inventory.addItemStackToInventory(outSlot)) {
                setInventorySlotContents(StorageScannerContainer.SLOT_OUT, ItemStack.EMPTY);
            }
        }
    }

    private void addItemStack(List<ItemStack> stacks, Set<Item> foundItems, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (foundItems.contains(stack.getItem())) {
            for (ItemStack s : stacks) {
                if (ItemHandlerHelper.canItemStacksStack(s, stack)) {
                    s.grow(stack.getCount());
                    return;
                }
            }
        }
        // If we come here we need to append a new stack
        stacks.add(stack.copy());
        foundItems.add(stack.getItem());
    }


    public List<ItemStack> getInventoryForBlock(BlockPos cpos) {
        Set<Item> foundItems = new HashSet<>();
        List<ItemStack> stacks = new ArrayList<>();

        lastSelectedInventory = cpos;

        if (cpos.getY() == -1) {
            // Get all starred inventories
            for (BlockPos blockPos : inventories) {
                if (routable.contains(blockPos)) {
                    addItemsFromInventory(blockPos, foundItems, stacks);
                }
            }
        } else {
            addItemsFromInventory(cpos, foundItems, stacks);
        }

        return stacks;
    }

    private void addItemsFromInventory(BlockPos cpos, Set<Item> foundItems, List<ItemStack> stacks) {
        TileEntity tileEntity = getWorld().getTileEntity(cpos);
        IItemHandler handler = getItemHandlerAt(tileEntity, null);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                addItemStack(stacks, foundItems, handler.getStackInSlot(i));
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);
        NBTTagList list = tagCompound.getTagList("inventories", Constants.NBT.TAG_COMPOUND);
        inventories.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = (NBTTagCompound) list.get(i);
            BlockPos c = BlockPosTools.readFromNBT(tag, "c");
            inventories.add(c);
        }
        list = tagCompound.getTagList("routable", Constants.NBT.TAG_COMPOUND);
        routable.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = (NBTTagCompound) list.get(i);
            BlockPos c = BlockPosTools.readFromNBT(tag, "c");
            routable.add(c);
        }
        list = tagCompound.getTagList("fromxnet", Constants.NBT.TAG_COMPOUND);
        inventoriesFromXNet.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = (NBTTagCompound) list.get(i);
            BlockPos c = BlockPosTools.readFromNBT(tag, "c");
            inventoriesFromXNet.add(c);
        }
    }

    @Override
    public void readRestorableFromNBT(NBTTagCompound tagCompound) {
        super.readRestorableFromNBT(tagCompound);
        readBufferFromNBT(tagCompound, inventoryHelper);
        radius = tagCompound.getInteger("radius");
        exportToCurrent = tagCompound.getBoolean("exportC");
        sortMode = tagCompound.getString("sortMode");
        if (tagCompound.hasKey("wideview")) {
            openWideView = tagCompound.getBoolean("wideview");
        } else {
            openWideView = true;
        }
        craftingGrid.readFromNBT(tagCompound.getCompoundTag("grid"));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
        super.writeToNBT(tagCompound);
        NBTTagList list = new NBTTagList();
        for (BlockPos c : inventories) {
            NBTTagCompound tag = BlockPosTools.writeToNBT(c);
            list.appendTag(tag);
        }
        tagCompound.setTag("inventories", list);
        list = new NBTTagList();
        for (BlockPos c : routable) {
            NBTTagCompound tag = BlockPosTools.writeToNBT(c);
            list.appendTag(tag);
        }
        tagCompound.setTag("routable", list);
        list = new NBTTagList();
        for (BlockPos c : inventoriesFromXNet) {
            NBTTagCompound tag = BlockPosTools.writeToNBT(c);
            list.appendTag(tag);
        }
        tagCompound.setTag("fromxnet", list);
        return tagCompound;
    }

    @Override
    public void writeRestorableToNBT(NBTTagCompound tagCompound) {
        super.writeRestorableToNBT(tagCompound);
        writeBufferToNBT(tagCompound, inventoryHelper);
        tagCompound.setInteger("radius", radius);
        tagCompound.setBoolean("exportC", exportToCurrent);
        tagCompound.setBoolean("wideview", openWideView);
        tagCompound.setString("sortMode", sortMode);
        tagCompound.setTag("grid", craftingGrid.writeToNBT());
    }

    private void clearGrid() {
        CraftingGridInventory inventory = craftingGrid.getCraftingGridInventory();
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            inventory.setInventorySlotContents(i, ItemStack.EMPTY);
        }
        markDirty();
    }

    @Override
    public boolean execute(EntityPlayerMP playerMP, String command, TypedMap params) {
        boolean rc = super.execute(playerMP, command, params);
        if (rc) {
            return true;
        }
        if (CMD_UP.equals(command)) {
            moveUp(params.get(PARAM_INDEX));
            return true;
        } else if (CMD_TOP.equals(command)) {
            moveTop(params.get(PARAM_INDEX));
            return true;
        } else if (CMD_DOWN.equals(command)) {
            moveDown(params.get(PARAM_INDEX));
            return true;
        } else if (CMD_BOTTOM.equals(command)) {
            moveBottom(params.get(PARAM_INDEX));
            return true;
        } else if (CMD_REMOVE.equals(command)) {
            removeInventory(params.get(PARAM_INDEX));
            return true;
        } else if (CMD_TOGGLEROUTABLE.equals(command)) {
            toggleRoutable(params.get(PARAM_POS));
            return true;
        } else if (CMD_SETVIEW.equals(command)) {
            setOpenWideView(params.get(PARAM_VIEW));
            return true;
        } else if (CMD_UPDATESORTMODE.equals(command)) {
            setSortMode(params.get(PARAM_SORTMODE));
            return true;
        }
        return false;
    }

    @Nullable
    @Override
    public TypedMap executeWithResult(String command, TypedMap args) {
        TypedMap result = super.executeWithResult(command, args);
        if (result != null) {
            return result;
        }
        if (CMD_SCANNER_INFO.equals(command)) {
            return TypedMap.builder()
                    .put(PARAM_ENERGY, getStoredPower())
                    .put(PARAM_EXPORT, isExportToCurrent())
                    .build();
        }
        return null;
    }

    @Override
    public boolean receiveDataFromServer(String command, @Nonnull TypedMap result) {
        boolean rc = super.receiveDataFromServer(command, result);
        if (rc) {
            return rc;
        }
        if (CMD_SCANNER_INFO.equals(command)) {
            rfReceived = result.get(PARAM_ENERGY);
            exportToCurrentReceived = result.get(PARAM_EXPORT);
            return true;
        }
        return false;
    }

    /**
     * Return true if this is a dummy TE. i.e. this happens only when accessing a
     * storage scanner in a tablet on the client side.
     */
    public boolean isDummy() {
        return monitorDim != null;
    }

    /**
     * This is used client side only for the GUI.
     * Return the position of the crafting grid container. This
     * is either the position of this tile entity (in case we are just looking
     * directly at the storage scanner), the position of a 'watching' tile
     * entity (in case we are a dummy for the storage terminal) or else null
     * in case we're using a handheld item.
     *
     * @return
     */
    public BlockPos getCraftingGridContainerPos() {
        return getPos();
    }

    /**
     * This is used client side only for the GUI.
     * Get the real crafting grid provider
     */
    public CraftingGridProvider getCraftingGridProvider() {
        return this;
    }

    /**
     * This is used client side only for the GUI.
     * Return the position of the actual storage scanner
     *
     * @return
     */
    public BlockPos getStorageScannerPos() {
        return getPos();
    }

    public int getDimension() {
        if (isDummy()) {
            return monitorDim;
        } else {
            return getWorld().provider.getDimension();
        }
    }

    @Override
    public InventoryHelper getInventoryHelper() {
        return inventoryHelper;
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        // We only allow insertion in the auto slot for the storage scanner (for automation)
        return index == StorageScannerContainer.SLOT_IN_AUTO;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return true;
//        return canPlayerAccess(player);
    }

    @Override
    public boolean canExtractItem(int index, ItemStack stack, EnumFacing direction) {
        return StorageScannerContainer.factory.isOutputSlot(index);
    }

    @Override
    public boolean canInsertItem(int index, ItemStack itemStackIn, EnumFacing direction) {
        return isItemValidForSlot(index, itemStackIn);
    }

    @Override
    public int[] getSlotsForFace(EnumFacing side) {
        return SLOTS;
    }

    public String getSortMode() {
        return sortMode;
    }

    public void setSortMode(String sortMode) {
        this.sortMode = sortMode;
        markDirty();
    }
}
