package journeymap.client.model;

import net.minecraftforge.fml.client.*;
import journeymap.client.data.*;
import journeymap.common.*;
import journeymap.common.log.*;
import net.minecraft.client.*;
import net.minecraft.util.math.*;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.player.*;
import net.minecraft.entity.*;
import net.minecraft.client.entity.*;
import java.util.*;
import com.google.common.collect.*;
import net.minecraft.util.*;
import net.minecraft.entity.passive.*;
import journeymap.client.log.*;
import net.minecraft.client.renderer.entity.*;

public class EntityHelper
{
    public static EntityDistanceComparator entityDistanceComparator;
    public static EntityDTODistanceComparator entityDTODistanceComparator;
    public static EntityMapComparator entityMapComparator;
    private static final String[] HORSE_TEXTURES;
    
    public static List<EntityDTO> getEntitiesNearby(final String timerName, final int maxEntities, final boolean hostile, final Class... entityClasses) {
        final StatTimer timer = StatTimer.get("EntityHelper." + timerName);
        timer.start();
        final Minecraft mc = FMLClientHandler.instance().getClient();
        List<EntityDTO> list = new ArrayList<EntityDTO>();
        final List<Entity> allEntities = new ArrayList<Entity>(mc.field_71441_e.field_72996_f);
        final AxisAlignedBB bb = getBB(mc.field_71439_g);
        try {
            for (final Entity entity : allEntities) {
                if (entity instanceof EntityLivingBase && !entity.field_70128_L && entity.field_70175_ag && bb.func_72326_a(entity.func_174813_aQ())) {
                    for (final Class entityClass : entityClasses) {
                        if (entityClass.isAssignableFrom(entity.getClass())) {
                            final EntityLivingBase entityLivingBase = (EntityLivingBase)entity;
                            final EntityDTO dto = DataCache.INSTANCE.getEntityDTO(entityLivingBase);
                            dto.update(entityLivingBase, hostile);
                            list.add(dto);
                            break;
                        }
                    }
                }
            }
            if (list.size() > maxEntities) {
                final int before = list.size();
                EntityHelper.entityDTODistanceComparator.player = (EntityPlayer)mc.field_71439_g;
                Collections.sort(list, EntityHelper.entityDTODistanceComparator);
                list = list.subList(0, maxEntities);
            }
        }
        catch (Throwable t) {
            Journeymap.getLogger().warn("Failed to " + timerName + ": " + LogFormatter.toString(t));
        }
        timer.stop();
        return list;
    }
    
    public static List<EntityDTO> getMobsNearby() {
        return getEntitiesNearby("getMobsNearby", Journeymap.getClient().getCoreProperties().maxMobsData.get(), true, IMob.class);
    }
    
    public static List<EntityDTO> getVillagersNearby() {
        return getEntitiesNearby("getVillagersNearby", Journeymap.getClient().getCoreProperties().maxVillagersData.get(), false, EntityVillager.class, INpc.class);
    }
    
    public static List<EntityDTO> getAnimalsNearby() {
        return getEntitiesNearby("getAnimalsNearby", Journeymap.getClient().getCoreProperties().maxAnimalsData.get(), false, EntityAnimal.class, EntityGolem.class, EntityWaterMob.class);
    }
    
    public static boolean isPassive(final EntityLiving entityLiving) {
        if (entityLiving == null) {
            return false;
        }
        if (entityLiving instanceof IMob) {
            return false;
        }
        final EntityLivingBase attackTarget = entityLiving.func_70638_az();
        return attackTarget == null || (!(attackTarget instanceof EntityPlayer) && !(attackTarget instanceof IEntityOwnable));
    }
    
    public static List<EntityDTO> getPlayersNearby() {
        final StatTimer timer = StatTimer.get("EntityHelper.getPlayersNearby");
        timer.start();
        final Minecraft mc = FMLClientHandler.instance().getClient();
        List<EntityPlayer> allPlayers = new ArrayList<EntityPlayer>(mc.field_71441_e.field_73010_i);
        allPlayers.remove(mc.field_71439_g);
        final int max = Journeymap.getClient().getCoreProperties().maxPlayersData.get();
        if (allPlayers.size() > max) {
            EntityHelper.entityDistanceComparator.player = (EntityPlayer)mc.field_71439_g;
            Collections.sort(allPlayers, (Comparator<? super EntityPlayer>)EntityHelper.entityDistanceComparator);
            allPlayers = allPlayers.subList(0, max);
        }
        final List<EntityDTO> playerDTOs = new ArrayList<EntityDTO>(allPlayers.size());
        for (final EntityPlayer player : allPlayers) {
            final EntityDTO dto = DataCache.INSTANCE.getEntityDTO((EntityLivingBase)player);
            dto.update((EntityLivingBase)player, false);
            playerDTOs.add(dto);
        }
        timer.stop();
        return playerDTOs;
    }
    
    private static AxisAlignedBB getBB(final EntityPlayerSP player) {
        final int lateralDistance = Journeymap.getClient().getCoreProperties().radarLateralDistance.get();
        final int verticalDistance = Journeymap.getClient().getCoreProperties().radarVerticalDistance.get();
        return getBoundingBox((EntityPlayer)player, lateralDistance, verticalDistance);
    }
    
    public static AxisAlignedBB getBoundingBox(final EntityPlayer player, final double lateralDistance, final double verticalDistance) {
        return player.func_174813_aQ().func_72314_b(lateralDistance, verticalDistance, lateralDistance);
    }
    
    public static Map<String, EntityDTO> buildEntityIdMap(final List<? extends EntityDTO> list, final boolean sort) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyMap();
        }
        if (sort) {
            Collections.sort(list, new EntityMapComparator());
        }
        final LinkedHashMap<String, EntityDTO> idMap = new LinkedHashMap<String, EntityDTO>(list.size());
        for (final EntityDTO entityMap : list) {
            idMap.put("id" + entityMap.entityId, entityMap);
        }
        return (Map<String, EntityDTO>)ImmutableSortedMap.copyOf((Map)idMap);
    }
    
    public static ResourceLocation getIconTextureLocation(final Entity entity) {
        try {
            final Render entityRender = FMLClientHandler.instance().getClient().func_175598_ae().func_78713_a(entity);
            ResourceLocation original = null;
            if (entityRender instanceof RenderHorse) {
                final EntityHorse horse = (EntityHorse)entity;
                original = new ResourceLocation("minecraft", horse.func_110212_cp()[0]);
            }
            else {
                original = RenderFacade.getEntityTexture(entityRender, entity);
            }
            if (original == null) {
                JMLogger.logOnce("Can't get entityTexture for " + entity.getClass() + " via " + entityRender.getClass(), null);
                return null;
            }
            if (!original.func_110623_a().contains("/entity/")) {
                return null;
            }
            final ResourceLocation entityIconLoc = new ResourceLocation(original.func_110624_b(), original.func_110623_a().replace("/entity/", "/entity_icon/"));
            return entityIconLoc;
        }
        catch (Throwable t) {
            JMLogger.logOnce("Can't get entityTexture for " + entity.func_70005_c_(), t);
            return null;
        }
    }
    
    static {
        EntityHelper.entityDistanceComparator = new EntityDistanceComparator();
        EntityHelper.entityDTODistanceComparator = new EntityDTODistanceComparator();
        EntityHelper.entityMapComparator = new EntityMapComparator();
        HORSE_TEXTURES = new String[] { "textures/entity/horse/horse_white.png", "textures/entity/horse/horse_creamy.png", "textures/entity/horse/horse_chestnut.png", "textures/entity/horse/horse_brown.png", "textures/entity/horse/horse_black.png", "textures/entity/horse/horse_gray.png", "textures/entity/horse/horse_darkbrown.png" };
    }
    
    private static class EntityMapComparator implements Comparator<EntityDTO>
    {
        @Override
        public int compare(final EntityDTO o1, final EntityDTO o2) {
            Integer o1rank = 0;
            Integer o2rank = 0;
            if (o1.customName != null) {
                ++o1rank;
            }
            else if (o1.username != null) {
                o1rank += 2;
            }
            if (o2.customName != null) {
                ++o2rank;
            }
            else if (o2.username != null) {
                o2rank += 2;
            }
            return o1rank.compareTo(o2rank);
        }
    }
    
    private static class EntityDistanceComparator implements Comparator<Entity>
    {
        EntityPlayer player;
        
        @Override
        public int compare(final Entity o1, final Entity o2) {
            return Double.compare(o1.func_70068_e((Entity)this.player), o2.func_70068_e((Entity)this.player));
        }
    }
    
    private static class EntityDTODistanceComparator implements Comparator<EntityDTO>
    {
        EntityPlayer player;
        
        @Override
        public int compare(final EntityDTO o1, final EntityDTO o2) {
            final EntityLivingBase e1 = o1.entityLivingRef.get();
            final EntityLivingBase e2 = o2.entityLivingRef.get();
            if (e1 == null || e2 == null) {
                return 0;
            }
            return Double.compare(e1.func_70068_e((Entity)this.player), e2.func_70068_e((Entity)this.player));
        }
    }
}
