package launchserver.auth.provider;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import launcher.helper.IOHelper;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

public final class JsonAuthProvider extends AuthProvider {
    private static final int TIMEOUT = Integer.parseInt(
            System.getProperty("launcher.connection.timeout", Integer.toString(1500)));

    private final URL url;
    private final String userKeyName;
    private final String passKeyName;
    private final String responseUserKeyName;
    private final String responseErrorKeyName;

    JsonAuthProvider(BlockConfigEntry block) {
        super(block);
        String configUrl = block.getEntryValue("url", StringConfigEntry.class);
        userKeyName = block.getEntryValue("userKeyName", StringConfigEntry.class);
        passKeyName = block.getEntryValue("passKeyName", StringConfigEntry.class);
        responseUserKeyName = block.getEntryValue("responseUserKeyName", StringConfigEntry.class);
        responseErrorKeyName = block.getEntryValue("responseErrorKeyName", StringConfigEntry.class);
        url = IOHelper.convertToURL(configUrl);
    }

    @Override
    public String auth(String login, String password) throws IOException {
        JsonObject request = Json.object().add(userKeyName, login).add(passKeyName, password);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        if (TIMEOUT > 0) {
            connection.setConnectTimeout(TIMEOUT);
        }

        OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), Charset.forName("UTF-8"));
        writer.write(request.toString());
        writer.flush();
        writer.close();

        InputStreamReader reader = new InputStreamReader(connection.getInputStream(), "UTF-8");

        JsonValue content = Json.parse(reader);
        if (!content.isObject()) {
            return authError("Authentication server response is malformed");
        }

        JsonObject response = content.asObject();
        String value;

        if ((value = response.getString(responseUserKeyName, null)) != null) {
            return value;
        } else if ((value = response.getString(responseErrorKeyName, null)) != null) {
            return authError(value);
        } else {
            return authError("Authentication server response is malformed");
        }
    }

    @Override
    public void close() {
        // pass
    }
}
