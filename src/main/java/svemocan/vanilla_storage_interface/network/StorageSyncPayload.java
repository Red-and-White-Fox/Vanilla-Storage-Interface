package svemocan.vanilla_storage_interface.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.List;

public record StorageSyncPayload(List<VirtualItem> items, int emptyShulkerSlots, net.minecraft.util.math.BlockPos pos, boolean isVirtual) implements CustomPayload {

    public static final CustomPayload.Id<StorageSyncPayload> ID = new CustomPayload.Id<>(Identifier.of("vanilla_storage_interface", "storage_sync"));

    public static final PacketCodec<RegistryByteBuf, StorageSyncPayload> CODEC = PacketCodec.tuple(
            VirtualItem.CODEC.collect(PacketCodecs.toList()), StorageSyncPayload::items,
            PacketCodecs.INTEGER, StorageSyncPayload::emptyShulkerSlots,
            net.minecraft.util.math.BlockPos.PACKET_CODEC, StorageSyncPayload::pos,
            net.minecraft.network.codec.PacketCodecs.BOOL, StorageSyncPayload::isVirtual,
            StorageSyncPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}