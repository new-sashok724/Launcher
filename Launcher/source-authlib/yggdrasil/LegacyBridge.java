package com.mojang.authlib.yggdrasil;

import java.util.HashMap;
import java.util.Map;

import launcher.LauncherAPI;
import launcher.client.ClientLauncher;
import launcher.client.PlayerProfile;
import launcher.helper.CommonHelper;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.request.auth.CheckServerRequest;
import launcher.request.auth.JoinServerRequest;

@LauncherAPI
public final class LegacyBridge {
    private LegacyBridge() {
    }

    @SuppressWarnings("unused")
    public static boolean checkServer(String username, String serverID) throws Exception {
        LogHelper.debug("LegacyBridge.checkServer, Username: '%s', Server ID: %s", username, serverID);
        return new CheckServerRequest(username, serverID).request() != null;
    }

    @SuppressWarnings("unused") // Result is user properties (Used by BungeeCord)
    public static Map<String, String> checkServerWithProperties(String username, String serverID) throws Exception {
        PlayerProfile pp = new CheckServerRequest(username, serverID).request();
        if (pp == null) {
            return null;
        }

        // Add properties
        Map<String, String> properties = new HashMap<>(5);
        properties.put("uuid", pp.uuid.toString());
        properties.put("uuid-hash", ClientLauncher.toHash(pp.uuid));
        if (pp.skin != null) {
            properties.put(ClientLauncher.SKIN_URL_PROPERTY, pp.skin.url);
            properties.put(ClientLauncher.SKIN_DIGEST_PROPERTY, SecurityHelper.toHex(pp.skin.digest));
        }
        if (pp.cloak != null) {
            properties.put(ClientLauncher.SKIN_URL_PROPERTY, pp.cloak.url);
            properties.put(ClientLauncher.SKIN_DIGEST_PROPERTY, SecurityHelper.toHex(pp.cloak.digest));
        }

        // We're done
        return properties;
    }

    @SuppressWarnings("unused")
    public static String getCloakURL(String username) {
        LogHelper.debug("LegacyBridge.getCloakURL: '%s'", username);
        return CommonHelper.replace(System.getProperty("launcher.legacy.cloaksURL",
            "http://skins.minecraft.net/MinecraftCloaks/%username%.png"), "username", IOHelper.urlEncode(username));
    }

    @SuppressWarnings("unused")
    public static String getSkinURL(String username) {
        LogHelper.debug("LegacyBridge.getSkinURL: '%s'", username);
        return CommonHelper.replace(System.getProperty("launcher.legacy.skinsURL",
            "http://skins.minecraft.net/MinecraftSkins/%username%.png"), "username", IOHelper.urlEncode(username));
    }

    @SuppressWarnings("unused")
    public static String joinServer(String username, String accessToken, String serverID) {
        if (!ClientLauncher.isLaunched()) {
            return "Bad Login (Cheater)";
        }

        // Join server
        LogHelper.debug("LegacyBridge.joinServer, Username: '%s', Access token: %s, Server ID: %s", username, accessToken, serverID);
        try {
            return new JoinServerRequest(username, accessToken, serverID).request() ? "OK" : "Bad Login (Clientside)";
        } catch (Exception e) {
            return e.toString();
        }
    }
}
