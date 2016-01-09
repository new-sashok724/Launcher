package net.sashok724.launcher.server.texture;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.UUID;

import net.sashok724.launcher.client.client.ClientLauncher;
import net.sashok724.launcher.client.client.PlayerProfile;
import net.sashok724.launcher.client.helper.CommonHelper;
import net.sashok724.launcher.client.helper.IOHelper;
import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.client.serialize.config.entry.BlockConfigEntry;
import net.sashok724.launcher.client.serialize.config.entry.StringConfigEntry;

public final class RequestTextureProvider extends TextureProvider {
	private static final UUID ZERO_UUID = new UUID(0, 0);

	// Instance
	private final String skinURL;
	private final String cloakURL;

	public RequestTextureProvider(BlockConfigEntry block) {
		super(block);
		skinURL = block.getEntryValue("skinsURL", StringConfigEntry.class);
		cloakURL = block.getEntryValue("cloaksURL", StringConfigEntry.class);

		// Verify
		IOHelper.verifyURL(getTextureURL(skinURL, ZERO_UUID, "skinUsername"));
		IOHelper.verifyURL(getTextureURL(cloakURL, ZERO_UUID, "cloakUsername"));
	}

	@Override
	public void close() {
		// Do nothing
	}

	@Override
	public PlayerProfile.Texture getCloakTexture(UUID uuid, String username) throws IOException {
		return getTexture(getTextureURL(cloakURL, uuid, username));
	}

	@Override
	public PlayerProfile.Texture getSkinTexture(UUID uuid, String username) throws IOException {
		return getTexture(getTextureURL(skinURL, uuid, username));
	}

	private static PlayerProfile.Texture getTexture(String url) throws IOException {
		LogHelper.debug("Getting texture: '%s'", url);
		try {
			return new PlayerProfile.Texture(url);
		} catch (FileNotFoundException e) {
			return null; // Simply not found
		}
	}

	private static String getTextureURL(String url, UUID uuid, String username) {
		return CommonHelper.replace(url, "username", IOHelper.urlEncode(username),
			"uuid", IOHelper.urlEncode(uuid.toString()), "hash", IOHelper.urlEncode(ClientLauncher.toHash(uuid)));
	}
}
