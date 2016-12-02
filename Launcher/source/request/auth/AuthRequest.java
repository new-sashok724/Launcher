package launcher.request.auth;

import java.io.IOException;
import java.util.Arrays;

import launcher.Launcher;
import launcher.Launcher.Config;
import launcher.LauncherAPI;
import launcher.client.PlayerProfile;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.request.Request;
import launcher.request.auth.AuthRequest.Result;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;

public final class AuthRequest extends Request<Result> {
    private final String login;
    private final byte[] encryptedPassword;

    @LauncherAPI
    public AuthRequest(Config config, String login, byte[] encryptedPassword) {
        super(config);
        this.login = VerifyHelper.verify(login, VerifyHelper.NOT_EMPTY, "Login can't be empty");
        this.encryptedPassword = Arrays.copyOf(encryptedPassword, encryptedPassword.length);
    }

    @LauncherAPI
    public AuthRequest(String login, byte[] encryptedPassword) {
        this(null, login, encryptedPassword);
    }

    @Override
    public Type getType() {
        return Type.AUTH;
    }

    @Override
    protected Result requestDo(HInput input, HOutput output) throws IOException {
        output.writeString(login, 255);
        output.writeByteArray(encryptedPassword, SecurityHelper.CRYPTO_MAX_LENGTH);
        output.flush();

        // Read UUID and access token
        readError(input);
        PlayerProfile pp = new PlayerProfile(input);
        String accessToken = input.readASCII(-SecurityHelper.TOKEN_STRING_LENGTH);
        return new Result(pp, accessToken);
    }

    public static final class Result {
        @LauncherAPI public final PlayerProfile pp;
        @LauncherAPI public final String accessToken;

        private Result(PlayerProfile pp, String accessToken) {
            this.pp = pp;
            this.accessToken = accessToken;
        }
    }
}
