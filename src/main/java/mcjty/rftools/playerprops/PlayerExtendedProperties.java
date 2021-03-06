package mcjty.rftools.playerprops;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;

public class PlayerExtendedProperties {

    @CapabilityInject(FavoriteDestinationsProperties.class)
    public static Capability<FavoriteDestinationsProperties> FAVORITE_DESTINATIONS_CAPABILITY;

    @CapabilityInject(BuffProperties.class)
    public static Capability<BuffProperties> BUFF_CAPABILITY;

    public static FavoriteDestinationsProperties getFavoriteDestinations(EntityPlayer player) {
        return player.getCapability(FAVORITE_DESTINATIONS_CAPABILITY, null);
    }

    public static BuffProperties getBuffProperties(EntityPlayer player) {
        return player.getCapability(BUFF_CAPABILITY, null);
    }
}
