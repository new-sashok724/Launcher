package com.mojang.authlib.yggdrasil;

import java.util.UUID;

import launcher.client.ClientLauncher;
import launcher.client.PlayerProfile;
import launcher.helper.SecurityHelper;

public final class CompatProfile {
    public final UUID uuid;
    public final String uuidHash, username;
    public final String skinURL, skinHash;
    public final String cloakURL, cloakHash;

    public CompatProfile(UUID uuid, String username, String skinURL, String skinHash, String cloakURL, String cloakHash) {
        this.uuid = uuid;
        this.uuidHash = ClientLauncher.toHash(uuid);
        this.username = username;
        this.skinURL = skinURL;
        this.skinHash = skinHash;
        this.cloakURL = cloakURL;
        this.cloakHash = cloakHash;
    }

    public static CompatProfile fromPlayerProfile(PlayerProfile profile) {
        return profile == null ? null : new CompatProfile(profile.uuid, profile.username,
            profile.skin == null ? null : profile.skin.url,
            profile.skin == null ? null : SecurityHelper.toHex(profile.skin.digest),
            profile.cloak == null ? null : profile.cloak.url,
            profile.cloak == null ? null : SecurityHelper.toHex(profile.cloak.digest)
        );
    }
}
