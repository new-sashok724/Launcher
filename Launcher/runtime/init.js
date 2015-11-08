var app, stage, scene, jvmDirName;

// Engine scripts (API should be imported through static link)
launcher.loadScript(LauncherClass.static.getResourceURL("engine/api.js"));
launcher.loadScript(Launcher.getResourceURL("config.js"));

// Dialog scripts
launcher.loadScript(Launcher.getResourceURL("dialog/dialog.js"));

// Override application class
var LauncherApp = Java.extend(JSApplication, {
	init: function() {
		app = JSApplication.getInstance();
		cliParams.init(app.getParameters());
		settings.load();
	}, start: function(primaryStage) {
		stage = primaryStage;
		stage.setResizable(false);
		stage.setTitle(config.title);

		// Set icons
		for each (var icon in config.icons) {
			var iconURL = Launcher.getResourceURL(icon).toString();
			stage.getIcons().add(new javafx.scene.image.Image(iconURL));
		}

		// Load dialog FXML
		rootPane = loadFXML("dialog/dialog.fxml");
		initDialog();

		// Set scene
		scene = new javafx.scene.Scene(rootPane);
		stage.setScene(scene);

		// Center and show stage
		stage.sizeToScene();
		stage.centerOnScreen();
		stage.show();
	}, stop: function() {
		settings.save();
	}
});

// Helper functions
function loadFXML(name) {
	var loader = new javafx.fxml.FXMLLoader(Launcher.getResourceURL(name));
	loader.setCharset(IOHelper.UNICODE_CHARSET);
	return loader.load();
}

function setRootParent(parent) {
	scene.setRoot(parent);
}

// Start function - there all begins
function start(args) {
	// Set JVM dir name
	LogHelper.debug("Setting JVM dir name");
	switch (JVMHelper.OS_TYPE) {
		case JVMHelperOS.MUSTDIE: jvmDirName = JVMHelper.OS_BITS === 32 ? config.jvmMustdie32Dir : // 32-bit Mustdie
			jvmDirName = JVMHelper.OS_BITS === 64 ? config.jvmMustdie64Dir : config.jvmUnknownDir; break; // 64-bit Mustdie
		case JVMHelperOS.LINUX: jvmDirName = JVMHelper.OS_BITS === 32 ? config.jvmLinux32Dir : // 32-bit Linux
			jvmDirName = JVMHelper.OS_BITS === 64 ? config.jvmLinux64Dir : config.jvmUnknownDir; break; // 64-bit Linux
		case JVMHelperOS.MACOSX: jvmDirName = JVMHelper.OS_BITS === 64 ? config.jvmMacOSXDir : config.jvmUnknownDir; break; // 64-bit MacOSX
		default: jvmDirName = config.jvmUnknownDir; LogHelper.warning("Unknown OS: '%s'", JVMHelper.OS_TYPE.name); break; // Unknown OS
	}

	// Set font rendering properties
	LogHelper.debug("Setting FX properties");
	java.lang.System.setProperty("prism.lcdtext", "false");

	// Start laucher JavaFX stage
	LogHelper.debug("Launching JavaFX application");
	javafx.application.Application.launch(LauncherApp.class, args);
}
