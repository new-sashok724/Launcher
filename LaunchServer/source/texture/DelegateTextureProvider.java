package launchserver.texture;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import launcher.LauncherAPI;
import launcher.client.PlayerProfile.Texture;
import launcher.helper.VerifyHelper;
import launcher.serialize.config.entry.BlockConfigEntry;

public class DelegateTextureProvider extends TextureProvider {
    private volatile TextureProvider delegate;

    public DelegateTextureProvider(BlockConfigEntry block) {
        super(block);
    }

    @Override
    public void close() throws IOException {
        TextureProvider delegate = this.delegate;
        if (delegate != null) {
            delegate.close();
        }
    }

    @Override
    public Texture getCloakTexture(UUID uuid, String username) throws IOException {
        return getDelegate().getCloakTexture(uuid, username);
    }

    @Override
    public Texture getSkinTexture(UUID uuid, String username) throws IOException {
        return getDelegate().getSkinTexture(uuid, username);
    }

    @LauncherAPI
    public void setDelegate(TextureProvider delegate) {
        this.delegate = delegate;
    }

    private TextureProvider getDelegate() {
        return VerifyHelper.verify(delegate, Objects::nonNull, "Delegate texture provider wasn't set");
    }
}
