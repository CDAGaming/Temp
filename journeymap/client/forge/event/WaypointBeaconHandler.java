package journeymap.client.forge.event;

import net.minecraft.client.*;
import net.minecraftforge.fml.client.*;
import net.minecraftforge.client.event.*;
import journeymap.common.*;
import journeymap.client.render.ingame.*;
import net.minecraftforge.fml.relauncher.*;
import net.minecraftforge.fml.common.eventhandler.*;

public class WaypointBeaconHandler implements EventHandlerManager.EventHandler
{
    final Minecraft mc;
    
    public WaypointBeaconHandler() {
        this.mc = FMLClientHandler.instance().getClient();
    }
    
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onRenderWorldLastEvent(final RenderWorldLastEvent event) {
        if (this.mc.field_71439_g != null && Journeymap.getClient().getWaypointProperties().beaconEnabled.get() && !this.mc.field_71474_y.field_74319_N) {
            this.mc.field_71424_I.func_76320_a("journeymap");
            this.mc.field_71424_I.func_76320_a("beacons");
            RenderWaypointBeacon.renderAll();
            this.mc.field_71424_I.func_76319_b();
            this.mc.field_71424_I.func_76319_b();
        }
    }
}
