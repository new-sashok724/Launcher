package net.sashok724.launcher.client.request;

import net.sashok724.launcher.client.Launcher;
import net.sashok724.launcher.client.LauncherAPI;
import net.sashok724.launcher.client.helper.VerifyHelper;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;

public abstract class CustomRequest<T> extends Request<T> {
	@LauncherAPI
	public CustomRequest(Launcher.Config config) {
		super(config);
	}

	@LauncherAPI
	public CustomRequest() {
		this(null);
	}

	@Override
	public final Type getType() {
		return Type.CUSTOM;
	}

	@Override
	protected final T requestDo(HInput input, HOutput output) throws Exception {
		output.writeASCII(VerifyHelper.verifyIDName(getName()), 255);
		output.flush();

		// Custom request redirect
		return requestDoCustom(input, output);
	}

	@LauncherAPI
	public abstract String getName();

	@LauncherAPI
	protected abstract T requestDoCustom(HInput input, HOutput output);
}
