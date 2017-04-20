package com.mojang.authlib.minecraft;

public final class MinecraftProfileTexture {
    public static final int PROFILE_TEXTURE_COUNT = Type.values().length;

    // Instance
    private final String url;
    private final String hash;

    public MinecraftProfileTexture(String url, String hash) {
        this.url = url;
        this.hash = hash;
    }

    @Override
    public String toString() {
        return String.format("MinecraftProfileTexture{url='%s',hash=%s}", url, hash);
    }

    @SuppressWarnings("unused")
    public String getHash() {
        return hash;
    }

    @SuppressWarnings({ "unused", "SameReturnValue" })
    public String getMetadata(String key) {
        return null;
    }

    @SuppressWarnings("unused")
    public String getUrl() {
        return url;
    }

    public enum Type {
        SKIN,
        CAPE,
        ELYTRA
    }
}
