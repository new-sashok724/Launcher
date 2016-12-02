package launcher.serialize.config.entry;

import java.io.IOException;

import launcher.LauncherAPI;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;

public final class StringConfigEntry extends ConfigEntry<String> {
    @LauncherAPI
    public StringConfigEntry(String value, boolean ro, int cc) {
        super(value, ro, cc);
    }

    @LauncherAPI
    public StringConfigEntry(HInput input, boolean ro) throws IOException {
        this(input.readString(0), ro, 0);
    }

    @Override
    public Type getType() {
        return Type.STRING;
    }

    @Override
    protected void uncheckedSetValue(String value) {
        super.uncheckedSetValue(value);
    }

    @Override
    public void write(HOutput output) throws IOException {
        output.writeString(getValue(), 0);
    }
}
