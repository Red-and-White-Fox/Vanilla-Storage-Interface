package svemocan.vanilla_storage_interface.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record VirtualItem(ItemStack stack, int count) {
    public static final StreamCodec<RegistryFriendlyByteBuf, VirtualItem> CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, VirtualItem::stack,
            ByteBufCodecs.INT, VirtualItem::count,
            VirtualItem::new
    );
}