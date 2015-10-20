package launcher.request;

import launcher.Launcher;
import launcher.helper.VerifyHelper;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;

public abstract class CustomRequest<T> extends Request<T> {
	public CustomRequest(Launcher.Config config) {
		super(config);
	}

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

	public abstract String getName();

	protected abstract T requestDoCustom(HInput input, HOutput output);
}
