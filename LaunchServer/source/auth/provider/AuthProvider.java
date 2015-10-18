package launchserver.auth.provider;

import java.io.Flushable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import launcher.LauncherAPI;
import launcher.helper.VerifyHelper;
import launcher.serialize.config.ConfigObject;
import launcher.serialize.config.entry.BlockConfigEntry;

public abstract class AuthProvider extends ConfigObject implements Flushable {
	private static final Map<String, Adapter<AuthProvider>> AUTH_PROVIDERS = new ConcurrentHashMap<>(8);

	@LauncherAPI
	protected AuthProvider(BlockConfigEntry block) {
		super(block);
	}

	@LauncherAPI
	public abstract String auth(String login, String password) throws Exception;

	@LauncherAPI
	public static AuthProvider newProvider(String name, BlockConfigEntry block) {
		VerifyHelper.verifyIDName(name);
		Adapter<AuthProvider> authHandlerAdapter = VerifyHelper.getMapValue(AUTH_PROVIDERS, name,
			String.format("Unknown auth provider: '%s'", name));
		return authHandlerAdapter.convert(block);
	}

	@LauncherAPI
	public static void registerProvider(String name, Adapter<AuthProvider> adapter) {
		VerifyHelper.verify(AUTH_PROVIDERS.putIfAbsent(name, Objects.requireNonNull(adapter, "adapter")),
			a -> a == null, String.format("Auth provider has been already registered: '%s'", name));
	}

	static {
		registerProvider("null", NullAuthProvider::new);
		registerProvider("accept", AcceptAuthProvider::new);
		registerProvider("reject", RejectAuthProvider::new);
		registerProvider("mysql", MySQLAuthProvider::new);
		registerProvider("http", HTTPAuthProvider::new);
		registerProvider("file", FileAuthProvider::new);
	}
}
