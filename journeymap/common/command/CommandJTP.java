package journeymap.common.command;

import net.minecraft.server.*;
import net.minecraft.command.*;
import journeymap.common.network.model.*;
import journeymap.common.feature.*;
import net.minecraft.entity.*;

public class CommandJTP extends CommandBase
{
    public boolean func_184882_a(final MinecraftServer server, final ICommandSender sender) {
        return true;
    }
    
    public String func_71517_b() {
        return "jtp";
    }
    
    public String func_71518_a(final ICommandSender sender) {
        return "/jtp <x y z dim>";
    }
    
    public void func_184881_a(final MinecraftServer server, final ICommandSender sender, final String[] args) throws CommandException {
        if (args.length < 4) {
            throw new CommandException(this.func_71518_a(sender), new Object[0]);
        }
        final Entity player = (Entity)func_71521_c(sender);
        try {
            final double x = Double.parseDouble(args[0]);
            final double y = Double.parseDouble(args[1]);
            final double z = Double.parseDouble(args[2]);
            final int dim = Integer.parseInt(args[3]);
            final Location location = new Location(x, y, z, dim);
            JourneyMapTeleport.attemptTeleport(player, location);
        }
        catch (NumberFormatException nfe) {
            throw new CommandException("Numbers only! Usage: " + this.func_71518_a(sender) + nfe, new Object[0]);
        }
        catch (Exception e) {
            throw new CommandException("/jtp failed Usage: " + this.func_71518_a(sender), new Object[0]);
        }
    }
}
