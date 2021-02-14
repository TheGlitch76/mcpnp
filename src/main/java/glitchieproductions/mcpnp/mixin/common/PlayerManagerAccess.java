package glitchieproductions.mcpnp.mixin.common;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.server.PlayerManager;
import net.minecraft.world.GameMode;

@Mixin(PlayerManager.class)
public interface PlayerManagerAccess {
	@Accessor("gameMode")
	GameMode mcpnp$getGameMode();
}
