package journeymap.client.command;

import net.minecraft.client.*;
import journeymap.client.model.*;
import net.minecraftforge.fml.client.*;
import com.mojang.authlib.*;
import journeymap.common.*;
import journeymap.common.log.*;
import net.minecraft.server.integrated.*;
import net.minecraft.server.management.*;
import java.util.*;

public class CmdTeleportWaypoint
{
    final Minecraft mc;
    final Waypoint waypoint;
    
    public CmdTeleportWaypoint(final Waypoint waypoint) {
        this.mc = FMLClientHandler.instance().getClient();
        this.waypoint = waypoint;
    }
    
    public static boolean isPermitted(final Minecraft mc) {
        if (mc.func_71401_C() != null) {
            final IntegratedServer mcServer = mc.func_71401_C();
            PlayerList configurationManager = null;
            GameProfile profile = null;
            try {
                profile = new GameProfile(mc.field_71439_g.func_110124_au(), mc.field_71439_g.func_70005_c_());
                configurationManager = mcServer.func_184103_al();
                return configurationManager.func_152596_g(profile);
            }
            catch (Exception e) {
                e.printStackTrace();
                try {
                    if (profile != null && configurationManager != null) {
                        return mcServer.func_71264_H() && mcServer.field_71305_c[0].func_72912_H().func_76086_u() && mcServer.func_71214_G().equalsIgnoreCase(profile.getName());
                    }
                    Journeymap.getLogger().warn("Failed to check teleport permission both ways: " + LogFormatter.toString(e) + ", and profile or configManager were null.");
                    return true;
                }
                catch (Exception e2) {
                    Journeymap.getLogger().warn("Failed to check teleport permission. Both ways failed: " + LogFormatter.toString(e) + ", and " + LogFormatter.toString(e2));
                }
            }
        }
        return true;
    }
    
    public void run() {
        double x = this.waypoint.getBlockCenteredX();
        double z = this.waypoint.getBlockCenteredZ();
        final TreeSet<Integer> dim = (TreeSet<Integer>)(TreeSet)this.waypoint.getDimensions();
        if (dim.first() == -1 && this.mc.field_71439_g.field_71093_bK != -1) {
            x /= 8.0;
            z /= 8.0;
        }
        else if (dim.first() != -1 && this.mc.field_71439_g.field_71093_bK == -1) {
            x *= 8.0;
            z *= 8.0;
        }
        if (Journeymap.getClient().isServerEnabled() || FMLClientHandler.instance().getClient().func_71356_B()) {
            this.mc.field_71439_g.func_71165_d(String.format("/jtp %s %s %s %s", x, this.waypoint.getY(), z, dim.first()));
        }
        else {
            this.mc.field_71439_g.func_71165_d(String.format("/tp %s %s %s %s", this.mc.field_71439_g.func_70005_c_(), this.waypoint.getX(), this.waypoint.getY(), this.waypoint.getZ()));
        }
    }
}
