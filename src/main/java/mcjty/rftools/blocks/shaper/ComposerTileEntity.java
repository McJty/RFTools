package mcjty.rftools.blocks.shaper;

import mcjty.lib.container.DefaultSidedInventory;
import mcjty.lib.container.InventoryHelper;
import mcjty.lib.entity.GenericTileEntity;
import mcjty.rftools.blocks.builder.BuilderSetup;
import mcjty.rftools.items.builder.ShapeCardItem;
import mcjty.rftools.shapes.Shape;
import mcjty.rftools.shapes.ShapeModifier;
import mcjty.rftools.shapes.ShapeOperation;
import mcjty.rftools.shapes.ShapeRotation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.util.Constants;

public class ComposerTileEntity extends GenericTileEntity implements DefaultSidedInventory, ITickable {

    private InventoryHelper inventoryHelper = new InventoryHelper(this, ComposerContainer.factory, ComposerContainer.SLOT_COUNT*2 + 1);
    private ShapeModifier modifiers[] = new ShapeModifier[ComposerContainer.SLOT_COUNT];

    public ComposerTileEntity() {
        for (int i = 0; i < modifiers.length ; i++) {
            modifiers[i] = new ShapeModifier(ShapeOperation.UNION, false, ShapeRotation.NONE);
        }
    }

    @Override
    public void update() {
        if (!getWorld().isRemote) {
            ItemStack output = getStackInSlot(ComposerContainer.SLOT_OUT);
            if (!output.isEmpty()) {
                NBTTagList list = new NBTTagList();
                for (int i = ComposerContainer.SLOT_TABS; i < ComposerContainer.SLOT_TABS + ComposerContainer.SLOT_COUNT; i++) {
                    ItemStack item = getStackInSlot(i);
                    if (!item.isEmpty()) {
                        if (item.hasTagCompound()) {
                            NBTTagCompound copy = item.getTagCompound().copy();
                            ShapeModifier modifier = modifiers[i - 1];
                            ShapeCardItem.setModifier(copy, modifier);
                            ItemStack materialGhost = getStackInSlot(i + ComposerContainer.SLOT_COUNT);
                            ShapeCardItem.setGhostMaterial(copy, materialGhost);
                            list.appendTag(copy);
                        }
                    }
                }
                ShapeCardItem.setChildren(output, list);
                if (!ShapeCardItem.getShape(output).isComposition()) {
                    ShapeCardItem.setShape(output, Shape.SHAPE_COMPOSITION, true);
                }
            }
        }
    }

    public ShapeModifier[] getModifiers() {
        return modifiers;
    }

    public void setModifiers(ShapeModifier[] modifiers) {
        this.modifiers = modifiers;
        markDirtyClient();
    }

    @Override
    public InventoryHelper getInventoryHelper() {
        return inventoryHelper;
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
        return stack.getItem() == BuilderSetup.shapeCardItem;
    }

    @Override
    public void readRestorableFromNBT(NBTTagCompound tagCompound) {
        super.readRestorableFromNBT(tagCompound);
        readBufferFromNBT(tagCompound, inventoryHelper);
        NBTTagList list = tagCompound.getTagList("ops", Constants.NBT.TAG_COMPOUND);
        for (int i = 0 ; i < list.tagCount() ; i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            String op = tag.getString("mod_op");
            boolean flipY = tag.getBoolean("mod_flipy");
            String rot = tag.getString("mod_rot");
            ShapeOperation operation = ShapeOperation.getByName(op);
            if (operation == null) {
                operation = ShapeOperation.UNION;
            }
            ShapeRotation rotation = ShapeRotation.getByName(rot);
            if (rotation == null) {
                rotation = ShapeRotation.NONE;
            }
            modifiers[i] = new ShapeModifier(operation, flipY, rotation);
        }
    }

    @Override
    public void writeRestorableToNBT(NBTTagCompound tagCompound) {
        super.writeRestorableToNBT(tagCompound);
        writeBufferToNBT(tagCompound, inventoryHelper);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < ComposerContainer.SLOT_COUNT ; i++) {
            NBTTagCompound tc = new NBTTagCompound();
            ShapeModifier mod = modifiers[i];
            tc.setString("mod_op", mod.getOperation().getCode());
            tc.setBoolean("mod_flipy", mod.isFlipY());
            tc.setString("mod_rot", mod.getRotation().getCode());
            list.appendTag(tc);
        }
        tagCompound.setTag("ops", list);
    }
}
