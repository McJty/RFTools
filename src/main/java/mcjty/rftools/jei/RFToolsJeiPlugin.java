package mcjty.rftools.jei;

import mcjty.lib.varia.ItemStackList;
import mcjty.rftools.blocks.crafter.CrafterConfiguration;
import mcjty.rftools.network.RFToolsMessages;
import mezz.jei.api.BlankModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.recipe.transfer.IRecipeTransferRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

@JEIPlugin
public class RFToolsJeiPlugin extends BlankModPlugin {

    public static void transferRecipe(Map<Integer, ? extends IGuiIngredient<ItemStack>> guiIngredients, BlockPos pos) {
        ItemStackList items = ItemStackList.create(10);
        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> entry : guiIngredients.entrySet()) {
            int recipeSlot = entry.getKey();
            List<ItemStack> allIngredients = entry.getValue().getAllIngredients();
            if (!allIngredients.isEmpty()) {
                items.set(recipeSlot, allIngredients.get(0));
            }
        }

        RFToolsMessages.INSTANCE.sendToServer(new PacketSendRecipe(items, pos));
    }

    @Override
    public void register(@Nonnull IModRegistry registry) {
        IRecipeTransferRegistry transferRegistry = registry.getRecipeTransferRegistry();
        if(CrafterConfiguration.enabled)
            CrafterRecipeTransferHandler.register(transferRegistry);
        ModularStorageRecipeTransferHandler.register(transferRegistry);
        ModularStorageItemRecipeTransferHandler.register(transferRegistry);
        RemoteStorageItemRecipeTransferHandler.register(transferRegistry);
        StorageScannerRecipeTransferHandler.register(transferRegistry);
    }
}
