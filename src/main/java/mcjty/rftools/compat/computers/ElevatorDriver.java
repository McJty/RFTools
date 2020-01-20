package mcjty.rftools.compat.computers;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import mcjty.lib.integration.computers.AbstractOCDriver;
import mcjty.rftools.blocks.elevator.ElevatorTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class ElevatorDriver {

	public static class OCDriver extends AbstractOCDriver {

		public OCDriver() {
			super("rftools_elevator", ElevatorTileEntity.class);
		}

		public static class InternalManagedEnvironment extends AbstractOCDriver.InternalManagedEnvironment<ElevatorTileEntity> {

			public InternalManagedEnvironment(ElevatorTileEntity tile) {
				super(tile, "rftools_elevator");
			}

			@Callback(doc = "function():number; Get the currently stored energy")
			public Object[] getEnergy(Context c, Arguments a) {
				BlockPos controllerPos = tile.findBottomElevator();
				ElevatorTileEntity controller = (ElevatorTileEntity) tile.getWorld().getTileEntity(controllerPos);
				if(controller == null) {
					throw new IllegalStateException("no valid elevator found");
				}
				return new Object[] { controller.getStoredPower() };
			}

			@Callback(doc = "function():number; Get the maximum stored energy")
			public Object[] getMaxEnergy(Context c, Arguments a) {
				BlockPos controllerPos = tile.findBottomElevator();
				ElevatorTileEntity controller = (ElevatorTileEntity) tile.getWorld().getTileEntity(controllerPos);
				if(controller == null) {
					throw new IllegalStateException("no valid elevator found");
				}
				return new Object[] { controller.getCapacity() };
			}

			@Callback(doc = "function():number; Returns the level the elevator is currently at")
			public Object[] getCurrentLevel(Context c, Arguments a) {
				List<Integer> heights = new ArrayList<>();
				tile.findElevatorBlocks(heights);
				return new Object[] { tile.getCurrentLevel(heights) };
			}

			@Callback(doc = "function():number; Returns the number of levels")
			public Object[] getLevelCount(Context c, Arguments a) {
				List<Integer> heights = new ArrayList<>();
				tile.findElevatorBlocks(heights);
				return new Object[] { tile.getLevelCount(heights) };
			}

			@Callback(doc = "function():boolean; Returns whether the elevator is currently in motion")
			public Object[] isMoving(Context c, Arguments a) {
				BlockPos controllerPos = tile.findBottomElevator();
				ElevatorTileEntity controller = (ElevatorTileEntity) tile.getWorld().getTileEntity(controllerPos);
				if(controller == null) {
					throw new IllegalStateException("no valid elevator found");
				}
				return new Object[] { controller.isMoving() };
			}

			@Callback(doc = "function(); Make the elevator go to the level of this controller, if possible")
			public Object[] movePlatformHere(Context c, Arguments a) {
				tile.movePlatformHere();
				return new Object[] {};
			}

			@Callback(doc = "function():boolean; Returns whether the elevator is at the level of this controller")
			public Object[] isPlatformHere(Context c, Arguments a) {
				return new Object[] { tile.isPlatformHere() };
			}

			@Override
			public int priority() {
				return 4;
			}
		}

		@Override
		public AbstractManagedEnvironment createEnvironment(World world, BlockPos pos, EnumFacing side, TileEntity tile) {
			return new InternalManagedEnvironment((ElevatorTileEntity) tile);
		}
	}
}
