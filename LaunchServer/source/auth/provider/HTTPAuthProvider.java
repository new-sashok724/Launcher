package launchserver.auth.provider;

import java.io.IOException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import launcher.helper.CommonHelper;
import launcher.helper.IOHelper;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;
import launchserver.auth.AuthException;

public final class HTTPAuthProvider extends AuthProvider {
	private final String url;
	private final Pattern response;

	public HTTPAuthProvider(BlockConfigEntry block) {
		super(block);
		url = block.getEntryValue("url", StringConfigEntry.class);
		response = Pattern.compile(block.getEntryValue("response", StringConfigEntry.class));
	}

	@Override
	public String auth(String login, String password) throws IOException {
		String currentResponse = IOHelper.request(new URL(getFormattedURL(login, password)));
		Matcher matcher = response.matcher(currentResponse);
		if (!matcher.matches() || matcher.groupCount() < 1) {
			throw new AuthException(currentResponse);
		}
		return matcher.group("username");
	}

	@Override
	public void flush() {
		// Do nothing
	}

	@Override
	public void verify() {
		IOHelper.verifyURL(getFormattedURL("httpAuthLogin", "httpAuthPassword"));
	}

	private String getFormattedURL(String login, String password) {
		return CommonHelper.replace(url, "login", login, "password", password);
	}
}
