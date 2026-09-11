package dev.fixpot47.combohud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public final class ComboHudClient implements ClientModInitializer {
    public static final String MOD_ID = "combohud";

    private static final long COMBO_TIMEOUT_MS = 2_500L;
    private static final long HIT_CONFIRM_WINDOW_MS = 700L;
    private static final long END_DISPLAY_MS = 900L;

    private static int combo;
    private static int displayedCombo;
    private static UUID currentTargetId;
    private static Player currentTarget;
    private static long lastConfirmedHitAtMs;
    private static long hideAfterMs;

    private static Player pendingTarget;
    private static int pendingTargetHurtTime;
    private static long pendingAttackAtMs;

    private static int previousSelfHurtTime;

    @Override
    public void onInitializeClient() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!level.isClientSide() || !(entity instanceof Player target) || target == player) {
                return InteractionResult.PASS;
            }

            pendingTarget = target;
            pendingTargetHurtTime = target.hurtTime;
            pendingAttackAtMs = System.currentTimeMillis();
            return InteractionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(ComboHudClient::tick);

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath(MOD_ID, "combo_counter"),
                ComboHudClient::renderHud
        );
    }

    private static void tick(Minecraft client) {
        long now = System.currentTimeMillis();

        if (client.player == null || client.level == null) {
            clearAll();
            return;
        }

        detectCounterHit(client.player, now);
        confirmPendingHit(now);

        if (combo > 0) {
            if (currentTarget == null || currentTarget.isRemoved() || !currentTarget.isAlive()) {
                endCombo(now);
            } else if (now - lastConfirmedHitAtMs > COMBO_TIMEOUT_MS) {
                endCombo(now);
            }
        }

        if (combo == 0 && displayedCombo > 0 && now >= hideAfterMs) {
            displayedCombo = 0;
        }

        if (pendingTarget != null && now - pendingAttackAtMs > HIT_CONFIRM_WINDOW_MS) {
            clearPendingAttack();
        }

        previousSelfHurtTime = client.player.hurtTime;
    }

    private static void detectCounterHit(Player self, long now) {
        int hurtTime = self.hurtTime;
        if (hurtTime <= previousSelfHurtTime || combo <= 0 || currentTargetId == null) {
            return;
        }

        DamageSource source = self.getLastDamageSource();
        if (source == null) {
            return;
        }

        Entity attacker = source.getEntity();
        if (attacker instanceof Player player && player.getUUID().equals(currentTargetId)) {
            endCombo(now);
        }
    }

    private static void confirmPendingHit(long now) {
        if (pendingTarget == null || now - pendingAttackAtMs > HIT_CONFIRM_WINDOW_MS) {
            return;
        }

        if (pendingTarget.isRemoved() || !pendingTarget.isAlive()) {
            clearPendingAttack();
            return;
        }

        if (pendingTarget.hurtTime <= pendingTargetHurtTime) {
            return;
        }

        UUID targetId = pendingTarget.getUUID();
        if (currentTargetId != null
                && currentTargetId.equals(targetId)
                && now - lastConfirmedHitAtMs <= COMBO_TIMEOUT_MS) {
            combo++;
        } else {
            combo = 1;
        }

        currentTargetId = targetId;
        currentTarget = pendingTarget;
        lastConfirmedHitAtMs = now;
        displayedCombo = combo;
        hideAfterMs = Long.MAX_VALUE;
        clearPendingAttack();
    }

    private static void endCombo(long now) {
        if (combo > 0) {
            displayedCombo = combo;
            hideAfterMs = now + END_DISPLAY_MS;
        }

        combo = 0;
        currentTargetId = null;
        currentTarget = null;
        lastConfirmedHitAtMs = 0L;
        clearPendingAttack();
    }

    private static void clearPendingAttack() {
        pendingTarget = null;
        pendingTargetHurtTime = 0;
        pendingAttackAtMs = 0L;
    }

    private static void clearAll() {
        combo = 0;
        displayedCombo = 0;
        currentTargetId = null;
        currentTarget = null;
        lastConfirmedHitAtMs = 0L;
        hideAfterMs = 0L;
        previousSelfHurtTime = 0;
        clearPendingAttack();
    }

    private static void renderHud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (displayedCombo <= 0) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        String text = "Combo: " + displayedCombo;
        int hotbarLeft = graphics.guiWidth() / 2 - 91;
        int x = hotbarLeft - client.font.width(text) - 6;
        int y = graphics.guiHeight() - 15;

        graphics.text(client.font, text, x, y, 0xFFFFFFFF, true);
    }
}
