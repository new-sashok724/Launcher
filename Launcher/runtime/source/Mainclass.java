package launcher.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SignatureException;
import java.util.Properties;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.runtime.dialog.DialogController;
import launcher.runtime.storage.Settings;
import launcher.serialize.HInput;

public final class Mainclass extends Application {
	public static final Properties CONFIG = new Properties();
	public static final Path DIR;
	public static final Settings SETTINGS;

	public Mainclass() {
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		primaryStage.setResizable(false);
		primaryStage.setTitle(CONFIG.getProperty("window.title", "sashok724's Launcher"));
		primaryStage.setAlwaysOnTop(Boolean.parseBoolean(CONFIG.getProperty("window.alwaysOnTop")));

		// Set icon if specified
		String icon = CONFIG.getProperty("window.icon", "");
		if (!icon.isEmpty()) {
			primaryStage.getIcons().add(new Image(IOHelper.getResourceURL(icon).toString()));
		}

		// Set dialog controller and scene
		DialogController controller = new DialogController(this, primaryStage);
		primaryStage.setScene(new Scene(controller.loadDialog()));

		// Fix and show stage
		primaryStage.sizeToScene();
		primaryStage.centerOnScreen();
		primaryStage.show();
	}

	public static void main(String... args) throws Exception {
		LogHelper.debug("Settings JavaFX properties");
		System.setProperty("prism.lcdtext", "false");

		// Start application
		LogHelper.debug("Starting JavaFX application");
		Application.launch(Mainclass.class, args);
	}

	static {
		// Load launcher config
		try (BufferedReader reader = IOHelper.newReader(IOHelper.getResourceURL("launcher/runtime/config.properties"))) {
			CONFIG.load(reader);
		} catch (IOException e) {
			throw new RuntimeException("Can't load config.properties", e);
		}

		// Resolve and create dir
		DIR = IOHelper.HOME_DIR.resolve(CONFIG.getProperty("dir", "launcher"));
		if (!IOHelper.isDir(DIR)) {
			try {
				Files.createDirectory(DIR);
			} catch (IOException e) {
				LogHelper.error(e);
			}
		}

		// Load settings
		Settings settings;
		try (HInput input = new HInput(IOHelper.newInput(DIR.resolve("settings.bin")))) {
			settings = new Settings(input);
		} catch (IOException | SignatureException e) {
			settings = new Settings();
		}
		SETTINGS = settings;
	}
}
