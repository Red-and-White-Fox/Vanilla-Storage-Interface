package svemocan.vanilla_storage_interface.network;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

public record VirtualItem(ItemStack stack, int count) {
    public static final PacketCodec<RegistryByteBuf, VirtualItem> CODEC = PacketCodec.tuple(
            ItemStack.PACKET_CODEC, VirtualItem::stack,
            PacketCodecs.INTEGER, VirtualItem::count,
            VirtualItem::new
    );
}