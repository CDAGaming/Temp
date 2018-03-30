package journeymap.client.feature;

import net.minecraft.client.*;
import java.util.*;
import net.minecraft.server.integrated.*;
import net.minecraftforge.fml.client.*;

public class Policy
{
    static Minecraft mc;
    final Feature feature;
    final boolean allowInSingleplayer;
    final boolean allowInMultiplayer;
    
    public Policy(final Feature feature, final boolean allowInSingleplayer, final boolean allowInMultiplayer) {
        this.feature = feature;
        this.allowInSingleplayer = allowInSingleplayer;
        this.allowInMultiplayer = allowInMultiplayer;
    }
    
    public static Set<Policy> bulkCreate(final boolean allowInSingleplayer, final boolean allowInMultiplayer) {
        return bulkCreate(Feature.all(), allowInSingleplayer, allowInMultiplayer);
    }
    
    public static Set<Policy> bulkCreate(final EnumSet<Feature> features, final boolean allowInSingleplayer, final boolean allowInMultiplayer) {
        final Set<Policy> policies = new HashSet<Policy>();
        for (final Feature feature : features) {
            policies.add(new Policy(feature, allowInSingleplayer, allowInMultiplayer));
        }
        return policies;
    }
    
    public boolean isCurrentlyAllowed() {
        if (this.allowInSingleplayer == this.allowInMultiplayer) {
            return this.allowInSingleplayer;
        }
        final IntegratedServer server = Policy.mc.func_71401_C();
        final boolean isSinglePlayer = server != null && !server.func_71344_c();
        return (this.allowInSingleplayer && isSinglePlayer) || (this.allowInMultiplayer && !isSinglePlayer);
    }
    
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final Policy policy = (Policy)o;
        return this.allowInMultiplayer == policy.allowInMultiplayer && this.allowInSingleplayer == policy.allowInSingleplayer && this.feature == policy.feature;
    }
    
    @Override
    public int hashCode() {
        int result = this.feature.hashCode();
        result = 31 * result + (this.allowInSingleplayer ? 1 : 0);
        result = 31 * result + (this.allowInMultiplayer ? 1 : 0);
        return result;
    }
    
    static {
        Policy.mc = FMLClientHandler.instance().getClient();
    }
}
