package launcher.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.runtime.dialog.DialogController;

public final class Mainclass extends Application {
	public static final Properties PROPERTIES = new Properties();
	public static final Path DIR = IOHelper.HOME_DIR.resolve(IOHelper.toPath(
		PROPERTIES.getProperty("dir", "launcher")));

	public Mainclass() {
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		primaryStage.setResizable(false);
		primaryStage.setTitle(PROPERTIES.getProperty("window.title", "* Missing title *"));
		primaryStage.setAlwaysOnTop(Boolean.parseBoolean(PROPERTIES.getProperty("window.alwaysOnTop")));

		// Set icon if specified
		String icon = PROPERTIES.getProperty("window.icon", "");
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
		try (BufferedReader reader = IOHelper.newReader(IOHelper.getResourceURL("launcher/runtime/config.properties"))) {
			PROPERTIES.load(reader);
		} catch (IOException e) {
			throw new RuntimeException("Can't load config.properties", e);
		}
	}
}
