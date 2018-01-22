package mcjty.rftools.blocks.shaper;

import mcjty.rftools.RFTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.HashMap;
import java.util.Map;

public class ProjectorSounds {

    private static final Map<BlockPos, MovingSound> sounds = new HashMap<>();

    public static SoundEvent scanSound;

    public static void init(IForgeRegistry<SoundEvent> registry) {
        scanSound = registerSound(registry, new ResourceLocation(RFTools.MODID, "scan"));
    }

    private static SoundEvent registerSound(IForgeRegistry<SoundEvent> registry, ResourceLocation sound) {
        SoundEvent event = new SoundEvent(sound).setRegistryName(sound);
        registry.register(event);
        return event;
    }

    public static void stopSound(BlockPos pos) {
        if (sounds.containsKey(pos)) {
            MovingSound movingSound = sounds.get(pos);
            ((ProjectorSound)movingSound).stop();
            Minecraft.getMinecraft().getSoundHandler().stopSound(movingSound);
            sounds.remove(pos);
        }
    }

    private static void playSound(BlockPos pos, MovingSound sound) {
        stopSound(pos);
        Minecraft.getMinecraft().getSoundHandler().playSound(sound);
        sounds.put(pos, sound);
    }


    public static void playScan(World worldObj, BlockPos pos) {
        MovingSound sound = new ProjectorSound(worldObj, pos.getX(), pos.getY(), pos.getZ());
        playSound(pos, sound);
    }

    public static boolean isScanPlaying(BlockPos pos) {
        MovingSound movingSound = sounds.get(pos);
        return movingSound instanceof ProjectorSound;
    }
}
