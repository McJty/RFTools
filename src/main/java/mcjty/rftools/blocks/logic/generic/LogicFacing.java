package mcjty.rftools.blocks.logic.generic;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;

import static net.minecraft.util.EnumFacing.*;

public enum LogicFacing implements IStringSerializable {
    DOWN_TONORTH("down_tonorth", 0, NORTH),
    DOWN_TOSOUTH("down_tosouth", 1, SOUTH),
    DOWN_TOWEST("down_towest", 2, WEST),
    DOWN_TOEAST("down_toeast", 3, EAST),

    UP_TONORTH("up_tonorth", 0, NORTH),
    UP_TOSOUTH("up_tosouth", 1, SOUTH),
    UP_TOWEST("up_towest", 2, WEST),
    UP_TOEAST("up_toeast", 3, EAST),

    NORTH_TOWEST("north_towest", 0, WEST),
    NORTH_TOEAST("north_toeast", 1, EAST),
    NORTH_TOUP("north_toup", 2, UP),
    NORTH_TODOWN("north_todown", 3, DOWN),

    SOUTH_TOWEST("south_towest", 0, WEST),
    SOUTH_TOEAST("south_toeast", 1, EAST),
    SOUTH_TOUP("south_toup", 2, UP),
    SOUTH_TODOWN("south_todown", 3, DOWN),

    WEST_TONORTH("west_tonorth", 0, NORTH),
    WEST_TOSOUTH("west_tosouth", 1, SOUTH),
    WEST_TOUP("west_toup", 2, UP),
    WEST_TODOWN("west_todown", 3, DOWN),

    EAST_TONORTH("east_tonorth", 0, NORTH),
    EAST_TOSOUTH("east_tosouth", 1, SOUTH),
    EAST_TOUP("east_toup", 2, UP),
    EAST_TODOWN("east_todown", 3, DOWN);

    public static final LogicFacing[] VALUES = LogicFacing.values();

    private final String name;
    private final int meta;
    private final EnumFacing inputSide;

    LogicFacing(String name, int meta, EnumFacing inputSide) {
        this.name = name;
        this.meta = meta;
        this.inputSide = inputSide;
    }

    @Override
    public String getName() {
        return name;
    }

    public int getMeta() {
        return meta;
    }

    public EnumFacing getInputSide() {
        return inputSide;
    }

    public EnumFacing getSide() {
        return EnumFacing.VALUES[ordinal() / 4];
    }

    public static LogicFacing getFacingWithMeta(LogicFacing facing, int meta) {
        return LogicFacing.VALUES[(facing.ordinal() & ~3) + meta];
    }
}
