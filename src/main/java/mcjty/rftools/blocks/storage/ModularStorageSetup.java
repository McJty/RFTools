package mcjty.rftools.blocks.storage;

import mcjty.rftools.items.storage.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ModularStorageSetup {
    public static ModularStorageBlock modularStorageBlock;
    public static RemoteStorageBlock remoteStorageBlock;
    public static StorageTerminalBlock storageTerminalBlock;
    public static LevelEmitterBlock levelEmitterBlock;

    public static StorageModuleTabletItem storageModuleTabletItem;

    public static StorageModuleItem storageModuleItem;
    public static OreDictTypeItem oreDictTypeItem;
    public static GenericTypeItem genericTypeItem;
    public static StorageFilterItem storageFilterItem;

    public static void init() {
        modularStorageBlock = new ModularStorageBlock();
        remoteStorageBlock = new RemoteStorageBlock();
        storageTerminalBlock = new StorageTerminalBlock();
        levelEmitterBlock = new LevelEmitterBlock();

        storageModuleTabletItem = new StorageModuleTabletItem();
        storageModuleItem = new StorageModuleItem();
        oreDictTypeItem = new OreDictTypeItem();
        genericTypeItem = new GenericTypeItem();
        storageFilterItem = new StorageFilterItem();
    }

    @SideOnly(Side.CLIENT)
    public static void initClient() {
        modularStorageBlock.initModel();
        remoteStorageBlock.initModel();
        storageTerminalBlock.initModel();
        levelEmitterBlock.initModel();
        storageModuleTabletItem.initModel();
        storageModuleItem.initModel();
        oreDictTypeItem.initModel();
        genericTypeItem.initModel();
        storageFilterItem.initModel();
    }
}
