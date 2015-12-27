package launcher.runtime.dialog;

import java.io.IOException;
import java.net.URL;
import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionModel;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;
import launcher.client.ClientProfile;
import launcher.client.ServerPinger;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.VerifyHelper;
import launcher.runtime.Mainclass;
import launcher.runtime.dialog.overlay.SpinnerController;
import launcher.serialize.signed.SignedObjectHolder;

public final class DialogController {
	private static final ResourceBundle LOCALE = ResourceBundle.getBundle("launcher.runtime.dialog.locale");

	// Instance
	private final Map<ClientProfile, ServerPinger> pingers = new HashMap<>(16);
	private final Application app;
	private final Stage stage;

	// Overlay
	private final SpinnerController spinnerOverlay;

	// Layout
	@FXML private Pane root, layout, overlayDim, overlay;

	// Controls
	@FXML private WebView news;
	@FXML private TextField login, password;
	@FXML private CheckBox savePassword;
	@FXML private ComboBox<SignedObjectHolder<ClientProfile>> profiles;
	@FXML private Hyperlink link;

	// Action controls
	@FXML private ButtonBase play, settings;

	public DialogController(Application app, Stage stage) throws IOException {
		this.app = app;
		this.stage = stage;

		// Spinner
		spinnerOverlay = new SpinnerController(this);
	}

	public void hideOverlay(double delay, EventHandler<ActionEvent> handler) {
		fade(overlay, delay, 1.0D, 0.0D, e -> {
			overlay.setVisible(false);
			overlayDim.getChildren().remove(overlay);
			fade(overlayDim, 0.0D, 1.0D, 0.0D, e2 -> {
				overlayDim.setVisible(false);

				// Unfreeze layout
				layout.setDisable(false);
				layout.requestFocus();

				// Reset overlay state
				overlay = null;
				if (handler != null) {
					handler.handle(e2);
				}
			});
		});
	}

	public void initLauncher() {
		spinnerOverlay.reset();
		showOverlay(spinnerOverlay.getOverlay(), e -> spinnerOverlay.makeLauncherUpdateRequest(result -> {
			Mainclass.SETTINGS.setLauncherUpdateRequest(result);
			if (spinnerOverlay.isOfflineMode()) {
				initOfflineMode();
			}

			// Init profiles list and hide overlay
			setProfilesList(result.profiles);
			hideOverlay(0.0D, null);
			return null;
		}));
	}

	public void initOfflineMode() {
		stage.setTitle(stage.getTitle() + " [Offline]");

		// Set login field as username field
		login.setPromptText(LOCALE.getString("dialog.username"));
		if (!VerifyHelper.isValidUsername(Mainclass.SETTINGS.getLogin())) {
			login.setText(""); // Reset if not valid
		}

		// Disable password field
		password.setDisable(true);
		password.setPromptText(LOCALE.getString("dialog.unavailable"));
		password.setText("");

		// Switch news view to offline page
		URL offlineURL;
		try {
			offlineURL = IOHelper.getResourceURL("launcher/runtime/dialog/overlay/offline/index.html");
		} catch (NoSuchFileException e) {
			throw new RuntimeException(e);
		}
		news.getEngine().load(offlineURL.toString());
	}

	public Pane loadDialog() throws IOException {
		loadFXML(IOHelper.getResourceURL("launcher/runtime/dialog/dialog.fxml"), this);

		// Initialize webview
		news.setContextMenuEnabled(false);
		WebEngine engine = news.getEngine();
		engine.setUserDataDirectory(Mainclass.DIR.resolve("webserver").toFile());
		engine.load("https://launcher.sashok724.net/");

		// Initialize login field
		login.setOnAction(this::play);
		String loginValue = Mainclass.SETTINGS.getLogin();
		if (loginValue != null) {
			login.setText(loginValue);
		}

		// Initialize password field
		password.setOnAction(this::play);
		if (Mainclass.SETTINGS.getPassword() != null) {
			password.getStyleClass().add("hasSaved");
			password.setPromptText(LOCALE.getString("dialog.savedPassword"));
		}

		// Initialize save password checkbox
		savePassword.setSelected(Mainclass.SETTINGS.isPasswordSaved());

		// Initialize profiles combobox
		profiles.setCellFactory(ProfileListCell::new);
		profiles.setButtonCell(new ProfileListCell(null));

		// Initialize hyperlink
		link.setText(Mainclass.CONFIG.getProperty("dialog.link.text", "Missing text"));
		link.setOnAction(e -> app.getHostServices().showDocument(
			Mainclass.CONFIG.getProperty("dialog.link.url", "https://mysite.tld/")));

		// Initialize action buttons
		play.setOnAction(this::play);
		settings.setOnAction(this::settings);
		return root;
	}

	public void showOverlay(Pane newOverlay, EventHandler<ActionEvent> handler) {
		layout.setDisable(true);
		overlay = newOverlay;

		// Show dim pane
		overlayDim.setVisible(true);
		overlayDim.toFront();
		fade(overlayDim, 0.0D, 0.0D, 1.0D, e -> {
			overlayDim.requestFocus();
			overlayDim.getChildren().add(newOverlay);

			// Show overlay
			newOverlay.setVisible(true);
			fade(newOverlay, 0.0D, 0.0D, 1.0D, handler);
		});
	}

	public void swapOverlay(double delay, Pane newOverlay, EventHandler<ActionEvent> handler) {
		fade(overlay, delay, 1.0D, 0.0D, e -> {
			overlay.setVisible(false);
			overlayDim.requestFocus();

			// Swap overlays
			if (!newOverlay.equals(overlay)) {
				ObservableList<Node> children = overlayDim.getChildren();
				children.set(children.indexOf(overlay), newOverlay);
				overlay = newOverlay;
			}

			// Show new overlay
			overlay.setVisible(true);
			fade(overlay, 0.0D, 0.0D, 1.0D, handler);
		});
	}

	private void fade(Node node, double delay, double from, double to, EventHandler<ActionEvent> handler) {
		FadeTransition transition = new FadeTransition(Duration.millis(100.0D), node);
		if (handler != null) {
			transition.setOnFinished(handler);
		}

		// Launch transition
		transition.setDelay(javafx.util.Duration.millis(delay));
		transition.setFromValue(from);
		transition.setToValue(to);
		transition.play();
	}

	private void play(ActionEvent e) {
		if (overlay != null) {
			return;
		}

		// Get profile
		SelectionModel<SignedObjectHolder<ClientProfile>> sm = profiles.getSelectionModel();
		SignedObjectHolder<ClientProfile> profile = sm.getSelectedItem();
		if (profile == null) { // Verify is profile selected
			return;
		}

		// Get login
		String loginValue = login.getText();
		if (loginValue.isEmpty()) { // Verify is login specified
			return;
		}

		// Get password
		byte[] passwordValue = null;
		if (!spinnerOverlay.isOfflineMode()) {
			String passwordPlain = password.getText();
			if (!passwordPlain.isEmpty()) {
				passwordValue = Mainclass.SETTINGS.encryptPassword(passwordPlain);
			} else if (Mainclass.SETTINGS.getPassword() != null) {
				passwordValue = Mainclass.SETTINGS.getPassword();
			} else { // No password specified
				return;
			}

			// Remember or reset password
			Mainclass.SETTINGS.setPassword(savePassword.isSelected() ? passwordValue : null);
		}
		Mainclass.SETTINGS.setLogin(loginValue);

		// Show auth overlay
		spinnerOverlay.reset();
		byte[] fPassword = passwordValue;
		showOverlay(spinnerOverlay.getOverlay(), e2 -> spinnerOverlay.makeAuthRequest(loginValue, fPassword, result -> {
			System.out.println("KeK");
			return null;
		}));
	}

	private void setProfilesList(Collection<SignedObjectHolder<ClientProfile>> profilesList) {
		profiles.setItems(FXCollections.observableArrayList(profilesList));
		for (SignedObjectHolder<ClientProfile> profile : profilesList) {
			pingers.put(profile.object, new ServerPinger(
				profile.object.getServerSocketAddress(), profile.object.getVersion()));
		}

		// Set profiles seleciton model
		SelectionModel<SignedObjectHolder<ClientProfile>> sm = profiles.getSelectionModel();
		sm.selectedIndexProperty().addListener((o, ov, nv) -> Mainclass.SETTINGS.setProfileIndex(nv.intValue()));
		sm.select(Mainclass.SETTINGS.getProfileIndex(profilesList.size()));
	}

	private void settings(ActionEvent e) {
		LogHelper.debug("Settings");
	}

	public static Node loadFXML(URL url, Object controller) throws IOException {
		FXMLLoader loader = new FXMLLoader(url, LOCALE);
		loader.setCharset(IOHelper.UNICODE_CHARSET);
		loader.setController(controller);
		return loader.load();
	}

	private final class ProfileListCell extends ListCell<SignedObjectHolder<ClientProfile>> {
		@FXML private Pane cell;
		@FXML private Label title, status;
		@FXML private Circle statusCirc;

		private ProfileListCell(ListView<SignedObjectHolder<ClientProfile>> view) {
			setText(null);
			try {
				URL resourceURL = IOHelper.getResourceURL("launcher/runtime/dialog/profileCell.fxml");
				loadFXML(resourceURL, this);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		protected void updateItem(SignedObjectHolder<ClientProfile> item, boolean empty) {
			super.updateItem(item, empty);
			setGraphic(empty ? null : cell);
			if (empty) { // No need to update controls
				return;
			}

			// Update title
			title.setText(item.object.getTitle());

			// Update server status
			setServerStatus("...", Color.GREY);
			DialogTask<ServerPinger.Result> task = new DialogTask<>(() -> pingers.get(item.object).ping());
			task.setOnSucceeded(e -> {
				ServerPinger.Result result = task.getValue();
				Color color = result.isOverfilled() ? Color.YELLOW : Color.GREEN;
				setServerStatus(String.format("%d из %d", result.onlinePlayers, result.maxPlayers), color);
			});
			task.setOnFailed(e -> setServerStatus("Недоступен", Color.RED));
			task.start();
		}

		private void setServerStatus(String s, Color color) {
			status.setText(s);
			statusCirc.setFill(color);
		}
	}
}
