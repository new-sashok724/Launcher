package com.mojang.authlib.yggdrasil;

import java.util.UUID;

import launcher.LauncherAPI;
import launcher.client.ClientLauncher;
import launcher.client.PlayerProfile;
import launcher.helper.LogHelper;
import launcher.request.auth.CheckServerRequest;
import launcher.request.auth.JoinServerRequest;
import launcher.request.uuid.BatchProfileByUsernameRequest;
import launcher.request.uuid.ProfileByUUIDRequest;
import launcher.request.uuid.ProfileByUsernameRequest;

// Used to bypass Launcher's class name obfuscation and access API
@LauncherAPI
public final class CompatBridge {
    public static final int PROFILES_MAX_BATCH_SIZE = BatchProfileByUsernameRequest.MAX_BATCH_SIZE;

    private CompatBridge() {
    }

    @SuppressWarnings("unused")
    public static CompatProfile checkServer(String username, String serverID) throws Throwable {
        LogHelper.debug("CompatBridge.checkServer, Username: '%s', Server ID: %s", username, serverID);
        return CompatProfile.fromPlayerProfile(new CheckServerRequest(username, serverID).request());
    }

    @SuppressWarnings("unused")
    public static boolean joinServer(String username, String accessToken, String serverID) throws Throwable {
        if (!ClientLauncher.isLaunched()) {
            throw new IllegalStateException("Bad Login (Cheater)");
        }

        // Join server
        LogHelper.debug("LegacyBridge.joinServer, Username: '%s', Access token: %s, Server ID: %s", username, accessToken, serverID);
        return new JoinServerRequest(username, accessToken, serverID).request();
    }

    @SuppressWarnings("unused")
    public static CompatProfile profileByUUID(UUID uuid) throws Throwable {
        return CompatProfile.fromPlayerProfile(new ProfileByUUIDRequest(uuid).request());
    }

    @SuppressWarnings("unused")
    public static CompatProfile profileByUsername(String username) throws Throwable {
        return CompatProfile.fromPlayerProfile(new ProfileByUsernameRequest(username).request());
    }

    @SuppressWarnings("unused")
    public static CompatProfile[] profilesByUsername(String... usernames) throws Throwable {
        PlayerProfile[] profiles = new BatchProfileByUsernameRequest(usernames).request();

        // Convert profiles
        CompatProfile[] resultProfiles = new CompatProfile[profiles.length];
        for (int i = 0; i < profiles.length; i++) {
            resultProfiles[i] = CompatProfile.fromPlayerProfile(profiles[i]);
        }

        // We're dones
        return resultProfiles;
    }
}
