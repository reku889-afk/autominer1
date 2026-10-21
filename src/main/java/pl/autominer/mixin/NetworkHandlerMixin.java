package pl.autominer.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.autominer.AutoMinerClient;

@Mixin(ClientPlayNetworkHandler.class)
public class NetworkHandlerMixin {

    @Inject(method = "onTitle", at = @At("HEAD"))
    private void autominer$title(TitleS2CPacket packet, CallbackInfo ci) {
        AutoMinerClient.checkCaptcha(packet.text().getString());
    }

    @Inject(method = "onSubtitle", at = @At("HEAD"))
    private void autominer$subtitle(SubtitleS2CPacket packet, CallbackInfo ci) {
        AutoMinerClient.checkCaptcha(packet.text().getString());
    }

    @Inject(method = "onOverlayMessage", at = @At("HEAD"))
    private void autominer$overlay(OverlayMessageS2CPacket packet, CallbackInfo ci) {
        AutoMinerClient.checkCaptcha(packet.text().getString());
    }
}
