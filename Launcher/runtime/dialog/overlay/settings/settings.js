var settings = {
	file: dir.resolve("settings.bin"), // Settings file
	login: null, rsaPassword: null, profile: 0, // Auth
	autoEnter: false, fullScreen: false, ram: 0, // Client

	/* Settings and overlay functions */
	load: function() {
		LogHelper.debug("Loading settings file");
		try {
			tryWithResources(new HInput(IOHelper.newInput(settings.file)), settings.read);
		} catch(e) {
			LogHelper.error(e);
			settings.setDefault();
		}
	},

	save: function() {
		LogHelper.debug("Saving settings file");
		try {
			tryWithResources(new HOutput(IOHelper.newOutput(settings.file)), settings.write);
		} catch(e) {
			LogHelper.error(e);
		}
	},

	// Internal functions
	read: function(input) {
		var magic = input.readInt();
		if(magic != config.settingsMagic) {
			throw new java.io.IOException("Settings magic mismatch: " + java.lang.Integer.toString(magic, 16));
		}
		
		// Launcher settings
		var debug = input.readBoolean();
		if(!LogHelper.isDebugEnabled() && debug) {
			LogHelper.setDebugEnabled(true);
		}

		// Auth settings
		settings.login = input.readBoolean() ? input.readString(255) : null;
		settings.rsaPassword = input.readBoolean() ? input.readByteArray(IOHelper.BUFFER_SIZE) : null;
		settings.profile = input.readLength(0);

		// Client settings
		settings.autoEnter = input.readBoolean();
		settings.fullScreen = input.readBoolean();
		settings.setRAM(input.readLength(0));

		// Apply CLI params
		cliParams.applySettings();
	},

	write: function(output) {
		output.writeInt(config.settingsMagic);
		
		// Launcher settings
		output.writeBoolean(LogHelper.isDebugEnabled());

		// Auth settings
		output.writeBoolean(settings.login !== null);
		if(settings.login !== null) {
			output.writeString(settings.login, 255);
		}
		output.writeBoolean(settings.rsaPassword !== null);
		if(settings.rsaPassword !== null) {
			output.writeByteArray(settings.rsaPassword, IOHelper.BUFFER_SIZE);
		}
		output.writeLength(settings.profile, 0);

		// Client settings
		output.writeBoolean(settings.autoEnter);
		output.writeBoolean(settings.fullScreen);
		output.writeLength(settings.ram, JVMHelper.RAM);
	},

	setDefault: function() {
		// Auth settings
		settings.login = null;
		settings.rsaPassword = null;
		settings.profile = 0;

		// Client settings
		settings.autoEnter = config.autoEnterDefault;
		settings.fullScreen = config.fullScreenDefault;
		settings.setRAM(config.ramDefault);

		// Apply CLI params
		cliParams.applySettings();
	},

	setPassword: function(password) {
		var encrypted = SecurityHelper.newRSAEncryptCipher(Launcher.getConfig().publicKey).doFinal(IOHelper.encode(password));
		settings.password = encrypted;
		return encrypted;
	},

	setRAM: function(ram) {
		settings.ram = java.lang.Math["min(int,int)"](((ram / 256) | 0) * 256, JVMHelper.RAM);
	},

	/* ===================== OVERLAY ===================== */
	overlay: null, ramLabel: null, dirLabel: null,
	deleteDirPressedAgain: false,

	initOverlay: function() {
		settings.overlay = loadFXML("dialog/overlay/settings/settings.fxml");

		// Lookup autoEnter checkbox
		var autoEnterBox = settings.overlay.lookup("#autoEnter");
		autoEnterBox.setSelected(settings.autoEnter);
		autoEnterBox.selectedProperty()["addListener(javafx.beans.value.ChangeListener)"](
			function(o, ov, nv) settings.autoEnter = nv);

		// Lookup fullScreen checkbox
		var fullScreenBox = settings.overlay.lookup("#fullScreen");
		fullScreenBox.setSelected(settings.fullScreen);
		fullScreenBox.selectedProperty()["addListener(javafx.beans.value.ChangeListener)"](
			function(o, ov, nv) settings.fullScreen = nv);

		// Lookup RAM label
		settings.ramLabel = settings.overlay.lookup("#ramLabel");
		settings.updateRAMLabel();

		// Lookup RAM slider options
		var ramSlider = settings.overlay.lookup("#ramSlider");
		ramSlider.setMin(0);
		ramSlider.setMax(JVMHelper.RAM);
		ramSlider.setSnapToTicks(true);
		ramSlider.setShowTickMarks(true);
		ramSlider.setShowTickLabels(true);
		ramSlider.setMinorTickCount(3);
		ramSlider.setMajorTickUnit(1024);
		ramSlider.setBlockIncrement(1024);
		ramSlider.setValue(settings.ram);
		ramSlider.valueProperty()["addListener(javafx.beans.value.ChangeListener)"](function(o, ov, nv) {
			settings.setRAM(nv);
			settings.updateRAMLabel();
		});

		// Lookup dir label
		settings.dirLabel = settings.overlay.lookup("#dirLabel");
		settings.updateDirLabel();

		// Lookup open dir button
		settings.overlay.lookup("#openDir").setOnAction(function(event)
			app.getHostServices().showDocument(updatesDir.toUri()));

		// Lookup delete dir button
		var deleteDirButton = settings.overlay.lookup("#deleteDir");
		deleteDirButton.setOnAction(function(event) {
			if(!settings.deleteDirPressedAgain) {
				settings.deleteDirPressedAgain = true;
				deleteDirButton.setText("Подтвердить вменяемость");
				return;
			}

			// Delete dir!
			settings.deleteUpdatesDir();
			settings.deleteDirPressedAgain = false;
			deleteDirButton.setText("Ещё раз попробовать");
		});
		
		// Lookup debug checkbox
		var debugBox = settings.overlay.lookup("#debug");
		debugBox.setSelected(LogHelper.isDebugEnabled());
		debugBox.selectedProperty()["addListener(javafx.beans.value.ChangeListener)"](
			function(o, ov, nv) LogHelper.setDebugEnabled(nv));

		// Lookup apply settings button
		settings.overlay.lookup("#apply").setOnAction(function(event) overlay.hide(0, null));
	},

	updateRAMLabel: function() {
		settings.ramLabel.setText(settings.ram <= 0 ? "Автоматически" : settings.ram + " MiB");
	},

	deleteUpdatesDir: function() {
		processing.description.setText("Удаление директории загрузок");
		overlay.swap(0, processing.overlay, function(event) {
			var task = newTask(function() IOHelper.deleteDir(updatesDir, false));
			task.setOnSucceeded(function(event) overlay.swap(0, settings.overlay, null));
			task.setOnFailed(function(event) {
				processing.setError(task.getException());
				overlay.swap(2500, settings.overlay, null);
			});
			startTask(task);
		});
	},

	updateDirLabel: function() {
		settings.dirLabel.setText(IOHelper.toString(updatesDir));
	}
};

/* ====================== CLI PARAMS ===================== */
var cliParams = {
	login: null, password: null, profile: -1, autoLogin: false, // Auth
	autoEnter: null, fullScreen: null, ram: -1, // Client

	init: function(params) {
		var named = params.getNamed();
		var unnamed = params.getUnnamed();

		// Set auth cli params
		cliParams.login = named.get("login");
		cliParams.password = named.get("password");
		if(named.containsKey("profile")) {
			cliParams.profile = java.lang.Integer.parseUnsignedInt(named.get("profile"));
		}
		cliParams.autoLogin = unnamed.contains("--autoLogin");

		// Set client cli params
		if(named.containsKey("autoEnter")) {
			cliParams.autoEnter = java.lang.Boolean.parseBoolean(named.get("autoEnter"));
		}
		if(named.containsKey("fullScreen")) {
			cliParams.fullScreen = java.lang.Boolean.parseBoolean(named.get("fullScreen"));
		}
		if(named.containsKey("ram")) {
			cliParams.ram = java.lang.Integer.parseUnsignedInt(named.get("ram"));
		}
	},

	applySettings: function() {
		// Update auth settings
		if(cliParams.login !== null) {
			settings.login = cliParams.login;
		}
		if(cliParams.password !== null) {
			settings.setPassword(cliParams.password);
		}
		if(cliParams.profile >= 0) {
			settings.profile = cliParams.profile;
		}

		// Update client settings
		if(cliParams.autoEnter !== null) {
			settings.autoLogin = cliParams.autoEnter;
		}
		if(cliParams.fullScreen !== null) {
			settings.fullScreen = cliParams.fullScreen;
		}
		if(cliParams.ram >= 0) {
			settings.setRAM(cliParams.ram);
		}
	}
};
