package launcher.serialize.stream;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import launcher.LauncherAPI;
import launcher.helper.VerifyHelper;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.stream.EnumSerializer.Itf;

public final class EnumSerializer<E extends Enum<?> & Itf> {
	private final Map<Integer, E> map = new HashMap<>(16);

	@LauncherAPI
	public EnumSerializer(Class<E> clazz) {
		for (Field field : clazz.getFields()) {
			if (!field.isEnumConstant()) {
				continue;
			}

			// Add to map
			Itf itf;
			try {
				itf = (Itf) field.get(null);
			} catch (IllegalAccessException e) {
				throw new InternalError(e);
			}
			map.put(itf.getNumber(), clazz.cast(itf));
		}
	}

	@LauncherAPI
	public E read(HInput input) throws IOException {
		int n = input.readVarInt();
		return VerifyHelper.getMapValue(map, n, "Unknown enum number: " + n);
	}

	@LauncherAPI
	public static void write(HOutput output, Itf itf) throws IOException {
		output.writeVarInt(itf.getNumber());
	}

	@FunctionalInterface
	public interface Itf {
		@LauncherAPI
		int getNumber();
	}
}
