package net.sashok724.launcher.client.runtime;

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
import net.sashok724.launcher.client.helper.CommonHelper;
import net.sashok724.launcher.client.helper.IOHelper;
import net.sashok724.launcher.client.helper.JVMHelper;
import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.client.runtime.dialog.DialogController;
import net.sashok724.launcher.client.runtime.storage.Settings;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;

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
		primaryStage.setScene(new Scene(controller.getRoot()));

		// Fix and show stage
		primaryStage.sizeToScene();
		primaryStage.centerOnScreen();
		primaryStage.show();

		// Initialize launcher
		controller.initLauncher();
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
		LogHelper.debug("Loading runtime config file");
		try (BufferedReader reader = IOHelper.newReader(IOHelper.getResourceURL("net.sashok724.launcher.client.runtime/config.properties"))) {
			CONFIG.load(reader);
		} catch (IOException e) {
			throw new RuntimeException("Can't load config.properties", e);
		}

		// Resolve and create dir
		LogHelper.debug("Resolving runtime directory");
		DIR = IOHelper.HOME_DIR.resolve(CONFIG.getProperty("dir", "launcher"));
		if (!IOHelper.isDir(DIR)) {
			LogHelper.subDebug("Creating runtime directory");
			try {
				Files.createDirectory(DIR);
			} catch (IOException e) {
				LogHelper.error(e);
			}
		}

		// Load settings
		LogHelper.debug("Loading settings file");
		Settings settings;
		Path settingsFile = DIR.resolve("settings.bin");
		try (HInput input = new HInput(IOHelper.newInput(settingsFile))) {
			settings = new Settings(input);
		} catch (IOException | SignatureException e) {
			LogHelper.error(e);
			settings = new Settings();
		}
		SETTINGS = settings;

		// Set settings shutdown hook
		Settings finalSettings = settings;
		JVMHelper.RUNTIME.addShutdownHook(CommonHelper.newThread("Settings Save Thread", false, () -> {
			LogHelper.debug("Saving settings file");
			try (HOutput output = new HOutput(IOHelper.newOutput(settingsFile))) {
				finalSettings.write(output);
			} catch (IOException e) {
				LogHelper.error(e);
			}
		}));
	}
}
