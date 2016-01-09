package net.sashok724.launcher.server.texture;

import java.util.UUID;

import net.sashok724.launcher.client.client.PlayerProfile;
import net.sashok724.launcher.client.serialize.config.entry.BlockConfigEntry;

public final class VoidTextureProvider extends TextureProvider {
	public VoidTextureProvider(BlockConfigEntry block) {
		super(block);
	}

	@Override
	public void close() {
		// Do nothing
	}

	@Override
	public PlayerProfile.Texture getCloakTexture(UUID uuid, String username) {
		return null; // Always nothing
	}

	@Override
	public PlayerProfile.Texture getSkinTexture(UUID uuid, String username) {
		return null; // Always nothing
	}
}
