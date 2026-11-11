package svemocan.vanilla_storage_interface.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StorageSyncPayload(List<VirtualItem> items, int emptyShulkerSlots, net.minecraft.core.BlockPos pos, boolean isVirtual) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StorageSyncPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("vanilla_storage_interface", "storage_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageSyncPayload> CODEC = StreamCodec.composite(
            VirtualItem.CODEC.apply(ByteBufCodecs.list()), StorageSyncPayload::items,
            ByteBufCodecs.INT, StorageSyncPayload::emptyShulkerSlots,
            net.minecraft.core.BlockPos.STREAM_CODEC, StorageSyncPayload::pos,
            net.minecraft.network.codec.ByteBufCodecs.BOOL, StorageSyncPayload::isVirtual,
            StorageSyncPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}