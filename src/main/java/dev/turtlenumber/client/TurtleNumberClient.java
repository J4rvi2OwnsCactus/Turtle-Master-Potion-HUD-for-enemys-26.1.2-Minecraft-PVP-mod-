package dev.turtlenumber.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.turtlenumber.TurtleEffectPayload;
import dev.turtlenumber.TurtleNumberMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Client: zeichnet die Zahl (weiß, ohne Hintergrund/Rand, mit Tiefentest) über dem Kopf. */
public class TurtleNumberClient implements ClientModInitializer {
	private static final double RANGE = 20.0;
	private static final double RANGE_SQ = RANGE * RANGE;
	/** Abstand über der Oberkante der Hitbox (der Vanilla-Name steht bei ca. +0.5). */
	private static final double HEIGHT_OFFSET = 0.9;
	private static final float TEXT_SCALE = 0.025f;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int WHITE = 0xFFFFFFFF; // #FFFFFF, voll deckend

	/** Entity-ID -> Spielzeit-Tick, an dem die Effekte enden (vom Server gemeldet). */
	private static final Map<Integer, Long> END_TICKS = new HashMap<>();
	/** Hat der Server die Mod? Dann zählen nur seine Daten, sonst Fallback auf lokal bekannte Effekte. */
	private static boolean serverSync = false;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(TurtleEffectPayload.TYPE, (payload, context) -> {
			ClientLevel level = context.client().level;
			if (level == null) {
				return;
			}
			serverSync = true;
			if (payload.ticks() > 0) {
				END_TICKS.put(payload.entityId(), level.getGameTime() + payload.ticks());
			} else {
				END_TICKS.remove(payload.entityId());
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			END_TICKS.clear();
			serverSync = false;
		});

		LevelRenderEvents.COLLECT_SUBMITS.register(TurtleNumberClient::submitNumbers);
	}

	private static void submitNumbers(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		LocalPlayer self = mc.player;
		if (level == null || self == null) {
			return;
		}

		float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		long now = level.getGameTime();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Quaternionf orientation = context.levelState().cameraRenderState.orientation;
		PoseStack pose = context.poseStack();

		for (Player player : level.players()) {
			if (player == self || player.isInvisible() || player.isSpectator()) {
				continue; // nur sichtbare andere Spieler
			}
			if (self.distanceToSqr(player) > RANGE_SQ) {
				continue; // außerhalb von 20 Blöcken
			}
			int ticks = ticksLeft(player, now);
			if (ticks <= 0) {
				END_TICKS.remove(player.getId());
				continue;
			}

			String text = Integer.toString((ticks + 19) / 20); // Sekunden, aufgerundet
			Vec3 pos = player.getPosition(partialTick);

			pose.pushPose();
			pose.translate(
					pos.x - camera.x,
					pos.y + player.getBbHeight() + HEIGHT_OFFSET - camera.y,
					pos.z - camera.z
			);
			pose.mulPose(orientation); // immer zur Kamera gedreht
			pose.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

			float x = -mc.font.width(text) / 2.0f; // mittig
			context.submitNodeCollector().submitText(
					pose, x, -4.0f,
					Component.literal(text).getVisualOrderText(),
					false,                   // kein Schatten
					Font.DisplayMode.NORMAL, // normaler Tiefentest: Blöcke verdecken die Zahl
					FULL_BRIGHT,
					WHITE,
					0,                       // kein Hintergrund
					0                        // keine Umrandung
			);
			pose.popPose();
		}
	}

	private static int ticksLeft(Player player, long now) {
		Long end = END_TICKS.get(player.getId());
		if (end != null) {
			return (int) (end - now);
		}
		return serverSync ? 0 : TurtleNumberMod.remainingTicks(player);
	}
}
