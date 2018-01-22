package mcjty.rftools.blocks.screens;

import mcjty.rftools.items.screenmodules.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ScreenSetup {
    public static ScreenBlock screenBlock;
    public static ScreenBlock creativeScreenBlock;
    public static ScreenHitBlock screenHitBlock;
    public static ScreenControllerBlock screenControllerBlock;

    public static TextModuleItem textModuleItem;
    public static EnergyModuleItem energyModuleItem;
    public static EnergyPlusModuleItem energyPlusModuleItem;
    public static InventoryModuleItem inventoryModuleItem;
    public static InventoryPlusModuleItem inventoryPlusModuleItem;
    public static ClockModuleItem clockModuleItem;
    public static FluidModuleItem fluidModuleItem;
    public static FluidPlusModuleItem fluidPlusModuleItem;
    public static MachineInformationModuleItem machineInformationModuleItem;
    public static ComputerModuleItem computerModuleItem;
    public static ButtonModuleItem buttonModuleItem;
    public static ElevatorButtonModuleItem elevatorButtonModuleItem;
    public static RedstoneModuleItem redstoneModuleItem;
    public static CounterModuleItem counterModuleItem;
    public static CounterPlusModuleItem counterPlusModuleItem;
    public static StorageControlModuleItem storageControlModuleItem;
    public static DumpModuleItem dumpModuleItem;

    public static void init() {
        screenBlock = new ScreenBlock("screen", ScreenTileEntity.class);
        creativeScreenBlock = new ScreenBlock("creative_screen", CreativeScreenTileEntity.class) {
            @Override
            public boolean isCreative() {
                return true;
            }
        };
        screenHitBlock = new ScreenHitBlock();
        screenControllerBlock = new ScreenControllerBlock();

        textModuleItem = new TextModuleItem();
        inventoryModuleItem = new InventoryModuleItem();
        inventoryPlusModuleItem = new InventoryPlusModuleItem();
        energyModuleItem = new EnergyModuleItem();
        energyPlusModuleItem = new EnergyPlusModuleItem();
        clockModuleItem = new ClockModuleItem();
        fluidModuleItem = new FluidModuleItem();
        fluidPlusModuleItem = new FluidPlusModuleItem();
        machineInformationModuleItem = new MachineInformationModuleItem();
        computerModuleItem = new ComputerModuleItem();
        buttonModuleItem = new ButtonModuleItem();
        elevatorButtonModuleItem = new ElevatorButtonModuleItem();
        redstoneModuleItem = new RedstoneModuleItem();
        counterModuleItem = new CounterModuleItem();
        counterPlusModuleItem = new CounterPlusModuleItem();
        storageControlModuleItem = new StorageControlModuleItem();
        dumpModuleItem = new DumpModuleItem();
    }

    @SideOnly(Side.CLIENT)
    public static void initClient() {
        screenBlock.initModel();
        creativeScreenBlock.initModel();
        screenHitBlock.initModel();
        screenControllerBlock.initModel();

        textModuleItem.initModel();
        inventoryModuleItem.initModel();
        inventoryPlusModuleItem.initModel();
        energyModuleItem.initModel();
        energyPlusModuleItem.initModel();
        clockModuleItem.initModel();
        fluidModuleItem.initModel();
        fluidPlusModuleItem.initModel();
        machineInformationModuleItem.initModel();
        computerModuleItem.initModel();
        buttonModuleItem.initModel();
        elevatorButtonModuleItem.initModel();
        redstoneModuleItem.initModel();
        counterModuleItem.initModel();
        counterPlusModuleItem.initModel();
        storageControlModuleItem.initModel();
        dumpModuleItem.initModel();
    }
}
