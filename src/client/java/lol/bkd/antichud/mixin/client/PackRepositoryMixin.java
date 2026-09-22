package lol.bkd.antichud.mixin.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.CompositePackResources;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.impl.resource.pack.ModNioPackResources;
import net.fabricmc.fabric.impl.resource.pack.BuiltinModResourcePackSource;
import net.fabricmc.fabric.impl.resource.ResourceLoaderImpl;

@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    @Inject(method = "rebuildSelected", at = @At("RETURN"), cancellable = true)
    private void antichud$injectDefaultTexturesPack(Collection<String> enabledNames, CallbackInfoReturnable<List<Pack>> cir) {
        List<Pack> enabledProfiles = new ArrayList<>(cir.getReturnValue());
        
        FabricLoader.getInstance().getModContainer("antichud").ifPresent(container -> {
            ModNioPackResources resourcePack = ModNioPackResources.create(
                "antichud:default_textures",
                container,
                "resourcepacks/antichud_default_textures",
                PackType.CLIENT_RESOURCES,
                PackActivationType.ALWAYS_ENABLED,
                false
            );
            
            if (resourcePack != null && !resourcePack.getNamespaces(PackType.CLIENT_RESOURCES).isEmpty()) {
                PackLocationInfo info = new PackLocationInfo(
                    resourcePack.packId(),
                    Component.literal("Antichud Default Textures"),
                    new BuiltinModResourcePackSource(container.getMetadata().getName()),
                    resourcePack.knownPackInfo()
                );
                
                PackSelectionConfig selectionInfo = new PackSelectionConfig(
                    true,
                    Pack.Position.TOP,
                    false
                );
                
                Pack profile = Pack.readMetaAndCreate(info, new Pack.ResourcesSupplier() {
                    @Override
                    public PackResources openPrimary(PackLocationInfo location) {
                        return resourcePack;
                    }
                    
                    @Override
                    public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                        if (metadata.overlays().isEmpty()) {
                            return resourcePack;
                        }
                        java.util.List<PackResources> overlays = new java.util.ArrayList<>(metadata.overlays().size());
                        for (String overlay : metadata.overlays()) {
                            overlays.add(resourcePack.createOverlay(overlay));
                        }
                        return new CompositePackResources(resourcePack, overlays);
                    }
                }, PackType.CLIENT_RESOURCES, selectionInfo);
                
                if (profile != null) {
                    enabledProfiles.add(profile);
                    cir.setReturnValue(enabledProfiles);
                }
            }
        });
    }
}