package svemocan.vanilla_storage_interface.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Modes: 1 = Ender, 2 = Shulker
public record OpenRawUIPayload(int mode, int targetId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenRawUIPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("vanilla_storage_interface", "open_raw_ui"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRawUIPayload> CODEC = StreamCodec.composite(ByteBufCodecs.INT, OpenRawUIPayload::mode, ByteBufCodecs.INT, OpenRawUIPayload::targetId, OpenRawUIPayload::new);
    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}