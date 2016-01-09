package net.sashok724.launcher.client.serialize.stream;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import net.sashok724.launcher.client.LauncherAPI;
import net.sashok724.launcher.client.helper.VerifyHelper;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;
import net.sashok724.launcher.client.serialize.stream.EnumSerializer.Itf;

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
			VerifyHelper.putIfAbsent(map, itf.getNumber(), clazz.cast(itf),
				"Duplicate number for enum constant " + field.getName());
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
