package pl.autominer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoMinerClient implements ClientModInitializer {

    private static final double REACH = 4.5;
    private static final Pattern CAPTCHA = Pattern.compile("/kopanie\\s+(\\S+)");
    private static final Random RANDOM = new Random();

    private static BlockPos point1, point2;
    private static boolean active = false;
    private static BlockPos currentTarget = null;

    // captcha
    private static String pendingCode = null;
    private static int captchaDelay = -1;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("point1").executes(ctx -> {
                point1 = pickPos();
                msg("Point 1: " + (point1 == null ? "brak" : point1.toShortString()));
                return 1;
            }));
            dispatcher.register(ClientCommandManager.literal("point2").executes(ctx -> {
                point2 = pickPos();
                msg("Point 2: " + (point2 == null ? "brak" : point2.toShortString()));
                return 1;
            }));
            dispatcher.register(ClientCommandManager.literal("startkopanie").executes(ctx -> {
                if (point1 == null || point2 == null) {
                    msg("Najpierw ustaw /point1 i /point2!");
                    return 0;
                }
                active = true;
                currentTarget = null;
                msg("Start kopania.");
                return 1;
            }));
            dispatcher.register(ClientCommandManager.literal("stopkopanie").executes(ctx -> {
                stop();
                msg("Stop kopania.");
                return 1;
            }));
        });

        // captcha z czatu
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> checkCaptcha(message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) ->
                checkCaptcha(message.getString()));

        ClientTickEvents.END_CLIENT_TICK.register(AutoMinerClient::tick);
    }

    /** Wywolywane z czatu oraz z mixina (title/subtitle/actionbar). */
    public static void checkCaptcha(String text) {
        if (!active || text == null) return;
        Matcher m = CAPTCHA.matcher(text);
        if (m.find()) {
            pendingCode = m.group(1);
            captchaDelay = 20 + RANDOM.nextInt(30); // 1-2.5 sek. opoznienia
        }
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) return;
        if (!active) return;

        // obsluga captchy - kopanie wstrzymane
        if (pendingCode != null) {
            client.interactionManager.cancelBlockBreaking();
            if (--captchaDelay <= 0) {
                player.networkHandler.sendChatCommand("kopanie " + pendingCode);
                msg("Wpisano captche: " + pendingCode);
                pendingCode = null;
                captchaDelay = -1;
            }
            return;
        }

        if (client.currentScreen != null) return;

        Vec3d eye = player.getEyePos();

        // sprawdz czy aktualny cel nadal jest dobry
        if (currentTarget != null) {
            if (!inBox(currentTarget) || !mineable(client, currentTarget) || !inReach(eye, currentTarget)) {
                currentTarget = null;
            }
        }
        if (currentTarget == null) currentTarget = findTarget(client, eye);
        if (currentTarget == null) return;

        Vec3d center = Vec3d.ofCenter(currentTarget);
        lookAt(player, eye, center);

        Direction dir = Direction.getFacing(eye.x - center.x, eye.y - center.y, eye.z - center.z);
        client.interactionManager.updateBlockBreakingProgress(currentTarget, dir);
        player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
    }

    private static BlockPos findTarget(MinecraftClient client, Vec3d eye) {
        BlockPos min = min(), max = max();
        int r = (int) Math.ceil(REACH);
        BlockPos base = BlockPos.ofFloored(eye);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = Math.max(min.getX(), base.getX() - r); x <= Math.min(max.getX(), base.getX() + r); x++)
            for (int y = Math.max(min.getY(), base.getY() - r); y <= Math.min(max.getY(), base.getY() + r); y++)
                for (int z = Math.max(min.getZ(), base.getZ() - r); z <= Math.min(max.getZ(), base.getZ() + r); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!mineable(client, pos) || !inReach(eye, pos)) continue;
                    double d = Vec3d.ofCenter(pos).squaredDistanceTo(eye);
                    if (d < bestDist) { bestDist = d; best = pos; }
                }
        return best;
    }

    private static boolean mineable(MinecraftClient client, BlockPos pos) {
        var state = client.world.getBlockState(pos);
        return !state.isAir() && state.getHardness(client.world, pos) >= 0
                && state.getFluidState().isEmpty();
    }

    private static boolean inReach(Vec3d eye, BlockPos pos) {
        return Vec3d.ofCenter(pos).squaredDistanceTo(eye) <= REACH * REACH;
    }

    private static boolean inBox(BlockPos p) {
        BlockPos a = min(), b = max();
        return p.getX() >= a.getX() && p.getX() <= b.getX()
            && p.getY() >= a.getY() && p.getY() <= b.getY()
            && p.getZ() >= a.getZ() && p.getZ() <= b.getZ();
    }

    private static BlockPos min() {
        return new BlockPos(Math.min(point1.getX(), point2.getX()),
                Math.min(point1.getY(), point2.getY()), Math.min(point1.getZ(), point2.getZ()));
    }

    private static BlockPos max() {
        return new BlockPos(Math.max(point1.getX(), point2.getX()),
                Math.max(point1.getY(), point2.getY()), Math.max(point1.getZ(), point2.getZ()));
    }

    private static void lookAt(ClientPlayerEntity p, Vec3d eye, Vec3d target) {
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        p.setPitch((float) -Math.toDegrees(Math.atan2(dy, horiz)));
    }

    private static BlockPos pickPos() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null) return null;
        if (c.crosshairTarget != null && c.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            return ((BlockHitResult) c.crosshairTarget).getBlockPos();
        }
        return c.player.getBlockPos().down();
    }

    private static void stop() {
        active = false;
        currentTarget = null;
        pendingCode = null;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.interactionManager != null) c.interactionManager.cancelBlockBreaking();
    }

    private static void msg(String s) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player != null) c.player.sendMessage(Text.literal("[AutoMiner] " + s), false);
    }
}
