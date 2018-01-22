package mcjty.rftools.commands;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public abstract class AbstractRfToolsCommand implements RfToolsCommand {

    protected String fetchString(ICommandSender sender, String[] args, int index, String defaultValue) {
        try {
            return args[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            return defaultValue;
        }
    }

    protected boolean fetchBool(ICommandSender sender, String[] args, int index, boolean defaultValue) {
        boolean value;
        try {
            value = Boolean.valueOf(args[index]);
        } catch (NumberFormatException e) {
            value = false;
            ITextComponent component = new TextComponentString(TextFormatting.RED + "Parameter is not a valid boolean!");
            if (sender instanceof EntityPlayer) {
                ((EntityPlayer) sender).sendStatusMessage(component, false);
            } else {
                sender.sendMessage(component);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            return defaultValue;
        }
        return value;
    }

    protected int fetchInt(ICommandSender sender, String[] args, int index, int defaultValue) {
        int value;
        try {
            value = Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            value = 0;
            ITextComponent component = new TextComponentString(TextFormatting.RED + "Parameter is not a valid integer!");
            if (sender instanceof EntityPlayer) {
                ((EntityPlayer) sender).sendStatusMessage(component, false);
            } else {
                sender.sendMessage(component);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            return defaultValue;
        }
        return value;
    }

    protected float fetchFloat(ICommandSender sender, String[] args, int index, float defaultValue) {
        float value;
        try {
            value = Float.parseFloat(args[index]);
        } catch (NumberFormatException e) {
            value = 0.0f;
            ITextComponent component = new TextComponentString(TextFormatting.RED + "Parameter is not a valid real number!");
            if (sender instanceof EntityPlayer) {
                ((EntityPlayer) sender).sendStatusMessage(component, false);
            } else {
                sender.sendMessage(component);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            return defaultValue;
        }
        return value;
    }

    @Override
    public boolean isClientSide() {
        return false;
    }
}
