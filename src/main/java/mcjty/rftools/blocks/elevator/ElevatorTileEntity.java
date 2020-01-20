package mcjty.rftools.blocks.elevator;


import com.mojang.authlib.GameProfile;
import mcjty.lib.tileentity.GenericEnergyReceiverTileEntity;
import mcjty.lib.gui.widgets.TextField;
import mcjty.lib.varia.Broadcaster;
import mcjty.rftools.blocks.builder.BuilderTileEntity;
import mcjty.rftools.blocks.shield.RelCoordinate;
import mcjty.rftools.playerprops.BuffProperties;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.lib.typed.TypedMap;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static mcjty.lib.blocks.BaseBlock.FACING_HORIZ;

public class ElevatorTileEntity extends GenericEnergyReceiverTileEntity implements ITickable {

    public static String CMD_SETNAME = "elevator.setName";

    private boolean prevIn = false;

    private double movingY = -1;
    private int startY;
    private int stopY;

    private String name = "";

    // The positions of the blocks we are currently moving (with 'y' set to the height of the controller)
    private Set<BlockPos> positions = new HashSet<>();
    private Bounds bounds;
    // The state that is moving
    private IBlockState movingState;

    // Cache: points to the current controller (bottom elevator block)
    private BlockPos cachedControllerPos;
    private int cachedLevels;       // Cached number of levels
    private int cachedCurrent = -1;

    private boolean redstoneOut = false;

    // All entities currently on the platform (server side only)
    private Set<Entity> entitiesOnPlatform = new HashSet<>();
    private boolean entitiesOnPlatformComplete = false; // If true then we know entitiesOnPlatform is complete, otherwise it only contains players.

    private FakePlayer harvester = null;

    public ElevatorTileEntity() {
        super(ElevatorConfiguration.MAXENERGY.get(), ElevatorConfiguration.RFPERTICK.get());
    }

    private FakePlayer getHarvester() {
        if (harvester == null) {
            harvester = FakePlayerFactory.get((WorldServer) world, new GameProfile(UUID.nameUUIDFromBytes("rftools_elevator".getBytes()), "rftools_elevator"));
        }
        harvester.setWorld(world);
        harvester.setPosition(pos.getX(), pos.getY(), pos.getZ());
        return harvester;
    }


    public void clearCaches(EnumFacing side) {
        for (int y = 0 ; y < getWorld().getHeight() ; y++) {
            BlockPos pos2 = getPosAtY(getPos(), y);
            if (getWorld().getBlockState(pos2).getBlock() == ElevatorSetup.elevatorBlock) {
                TileEntity te = getWorld().getTileEntity(pos2);
                if (te instanceof ElevatorTileEntity) {
                    EnumFacing side2 = getWorld().getBlockState(pos2).getValue(FACING_HORIZ);
                    if (side2 == side) {
                        ElevatorTileEntity tileEntity = (ElevatorTileEntity) te;
                        tileEntity.cachedControllerPos = null;
                        tileEntity.cachedLevels = 0;
                        tileEntity.cachedCurrent = -1;
                    }
                }
            }
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        markDirtyClient();
    }

    private void setRedstoneState() {
        markDirty();
        getWorld().notifyNeighborsOfStateChange(this.pos, this.getBlockType(), false);
    }


    @Override
    public void update() {
        if (!getWorld().isRemote) {
            boolean newout = isPlatformHere();
            if (newout != redstoneOut) {
                redstoneOut = newout;
                setRedstoneState();
            }

            if (isMoving()) {
                markDirty();

                double d = calculateSpeed();
                boolean stopped = handlePlatformMovement(d);
                if (stopped) {
                    stopMoving();
                    moveEntities(0, true);
                    clearMovement();
                } else {
                    moveEntities(d, false);
                }
                return;
            }

            if ((powerLevel > 0) == prevIn) {
                return;
            }
            prevIn = (powerLevel > 0);
            markDirty();

            if (powerLevel > 0) {
                movePlatformHere();
            }
        } else {
            if (isMoving()) {
                handleClientMovement();
            }
            handleSound();
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (getWorld().isRemote) {
            stopSounds();
        }
    }

    @SideOnly(Side.CLIENT)
    private void stopSounds() {
        ElevatorSounds.stopSound(getWorld(), getPos());
    }

    @SideOnly(Side.CLIENT)
    protected void handleSound() {
        if (ElevatorConfiguration.baseElevatorVolume.get() < 0.01f) {
            // No sounds.
            return;
        }

        if (!isMoving()) {
            stopSounds();
            return;
        }
        boolean startup = Math.abs(startY-movingY) < ElevatorConfiguration.maxSpeedDistanceStart.get();
        boolean shutdown = Math.abs(movingY-stopY) < ElevatorConfiguration.maxSpeedDistanceEnd.get() * 2;

        if (shutdown) {
            if (!ElevatorSounds.isStopPlaying(getWorld(), pos)) {
                ElevatorSounds.playStop(getWorld(), pos);
            }
        } else if (startup) {
            if (!ElevatorSounds.isStartupPlaying(getWorld(), pos)) {
                ElevatorSounds.playStartup(getWorld(), pos);
            }
        } else {
            if (!ElevatorSounds.isLoopPlaying(getWorld(), pos)) {
                ElevatorSounds.playLoop(getWorld(), pos);
            }
        }
        ElevatorSounds.moveSound(getWorld(), pos, (float) movingY);
    }

    @SideOnly(Side.CLIENT)
    private void handleClientMovement() {
        double d = calculateSpeed();
        handlePlatformMovement(d);
        if (bounds != null) {
            EntityPlayerSP player = Minecraft.getMinecraft().player;
            AxisAlignedBB aabb = getAABBAboveElevator(d);
            boolean on = player.getEntityBoundingBox().intersects(aabb);
            if (on) {
                player.setPosition(player.posX, movingY + 1, player.posZ);
            }
        }
    }

    private double calculateSpeed() {
        // The speed center y location is the location at which speed is maximum.
        // It is located closer to the end to make sure slowing down is a shorter period then speeding up.
        double speedDiff = ElevatorConfiguration.maximumSpeed.get() - ElevatorConfiguration.minimumSpeed.get();
        double speedFromStart = ElevatorConfiguration.minimumSpeed.get() + speedDiff * Math.abs((movingY - startY) / ElevatorConfiguration.maxSpeedDistanceStart.get());
        double speedFromStop = ElevatorConfiguration.minimumSpeed.get() + speedDiff * Math.abs((movingY - stopY) / ElevatorConfiguration.maxSpeedDistanceEnd.get());
        double d = Math.min(speedFromStart, speedFromStop);
        if (stopY < startY) {
            d = -d;
        }
        return d;
    }

    private boolean handlePlatformMovement(double d) {
        if (stopY > startY) {
            if (movingY >= stopY) {
                return true;
            }
            movingY += d;

            if (movingY >= stopY) {
                movingY = stopY;
            }
        } else {
            if (movingY <= stopY) {
                return true;
            }
            movingY += d;

            if (movingY <= stopY) {
                movingY = stopY;
            }
        }
        return false;
    }

    // Only server side
    private void moveEntities(double speed, boolean stop) {
        if (bounds == null) {
            return;
        }
        double offset = speed > 0 ? speed * 2 : speed;
        Set<Entity> oldEntities = this.entitiesOnPlatform;
        entitiesOnPlatform = new HashSet<>();
        List<Entity> entities = getWorld().getEntitiesWithinAABB(Entity.class, getAABBAboveElevator(speed));
        for (Entity entity : entities) {
            entity.fallDistance = 0;
            entitiesOnPlatform.add(entity);
            moveEntityOnPlatform(stop, offset, entity);
            entity.onGround = true;
            entity.fallDistance = 0;
        }

        for (Entity entity : oldEntities) {
            if (!this.entitiesOnPlatform.contains(entity)) {
                // Entity was on the platform before but it isn't anymore. If it was a player we do a safety check
                // to ensure it is still in the patform shaft and in that case put it back on the platform.
                // We also put back the entity if we know the list is complete.
                if (entity instanceof EntityPlayer || entitiesOnPlatformComplete) {
                    if (entity.getEntityBoundingBox().intersects(getAABBBigMargin())) {
                        // Entity is no longer on the platform but was on the platform before and
                        // is still in the elevator shaft. In that case we put it back.
                        entity.fallDistance = 0;
                        entitiesOnPlatform.add(entity);
                        moveEntityOnPlatform(stop, offset, entity);
                        entity.onGround = true;
                        entity.fallDistance = 0;
                    }
                }

                if (entity instanceof EntityPlayer) {
                    BuffProperties.disableElevatorMode((EntityPlayer) entity);
                }
            }
        }

        // Entities on platform is now complete so set this to true
        entitiesOnPlatformComplete = true;
    }

    private void moveEntityOnPlatform(boolean stop, double offset, Entity entity) {
        if (entity instanceof EntityPlayer) {
            double dy = 1;
            EntityPlayer player = (EntityPlayer) entity;
            if (stop) {
                BuffProperties.disableElevatorMode(player);
                entity.posY = movingY + dy;
                entity.setPositionAndUpdate(entity.posX, movingY + dy, entity.posZ);
            } else {
                BuffProperties.enableElevatorMode(player);
                entity.setPosition(entity.posX, movingY + dy, entity.posZ);
            }
        } else {
            double dy = 1.2 + offset;
            entity.posY = movingY + dy;
            entity.setPositionAndUpdate(entity.posX, movingY + dy, entity.posZ);
        }
    }

    // Find the position of the bottom elevator.
    public BlockPos findBottomElevator() {
        if (cachedControllerPos != null) {
            return cachedControllerPos;
        }

        // The orientation of this elevator.
        IBlockState blockState = getWorld().getBlockState(getPos());
        if (blockState.getBlock() != ElevatorSetup.elevatorBlock) {
            return null;
        }
        EnumFacing side = blockState.getValue(FACING_HORIZ);

        for (int y = 0; y < getWorld().getHeight(); y++) {
            BlockPos elevatorPos = getPosAtY(getPos(), y);
            IBlockState otherState = getWorld().getBlockState(elevatorPos);
            if (otherState.getBlock() == ElevatorSetup.elevatorBlock) {
                EnumFacing otherSide = otherState.getValue(FACING_HORIZ);
                if (otherSide == side) {
                    cachedControllerPos = elevatorPos;
                    return elevatorPos;
                }
            }
        }
        return null;
    }

    // Find the position of the elevator that has the platform.
    private BlockPos findElevatorWithPlatform() {
        // The orientation of this elevator.
        EnumFacing side = getWorld().getBlockState(getPos()).getValue(FACING_HORIZ);

        for (int y = 0; y < getWorld().getHeight(); y++) {
            BlockPos elevatorPos = getPosAtY(getPos(), y);
            IBlockState otherState = getWorld().getBlockState(elevatorPos);
            if (otherState.getBlock() == ElevatorSetup.elevatorBlock) {
                EnumFacing otherSide = otherState.getValue(FACING_HORIZ);
                if (otherSide == side) {
                    BlockPos frontPos = elevatorPos.offset(side);
                    if (isValidPlatformBlock(frontPos)) {
                        return elevatorPos;
                    }
                }
            }
        }
        return null;
    }

    private boolean isValidPlatformBlock(BlockPos frontPos) {
        World world = getWorld();
        IBlockState state = world.getBlockState(frontPos);
        Block block = state.getBlock();
        return !block.isAir(state, world, frontPos) && !block.hasTileEntity(state);
    }

    public Set<BlockPos> getPositions() {
        return positions;
    }

    public IBlockState getMovingState() {
        return movingState;
    }

    private void stopMoving() {
        movingY = stopY;
        for (BlockPos pos : positions) {
            if (getWorld().getBlockState(pos).getBlock().isReplaceable(getWorld(), pos)) {
                getWorld().setBlockState(getPosAtY(pos, stopY), movingState, 3);
            }
        }
        // Current level will have to be recalculated
        cachedCurrent = -1;
        markDirtyClient();
    }

    private void clearMovement() {
        positions.clear();
        entitiesOnPlatform.clear();
        movingState = null;
        bounds = null;
        movingY = -1;
    }

    private static class Bounds {
        private int minX = 1000000000;
        private int minZ = 1000000000;
        private int maxX = -1000000000;
        private int maxZ = -1000000000;

        public Bounds() {
        }

        public Bounds(int minX, int minZ, int maxX, int maxZ) {
            this.maxX = maxX;
            this.maxZ = maxZ;
            this.minX = minX;
            this.minZ = minZ;
        }

        public void addPos(BlockPos pos) {
            if (pos.getX() < minX) {
                minX = pos.getX();
            }
            if (pos.getX() > maxX) {
                maxX = pos.getX();
            }
            if (pos.getZ() < minZ) {
                minZ = pos.getZ();
            }
            if (pos.getZ() > maxZ) {
                maxZ = pos.getZ();
            }
        }

        public int getMinX() {
            return minX;
        }

        public int getMinZ() {
            return minZ;
        }

        public int getMaxX() {
            return maxX;
        }

        public int getMaxZ() {
            return maxZ;
        }
    }

    // Only call this on the controller (bottom elevator)
    private boolean startMoving(BlockPos start, BlockPos stop, IBlockState state) {
        movingState = state;
        positions.clear();

        getBounds(start);
        if(bounds.getMaxX() < bounds.getMinX() || bounds.getMaxZ() < bounds.getMinZ()) {
            // No blocks were added to bounds. This happens when canMoveBlock
            // returns false for the platform block right in front of the elevator.
            // If this is the case, we can't move at all.
            bounds = null;
            return false;
        }

        movingY = start.getY();
        startY = start.getY();
        stopY = stop.getY();

        // @todo
        // Make sure positions is only sent to client at the beginning
        markDirtyClient();
        return true;
    }

    private boolean canMoveBlock(BlockPos pos) {
        World world = getWorld();
        IBlockState state = world.getBlockState(pos);
        return state == movingState && state.getBlockHardness(world, pos) >= 0 && BuilderTileEntity.allowedToBreak(state, world, pos, getHarvester());
    }

    // Always called on controller TE (bottom one)
    private void getBounds(BlockPos start) {
        EnumFacing side = getWorld().getBlockState(getPos()).getValue(FACING_HORIZ);
        bounds = new Bounds();
        for (int a = 1; a < ElevatorConfiguration.maxPlatformSize.get(); a++) {
            BlockPos offset = start.offset(side, a);
            if (canMoveBlock(offset)) {
                getWorld().setBlockToAir(offset);
                bounds.addPos(offset);
                positions.add(getPosAtY(offset, getPos().getY()));

                for (int b = 1; b <= (ElevatorConfiguration.maxPlatformSize.get() / 2); b++) {
                    BlockPos offsetLeft = offset.offset(side.rotateY(), b);
                    if (canMoveBlock(offsetLeft)) {
                        getWorld().setBlockToAir(offsetLeft);
                        bounds.addPos(offsetLeft);
                        positions.add(getPosAtY(offsetLeft, getPos().getY()));
                    } else {
                        break;
                    }
                }

                for (int b = 1; b <= (ElevatorConfiguration.maxPlatformSize.get() / 2); b++) {
                    BlockPos offsetRight = offset.offset(side.rotateYCCW(), b);
                    if (canMoveBlock(offsetRight)) {
                        getWorld().setBlockToAir(offsetRight);
                        bounds.addPos(offsetRight);
                        positions.add(getPosAtY(offsetRight, getPos().getY()));
                    } else {
                        break;
                    }
                }
            } else {
                break;
            }
        }
    }

    public AxisAlignedBB getAABBBigMargin() {
        return new AxisAlignedBB(bounds.getMinX(), movingY-150, bounds.getMinZ(), bounds.getMaxX() + 1, movingY + 150, bounds.getMaxZ() + 1);
    }


    public AxisAlignedBB getAABBAboveElevator(double speed) {
        double o1;
        double o2;
        if (speed > 0) {
            o1 = -speed * 2;
            o2 = 0;
        } else {
            o1 = 0;
            o2 = -speed * 2;
        }
        return new AxisAlignedBB(bounds.getMinX(), movingY-1+o1, bounds.getMinZ(), bounds.getMaxX() + 1, movingY + 3+o2, bounds.getMaxZ() + 1);
    }

    public boolean isMoving() {
        return movingY >= 0;
    }

    public double getMovingY() {
        return movingY;
    }

    // Go to the specific level (levels start at 0)
    public void toLevel(int level) {
        EnumFacing side = getWorld().getBlockState(getPos()).getValue(FACING_HORIZ);
        BlockPos controllerPos = findBottomElevator();
        for (int y = controllerPos.getY() ; y < getWorld().getHeight() ; y++) {
            BlockPos pos2 = getPosAtY(controllerPos, y);
            if (getWorld().getBlockState(pos2).getBlock() == ElevatorSetup.elevatorBlock) {
                TileEntity te2 = getWorld().getTileEntity(pos2);
                if (te2 instanceof ElevatorTileEntity) {
                    EnumFacing side2 = getWorld().getBlockState(pos2).getValue(FACING_HORIZ);
                    if (side == side2) {
                        if (level == 0) {
                            ((ElevatorTileEntity) te2).movePlatformHere();
                            return;
                        }
                        level--;
                    }
                }
            }
        }
    }

    public void findElevatorBlocks(List<Integer> heights) {
        BlockPos controllerPos = findBottomElevator();
        if (controllerPos == null) {
            // Cannot happen
            return;
        }
        IBlockState blockState = getWorld().getBlockState(getPos());
        if (blockState.getBlock() != ElevatorSetup.elevatorBlock) {
            return;
        }
        EnumFacing side = blockState.getValue(FACING_HORIZ);
        for (int y = controllerPos.getY() ; y < getWorld().getHeight() ; y++) {
            BlockPos pos2 = getPosAtY(controllerPos, y);
            TileEntity te2 = getWorld().getTileEntity(pos2);
            if (te2 instanceof ElevatorTileEntity) {
                EnumFacing side2 = getWorld().getBlockState(pos2).getValue(FACING_HORIZ);
                if (side == side2) {
                    heights.add(y);
                }
            }
        }
    }


    public int getCurrentLevel(List<Integer> heights) {
        BlockPos controllerPos = findBottomElevator();
        TileEntity te = getWorld().getTileEntity(controllerPos);
        if (te instanceof ElevatorTileEntity) {
            EnumFacing side = getWorld().getBlockState(controllerPos).getValue(FACING_HORIZ);
            ElevatorTileEntity controller = (ElevatorTileEntity) te;
            if (controller.cachedCurrent == -1) {
                int level = 0;
                for (Integer y : heights) {
                    BlockPos pos2 = getPosAtY(controllerPos, y);
                    BlockPos frontPos = pos2.offset(side);
                    if (isValidPlatformBlock(frontPos)) {
                        controller.cachedCurrent = level;
                    }
                    level++;
                }
            }
            return controller.cachedCurrent;
        }
        return 0;
    }

    public int getLevelCount(List<Integer> heights) {
        return heights.size();
    }

    // Return true if the platform is here (i.e. there is a block in front of the elevator)
    public boolean isPlatformHere() {
        IBlockState blockState = getWorld().getBlockState(getPos());
        if (blockState.getBlock() != ElevatorSetup.elevatorBlock) {
            return false;
        }
        EnumFacing side = blockState.getValue(FACING_HORIZ);
        BlockPos frontPos = getPos().offset(side);
        return isValidPlatformBlock(frontPos);
    }

    // Can be called on any elevator block. Not only the contoller (bottom one)
    public void movePlatformHere() {
        // Try to find a platform and move it to this elevator.
        // What about TE blocks in front of platform?

        // First check if the platform is here already:
        IBlockState blockState = getWorld().getBlockState(getPos());
        if (blockState.getBlock() != ElevatorSetup.elevatorBlock) {
            return;
        }
        EnumFacing side = blockState.getValue(FACING_HORIZ);
        BlockPos frontPos = getPos().offset(side);
        if (isValidPlatformBlock(frontPos)) {
            // Platform is already here (or something is blocking here)
            return;
        }

        // Find where the platform is.
        BlockPos platformPos = findElevatorWithPlatform();
        if (platformPos == null) {
            // No elevator platform found
            return;
        }

        // Find the bottom elevator (this is the one doing the work).
        BlockPos controllerPos = findBottomElevator();
        ElevatorTileEntity controller = (ElevatorTileEntity) getWorld().getTileEntity(controllerPos);

        if (controller.isMoving()) {
            // Already moving, do nothing
            return;
        }

        // Check if we have enough energy
        int rfNeeded = (int) (ElevatorConfiguration.rfPerHeightUnit.get() * Math.abs(getPos().getY() - platformPos.getY()) * (3.0f - getInfusedFactor()) / 3.0f);
        if (controller.getStoredPower() < rfNeeded) {
            Broadcaster.broadcast(getWorld(), getPos().getX(), getPos().getY(), getPos().getZ(), TextFormatting.RED + "Not enough power to move the elevator platform!", 10);
            return;
        }

        if(controller.startMoving(platformPos, getPos(), getWorld().getBlockState(platformPos.offset(side)))) {
            controller.consumeEnergy(rfNeeded);
        } else {
            Broadcaster.broadcast(getWorld(), getPos().getX(), getPos().getY(), getPos().getZ(), TextFormatting.RED + "The block in front of the elevator platform could not be moved!", 10);
        }

    }

    public static BlockPos getPosAtY(BlockPos p, int y) {
        return new BlockPos(p.getX(), y, p.getZ());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        if (isMoving()) {
            return new AxisAlignedBB(getPos().add(-9, 0, -9), getPos().add(9, 255, 9));
        }
        return super.getRenderBoundingBox();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public double getMaxRenderDistanceSquared() {
        if (isMoving()) {
            return 256 * 256;
        } else {
            return super.getMaxRenderDistanceSquared();
        }
    }

    private static short bytesToShort(byte b1, byte b2) {
        short s1 = (short) (b1 & 0xff);
        short s2 = (short) (b2 & 0xff);
        return (short) (s1 * 256 + s2);
    }

    private static byte shortToByte1(short s) {
        return (byte) ((s & 0xff00) >> 8);
    }

    private static byte shortToByte2(short s) {
        return (byte) (s & 0xff);
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        NBTTagCompound compound = packet.getNbtCompound();
        this.readFromNBTCommon(compound);
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        readFromNBTCommon(tagCompound);
        redstoneOut = tagCompound.getBoolean("rs");
        entitiesOnPlatformComplete = false;
        if (tagCompound.hasKey("players")) {
            entitiesOnPlatform.clear();
            WorldServer world = DimensionManager.getWorld(0);
            List<EntityPlayerMP> serverPlayers = world.getMinecraftServer().getPlayerList().getPlayers();
            NBTTagList playerList = tagCompound.getTagList("players", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < playerList.tagCount(); i++) {
                NBTTagCompound p = playerList.getCompoundTagAt(i);
                long lsb = p.getLong("lsb");
                long msb = p.getLong("msb");
                UUID uuid = new UUID(msb, lsb);
                for (EntityPlayerMP serverPlayer : serverPlayers) {
                    if (serverPlayer.getGameProfile().getId().equals(uuid)) {
                        entitiesOnPlatform.add(serverPlayer);
                        break;
                    }
                }
            }
        }
    }

    private void readFromNBTCommon(NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);
        prevIn = tagCompound.getBoolean("prevIn");
        movingY = tagCompound.getDouble("movingY");
        startY = tagCompound.getInteger("startY");
        stopY = tagCompound.getInteger("stopY");
        byte[] byteArray = tagCompound.getByteArray("relcoords");
        positions.clear();
        int j = 0;
        for (int i = 0; i < byteArray.length / 6; i++) {
            short dx = bytesToShort(byteArray[j + 0], byteArray[j + 1]);
            short dy = bytesToShort(byteArray[j + 2], byteArray[j + 3]);
            short dz = bytesToShort(byteArray[j + 4], byteArray[j + 5]);
            j += 6;
            RelCoordinate c = new RelCoordinate(dx, dy, dz);
            positions.add(new BlockPos(getPos().getX() + c.getDx(), getPos().getY() + c.getDy(), getPos().getZ() + c.getDz()));
        }
        if (tagCompound.hasKey("bminX")) {
            int bminX = tagCompound.getInteger("bminX");
            int bminZ = tagCompound.getInteger("bminZ");
            int bmaxX = tagCompound.getInteger("bmaxX");
            int bmaxZ = tagCompound.getInteger("bmaxZ");
            if(bminX <= bmaxX && bminZ <= bmaxZ) { // Fix saves that were affected by issue #1601 by validating bounds here
                bounds = new Bounds(bminX, bminZ, bmaxX, bmaxZ);
            }
        }
        if (tagCompound.hasKey("movingId")) {
            Integer id = tagCompound.getInteger("movingId");
            movingState = Block.getStateById(id);
        } else if (tagCompound.hasKey("movingBlock")) {
            // Deprecated (@todo remove in 1.13)
            String id = tagCompound.getString("movingBlock");
            int meta = tagCompound.getInteger("movingMeta");
            movingState = Block.REGISTRY.getObject(new ResourceLocation(id)).getStateFromMeta(meta);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
        super.writeToNBT(tagCompound);
        tagCompound.setBoolean("rs", redstoneOut);
        tagCompound.setBoolean("prevIn", prevIn);
        tagCompound.setDouble("movingY", movingY);
        tagCompound.setInteger("startY", startY);
        tagCompound.setInteger("stopY", stopY);
        byte[] blocks = new byte[positions.size() * 6];
        int j = 0;
        for (BlockPos pos : positions) {
            RelCoordinate c = new RelCoordinate(pos.getX() - getPos().getX(), pos.getY() - getPos().getY(), pos.getZ() - getPos().getZ());
            blocks[j + 0] = shortToByte1((short) c.getDx());
            blocks[j + 1] = shortToByte2((short) c.getDx());
            blocks[j + 2] = shortToByte1((short) c.getDy());
            blocks[j + 3] = shortToByte2((short) c.getDy());
            blocks[j + 4] = shortToByte1((short) c.getDz());
            blocks[j + 5] = shortToByte2((short) c.getDz());
            j += 6;
        }
        if (bounds != null) {
            tagCompound.setInteger("bminX", bounds.getMinX());
            tagCompound.setInteger("bminZ", bounds.getMinZ());
            tagCompound.setInteger("bmaxX", bounds.getMaxX());
            tagCompound.setInteger("bmaxZ", bounds.getMaxZ());
        }
        tagCompound.setByteArray("relcoords", blocks);
        if (movingState != null) {
            tagCompound.setInteger("movingId", Block.getStateId(movingState));
        }
        if (!getWorld().isRemote) {
            // Only do this server side
            if (!entitiesOnPlatform.isEmpty()) {
                NBTTagList playerList = new NBTTagList();
                for (Entity entity : entitiesOnPlatform) {
                    if (entity instanceof EntityPlayer) {
                        EntityPlayer player = (EntityPlayer) entity;
                        UUID id = player.getGameProfile().getId();
                        NBTTagCompound p = new NBTTagCompound();
                        p.setLong("lsb", id.getLeastSignificantBits());
                        p.setLong("msb", id.getMostSignificantBits());
                        playerList.appendTag(p);
                    }
                }
                tagCompound.setTag("players", playerList);
            }
        }
        return tagCompound;
    }

    @Override
    public void readRestorableFromNBT(NBTTagCompound tagCompound) {
        super.readRestorableFromNBT(tagCompound);
        name = tagCompound.getString("levelName");
    }

    @Override
    public void writeRestorableToNBT(NBTTagCompound tagCompound) {
        super.writeRestorableToNBT(tagCompound);
        tagCompound.setString("levelName", name == null ? "" : name);
    }

    @Override
    public boolean execute(EntityPlayerMP playerMP, String command, TypedMap params) {
        boolean rc = super.execute(playerMP, command, params);
        if (rc) {
            return true;
        }
        if (CMD_SETNAME.equals(command)) {
            setName(params.get(TextField.PARAM_TEXT));
            return true;
        }
        return false;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        clearCaches(world.getBlockState(pos).getValue(FACING_HORIZ));
    }

    @Override
    public void onBlockBreak(World world, BlockPos pos, IBlockState state) {
        super.onBlockBreak(world, pos, state);
        clearCaches(state.getValue(FACING_HORIZ));
    }

    @Override
    @net.minecraftforge.fml.common.Optional.Method(modid = "theoneprobe")
    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world, IBlockState blockState, IProbeHitData data) {
        super.addProbeInfo(mode, probeInfo, player, world, blockState, data);
        probeInfo.text(TextFormatting.BLUE + "Name: " + getName());
    }

    @SideOnly(Side.CLIENT)
    @Override
    @Optional.Method(modid = "waila")
    public void addWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        super.addWailaBody(itemStack, currenttip, accessor, config);
        long energy = getStoredPower();
        currenttip.add(TextFormatting.GREEN + "RF: " + energy);
        if (getName() != null && !getName().isEmpty()) {
            currenttip.add(TextFormatting.BLUE + "Name: " + getName());
        }
    }

    @Override
    public int getRedstoneOutput(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        EnumFacing direction = state.getValue(FACING_HORIZ);
        if (side == direction) {
            return isPlatformHere() ? 15 : 0;
        }
        return 0;
    }
}
