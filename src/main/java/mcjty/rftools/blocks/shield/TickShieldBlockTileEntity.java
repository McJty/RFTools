package mcjty.rftools.blocks.shield;

import mcjty.rftools.blocks.shield.filters.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.List;

public class TickShieldBlockTileEntity extends NoTickShieldBlockTileEntity implements ITickable {

    // Damage timer is not saved with the TE as it is not needed.
    private int damageTimer = 10;

    @Override
    public void update() {
        if (!getWorld().isRemote) {
            if (damageBits != 0) {
                handleDamage();
            }
        }
    }

    private void handleDamage() {
        damageTimer--;
        if (damageTimer > 0) {
            return;
        }
        damageTimer = 10;
        if (beamBox == null) {
            int xCoord = getPos().getX();
            int yCoord = getPos().getY();
            int zCoord = getPos().getZ();
            beamBox = new AxisAlignedBB(xCoord - .4, yCoord - .4, zCoord - .4, xCoord + 1.4, yCoord + 2.0, zCoord + 1.4);
        }

        if (shieldBlock != null) {
            ShieldTEBase shieldTileEntity = (ShieldTEBase) getWorld().getTileEntity(shieldBlock);
            if (shieldTileEntity != null) {
                List<Entity> l = getWorld().getEntitiesWithinAABB(Entity.class, beamBox);
                for (Entity entity : l) {
                    if ((damageBits & AbstractShieldBlock.META_HOSTILE) != 0 && entity instanceof IMob) {
                        if (checkEntityDamage(shieldTileEntity, HostileFilter.HOSTILE)) {
                            shieldTileEntity.applyDamageToEntity(entity);
                        }
                    } else if ((damageBits & AbstractShieldBlock.META_PASSIVE) != 0 && entity instanceof IAnimals) {
                        if (checkEntityDamage(shieldTileEntity, AnimalFilter.ANIMAL)) {
                            shieldTileEntity.applyDamageToEntity(entity);
                        }
                    } else if ((damageBits & AbstractShieldBlock.META_PLAYERS) != 0 && entity instanceof EntityPlayer) {
                        if (checkPlayerDamage(shieldTileEntity, (EntityPlayer) entity)) {
                            shieldTileEntity.applyDamageToEntity(entity);
                        }
                    }
                }
            }
        }
    }

    private boolean checkEntityDamage(ShieldTEBase shieldTileEntity, String filterName) {
        List<ShieldFilter> filters = shieldTileEntity.getFilters();
        for (ShieldFilter filter : filters) {
            if (DefaultFilter.DEFAULT.equals(filter.getFilterName())) {
                return ((filter.getAction() & ShieldFilter.ACTION_DAMAGE) != 0);
            } else if (filterName.equals(filter.getFilterName())) {
                return ((filter.getAction() & ShieldFilter.ACTION_DAMAGE) != 0);
            }
        }
        return false;
    }

    private boolean checkPlayerDamage(ShieldTEBase shieldTileEntity, EntityPlayer entity) {
        List<ShieldFilter> filters = shieldTileEntity.getFilters();
        for (ShieldFilter filter : filters) {
            if (DefaultFilter.DEFAULT.equals(filter.getFilterName())) {
                return ((filter.getAction() & ShieldFilter.ACTION_DAMAGE) != 0);
            } else if (PlayerFilter.PLAYER.equals(filter.getFilterName())) {
                PlayerFilter playerFilter = (PlayerFilter) filter;
                String name = playerFilter.getName();
                if ((name == null || name.isEmpty())) {
                    return ((filter.getAction() & ShieldFilter.ACTION_DAMAGE) != 0);
                } else if (name.equals(entity.getName())) {
                    return ((filter.getAction() & ShieldFilter.ACTION_DAMAGE) != 0);
                }
            }
        }
        return false;
    }

}
