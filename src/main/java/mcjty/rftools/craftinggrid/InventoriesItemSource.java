package mcjty.rftools.craftinggrid;

import mcjty.lib.container.InventoryHelper;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class InventoriesItemSource implements IItemSource {

    private List<Pair<IInventory, Integer>> inventories = new ArrayList<>();

    public InventoriesItemSource add(IInventory inventory, int offset) {
        inventories.add(Pair.of(inventory, offset));
        return this;
    }

    @Override
    public Iterable<Pair<IItemKey, ItemStack>> getItems() {
        return () -> new Iterator<Pair<IItemKey, ItemStack>>() {
            private int inventoryIndex = 0;
            private int slotIndex = 0;

            private boolean firstValidItem() {
                while (true) {
                    if (inventoryIndex >= inventories.size()) {
                        return false;
                    }
                    IInventory inventory = inventories.get(inventoryIndex).getLeft();
                    int offset = inventories.get(inventoryIndex).getRight();
                    if (slotIndex < offset) {
                        slotIndex = offset;
                    }
                    if (slotIndex < inventory.getSizeInventory()) {
                        return true;
                    } else {
                        slotIndex = 0;
                        inventoryIndex++;
                    }
                }
            }

            @Override
            public boolean hasNext() {
                return firstValidItem();
            }

            @Override
            public Pair<IItemKey, ItemStack> next() {
                IInventory inventory = inventories.get(inventoryIndex).getLeft();
                ItemKey key = new ItemKey(inventory, slotIndex);
                Pair<IItemKey, ItemStack> result = Pair.of(key, inventory.getStackInSlot(slotIndex));
                slotIndex++;
                return result;
            }
        };
    }

    @Override
    public ItemStack decrStackSize(IItemKey key, int amount) {
        ItemKey realKey = (ItemKey) key;
        ItemStack stack = realKey.getInventory().getStackInSlot(realKey.getSlot());
        ItemStack result = stack.splitStack(amount);
        if (stack.isEmpty()) {
            realKey.getInventory().setInventorySlotContents(realKey.getSlot(), ItemStack.EMPTY);
        }
        return result;
    }

    @Override
    public boolean insertStack(IItemKey key, ItemStack stack) {
        ItemKey realKey = (ItemKey) key;
        IInventory inventory = realKey.getInventory();
        ItemStack origStack = inventory.removeStackFromSlot(realKey.getSlot());
        if (!origStack.isEmpty()) {
            if (ItemHandlerHelper.canItemStacksStack(origStack, stack)) {
                if ((stack.getCount() + origStack.getCount()) > stack.getMaxStackSize()) {
                    return false;
                }
                stack.grow(origStack.getCount());
            } else {
                return false;
            }
        }
        inventory.setInventorySlotContents(realKey.getSlot(), stack);
        return true;
    }

    @Override
    public int insertStackAnySlot(IItemKey key, ItemStack stack) {
        ItemKey realKey = (ItemKey) key;
        IInventory inventory = realKey.getInventory();
        return InventoryHelper.mergeItemStack(inventory, true, stack, 0, inventory.getSizeInventory(), null);
    }

    private static class ItemKey implements IItemKey {
        private IInventory inventory;
        private int slot;

        public ItemKey(IInventory inventory, int slot) {
            this.inventory = inventory;
            this.slot = slot;
        }

        public IInventory getInventory() {
            return inventory;
        }

        public int getSlot() {
            return slot;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ItemKey itemKey = (ItemKey) o;

            if (slot != itemKey.slot) {
                return false;
            }
            return inventory.equals(itemKey.inventory);

        }

        @Override
        public int hashCode() {
            int result = inventory.hashCode();
            result = 31 * result + slot;
            return result;
        }
    }
}
