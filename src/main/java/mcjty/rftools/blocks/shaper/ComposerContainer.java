package mcjty.rftools.blocks.shaper;

import mcjty.lib.container.ContainerFactory;
import mcjty.lib.container.GenericContainer;
import mcjty.lib.container.SlotDefinition;
import mcjty.lib.container.SlotType;
import mcjty.rftools.blocks.builder.BuilderSetup;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

public class ComposerContainer extends GenericContainer {
    public static final String CONTAINER_INVENTORY = "container";

    public static final int SLOT_COUNT = 9;

    public static final int SLOT_OUT = 0;
    public static final int SLOT_TABS = 1;
    public static final int SLOT_GHOSTS = SLOT_TABS + SLOT_COUNT;

    public static final ContainerFactory factory = new ContainerFactory() {
        @Override
        protected void setup() {
            addSlot(new SlotDefinition(SlotType.SLOT_SPECIFICITEM,
                    new ItemStack(BuilderSetup.shapeCardItem)), CONTAINER_INVENTORY, SLOT_OUT, 18, 200);
            addSlotBox(new SlotDefinition(SlotType.SLOT_SPECIFICITEM,
                            new ItemStack(BuilderSetup.shapeCardItem)),
                    CONTAINER_INVENTORY, SLOT_TABS, 18, 7, 1, 18, SLOT_COUNT, 18);
            addSlotBox(new SlotDefinition(SlotType.SLOT_GHOST),
                    CONTAINER_INVENTORY, SLOT_GHOSTS, 36, 7, 1, 18, SLOT_COUNT, 18);
            layoutPlayerInventorySlots(85, 142);
        }
    };

    public ComposerContainer(EntityPlayer player, IInventory containerInventory) {
        super(factory);
        addInventory(CONTAINER_INVENTORY, containerInventory);
        addInventory(ContainerFactory.CONTAINER_PLAYER, player.inventory);
        generateSlots();
    }
}
