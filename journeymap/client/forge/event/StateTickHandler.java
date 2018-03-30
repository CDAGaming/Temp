package journeymap.client.forge.event;

import net.minecraftforge.fml.relauncher.*;
import net.minecraft.client.*;
import net.minecraftforge.fml.client.*;
import net.minecraftforge.fml.common.gameevent.*;
import journeymap.common.*;
import journeymap.client.api.impl.*;
import journeymap.common.log.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraft.util.math.*;
import journeymap.client.api.event.*;
import journeymap.client.model.*;
import journeymap.client.waypoint.*;
import net.minecraft.entity.player.*;
import journeymap.client.properties.*;
import net.minecraft.client.resources.*;
import net.minecraft.util.text.*;

@SideOnly(Side.CLIENT)
public class StateTickHandler implements EventHandlerManager.EventHandler
{
    static boolean javaChecked;
    Minecraft mc;
    int counter;
    private boolean deathpointCreated;
    
    public StateTickHandler() {
        this.mc = FMLClientHandler.instance().getClient();
        this.counter = 0;
    }
    
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            return;
        }
        this.mc.field_71424_I.func_76320_a("journeymap");
        if (this.mc.field_71439_g != null && this.mc.field_71439_g.field_70128_L) {
            if (!this.deathpointCreated) {
                this.deathpointCreated = true;
                this.createDeathpoint();
            }
        }
        else {
            this.deathpointCreated = false;
        }
        if (!StateTickHandler.javaChecked && this.mc.field_71439_g != null && !this.mc.field_71439_g.field_70128_L) {
            this.checkJava();
        }
        try {
            if (this.counter == 20) {
                this.mc.field_71424_I.func_76320_a("mainTasks");
                Journeymap.getClient().performMainThreadTasks();
                this.counter = 0;
                this.mc.field_71424_I.func_76319_b();
            }
            else if (this.counter == 10) {
                this.mc.field_71424_I.func_76320_a("multithreadTasks");
                if (Journeymap.getClient().isMapping() && this.mc.field_71441_e != null) {
                    Journeymap.getClient().performMultithreadTasks();
                }
                ++this.counter;
                this.mc.field_71424_I.func_76319_b();
            }
            else if (this.counter == 5 || this.counter == 15) {
                this.mc.field_71424_I.func_76320_a("clientApiEvents");
                ClientAPI.INSTANCE.getClientEventManager().fireNextClientEvents();
                ++this.counter;
                this.mc.field_71424_I.func_76319_b();
            }
            else {
                ++this.counter;
            }
        }
        catch (Throwable t) {
            Journeymap.getLogger().warn("Error during onClientTick: " + LogFormatter.toPartialString(t));
        }
        finally {
            this.mc.field_71424_I.func_76319_b();
        }
    }
    
    private void createDeathpoint() {
        try {
            final EntityPlayer player = (EntityPlayer)this.mc.field_71439_g;
            if (player == null) {
                Journeymap.getLogger().error("Lost reference to player before Deathpoint could be created");
                return;
            }
            final WaypointProperties waypointProperties = Journeymap.getClient().getWaypointProperties();
            final boolean enabled = waypointProperties.managerEnabled.get() && waypointProperties.createDeathpoints.get();
            boolean cancelled = false;
            final BlockPos pos = new BlockPos(MathHelper.func_76128_c(player.field_70165_t), MathHelper.func_76128_c(player.field_70163_u), MathHelper.func_76128_c(player.field_70161_v));
            if (enabled) {
                final int dim = FMLClientHandler.instance().getClient().field_71439_g.field_70170_p.field_73011_w.getDimension();
                final DeathWaypointEvent event = new DeathWaypointEvent(pos, dim);
                ClientAPI.INSTANCE.getClientEventManager().fireDeathpointEvent(event);
                if (!event.isCancelled()) {
                    final Waypoint deathpoint = Waypoint.at(pos, Waypoint.Type.Death, dim);
                    WaypointStore.INSTANCE.save(deathpoint);
                }
                else {
                    cancelled = true;
                }
            }
            Journeymap.getLogger().info(String.format("%s died at %s. Deathpoints enabled: %s. Deathpoint created: %s", player.func_70005_c_(), pos, enabled, cancelled ? "cancelled" : true));
        }
        catch (Throwable t) {
            Journeymap.getLogger().error("Unexpected Error in createDeathpoint(): " + LogFormatter.toString(t));
        }
    }
    
    private void checkJava() {
        StateTickHandler.javaChecked = true;
        try {
            Class.forName("java.util.Objects");
        }
        catch (ClassNotFoundException e3) {
            try {
                final String error = I18n.func_135052_a("jm.error.java6", new Object[0]);
                FMLClientHandler.instance().getClient().field_71456_v.func_146158_b().func_146227_a((ITextComponent)new TextComponentString(error));
                Journeymap.getLogger().fatal("JourneyMap requires Java 7 or Java 8. Update your launcher profile to use a newer version of Java.");
            }
            catch (Exception e2) {
                e2.printStackTrace();
            }
            Journeymap.getClient().disable();
        }
    }
    
    static {
        StateTickHandler.javaChecked = false;
    }
}
