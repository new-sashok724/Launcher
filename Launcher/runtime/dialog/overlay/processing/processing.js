var processing = {
	overlay: null, spinner: null, description: null,
	processingImage: null, errorImage: null,

	initOverlay: function() {
		processing.overlay = loadFXML("dialog/overlay/processing/processing.fxml");
		
		// Lookup nodes
		processing.spinner = processing.overlay.lookup("#spinner");
		processing.description = processing.overlay.lookup("#description");

		// Set images
		processing.processingImage = new javafx.scene.image.Image(
			Launcher.getResourceURL("dialog/overlay/processing/spinner.gif").toString());
		processing.errorImage = new javafx.scene.image.Image(
			Launcher.getResourceURL("dialog/overlay/processing/error.png").toString());
	},

	resetOverlay: function() {
		processing.spinner.setImage(processing.processingImage);
		processing.description.getStyleClass().remove("error");
		processing.description.setText("...");
	},

	setError: function(e) {
		LogHelper.error(e);
		processing.description.textProperty().unbind();
		processing.spinner.setImage(processing.errorImage);
		processing.description.getStyleClass().add("error");
		processing.description.setText(e.toString());
	},

	setTaskProperties: function(task, callback, hide) {
		processing.description.textProperty().bind(task.messageProperty());
		task.setOnFailed(function(event) {
			processing.description.textProperty().unbind();
			processing.setError(task.getException());
			if(hide) {
				overlay.hide(2500, null);
			}
		});
		task.setOnSucceeded(function(event) {
			processing.description.textProperty().unbind();
			if(callback !== null) {
				callback(task.getValue());
			}
		});
	}
};

/* Export functions */
function makeLauncherRequest(callback) {
	var task = newRequestTask(new LauncherRequest());
	processing.setTaskProperties(task, callback, false);
	task.updateMessage("Обновление списка серверов");
	startTask(task);
}

function makeAuthRequest(username, rsaPassword, callback) {
	var task = newRequestTask(new AuthRequest(username, rsaPassword));
	processing.setTaskProperties(task, callback, true);
	task.updateMessage("Авторизация на сервере");
	startTask(task);
}

function launchClient(jvmDir, jvmHDir, clientHDir, profile, params, callback) {
	var task = newTask(function() ClientLauncher.launch(jvmDir, jvmHDir,
		clientHDir, profile, params, LogHelper.isDebugEnabled()));
	processing.setTaskProperties(task, callback, true);
	task.updateMessage("Запуск выбранного клиента");
	startTask(task);
}
