package dev.turtlenumber;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> Client: Verbleibende Ticks der Turtle-Master-Effekte eines Spielers.
 * {@code ticks <= 0} bedeutet: Anzeige entfernen.
 */
public record TurtleEffectPayload(int entityId, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TurtleEffectPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TurtleNumberMod.MOD_ID, "turtle_effect"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TurtleEffectPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TurtleEffectPayload::entityId,
			ByteBufCodecs.VAR_INT, TurtleEffectPayload::ticks,
			TurtleEffectPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
