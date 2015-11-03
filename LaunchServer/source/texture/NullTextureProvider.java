package launchserver.texture;

import java.io.IOException;
import java.util.UUID;

import launcher.LauncherAPI;
import launcher.client.PlayerProfile;
import launcher.helper.VerifyHelper;
import launcher.serialize.config.entry.BlockConfigEntry;

public final class NullTextureProvider extends TextureProvider {
	private volatile TextureProvider provider;

	public NullTextureProvider(BlockConfigEntry block) {
		super(block);
	}

	@Override
	public void flush() throws IOException {
		TextureProvider provider = this.provider;
		if (provider != null) {
			provider.flush();
		}
	}

	@Override
	public PlayerProfile.Texture getCloakTexture(UUID uuid, String username) throws IOException {
		return getProvider().getCloakTexture(uuid, username);
	}

	@Override
	public PlayerProfile.Texture getSkinTexture(UUID uuid, String username) throws IOException {
		return getProvider().getSkinTexture(uuid, username);
	}

	@LauncherAPI
	public void setBackend(TextureProvider provider) {
		this.provider = provider;
	}

	private TextureProvider getProvider() {
		return VerifyHelper.verify(provider, a -> a != null, "Backend texture provider wasn't set");
	}
}
