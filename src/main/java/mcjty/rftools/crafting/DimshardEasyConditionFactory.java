package mcjty.rftools.crafting;

import com.google.gson.JsonObject;
import mcjty.rftools.config.GeneralConfiguration;
import mcjty.rftools.RFTools;
import net.minecraftforge.common.crafting.IConditionFactory;
import net.minecraftforge.common.crafting.JsonContext;

import java.util.function.BooleanSupplier;

import static mcjty.rftools.config.GeneralConfiguration.CRAFT_EASY;

public class DimshardEasyConditionFactory implements IConditionFactory {
    @Override
    public BooleanSupplier parse(JsonContext context, JsonObject json) {
        return () -> {
            if (RFTools.setup.rftoolsDimensions) {
                return GeneralConfiguration.dimensionalShardRecipeWithDimensions.get() == CRAFT_EASY;
            } else {
                return GeneralConfiguration.dimensionalShardRecipeWithoutDimensions.get() == CRAFT_EASY;
            }
        };
    }
}
