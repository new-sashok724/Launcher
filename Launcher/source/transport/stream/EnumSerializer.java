package launcher.transport.stream;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import launcher.LauncherAPI;
import launcher.helper.VerifyHelper;
import launcher.transport.HInput;
import launcher.transport.HOutput;
import launcher.transport.stream.EnumSerializer.Serializable;

public final class EnumSerializer<E extends Enum<?> & Serializable> {
	private final Map<Integer, E> map = new HashMap<>(16);

	@LauncherAPI
	public EnumSerializer(Class<E> clazz) {
		for (Field field : clazz.getFields()) {
			if (!field.isEnumConstant()) {
				continue;
			}

			// Get enum value
			Serializable value;
			try {
				value = (Serializable) field.get(null);
			} catch (IllegalAccessException e) {
				throw new InternalError(e);
			}

			// Put value into map
			VerifyHelper.putIfAbsent(map, value.getNumber(), clazz.cast(value),
				"Duplicate number for enum constant " + field.getName());
		}
	}

	@LauncherAPI
	public E read(HInput input) throws IOException {
		int n = input.readVarInt();
		return VerifyHelper.getMapValue(map, n, "Unknown enum number: " + n);
	}

	@LauncherAPI
	public static void write(HOutput output, Serializable value) throws IOException {
		output.writeVarInt(value.getNumber());
	}

	@FunctionalInterface
	public interface Serializable {
		@LauncherAPI
		int getNumber();
	}
}
