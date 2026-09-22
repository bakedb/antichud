package lol.bkd.antichud.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public class LoadingOverlayLogoMixin {
    @Unique
    private static final int BANNER_WIDTH = 320;
    @Unique
    private static final int BANNER_MARGIN = 8;
    @Unique
    private static final int BANNER_TEXTURE_WIDTH = 716;
    @Unique
    private static final int BANNER_TEXTURE_HEIGHT = 72;
    @Unique
    private static final Identifier VANILLA_LOGO_LOCATION =
            Identifier.fromNamespaceAndPath("minecraft", "textures/gui/title/mojangstudios.png");
    @Unique
    private static boolean vanillaLogoRegistered;
    @Unique
    private static boolean skipVanillaDrawThisFrame;

    @Shadow
    @Final
    @Mutable
    public static Identifier MOJANG_STUDIOS_LOGO_LOCATION;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void antichud$repointLogo(CallbackInfo ci) {
        MOJANG_STUDIOS_LOGO_LOCATION = Identifier.fromNamespaceAndPath("antichud", "textures/gui/logo.png");
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void antichud$ensureVanillaLogoRegistered(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!vanillaLogoRegistered) {
            try {
                Minecraft.getInstance().getTextureManager().registerAndLoad(VANILLA_LOGO_LOCATION, new SimpleTexture(VANILLA_LOGO_LOCATION));
                vanillaLogoRegistered = true;
                skipVanillaDrawThisFrame = true;
            } catch (Exception ignored) {
            }
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private void antichud$drawBannerAndVanillaTopHalf(GuiGraphics graphics, RenderPipeline pipeline, Identifier texture, int x, int y, float u0, float v0, int rectWidth, int rectHeight, int srcWidth, int srcHeight, int textureWidth, int textureHeight, int argb, Operation<Void> original) {
        graphics.blit(pipeline, texture, BANNER_MARGIN, BANNER_MARGIN, 0.0F, 0.0F,
                BANNER_WIDTH, Math.round((float) BANNER_WIDTH * BANNER_TEXTURE_HEIGHT / BANNER_TEXTURE_WIDTH),
                BANNER_TEXTURE_WIDTH, BANNER_TEXTURE_HEIGHT, BANNER_TEXTURE_WIDTH, BANNER_TEXTURE_HEIGHT, 0xFFFFFFFF);
        if (vanillaLogoRegistered && !skipVanillaDrawThisFrame) {
            graphics.blit(pipeline, VANILLA_LOGO_LOCATION, x, y, u0, v0, rectWidth, rectHeight, srcWidth, srcHeight, textureWidth, textureHeight, argb);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private void antichud$drawVanillaBottomHalf(GuiGraphics graphics, RenderPipeline pipeline, Identifier texture, int x, int y, float u0, float v0, int rectWidth, int rectHeight, int srcWidth, int srcHeight, int textureWidth, int textureHeight, int argb, Operation<Void> original) {
        if (vanillaLogoRegistered && !skipVanillaDrawThisFrame) {
            graphics.blit(pipeline, VANILLA_LOGO_LOCATION, x, y, u0, v0, rectWidth, rectHeight, srcWidth, srcHeight, textureWidth, textureHeight, argb);
        }
        skipVanillaDrawThisFrame = false;
    }
}