package dev.turtlenumber;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Gemeinsamer Teil (Server): erkennt Resistance + Slowness und meldet es den Spielern in Reichweite.
 * Grund: Effekte anderer Spieler sind auf dem Client nicht zuverlässig bekannt.
 */
public class TurtleNumberMod implements ModInitializer {
	public static final String MOD_ID = "turtlenumber";

	/**
	 * true: nur die Stärken des Turtle-Master-Tranks zählen (Resistance >= III, Slowness >= IV).
	 * false: jede Kombination aus Resistance und Slowness löst die Anzeige aus.
	 */
	public static final boolean REQUIRE_TURTLE_MASTER_LEVELS = true;

	/** Spieler -> Spielzeit-Tick, an dem beide Effekte enden (zuletzt gesendeter Stand). */
	private static final Map<UUID, Long> ACTIVE = new HashMap<>();

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(TurtleEffectPayload.TYPE, TurtleEffectPayload.CODEC);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				update(player);
			}
		});

		// Spieler kommt in Sichtweite: aktuellen Stand nachliefern
		EntityTrackingEvents.START_TRACKING.register((trackedEntity, viewer) -> {
			if (trackedEntity instanceof ServerPlayer target) {
				Long end = ACTIVE.get(target.getUUID());
				if (end != null) {
					int ticks = (int) (end - target.level().getGameTime());
					if (ticks > 0) {
						ServerPlayNetworking.send(viewer, new TurtleEffectPayload(target.getId(), ticks));
					}
				}
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ACTIVE.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
	}

	private static void update(ServerPlayer player) {
		int ticks = remainingTicks(player);
		Long last = ACTIVE.get(player.getUUID());

		if (ticks > 0) {
			long end = player.level().getGameTime() + ticks;
			// Nur senden, wenn neu aktiviert oder die Dauer sich geändert hat (z. B. neuer Trank)
			if (last == null || Math.abs(end - last) > 1) {
				ACTIVE.put(player.getUUID(), end);
				broadcast(player, ticks);
			}
		} else if (last != null) {
			ACTIVE.remove(player.getUUID());
			broadcast(player, 0); // Effekt weg -> Zahl sofort entfernen
		}
	}

	private static void broadcast(ServerPlayer player, int ticks) {
		TurtleEffectPayload payload = new TurtleEffectPayload(player.getId(), ticks);
		for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}

	/** Verbleibende Ticks, solange BEIDE Effekte aktiv sind, sonst 0. Wird auch vom Client als Fallback genutzt. */
	public static int remainingTicks(LivingEntity entity) {
		MobEffectInstance resistance = entity.getEffect(MobEffects.RESISTANCE);
		MobEffectInstance slowness = entity.getEffect(MobEffects.SLOWNESS);
		if (resistance == null || slowness == null) {
			return 0;
		}
		if (REQUIRE_TURTLE_MASTER_LEVELS && (resistance.getAmplifier() < 2 || slowness.getAmplifier() < 3)) {
			return 0;
		}
		return Math.max(0, Math.min(duration(resistance), duration(slowness)));
	}

	private static int duration(MobEffectInstance effect) {
		return effect.isInfiniteDuration() ? 72000 : effect.getDuration();
	}
}
