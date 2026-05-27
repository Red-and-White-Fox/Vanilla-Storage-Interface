package svemocan.vanilla_storage_interface.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

// Modes: 1 = Ender, 2 = Shulker, 3 = Player Inv
public record OpenTerminalPayload(int mode, int targetId) implements CustomPayload {
    public static final CustomPayload.Id<OpenTerminalPayload> ID = new CustomPayload.Id<>(Identifier.of("vanilla_storage_interface", "open_terminal"));
    public static final PacketCodec<RegistryByteBuf, OpenTerminalPayload> CODEC = PacketCodec.tuple(PacketCodecs.INTEGER, OpenTerminalPayload::mode, PacketCodecs.INTEGER, OpenTerminalPayload::targetId, OpenTerminalPayload::new);
    @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}