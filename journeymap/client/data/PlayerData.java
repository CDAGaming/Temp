package journeymap.client.data;

import com.google.common.cache.*;
import net.minecraft.client.*;
import net.minecraft.entity.player.*;
import net.minecraft.world.*;
import net.minecraft.util.math.*;
import journeymap.client.model.*;
import net.minecraftforge.fml.client.*;
import net.minecraft.entity.*;
import journeymap.client.log.*;
import net.minecraft.world.biome.*;
import journeymap.common.*;

public class PlayerData extends CacheLoader<Class, EntityDTO>
{
    public static boolean playerIsUnderground(final Minecraft mc, final EntityPlayer player) {
        if (player.func_130014_f_().field_73011_w instanceof WorldProviderHell) {
            return true;
        }
        final int posX = MathHelper.func_76128_c(player.field_70165_t);
        final int posY = MathHelper.func_76128_c(player.func_174813_aQ().field_72338_b);
        final int posZ = MathHelper.func_76128_c(player.field_70161_v);
        final int offset = 1;
        boolean isUnderground = true;
        if (posY < 0) {
            return true;
        }
        int y = posY;
    Label_0157:
        for (int x = posX - 1; x <= posX + 1; ++x) {
            for (int z = posZ - 1; z <= posZ + 1; ++z) {
                y = posY + 1;
                final ChunkMD chunkMD = DataCache.INSTANCE.getChunkMD(new ChunkPos(x >> 4, z >> 4));
                if (chunkMD != null && chunkMD.ceiling(x & 0xF, z & 0xF) <= y) {
                    isUnderground = false;
                    break Label_0157;
                }
            }
        }
        return isUnderground;
    }
    
    public EntityDTO load(final Class aClass) throws Exception {
        final Minecraft mc = FMLClientHandler.instance().getClient();
        final EntityPlayer player = (EntityPlayer)mc.field_71439_g;
        final EntityDTO dto = DataCache.INSTANCE.getEntityDTO((EntityLivingBase)player);
        dto.update((EntityLivingBase)player, false);
        dto.biome = this.getPlayerBiome(player);
        dto.underground = playerIsUnderground(mc, player);
        return dto;
    }
    
    private String getPlayerBiome(final EntityPlayer player) {
        if (player != null) {
            try {
                final Biome biome = FMLClientHandler.instance().getClient().field_71441_e.getBiomeForCoordsBody(player.func_180425_c());
                if (biome != null) {
                    return biome.func_185359_l();
                }
            }
            catch (Exception e) {
                JMLogger.logOnce("Couldn't get player biome: " + e.getMessage(), e);
            }
        }
        return "?";
    }
    
    public long getTTL() {
        return Journeymap.getClient().getCoreProperties().cachePlayerData.get();
    }
}
