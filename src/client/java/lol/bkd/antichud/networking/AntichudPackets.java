package lol.bkd.antichud.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class AntichudPackets {
    public static final Identifier MOD_ANNOUNCE_ID = Identifier.fromNamespaceAndPath("antichud", "mod_announce");

    public record ModAnnouncePayload() implements CustomPacketPayload {
        public static final Type<ModAnnouncePayload> TYPE = new Type<>(MOD_ANNOUNCE_ID);
        public static final StreamCodec<FriendlyByteBuf, ModAnnouncePayload> CODEC = StreamCodec.unit(new ModAnnouncePayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ModAnnounceS2CPayload(String playerName, String playerUuid) implements CustomPacketPayload {
        public static final Type<ModAnnounceS2CPayload> TYPE = new Type<>(MOD_ANNOUNCE_ID);
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ModAnnounceS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ModAnnounceS2CPayload::playerName,
            ByteBufCodecs.STRING_UTF8, ModAnnounceS2CPayload::playerUuid,
            ModAnnounceS2CPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playC2S().register(ModAnnouncePayload.TYPE, ModAnnouncePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ModAnnounceS2CPayload.TYPE, ModAnnounceS2CPayload.CODEC);
    }
}