package launchserver.auth.provider;

import java.io.IOException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import launcher.helper.CommonHelper;
import launcher.helper.IOHelper;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;

public final class RequestAuthProvider extends AuthProvider {
    private final String url;
    private final Pattern response;

    public RequestAuthProvider(BlockConfigEntry block) {
        super(block);
        url = block.getEntryValue("url", StringConfigEntry.class);
        response = Pattern.compile(block.getEntryValue("response", StringConfigEntry.class));

        // Verify is valid URL
        IOHelper.verifyURL(getFormattedURL("urlAuthLogin", "urlAuthPassword", "urlAuthIP"));
    }

    @Override
    public String auth(String login, String password, String ip) throws IOException {
        String currentResponse = IOHelper.request(new URL(getFormattedURL(login, password, ip)));

        // Match username
        Matcher matcher = response.matcher(currentResponse);
        return matcher.matches() && matcher.groupCount() >= 1 ?
            matcher.group("username") : authError(currentResponse);
    }

    @Override
    public void close() {
        // Do nothing
    }

    private String getFormattedURL(String login, String password, String ip) {
        return CommonHelper.replace(url, "login", IOHelper.urlEncode(login), "password", IOHelper.urlEncode(password), "ip", IOHelper.urlEncode(ip));
    }
}
