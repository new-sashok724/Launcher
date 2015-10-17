package launchserver.helper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

import launcher.LauncherAPI;
import launcher.helper.IOHelper;

public final class LineReader extends BufferedReader {
	@LauncherAPI
	public LineReader(Reader in) {
		super(in, IOHelper.BUFFER_SIZE);
	}

	@Override
	public String readLine() throws IOException {
		String line;
		do {
			line = super.readLine();
			if (line == null) {
				return null;
			}

			// Trim comments
			int commentIndex = line.indexOf('#');
			if (commentIndex >= 0) {
				line = line.substring(0, commentIndex);
			}

			// Trim
			line = line.trim();
		} while (line.isEmpty());
		return line;
	}
}
