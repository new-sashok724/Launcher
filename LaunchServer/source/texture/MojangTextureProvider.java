package launchserver.texture;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import launcher.LauncherAPI;
import launcher.client.PlayerProfile.Texture;
import launcher.helper.IOHelper;
import launcher.helper.JVMHelper;
import launcher.helper.LogHelper;
import launcher.helper.VerifyHelper;
import launcher.serialize.config.entry.BlockConfigEntry;
import launchserver.auth.provider.MojangAuthProvider;

public final class MojangTextureProvider extends TextureProvider {
    @LauncherAPI
    public static final long CACHE_DURATION_MS = VerifyHelper.verifyLong(
        Long.parseLong(System.getProperty("launcher.mysql.cacheDurationHours", Integer.toString(24))),
        VerifyHelper.L_NOT_NEGATIVE, "launcher.mysql.cacheDurationHours can't be < 0") * 60L * 60L * 1000L;

    // Instance
    private final Map<String, CacheData> cache = new HashMap<>(1024);

    public MojangTextureProvider(BlockConfigEntry block) {
        super(block);
    }

    @Override
    public void close() {
        // Do nothing
    }

    @Override
    public synchronized Texture getCloakTexture(UUID uuid, String username) {
        return getCached(uuid, username).cloak;
    }

    @Override
    public synchronized Texture getSkinTexture(UUID uuid, String username) {
        return getCached(uuid, username).skin;
    }

    private CacheData getCached(UUID uuid, String username) {
        CacheData result = cache.get(username);

        // Have cached result?
        if (result != null && System.currentTimeMillis() < result.until) {
            if (result.exc != null) {
                JVMHelper.UNSAFE.throwException(result.exc);
            }
            return result;
        }

        try {
            // TODO Don't query UUID by username if using mojang auth handler (not implemented yet)
            URL uuidURL = new URL("https://api.mojang.com/users/profiles/minecraft/" + IOHelper.urlEncode(username));
            JsonObject uuidResponse = MojangAuthProvider.makeMojangRequest(uuidURL, null);
            if (uuidResponse == null) {
                throw new IllegalArgumentException("Empty UUID response");
            }
            String uuidResolved = uuidResponse.get("id").asString();

            // Obtain player profile
            URL profileURL = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidResolved);
            JsonObject profileResponse = MojangAuthProvider.makeMojangRequest(profileURL, null);
            if (profileResponse == null) {
                throw new IllegalArgumentException("Empty Mojang response");
            }
            JsonArray properties = (JsonArray) profileResponse.get("properties");
            if (properties == null) {
                LogHelper.subDebug("No properties");
                return cache(username, null, null, null);
            }

            // Find textures property
            JsonObject texturesProperty = null;
            for (JsonValue property : properties) {
                JsonObject property0 = property.asObject();
                if (property0.get("name").asString().equals("textures")) {
                    byte[] asBytes = Base64.getDecoder().decode(property0.get("value").asString());
                    String asString = new String(asBytes, StandardCharsets.UTF_8);
                    texturesProperty = Json.parse(asString).asObject();
                    break;
                }
            }
            if (texturesProperty == null) {
                LogHelper.subDebug("No textures property");
                return cache(username, null, null, null);
            }

            // Extract skin&cloak texture
            texturesProperty = (JsonObject) texturesProperty.get("textures");
            JsonObject skinProperty = (JsonObject) texturesProperty.get("SKIN");
            Texture skinTexture = skinProperty == null ? null : new Texture(skinProperty.get("url").asString(), false);
            JsonObject cloakProperty = (JsonObject) texturesProperty.get("CAPE");
            Texture cloakTexture = cloakProperty == null ? null : new Texture(cloakProperty.get("url").asString(), true);

            // We're done
            return cache(username, skinTexture, cloakTexture, null);
        } catch (Throwable exc) {
            cache(username, null, null, exc);
            JVMHelper.UNSAFE.throwException(exc);
        }

        // We're dones
        return result;
    }

    private CacheData cache(String username, Texture skin, Texture cloak, Throwable exc) {
        long until = CACHE_DURATION_MS == 0L ? Long.MIN_VALUE : System.currentTimeMillis() + CACHE_DURATION_MS;
        CacheData data = exc == null ? new CacheData(skin, cloak, until) : new CacheData(exc, until);
        if (CACHE_DURATION_MS != 0L) {
            cache.put(username, data);
        }
        return data;
    }

    private static final class CacheData {
        private final Texture skin, cloak;
        private final Throwable exc;
        private final long until;

        private CacheData(Texture skin, Texture cloak, long until) {
            this.skin = skin;
            this.cloak = cloak;
            this.until = until;
            exc = null;
        }

        private CacheData(Throwable exc, long until) {
            this.exc = exc;
            this.until = until;
            skin = cloak = null;
        }
    }
}
