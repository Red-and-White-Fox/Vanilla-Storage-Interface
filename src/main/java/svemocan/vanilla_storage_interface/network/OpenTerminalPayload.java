package svemocan.vanilla_storage_interface.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Modes: 1 = Ender, 2 = Shulker, 3 = Player Inv
public record OpenTerminalPayload(int mode, int targetId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenTerminalPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("vanilla_storage_interface", "open_terminal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTerminalPayload> CODEC = StreamCodec.composite(ByteBufCodecs.INT, OpenTerminalPayload::mode, ByteBufCodecs.INT, OpenTerminalPayload::targetId, OpenTerminalPayload::new);
    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}
