package svemocan.vanilla_storage_interface.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record StorageActionPayload(String action, ItemStack stack, int amount) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StorageActionPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("vanilla_storage_interface", "storage_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, StorageActionPayload::action,
            ItemStack.OPTIONAL_STREAM_CODEC, StorageActionPayload::stack,
            ByteBufCodecs.INT, StorageActionPayload::amount,
            StorageActionPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}
