package lol.bkd.antichud.mixin.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.gui.screens.LoadingOverlay$LogoTexture")
public class LoadingOverlayLogoTextureMixin {
    @Unique
    private static final byte[] TRANSPARENT_PNG = {
            (byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47, (byte) 0x0d, (byte) 0x0a, (byte) 0x1a, (byte) 0x0a, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0d,
            (byte) 0x49, (byte) 0x48, (byte) 0x44, (byte) 0x52, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
            (byte) 0x08, (byte) 0x06, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x1f, (byte) 0x15, (byte) 0xc4, (byte) 0x89, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x0d, (byte) 0x49, (byte) 0x44, (byte) 0x41, (byte) 0x54, (byte) 0x78, (byte) 0x9c, (byte) 0x63, (byte) 0x60, (byte) 0x60, (byte) 0x60, (byte) 0x60,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x05, (byte) 0x00, (byte) 0x01, (byte) 0xa5, (byte) 0xf6, (byte) 0x45, (byte) 0x40, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x49, (byte) 0x45, (byte) 0x4e, (byte) 0x44, (byte) 0xae, (byte) 0x42, (byte) 0x60, (byte) 0x82,
    };

    @Redirect(method = "loadContents",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/ResourceProvider;open(Lnet/minecraft/resources/Identifier;)Ljava/io/InputStream;"))
    private static InputStream antichud$openLogoFromGameResources(ResourceProvider vanillaProvider, Identifier location) throws IOException {
        InputStream classpath = LoadingOverlayLogoTextureMixin.class.getResourceAsStream("/assets/antichud/textures/gui/logo.png");
        if (classpath != null) {
            return classpath;
        }
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        try {
            return manager.open(location);
        } catch (IOException notReadyYet) {
            return new ByteArrayInputStream(TRANSPARENT_PNG);
        }
    }
}