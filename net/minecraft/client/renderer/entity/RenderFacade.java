package net.minecraft.client.renderer.entity;

import net.minecraft.entity.*;
import net.minecraft.util.*;

public class RenderFacade extends Render
{
    public RenderFacade(final RenderManager unused) {
        super(unused);
    }
    
    public static ResourceLocation getEntityTexture(final Render render, final Entity entity) {
        return render.func_110775_a(entity);
    }
    
    protected ResourceLocation func_110775_a(final Entity entity) {
        return null;
    }
    
    public void func_76986_a(final Entity entity, final double var2, final double var4, final double var6, final float var8, final float var9) {
    }
}
