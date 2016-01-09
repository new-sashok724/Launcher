package net.sashok724.launcher.server.auth.provider;

import java.io.IOException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sashok724.launcher.client.helper.CommonHelper;
import net.sashok724.launcher.client.helper.IOHelper;
import net.sashok724.launcher.client.serialize.config.entry.BlockConfigEntry;
import net.sashok724.launcher.client.serialize.config.entry.StringConfigEntry;
import net.sashok724.launcher.server.auth.AuthException;

public final class RequestAuthProvider extends AuthProvider {
	private final String url;
	private final Pattern response;

	public RequestAuthProvider(BlockConfigEntry block) {
		super(block);
		url = block.getEntryValue("url", StringConfigEntry.class);
		response = Pattern.compile(block.getEntryValue("net/sashok724/launcher/server/response", StringConfigEntry.class));

		// Verify is valid URL
		IOHelper.verifyURL(getFormattedURL("urlAuthLogin", "urlAuthPassword"));
	}

	@Override
	public String auth(String login, String password) throws IOException {
		String currentResponse = IOHelper.request(new URL(getFormattedURL(login, password)));

		// Match username
		Matcher matcher = response.matcher(currentResponse);
		if (!matcher.matches() || matcher.groupCount() < 1) {
			throw new AuthException(currentResponse);
		}
		return matcher.group("username");
	}

	@Override
	public void close() {
		// Do nothing
	}

	private String getFormattedURL(String login, String password) {
		return CommonHelper.replace(url, "login", IOHelper.urlEncode(login), "password", IOHelper.urlEncode(password));
	}
}
