package svemocan.vanilla_storage_interface.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

// Modes: 1 = Ender, 2 = Shulker
public record OpenRawUIPayload(int mode, int targetId) implements CustomPayload {
    public static final CustomPayload.Id<OpenRawUIPayload> ID = new CustomPayload.Id<>(Identifier.of("vanilla_storage_interface", "open_raw_ui"));
    public static final PacketCodec<RegistryByteBuf, OpenRawUIPayload> CODEC = PacketCodec.tuple(PacketCodecs.INTEGER, OpenRawUIPayload::mode, PacketCodecs.INTEGER, OpenRawUIPayload::targetId, OpenRawUIPayload::new);
    @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}