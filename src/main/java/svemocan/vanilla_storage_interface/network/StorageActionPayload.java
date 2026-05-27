package svemocan.vanilla_storage_interface.network;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record StorageActionPayload(String action, ItemStack stack, int amount) implements CustomPayload {
    public static final CustomPayload.Id<StorageActionPayload> ID = new CustomPayload.Id<>(Identifier.of("vanilla_storage_interface", "storage_action"));

    public static final PacketCodec<RegistryByteBuf, StorageActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, StorageActionPayload::action,
            ItemStack.OPTIONAL_PACKET_CODEC, StorageActionPayload::stack,
            PacketCodecs.INTEGER, StorageActionPayload::amount,
            StorageActionPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}