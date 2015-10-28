package com.mojang.authlib.yggdrasil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.AuthenticationService;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.BaseMinecraftSessionService;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import launcher.client.ClientLauncher;
import launcher.client.PlayerProfile;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.request.auth.CheckServerRequest;
import launcher.request.auth.JoinServerRequest;
import launcher.request.uuid.ProfileByUUIDRequest;

public final class YggdrasilMinecraftSessionService extends BaseMinecraftSessionService {
	private static final boolean DISABLE_TEXTURES = Boolean.getBoolean("launcher.authlib.disableTextures");

	// Constants
	private static final String SKIN_HASH_PROPERTY = "l_skinHash";
	private static final String CLOAK_HASH_PROPERTY = "l_cloakHash";

	public YggdrasilMinecraftSessionService(AuthenticationService service) {
		super(service);
		LogHelper.debug("Patched MinecraftSessionService created");
	}

	@Override
	public GameProfile fillProfileProperties(GameProfile profile, boolean requireSecure) {
		// Verify has UUID
		UUID uuid = profile.getUUID();
		LogHelper.debug("fillProfileProperties, UUID: %s", uuid);
		if (uuid == null) {
			return profile;
		}

		// Make profile request
		PlayerProfile pp;
		try {
			pp = new ProfileByUUIDRequest(uuid).request();
		} catch (Exception e) {
			LogHelper.debug("Couldn't fetch profile properties for '%s': %s", profile, e);
			return profile;
		}

		// Verify is found
		if (pp == null) {
			LogHelper.debug("Couldn't fetch profile properties for '%s' as the profile does not exist", profile);
			return profile;
		}

		// Create new game profile from player profile
		LogHelper.debug("Successfully fetched profile properties for '%s'", profile);
		fillTextureProperties(profile, pp);
		return toGameProfile(pp);
	}

	@Override
	public Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> getTextures(GameProfile profile, boolean requireSecure) {
		LogHelper.debug("getTextures, Username: '%s'", profile.getName());
		Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures =
			new EnumMap<>(MinecraftProfileTexture.Type.class);

		// Add skin URL to textures map
		Iterator<Property> skinURL = profile.getProperties().get(ClientLauncher.SKIN_URL_PROPERTY).iterator();
		Iterator<Property> skinHash = profile.getProperties().get(SKIN_HASH_PROPERTY).iterator();
		if (skinURL.hasNext() && skinHash.hasNext()) {
			String urlValue = skinURL.next().getValue();
			String hashValue = skinHash.next().getValue();
			textures.put(MinecraftProfileTexture.Type.SKIN, new MinecraftProfileTexture(urlValue, hashValue));
		}

		// Add cloak URL to textures map
		Iterator<Property> cloakURL = profile.getProperties().get(ClientLauncher.CLOAK_URL_PROPERTY).iterator();
		Iterator<Property> cloakHash = profile.getProperties().get(CLOAK_HASH_PROPERTY).iterator();
		if (cloakURL.hasNext() && cloakHash.hasNext()) {
			String urlValue = cloakURL.next().getValue();
			String hashValue = cloakHash.next().getValue();
			textures.put(MinecraftProfileTexture.Type.CAPE, new MinecraftProfileTexture(urlValue, hashValue));
		}

		// Return filled textures
		return textures;
	}

	@Override
	public GameProfile hasJoinedServer(GameProfile profile, String serverID) throws AuthenticationUnavailableException {
		String username = profile.getName();
		LogHelper.debug("checkServer, Username: '%s', Server ID: %s", username, serverID);

		// Make checkServer request
		PlayerProfile pp;
		try {
			pp = new CheckServerRequest(username, serverID).request();
		} catch (Exception e) {
			LogHelper.error(e);
			throw new AuthenticationUnavailableException(e);
		}

		// Return profile if found
		return pp == null ? null : toGameProfile(pp);
	}

	@Override
	public void joinServer(GameProfile profile, String accessToken, String serverID) throws AuthenticationException {
		if (!ClientLauncher.isLaunched()) {
			throw new AuthenticationException("Bad Login (Cheater)");
		}

		// Join server
		String username = profile.getName();
		LogHelper.debug("joinServer, Username: '%s', Access token: %s, Server ID: %s",
				username, accessToken, serverID);

		// Make joinServer request
		boolean success;
		try {
			success = new JoinServerRequest(username, accessToken, serverID).request();
		} catch (Exception e) {
			throw new AuthenticationUnavailableException(e);
		}

		// Verify is success
		if (!success) {
			throw new AuthenticationException("Bad Login (Clientside)");
		}
	}

	public static void fillTextureProperties(GameProfile profile, PlayerProfile pp) {
		if (DISABLE_TEXTURES) {
			return; // :(
		}

		// Get property map
		PropertyMap properties = profile.getProperties();

		// Hash skin
		String skinHash = hash(pp.skinURL);
		if (skinHash != null) {
			properties.put(ClientLauncher.SKIN_URL_PROPERTY, new Property(ClientLauncher.SKIN_URL_PROPERTY, pp.skinURL, ""));
			properties.put(SKIN_HASH_PROPERTY, new Property(SKIN_HASH_PROPERTY, skinHash, ""));
		}

		// Hash cloak
		String cloakHash = hash(pp.cloakURL);
		if (cloakHash != null) {
			properties.put(ClientLauncher.CLOAK_URL_PROPERTY, new Property(ClientLauncher.CLOAK_URL_PROPERTY, pp.cloakURL, ""));
			properties.put(CLOAK_HASH_PROPERTY, new Property(CLOAK_HASH_PROPERTY, cloakHash, ""));
		}
	}

	public static GameProfile toGameProfile(PlayerProfile pp) {
		GameProfile profile = new GameProfile(pp.uuid, pp.username);
		fillTextureProperties(profile, pp);
		return profile;
	}

	private static String hash(String url) {
		if (url == null) {
			return null;
		}

		// Try get digest of the texture
		try {
			return SecurityHelper.toHex(SecurityHelper.digest(SecurityHelper.DigestAlgorithm.SHA256, new URL(url)));
		} catch (FileNotFoundException e) {
			return null; // Just didn't has texture
		} catch (IOException e) {
			LogHelper.error("Can't hash texture: %s, %s", url, e.toString());
			return null;
		}
	}
}
