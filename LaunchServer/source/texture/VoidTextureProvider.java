package launchserver.texture;

import java.util.UUID;

import launcher.client.PlayerProfile;
import launcher.client.PlayerProfile.Texture;
import launcher.serialize.config.entry.BlockConfigEntry;

public final class VoidTextureProvider extends TextureProvider {
    public VoidTextureProvider(BlockConfigEntry block) {
        super(block);
    }

    @Override
    public void close() {
        // Do nothing
    }

    @Override
    public Texture getCloakTexture(UUID uuid, String username) {
        return null; // Always nothing
    }

    @Override
    public Texture getSkinTexture(UUID uuid, String username) {
        return null; // Always nothing
    }
}
