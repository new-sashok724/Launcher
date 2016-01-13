package launcher.request;

import launcher.Launcher;
import launcher.LauncherAPI;
import launcher.helper.VerifyHelper;
import launcher.transport.HInput;
import launcher.transport.HOutput;

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
