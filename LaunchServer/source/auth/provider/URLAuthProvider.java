package launchserver.auth.provider;

import java.io.IOException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import launcher.helper.CommonHelper;
import launcher.helper.IOHelper;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;

public final class URLAuthProvider extends AuthProvider {
	private final String url;
	private final Pattern response;

	public URLAuthProvider(BlockConfigEntry block) {
		super(block);
		url = block.getEntryValue("url", StringConfigEntry.class);
		response = Pattern.compile(block.getEntryValue("response", StringConfigEntry.class));

		// Verify is valid URL
		IOHelper.verifyURL(getFormattedURL("urlAuthLogin", "urlAuthPassword"));
	}

	@Override
	public String auth(String login, String password) throws IOException {
		String currentResponse = IOHelper.request(new URL(getFormattedURL(login, password)));

		// Match username
		Matcher matcher = response.matcher(currentResponse);
		return matcher.matches() && matcher.groupCount() >= 1 ?
			matcher.group("username") : authError(currentResponse);
	}

	@Override
	public void flush() {
		// Do nothing
	}

	private String getFormattedURL(String login, String password) {
		return CommonHelper.replace(url, "login", login, "password", password);
	}
}
